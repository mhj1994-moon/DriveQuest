package com.example.drivequest

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.ArraySet
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Availability only. Never requests/grants privileged permissions or controls ADAS. */
@RunWith(AndroidJUnit4::class)
class AdasAvailabilityDiagnosticTest {
    @Test fun inspectLeadVehicleDistanceAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permission = context.checkSelfPermission(Car.PERMISSION_READ_ADAS_STATES) == PackageManager.PERMISSION_GRANTED
        Log.i("DriveQuestAdas", "read_adas_permission=$permission")
        val connected = CountDownLatch(1)
        var manager: CarPropertyManager? = null
        var failure: String? = null
        val car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
            if (ready) {
                try { manager = service.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager }
                catch (e: Exception) { failure = e.javaClass.simpleName }
                connected.countDown()
            }
        }
        try {
            check(connected.await(10, TimeUnit.SECONDS)) { "car service connection timeout" }
            check(manager != null) { "car property manager unavailable: $failure" }
            val ids = ArraySet<Int>().apply {
                add(VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE)
                add(VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP)
            }
            try {
                val configs = manager!!.getPropertyList(ids)
                Log.i("DriveQuestAdas", "visible_configs=${configs.map { it.propertyId }}")
            } catch (e: SecurityException) {
                Log.i("DriveQuestAdas", "config_access_denied=${e.javaClass.simpleName}")
            }
            if (permission) {
                try {
                    val value = manager!!.getIntProperty(VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE, 0)
                    Log.i("DriveQuestAdas", "lead_distance_raw=$value; unit requires platform confirmation")
                } catch (e: Exception) {
                    Log.i("DriveQuestAdas", "lead_distance_unavailable=${e.javaClass.simpleName}")
                }
            } else Log.i("DriveQuestAdas", "lead_distance_read_skipped=permission_not_granted")
        } finally { car?.disconnect() }
    }
}
