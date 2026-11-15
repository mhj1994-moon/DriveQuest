package com.example.drivequest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only emulator motion observation. Does not change route, position or vehicle values. */
@RunWith(AndroidJUnit4::class)
class EmulatorMotionDiagnosticTest {
    @Test fun observeDistanceAndPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val vehicle = Vehicle(context)
        val navi = NaviHelper(context)
        val connected = CountDownLatch(1)
        val locationCount = AtomicInteger(0)
        val locationChanges = AtomicInteger(0)
        var connectionError: Throwable? = null
        var last: Pair<Double, Double>? = null
        val listener = object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
                synchronized(locationChanges) {
                    val position = info.latitude to info.longitude
                    val previous = last
                    if (previous != null && position != previous) locationChanges.incrementAndGet()
                    last = position
                    locationCount.incrementAndGet()
                }
                Log.i("DriveQuestMotion", "location_response count=${locationCount.get()} changes=${locationChanges.get()} valid=${info.latitude in -90.0..90.0 && info.longitude in -180.0..180.0}")
            }
            override fun onError(errorCode: NaviErrorCode) {
                Log.w("DriveQuestMotion", "navi_error=$errorCode")
            }
        }
        try {
            vehicle.initialize()
            navi.initialize({ connected.countDown() }, { connectionError = it; connected.countDown() })
            check(connected.await(15, TimeUnit.SECONDS)) { "navigation connection timeout" }
            connectionError?.let { throw AssertionError("navigation connection failed", it) }
            navi.addListener(listener)
            val odometer = vehicle.getOdometer()
            Log.i("DriveQuestMotion", "OBSERVATION_START duration_seconds=75")
            repeat(15) { sample ->
                val complete = CountDownLatch(2)
                odometer.getCurrentDistanceDriven({ value ->
                    Log.i("DriveQuestMotion", "sample=$sample odometer_km=$value")
                    complete.countDown()
                }, { error ->
                    Log.w("DriveQuestMotion", "sample=$sample odometer_error=${error.javaClass.simpleName}")
                    complete.countDown()
                })
                odometer.getTripCurrentDrivingDistance({ value ->
                    Log.i("DriveQuestMotion", "sample=$sample trip_m=$value")
                    complete.countDown()
                }, { error ->
                    Log.w("DriveQuestMotion", "sample=$sample trip_error=${error.javaClass.name} message=${error.message}")
                    complete.countDown()
                })
                navi.getCurrentLocationInfo()
                check(complete.await(5, TimeUnit.SECONDS)) { "vehicle callback timeout" }
                Thread.sleep(5000)
            }
            Log.i("DriveQuestMotion", "OBSERVATION_END location_responses=${locationCount.get()} location_changes=${locationChanges.get()}")
            check(locationCount.get() > 0) { "no current-location response; position remains unverified" }
        } finally {
            navi.removeListener(listener)
            navi.release()
            vehicle.release()
        }
    }
}
