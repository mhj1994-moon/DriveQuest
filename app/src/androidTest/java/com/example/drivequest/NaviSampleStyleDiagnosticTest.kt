package com.example.drivequest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

/** 공식 샘플 초기화 방식 비교. 고정 대기는 제품 구현에 쓰지 않는다. */
@RunWith(AndroidJUnit4::class)
class NaviSampleStyleDiagnosticTest {
    @Test fun requestVirtualRoute() {
        val navi = NaviHelper(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        val destination = CountDownLatch(1)
        val routeState = CountDownLatch(1)
        val outcome = CountDownLatch(1)
        val started = CountDownLatch(1)
        val afterDestination = CountDownLatch(1)
        val afterRouteState = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        var destinationEmpty = false
        var isRouting = true
        var requested = false
        var result = "timeout"
        var matchesRequestedDestination = false
        var routeGuidanceActive = false
        val listener = object : NaviHelperEventListener {
            override fun onDestinationInfo(info: DestinationInfo) {
                if (requested) {
                    matchesRequestedDestination = info.poiId == "36khp37hh8gqcm5nqhe5"
                    Log.i("DriveQuestSample", "requested destination matches=$matchesRequestedDestination")
                    afterDestination.countDown()
                } else {
                    destinationEmpty = info.name.isBlank() && info.poiId.isBlank()
                    destination.countDown()
                }
            }
            override fun onRouteStateInfo(info: RouteStateInfo) {
                if (requested) {
                    routeGuidanceActive = info.naviMode == ai.pleos.playground.navi.constants.NaviMode.ROUTE_GUIDING
                    Log.i("DriveQuestSample", "after request: isRouting=${info.isRouting}, mode=${info.naviMode}")
                    afterRouteState.countDown()
                } else {
                    isRouting = info.isRouting
                    routeState.countDown()
                }
            }
            override fun onRouteStarted(info: RouteStartInfo) {
                Log.i("DriveQuestSample", "route started callback: distance=${info.distance}, duration=${info.duration}")
                started.countDown()
            }
            @Deprecated("진단 전용: 기존 이벤트와 신규 이벤트 비교")
            override fun onRouteEnded() {
                Log.i("DriveQuestSample", "legacy onRouteEnded callback")
                if (requested) {
                    result = "legacy route completion callback"
                    outcome.countDown()
                }
            }
            override fun onDestinationChanged() {
                Log.i("DriveQuestSample", "destination changed callback")
            }
            override fun onNavigationStatus(status: ai.pleos.playground.navi.constants.NavigationStatus) {
                Log.i("DriveQuestSample", "navigation status=$status")
            }
            override fun onRoutingCompleted() {
                result = "routing completed callback"
                outcome.countDown()
            }
            override fun onRouteCancelled() { cancelled.countDown() }
            override fun onError(errorCode: NaviErrorCode) {
                result = "navigation error: $errorCode"
                outcome.countDown()
            }
        }
        try {
            navi.initialize()
            navi.addListener(listener)
            Thread.sleep(2000)
            navi.getDestinationInfo()
            check(destination.await(15, TimeUnit.SECONDS) && destinationEmpty) { "existing destination or unknown state; skipping route request" }
            navi.getRouteStateInfo()
            check(routeState.await(15, TimeUnit.SECONDS) && !isRouting) { "routing in progress or unknown state; skipping route request" }
            requested = true
            navi.requestRoute(RouteInfo(
                latitude = 37.48544722, longitude = 127.03636666,
                poiName = "42dot", poiId = "36khp37hh8gqcm5nqhe5",
                poiSubId = "0", address = "서울시 강남구 남부순환로 2621"
            ))
            check(started.await(15, TimeUnit.SECONDS)) { "route started callback timeout" }
            check(outcome.await(20, TimeUnit.SECONDS)) { "route completion callback timeout" }
            Log.i("DriveQuestSample", "result=$result")
            check(result == "routing completed callback" || result == "legacy route completion callback") { result }
            navi.getDestinationInfo()
            check(afterDestination.await(5, TimeUnit.SECONDS) && matchesRequestedDestination) { "requested destination not confirmed" }
            navi.getRouteStateInfo()
            check(afterRouteState.await(5, TimeUnit.SECONDS) && routeGuidanceActive) { "route guidance not confirmed" }
        } finally {
            try {
                if (requested) {
                    navi.cancelRoute()
                    check(cancelled.await(10, TimeUnit.SECONDS)) { "route cancellation unconfirmed" }
                    Log.i("DriveQuestSample", "route cancellation callback")
                }
            } finally {
                navi.removeListener(listener)
                navi.release()
            }
        }
    }
}
