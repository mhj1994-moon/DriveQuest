package com.example.drivequest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

/** Pleos 에뮬레이터의 가상 값에 대한 읽기 전용 진단. */
@RunWith(AndroidJUnit4::class)
class VehicleDiagnosticTest {
    private fun <T> awaitResult(name: String, request: ((T) -> Unit, (Exception) -> Unit) -> Unit): T {
        val done = CountDownLatch(1)
        var value: T? = null
        var failure: Exception? = null
        request({ value = it; done.countDown() }, { failure = it; done.countDown() })
        check(done.await(15, TimeUnit.SECONDS)) { "$name: callback timeout" }
        failure?.let { throw AssertionError("$name failed: ${it.javaClass.simpleName}: ${it.message}", it) }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    @Test
    fun inspectVirtualVehicle() {
        val vehicle = Vehicle(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        vehicle.initialize()
        try {
            val brake = vehicle.getBrake()
            val signal = vehicle.getTurnSignal()
            val odometer = vehicle.getOdometer()
            val battery = vehicle.getEvBattery()
            val checks = listOf<Triple<String, ((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit, ((Any?) -> Unit, (Exception) -> Unit) -> Unit>>(
                Triple("brake", brake::checkBrakePedalStatusCapabilities) { ok, fail -> brake.getCurrentBrakePedalStatus(ok, fail) },
                Triple("signal", signal::checkTurnSignalStateCapabilities) { ok, fail -> signal.getCurrentTurnSignalState(ok, fail) },
                Triple("odometer_km", odometer::checkCurrentDistanceDrivenCapabilities) { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) },
                Triple("battery_percent", battery::checkEvBatteryStateOfChargeCapabilities) { ok, fail -> battery.getEvBatteryStateOfCharge(ok, fail) }
            )
            val errors = mutableListOf<String>()
            checks.forEach { (name, capabilityRequest, valueRequest) ->
                try {
                    val capability = awaitResult(name, capabilityRequest)
                    Log.i("DriveQuestDiag", "$name capability=${capability.accessType}")
                    if (capability.accessType == AccessType.READ || capability.accessType == AccessType.READ_WRITE) {
                        val result = awaitResult(name, valueRequest)
                        Log.i("DriveQuestDiag", "$name value=$result")
                    }
                } catch (e: Throwable) {
                    val message = "$name error=${e.javaClass.simpleName}: ${e.message}"
                    errors.add(message)
                    Log.e("DriveQuestDiag", message)
                }
            }
            check(errors.isEmpty()) { errors.joinToString("; ") }
        } finally {
            vehicle.release()
        }
    }
}
