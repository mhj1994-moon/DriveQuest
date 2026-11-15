package com.example.drivequest

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import android.os.SystemClock
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/** Official Pleos SDK read-only adapter. Location stays in memory; no console transport. */
@SuppressLint("MissingPermission")
class EmulatorLink(private val context: Context) {
    var destination by mutableStateOf("확인 중")
        private set
    var route by mutableStateOf("확인 중")
        private set
    var routeGuiding by mutableStateOf(false)
        private set
    var odometerKm by mutableStateOf<Float?>(null)
        private set
    var odometer by mutableStateOf("확인 중")
        private set
    var brake by mutableStateOf("확인 중")
        private set
    var signal by mutableStateOf("확인 중")
        private set
    var status by mutableStateOf("연결 준비 중")
        private set
    var tripDistance by mutableStateOf<ReceivedValue<Double>?>(null)
        private set
    var tripDistanceStatus by mutableStateOf("지원 확인 중")
        private set
    var remainingRaw by mutableStateOf<ReceivedValue<Int>?>(null)
        private set
    var navigationPosition by mutableStateOf<ReceivedValue<Pair<Double, Double>>?>(null)
        private set
    var speedLimitRaw by mutableStateOf<ReceivedValue<Int>?>(null)
        private set
    var fuelEconomy by mutableStateOf<ReceivedValue<Float>?>(null)
        private set
    var fuelStatus by mutableStateOf("지원 확인 중")
        private set
    private var fuelReadable = false
    private var tripReadable = false
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var naviReady = false
    private var distanceRegistered = false
    private var brakeRegistered = false
    private var signalRegistered = false

    private fun post(token: Int, block: () -> Unit) {
        main.post { if (generation == token && vehicle != null) block() }
    }
    private fun readable(type: AccessType) = type == AccessType.READ || type == AccessType.READ_WRITE

    fun start() {
        if (vehicle != null) return
        val token = ++generation
        status = "Pleos SDK 연결 중"
        tripDistance = null; tripDistanceStatus = "지원 확인 중"; tripReadable = false
        remainingRaw = null; navigationPosition = null; speedLimitRaw = null
        fuelReadable = false; fuelEconomy = null; fuelStatus = "지원 확인 중"
        destination = "확인 중"; route = "확인 중"; routeGuiding = false
        odometerKm = null
        odometer = "확인 중"; brake = "확인 중"; signal = "확인 중"
        try {
            val v = Vehicle(context)
            vehicle = v
            v.initialize()
            val distance = v.getOdometer()
            distance.checkTripCurrentDrivingDistanceCapabilities({ cap -> post(token) {
                tripReadable = readable(cap.accessType)
                if (tripReadable) queryTripDistance() else tripDistanceStatus = "읽기 미지원"
            } }, { post(token) { tripDistanceStatus = "지원 확인 실패" } })
            v.getFuel().checkCurrentFuelEconomyCapabilities({ cap -> post(token) {
                fuelReadable = readable(cap.accessType)
                if (fuelReadable) queryFuelEconomy() else fuelStatus = "현재 연비 미지원"
            } }, { post(token) { fuelStatus = "연비 지원 확인 실패" } })
            val brakes = v.getBrake()
            val indicator = v.getTurnSignal()
            distance.checkCurrentDistanceDrivenCapabilities({ cap -> post(token) {
                if (readable(cap.accessType)) {
                    try {
                        distance.registerDistanceDriven(distanceListener); distanceRegistered = true
                        distance.getCurrentDistanceDriven({ value -> post(token) { setDistance(value) } },
                            { post(token) { odometer = "조회 실패" } })
                    } catch (_: Exception) { odometer = "접근 실패" }
                } else odometer = "읽기 미지원"
            } }, { post(token) { odometer = "지원 확인 실패" } })
            brakes.checkBrakePedalStatusCapabilities({ cap -> post(token) {
                if (readable(cap.accessType)) {
                    try {
                        brakes.registerBrakePedalStatus(brakeListener); brakeRegistered = true
                        brakes.getCurrentBrakePedalStatus({ value -> post(token) { setBrake(value) } },
                            { post(token) { brake = "조회 실패" } })
                    } catch (_: Exception) { brake = "접근 실패" }
                } else brake = "읽기 미지원"
            } }, { post(token) { brake = "지원 확인 실패" } })
            indicator.checkTurnSignalStateCapabilities({ cap -> post(token) {
                if (readable(cap.accessType)) {
                    try {
                        indicator.registerTurnSignalState(signalListener); signalRegistered = true
                        indicator.getCurrentTurnSignalState({ value -> post(token) { setSignal(value) } },
                            { post(token) { signal = "조회 실패" } })
                    } catch (_: Exception) { signal = "접근 실패" }
                } else signal = "읽기 미지원"
            } }, { post(token) { signal = "지원 확인 실패" } })
        } catch (_: Exception) {
            status = "차량 SDK 연결 실패"
            odometer = "연결 실패"; brake = "연결 실패"; signal = "연결 실패"
        }
        try {
            val helper = NaviHelper(context)
            navi = helper
            helper.initialize({
                main.post {
                    if (generation == token && navi === helper) {
                        try {
                            helper.addListener(naviListener)
                            naviReady = true
                            status = "Pleos SDK 응답 확인 중"
                            refreshNavigation()
                        } catch (_: Exception) { status = "내비게이션 조회 실패" }
                    }
                }
            }, { main.post { if (generation == token) status = "내비게이션 연결 실패" } })
        } catch (_: Exception) { status = "내비게이션 연결 실패" }
    }

