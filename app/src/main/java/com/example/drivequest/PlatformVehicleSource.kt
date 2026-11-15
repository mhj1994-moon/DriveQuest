package com.example.drivequest

import android.Manifest
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Official platform APIs only. No console, network, interpolation, or background collection. */
class PlatformVehicleSource(
    private val context: Context,
    private val speed: (ReceivedValue<Double>?, String) -> Unit,
    private val position: (ReceivedValue<VehiclePosition>?, String) -> Unit
) {
    companion object { const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED" }
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private val locations = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null
    private var speedListener: CarPropertyManager.CarPropertyEventCallback? = null
    @Volatile private var generation = 0
    private val main = Handler(Looper.getMainLooper())
    private var speedReader: ScheduledExecutorService? = null
    private var latestSpeedTimestamp = -1L

    private fun acceptSpeed(value: CarPropertyValue<*>, token: Int) {
        if (generation != token || value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
        if (!granted(SPEED_PERMISSION)) { speed(null, "차량 속도 권한 없음"); return }
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
            speed(null, "차량 속도 수신 대기"); return
        }
        val number = (value.value as? Float)?.let(VehicleUnits::speedKph)
        if (number == null) { speed(null, "차량 속도 값 오류"); return }
        if (value.timestamp < latestSpeedTimestamp) return
        latestSpeedTimestamp = value.timestamp
        speed(ReceivedValue(number, value.timestamp / 1_000_000), "AAOS Car API")
    }
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start() {
        stop()
        val token = generation
        speed(null, "차량 속도 연결 중")
        position(null, "위치 연결 중")
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            speed(null, "AAOS 차량 API 미지원")
        } else if (!granted(SPEED_PERMISSION)) {
            speed(null, "차량 속도 권한 없음")
        } else if (Build.VERSION.SDK_INT < 29) {
            speed(null, "차량 연결 API 미지원")
        } else try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (generation == token) {
                    if (!ready) {
                        speedReader?.shutdownNow(); speedReader = null
                        manager = null
                        speed(null, "차량 서비스 연결 끊김")
                    } else try {
                        if (!granted(SPEED_PERMISSION)) throw SecurityException()
                        val properties = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                        manager = properties
                        val id = VehiclePropertyIds.PERF_VEHICLE_SPEED
                        if (properties.propertyList.none { it.propertyId == id }) {
                            speed(null, "차량 속도 읽기 미지원")
                        } else {
                            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                                override fun onChangeEvent(value: CarPropertyValue<*>) {
                                    acceptSpeed(value, token)
                                }
                                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                                    if (generation == token && propertyId == id) speed(null, "차량 속도 수신 오류")
                                }
                            }
                            speedListener = callback
                            if (!properties.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_UI))
                                speed(null, "차량 속도 구독 실패")
                            // Read on a worker: Binder reads may block. Keep the vehicle timestamp,
                            // including repeated/cached values, so a successful read cannot hide stale data.
                            speedReader?.shutdownNow()
                            speedReader = Executors.newSingleThreadScheduledExecutor().also { reader ->
                                reader.scheduleWithFixedDelay({
                                    if (generation == token) {
                                        try {
                                            val value = properties.getProperty<Float>(id, 0)
                                            main.post {
                                                if (generation == token) {
                                                    if (value != null) acceptSpeed(value, token)
                                                    else speed(null, "차량 속도 수신 대기")
                                                }
                                            }
                                        } catch (_: SecurityException) {
                                            main.post { if (generation == token) speed(null, "차량 속도 권한 없음") }
                                        } catch (_: Exception) {
                                            main.post { if (generation == token) speed(null, "차량 속도 조회 재시도 중") }
                                        }
                                    }
                                }, 0, 1, TimeUnit.SECONDS)
                            }
                        }
                    } catch (_: SecurityException) { speed(null, "차량 속도 권한 없음") }
                    catch (_: Exception) { speed(null, "차량 속도 연결 실패") }
                }
            }
        } catch (_: Exception) { speed(null, "차량 속도 연결 실패") }
        catch (_: LinkageError) { speed(null, "AAOS 차량 API 미지원") }

        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) { position(null, "위치 권한 없음"); return }
        val provider = if (fine) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        try {
            if (locations == null || !locations.allProviders.contains(provider)) {
                position(null, "위치 제공자 미지원"); return
            }
            val callback = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (generation != token) return
                    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        position(null, "위치 권한 없음"); return
                    }
                    if (!VehicleUnits.validPosition(location.latitude, location.longitude)) { position(null, "위치 값 오류"); return }
                    val simulated = if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider
                    position(ReceivedValue(VehiclePosition(location.latitude, location.longitude, !granted(Manifest.permission.ACCESS_FINE_LOCATION), simulated),
                        location.elapsedRealtimeNanos / 1_000_000), "Android 위치 API")
                }
                override fun onProviderDisabled(provider: String) { if (generation == token) position(null, "기기 위치 사용 중지됨") }
                override fun onProviderEnabled(provider: String) { if (generation == token) position(null, "위치 수신 대기") }
                @Deprecated("Legacy callback")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            listener = callback
            position(null, if (locations.isProviderEnabled(provider)) "위치 수신 대기" else "기기 위치 사용 중지됨")
            locations.requestLocationUpdates(provider, 1000L, 0f, callback, Looper.getMainLooper())
        } catch (_: SecurityException) { position(null, "위치 권한 없음") }
        catch (_: Exception) { position(null, "위치 연결 실패") }
    }
    @Suppress("DEPRECATION")
    fun stop() {
        generation++
        speedReader?.shutdownNow(); speedReader = null
        latestSpeedTimestamp = -1L
        speedListener?.let { try { manager?.unregisterCallback(it) } catch (_: Exception) {} }
        speedListener = null; manager = null
        try { car?.disconnect() } catch (_: Exception) {}
        car = null
        listener?.let { try { locations?.removeUpdates(it) } catch (_: Exception) {} }
        listener = null
        speed(null, "수신 중지"); position(null, "수신 중지")
    }
}
