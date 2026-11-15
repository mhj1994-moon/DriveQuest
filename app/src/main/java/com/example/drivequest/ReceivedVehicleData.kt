package com.example.drivequest

/** Unit conversion and freshness only. Never derives distance from another signal. */
data class ReceivedValue<T>(val value: T, val elapsedMillis: Long) {
    fun fresh(now: Long, maxAgeMillis: Long): T? =
        value.takeIf { elapsedMillis >= 0 && now - elapsedMillis in 0..maxAgeMillis }
}
object VehicleUnits {
    fun speedKph(metersPerSecond: Float): Double? =
        metersPerSecond.toDouble().takeIf { it.isFinite() }?.times(3.6)
    fun tripKm(meters: Float?): Double? =
        meters?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }?.div(1000.0)
    fun validPosition(latitude: Double, longitude: Double) =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
data class VehiclePosition(val latitude: Double, val longitude: Double, val approximate: Boolean, val simulated: Boolean)
fun receivedKm(value: Double?, missing: String = "수신 대기") = value?.let { "%.2f km".format(it) } ?: missing
