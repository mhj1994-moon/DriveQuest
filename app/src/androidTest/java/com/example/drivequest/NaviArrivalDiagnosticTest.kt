package com.example.drivequest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in long check: no synthetic callback is counted as a real navigation event. */
@RunWith(AndroidJUnit4::class)
class NaviArrivalDiagnosticTest {
    @Test fun observeCompletionAndArrival() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLongNavigationCheck") == "true")
        val navi = NaviHelper(instrumentation.targetContext.applicationContext)
        val connected = CountDownLatch(1)
        val destination = CountDownLatch(1)
        val routeState = CountDownLatch(1)
        val both = CountDownLatch(2)
        val cancelled = CountDownLatch(1)
        val gotNewCompletion = AtomicBoolean(false)
        val gotArrival = AtomicBoolean(false)
        var empty = false
        var routing = true
        var connectError: Throwable? = null
        var requested = false
        val listener = object : NaviHelperEventListener {
            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                empty = destinationInfo.name.isBlank() && destinationInfo.poiId.isBlank()
                destination.countDown()
            }
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                routing = routeStateInfo.isRouting
                routeState.countDown()
            }
            @Deprecated("진단용 기존 이벤트 확인")
            override fun onRouteEnded() {
                Log.i("DriveQuestArrival", "legacy completion received")
            }
            override fun onRoutingCompleted() {
                if (gotNewCompletion.compareAndSet(false, true)) both.countDown()
                Log.i("DriveQuestArrival", "new completion received")
            }
            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                if (gotArrival.compareAndSet(false, true)) both.countDown()
                Log.i("DriveQuestArrival", "destination arrival received")
            }
            override fun onError(errorCode: NaviErrorCode) {
                Log.e("DriveQuestArrival", "navigation error=$errorCode")
            }
            override fun onRouteCancelled() { cancelled.countDown() }
        }
        try {
            navi.initialize({ connected.countDown() }, { connectError = it; connected.countDown() })
            check(connected.await(20, TimeUnit.SECONDS)) { "connection timeout" }
            connectError?.let { throw AssertionError("connection failed", it) }
            navi.addListener(listener)
            navi.getDestinationInfo()
            check(destination.await(15, TimeUnit.SECONDS) && empty) { "existing destination or unknown state" }
            navi.getRouteStateInfo()
            check(routeState.await(15, TimeUnit.SECONDS) && !routing) { "route already searching or unknown" }
            requested = true
            navi.requestRoute(RouteInfo(
                latitude = 37.48544722, longitude = 127.03636666,
                poiName = "42dot", poiId = "36khp37hh8gqcm5nqhe5",
                poiSubId = "0", address = "서울시 강남구 남부순환로 2621"
            ))
            if (InstrumentationRegistry.getArguments().getString("inspectNavigationUi") == "true") {
                Thread.sleep(12000)
                val root = instrumentation.uiAutomation.rootInActiveWindow
                Log.i("DriveQuestArrival", "active package=${root?.packageName}")
                listOf("모의", "시뮬", "주행", "안내", "시작", "도착").forEach { word ->
                    root?.findAccessibilityNodeInfosByText(word)?.forEach { node ->
                        val rect = android.graphics.Rect()
                        node.getBoundsInScreen(rect)
                        Log.i("DriveQuestArrival", "control=$word text=${node.text} clickable=${node.isClickable} bounds=$rect")
                    }
                }
            }
            both.await(if (InstrumentationRegistry.getArguments().getString("inspectNavigationUi") == "true") 5 else 90, TimeUnit.SECONDS)
            Log.i("DriveQuestArrival", "observed newCompletion=${gotNewCompletion.get()} arrival=${gotArrival.get()}")
            check(gotNewCompletion.get() && gotArrival.get()) {
                "newCompletion=${gotNewCompletion.get()} arrival=${gotArrival.get()}"
            }
        } finally {
            try {
                if (requested) {
                    navi.cancelRoute()
                    if (cancelled.await(10, TimeUnit.SECONDS)) Log.i("DriveQuestArrival", "route cancelled")
                    else Log.w("DriveQuestArrival", "route cancellation unconfirmed")
                }
            } finally {
                navi.removeListener(listener)
                navi.release()
            }
        }
    }
}
