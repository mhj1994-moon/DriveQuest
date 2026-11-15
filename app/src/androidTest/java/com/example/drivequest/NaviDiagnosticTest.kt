package com.example.drivequest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

/** 에뮬레이터의 내비 연결과 현재 목적지 조회만 확인한다. */
@RunWith(AndroidJUnit4::class)
class NaviDiagnosticTest {
    @Test fun inspectConnectionAndDestination() {
        val navi = NaviHelper(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        val connected = CountDownLatch(1)
        val response = CountDownLatch(1)
        val routeResponse = CountDownLatch(1)
        var connectionFailure: Throwable? = null
        var result = "no response"
        val drivingEvents = java.util.concurrent.atomic.AtomicInteger(0)
        val listener = object : NaviHelperEventListener {
            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                result = "destination callback received"
                Log.i("DriveQuestNavi", "destination name present=${destinationInfo.name.isNotBlank()}, poi id present=${destinationInfo.poiId.isNotBlank()}")
                Log.i("DriveQuestNavi", "destination remaining_distance_raw=${destinationInfo.remainDistance}, remaining_time_raw=${destinationInfo.remainTime}")
                response.countDown()
            }
            override fun onDrivingInfo(drivingInfo: DrivingInfo) {
                drivingEvents.incrementAndGet()
                Log.i("DriveQuestNavi", "driving_info distance_raw=${drivingInfo.destination.distance} duration_raw=${drivingInfo.destination.duration}")
            }
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                Log.i("DriveQuestNavi", "route state: isRouting=${routeStateInfo.isRouting}, mode=${routeStateInfo.naviMode}")
                routeResponse.countDown()
            }
            override fun onError(errorCode: NaviErrorCode) {
                result = "navigation error: $errorCode"
                response.countDown()
            }
        }
        try {
            navi.initialize({ connected.countDown() }, { connectionFailure = it; connected.countDown() })
            check(connected.await(20, TimeUnit.SECONDS)) { "NaviHelper connection timeout" }
            connectionFailure?.let { throw AssertionError("NaviHelper connection failed", it) }
            Log.i("DriveQuestNavi", "service connected")
            navi.addListener(listener)
            navi.getDestinationInfo()
            check(response.await(15, TimeUnit.SECONDS)) { "destination callback timeout" }
            Log.i("DriveQuestNavi", result)
            check(result == "destination callback received") { result }
            navi.getRouteStateInfo()
            check(routeResponse.await(15, TimeUnit.SECONDS)) { "route state callback timeout" }
            Thread.sleep(2000)
            Log.i("DriveQuestNavi", "read_only_probe driving_events=${drivingEvents.get()}; absence does not establish unsupported")
        } finally {
            navi.removeListener(listener)
            navi.release()
        }
    }
}