    fun refreshNavigation() {
        if (!naviReady) return
        try { navi?.getDestinationInfo(); navi?.getRouteStateInfo(); navi?.getCurrentLocationInfo() }
        catch (_: Exception) { status = "내비게이션 조회 실패" }
    }
    fun queryFuelEconomy() {
        if (!fuelReadable) return
        val token = generation
        try {
            vehicle?.getFuel()?.getCurrentFuelEconomy({ value -> post(token) {
                fuelEconomy = value?.takeIf { it.isFinite() && it >= 0 }?.let { ReceivedValue(it, SystemClock.elapsedRealtime()) }
                fuelStatus = if (fuelEconomy == null) "연비 값 없음" else "Vehicle SDK · 현재 차량 주행 세션"
            } }, { post(token) { fuelEconomy = null; fuelStatus = "현재 연비 조회 실패" } })
        } catch (_: SecurityException) { fuelEconomy = null; fuelStatus = "연비 권한 없음" }
        catch (_: Exception) { fuelEconomy = null; fuelStatus = "현재 연비 조회 실패" }
    }
    fun queryTripDistance() {
        if (!tripReadable) return
        val token = generation
        try {
            vehicle?.getOdometer()?.getTripCurrentDrivingDistance({ value -> post(token) {
                tripDistance = VehicleUnits.tripKm(value)?.let { ReceivedValue(it, SystemClock.elapsedRealtime()) }
                tripDistanceStatus = if (tripDistance == null) "값 없음" else "Vehicle SDK · 현재 차량 주행 세션"
            } }, { error -> post(token) {
                tripDistance = null
                tripDistanceStatus = if (error.message?.contains("NOT_SUPPORTED") == true) "차량 주행거리 미지원" else "차량 주행거리 조회 실패"
            } })
        } catch (_: SecurityException) { tripDistance = null; tripDistanceStatus = "차량 주행거리 권한 없음" }
        catch (_: Exception) { tripDistance = null; tripDistanceStatus = "차량 주행거리 조회 실패" }
    }
    private fun setDistance(value: Float?) {
        odometerKm = value?.takeIf { it.isFinite() && it >= 0f }
        odometer = odometerKm?.let { "%.1f km".format(it) } ?: "값 없음"
    }
    private fun setBrake(value: Boolean?) { brake = when (value) { true -> "페달 누름"; false -> "페달 놓음"; null -> "값 없음" } }
    private fun setSignal(value: TurnSignalValue?) { signal = value?.toString() ?: "값 없음" }

    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) { val token = generation; post(token) { setDistance(value) } }
        override fun onFailed(e: Exception) { val token = generation; post(token) { odometer = "수신 실패" } }
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) { val token = generation; post(token) { setBrake(status) } }
        override fun onFailed(e: Exception) { val token = generation; post(token) { brake = "수신 실패" } }
    }
    private val signalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) { val token = generation; post(token) { setSignal(state) } }
        override fun onFailed(e: Exception) { val token = generation; post(token) { signal = "수신 실패" } }
    }
    private val naviListener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val token = generation
            post(token) {
                destination = destinationInfo.name.ifBlank { "설정된 목적지 없음" }
                remainingRaw = destinationInfo.remainDistance.takeIf { it >= 0 && destinationInfo.name.isNotBlank() }
                    ?.let { ReceivedValue(it, SystemClock.elapsedRealtime()) }
                status = "내비게이션 응답 수신"
            }
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val token = generation
            post(token) {
                navigationPosition = if (VehicleUnits.validPosition(currentLocationInfo.latitude, currentLocationInfo.longitude))
                    ReceivedValue(currentLocationInfo.latitude to currentLocationInfo.longitude, SystemClock.elapsedRealtime()) else null
            }
        }
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            val token = generation
            post(token) { speedLimitRaw = speedLimit.takeIf { it > 0 }?.let { ReceivedValue(it, SystemClock.elapsedRealtime()) } }
        }
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            val token = generation
            post(token) {
                routeGuiding = routeStateInfo.naviMode == ai.pleos.playground.navi.constants.NaviMode.ROUTE_GUIDING
                route = if (routeGuiding) "경로 안내 중" else "안내 상태: ${routeStateInfo.naviMode}"
            }
        }
        override fun onDestinationChanged() { val token = generation; post(token) { refreshNavigation() } }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { val token = generation; post(token) { refreshNavigation() } }
        override fun onRoutingCompleted() { val token = generation; post(token) { refreshNavigation() } }
        @Deprecated("Compatibility with emulator navigation service")
        override fun onRouteEnded() { val token = generation; post(token) { refreshNavigation() } }
        override fun onRouteCancelled() { val token = generation; post(token) { routeGuiding = false; remainingRaw = null; speedLimitRaw = null; destination = "경로 취소됨"; refreshNavigation() } }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            val token = generation
            post(token) { routeGuiding = false; remainingRaw = null; speedLimitRaw = null; route = "도착 이벤트 수신" }
        }
        override fun onError(errorCode: NaviErrorCode) { val token = generation; post(token) { status = "내비 오류: $errorCode" } }
    }

    fun stop() {
        generation++
        navi?.let {
            if (naviReady) try { it.removeListener(naviListener) } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        naviReady = false; navi = null
        tripReadable = false; tripDistance = null; remainingRaw = null; navigationPosition = null; speedLimitRaw = null
        fuelReadable = false; fuelEconomy = null; fuelStatus = "수신 중지"
        vehicle?.let {
            if (distanceRegistered) try { it.getOdometer().unregisterDistanceDriven(distanceListener) } catch (_: Exception) {}
            if (brakeRegistered) try { it.getBrake().unregisterBrakePedalStatus(brakeListener) } catch (_: Exception) {}
            if (signalRegistered) try { it.getTurnSignal().unregisterTurnSignalState(signalListener) } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        vehicle = null
        distanceRegistered = false; brakeRegistered = false; signalRegistered = false
        routeGuiding = false
        odometerKm = null
        status = "연결 중지됨"
    }
}
