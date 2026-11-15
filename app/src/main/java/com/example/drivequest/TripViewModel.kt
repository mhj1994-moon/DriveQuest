package com.example.drivequest

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.abs

/** Foreground-only official SDK/API collection. Raw location is never persisted. */
class TripViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val link = EmulatorLink(application)
    var ux by mutableStateOf(DrivingUxState()); private set
    private var now by mutableLongStateOf(SystemClock.elapsedRealtime())
    private var speedSample by mutableStateOf<ReceivedValue<Double>?>(null)
    private var positionSample by mutableStateOf<ReceivedValue<VehiclePosition>?>(null)
    var speedStatus by mutableStateOf("수신 대기"); private set
    var positionStatus by mutableStateOf("수신 대기"); private set
    val speedKph get() = speedSample?.fresh(now, 3000)
    val currentPosition get() = positionSample?.fresh(now, 15000)
    val speedText get() = speedKph?.let { "%.0f km/h".format(abs(it)) }
        ?: if (speedSample != null) "속도 갱신 지연" else speedStatus
    val locationText get() = currentPosition?.let {
        "%.5f, %.5f%s%s".format(it.latitude, it.longitude,
            if (it.approximate) " · 대략적 위치" else "", if (it.simulated) " · 모의 위치" else "")
    } ?: if (positionSample != null) "위치 갱신 지연" else positionStatus
    val distanceKm get() = link.tripDistance?.fresh(now, 15000)
    val distanceText get() = receivedKm(distanceKm,
        if (link.tripDistance != null) "거리 갱신 지연" else link.tripDistanceStatus)
    // navi-data 3.0.4 sources: DestinationInfo.remainDistance is meters.
    val remainingText get() = if (!link.routeGuiding) "내비 안내 대기" else
        link.remainingRaw?.fresh(now, 15000)?.let { receivedKm(it / 1000.0) } ?: "남은 거리 수신 대기"
    val connection get() = "속도 · $speedText / 위치 · " + if (currentPosition != null) "수신 중" else positionStatus
    var active by mutableStateOf(saved.get<Boolean>("active") ?: false); private set
    var destination by mutableStateOf(saved.get<String>("destination") ?: "내비게이션 목적지"); private set
    var tripId: String = saved.get<String>("tripId") ?: ""; private set
    var elapsedSeconds by mutableDoubleStateOf(saved.get<Double>("elapsedSeconds") ?: 0.0); private set
    val displayOnly get() = ux.restricted || !ux.known || abs(speedKph ?: 0.0) > 0.0
    private var observation = QuestObservation()
    var rapidText by mutableStateOf("관찰 대기"); private set
    val fuelText get() = link.fuelEconomy?.fresh(now, 15000)?.let { "%.1f L/100km".format(it) } ?: link.fuelStatus
    val ecoText get() = "구간 미평가 · 차량 현재 연비 $fuelText"
    val speedingText get() = "미평가 · 제한속도 단위·유효 구간 확인 필요"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val monitor = DrivingUxMonitor(application) { ux = it }
    private val platform = PlatformVehicleSource(application,
        { sample, status ->
            speedSample = sample; speedStatus = status
            now = SystemClock.elapsedRealtime()
            if (active) { observation.accept(sample?.takeIf { it.fresh(now, 3000) != null }); updateQuest() }
        },
        { sample, status -> positionSample = sample; positionStatus = status; now = SystemClock.elapsedRealtime() })

    private fun updateQuest() {
        rapidText = if (observation.samples < 2) "미평가 · 속도 표본 부족" else
            "관찰 ${observation.rapidCount}회" + if (observation.incomplete) " · 누락 구간 미평가" else " · 관찰 중"
    }
    fun permissionsChanged() { if (job != null) platform.start() }
    fun startForeground() {
        if (job != null) return
        monitor.start(); link.start(); platform.start()
        job = scope.launch {
            var previous = SystemClock.elapsedRealtime()
            var ticks = 0
            while (isActive) {
                delay(1000)
                now = SystemClock.elapsedRealtime()
                if (active) {
                    if (now - previous in 1..3000) elapsedSeconds += (now - previous) / 1000.0
                    if (speedKph == null) { observation.interrupt(); updateQuest() }
                    save()
                }
                previous = now
                if (++ticks % 5 == 0) { link.refreshNavigation(); link.queryTripDistance(); link.queryFuelEconomy() }
            }
        }
    }
    fun beginTrip(name: String) {
        if (displayOnly || active || !link.routeGuiding) return
        destination = name; tripId = UUID.randomUUID().toString(); active = true
        elapsedSeconds = 0.0; observation = QuestObservation(); rapidText = "관찰 대기"
        save()
    }
    fun finishTrip(): Boolean {
        if (displayOnly || !active) return false
        active = false; save(); return true
    }
    private fun save() {
        saved["active"] = active; saved["destination"] = destination
        saved["tripId"] = tripId; saved["elapsedSeconds"] = elapsedSeconds
        // Never restore an old distance estimate or coordinates as fresh SDK data.
        saved.remove<Double>("distanceKm")
    }
    fun stopForeground() {
        job?.cancel(); job = null
        if (active) { observation.interrupt(); updateQuest() }
        platform.stop(); monitor.stop(); link.stop(); save()
    }
    override fun onCleared() { stopForeground(); scope.cancel() }
}
