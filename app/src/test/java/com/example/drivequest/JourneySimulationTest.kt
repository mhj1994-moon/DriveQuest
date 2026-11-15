package com.example.drivequest

import org.junit.Assert.*
import org.junit.Test

class JourneySimulationTest {
    @Test fun receivedDistanceDoesNotInventMissingValues() {
        assertNull(VehicleUnits.tripKm(null))
        assertNull(VehicleUnits.tripKm(Float.NaN))
        assertNull(VehicleUnits.tripKm(-1f))
        assertEquals(6.44, VehicleUnits.tripKm(6440f)!!, 0.000001)
        assertEquals(0.0, VehicleUnits.tripKm(0f)!!, 0.0)
    }
    @Test fun staleOrFutureSignalCannotBeUsedAsCurrent() {
        val sample = ReceivedValue(72.0, 1000)
        assertEquals(72.0, sample.fresh(2000, 3000)!!, 0.0)
        assertNull(sample.fresh(4001, 3000))
        assertNull(sample.fresh(999, 3000))
        assertNull(ReceivedValue(72.0, -1).fresh(0, 3000))
    }
    @Test fun speedConversionKeepsReverseAndRejectsInvalidValues() {
        assertEquals(-36.0, VehicleUnits.speedKph(-10f)!!, 0.00001)
        assertNull(VehicleUnits.speedKph(Float.POSITIVE_INFINITY))
        assertFalse(VehicleUnits.validPosition(91.0, 127.0))
        assertFalse(VehicleUnits.validPosition(Double.NaN, 127.0))
        assertTrue(VehicleUnits.validPosition(37.5, 127.0))
    }
    @Test fun sustainedAccelerationCountsOneExcursionAndGapsAreUnrated() {
        val quest = QuestObservation()
        quest.accept(ReceivedValue(0.0, 0))
        quest.accept(ReceivedValue(10.8, 1000))
        quest.accept(ReceivedValue(21.6, 2000))
        assertEquals(1, quest.rapidCount)
        quest.accept(ReceivedValue(21.6, 3000))
        quest.accept(ReceivedValue(32.4, 4000))
        assertEquals(2, quest.rapidCount)
        quest.accept(null)
        quest.accept(ReceivedValue(100.0, 20000))
        assertEquals(2, quest.rapidCount)
        assertTrue(quest.incomplete)
    }
    @Test fun invalidSampleDoesNotCountAcrossAnOutage() {
        val quest = QuestObservation()
        quest.accept(ReceivedValue(0.0, 0))
        quest.accept(ReceivedValue(120.0, 10000))
        assertEquals(0, quest.rapidCount)
        assertTrue(quest.incomplete)
    }
}
