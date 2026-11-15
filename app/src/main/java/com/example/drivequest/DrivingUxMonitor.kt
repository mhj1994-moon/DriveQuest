package com.example.drivequest

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

data class DrivingUxState(
    val known: Boolean = false,
    val restricted: Boolean = true,
    val activeRestrictions: Int = 511,
    val maxItems: Int = 3,
    val maxTextLength: Int = 32
)

/** Uses the platform restriction state, never an override or a settings/allowlist change. */
class DrivingUxMonitor(private val context: Context, private val update: (DrivingUxState) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var generation = 0

    fun start() {
        val token = ++generation
        update(DrivingUxState())
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            update(DrivingUxState(known = true, restricted = false, activeRestrictions = 0))
            return
        }
        if (Build.VERSION.SDK_INT < 29) return
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (generation == token) {
                    if (ready) {
                        try {
                            val ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                            manager = ux
                            fun emit(value: android.car.drivingstate.CarUxRestrictions) {
                                if (generation == token) update(DrivingUxState(
                                    known = true, restricted = value.isRequiresDistractionOptimization,
                                    activeRestrictions = value.activeRestrictions,
                                    maxItems = value.maxCumulativeContentItems.coerceAtLeast(1),
                                    maxTextLength = value.maxRestrictedStringLength.coerceAtLeast(1)))
                            }
                            ux.registerListener { emit(it) }
                            emit(ux.currentCarUxRestrictions)
                        } catch (_: Exception) { update(DrivingUxState()) }
                    } else update(DrivingUxState())
                }
            }
        } catch (_: Exception) { update(DrivingUxState()) }
        catch (_: LinkageError) { update(DrivingUxState()) }
    }

    fun stop() {
        generation++
        try { manager?.unregisterListener() } catch (_: Exception) {}
        manager = null
        try { car?.disconnect() } catch (_: Exception) {}
        car = null
        update(DrivingUxState())
    }
}
