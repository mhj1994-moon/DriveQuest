package com.example.drivequest

import kotlin.math.abs

/** Provisional observation rule, not a safety judgment or a scoring rule. */
class QuestObservation {
    var rapidCount = 0; private set
    var samples = 0; private set
    var incomplete = false; private set
    private var previous: ReceivedValue<Double>? = null
    private var above = false
    private var excursionStart: Long? = null
    fun accept(sample: ReceivedValue<Double>?) {
        if (sample == null) { incomplete = true; previous = null; above = false; excursionStart = null; return }
        if (!sample.value.isFinite() || sample.elapsedMillis < 0) { interrupt(); return }
        val last = previous
        if (last?.elapsedMillis == sample.elapsedMillis) return
        if (last != null) {
            val dt = sample.elapsedMillis - last.elapsedMillis
            if (dt !in 100..2000) {
                incomplete = true; above = false; excursionStart = null
            } else {
                // Threshold 2.5 m/s² sustained for 1 second; count once per excursion.
                val acceleration = (abs(sample.value) - abs(last.value)) / 3.6 / (dt / 1000.0)
                if (acceleration >= 2.5) {
                    if (excursionStart == null) excursionStart = last.elapsedMillis
                    if (!above && sample.elapsedMillis - excursionStart!! >= 1000) { rapidCount++; above = true }
                } else { above = false; excursionStart = null }
            }
        }
        previous = sample
        samples++
    }
    fun interrupt() = accept(null)
}
