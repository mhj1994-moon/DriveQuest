package com.example.drivequest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reads current state only. No route playback, permissions grant, quest start, or rewards. */
@RunWith(AndroidJUnit4::class)
class OfficialDataSourceDiagnosticTest {
    @Test fun sdkAdapterReportsReceivedAndUnavailableSignalsWithoutConsole() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val speedResponse = CountDownLatch(1)
        val positionResponse = CountDownLatch(1)
        lateinit var link: EmulatorLink
        lateinit var platform: PlatformVehicleSource
        var speedStatus = ""
        var positionStatus = ""
        instrumentation.runOnMainSync {
            link = EmulatorLink(context)
            platform = PlatformVehicleSource(context,
                { _, status -> speedStatus = status; if (status != "차량 속도 연결 중" && status != "수신 중지") speedResponse.countDown() },
                { _, status -> positionStatus = status; if (status != "위치 연결 중" && status != "수신 중지") positionResponse.countDown() })
            link.start(); platform.start()
        }
        try {
            assertTrue("speed must produce a value or explicit availability status", speedResponse.await(10, TimeUnit.SECONDS))
            assertTrue("position must produce an explicit permission/provider status", positionResponse.await(10, TimeUnit.SECONDS))
            Thread.sleep(6000)
            instrumentation.runOnMainSync {
                link.queryTripDistance(); link.queryFuelEconomy(); link.refreshNavigation()
            }
            Thread.sleep(2000)
            instrumentation.runOnMainSync {
                assertNotEquals("지원 확인 중", link.tripDistanceStatus)
                Log.i("DriveQuestOfficial", "speed_status=$speedStatus; position_status=$positionStatus")
                Log.i("DriveQuestOfficial", "trip_status=${link.tripDistanceStatus}; trip_km=${link.tripDistance?.value}; odo=${link.odometer}")
                Log.i("DriveQuestOfficial", "fuel_status=${link.fuelStatus}; fuel_l_per_100km=${link.fuelEconomy?.value}; remaining_m=${link.remainingRaw?.value}")
                Log.i("DriveQuestOfficial", "nav_position_received=${link.navigationPosition != null}; speed_limit_callback_received=${link.speedLimitRaw != null}")
            }
        } finally {
            instrumentation.runOnMainSync { platform.stop(); link.stop() }
        }
    }
}
