package com.example.stopwatchapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PaceCalculatorTest {

    @Test
    fun calculatePace_1kmIn5Minutes_returns5_00() {
        val ms = 5L * 60 * 1000 // 5 minutes
        val distanceM = 1000
        val result = PaceCalculator.calculatePace(ms, distanceM)
        assertEquals("5:00", result)
    }

    @Test
    fun calculatePace_1kmIn4m30s_returns4_30() {
        val ms = (4L * 60 + 30) * 1000 // 4m 30s
        val distanceM = 1000
        val result = PaceCalculator.calculatePace(ms, distanceM)
        assertEquals("4:30", result)
    }

    @Test
    fun calculatePace_400mIn100s_returns4_10() {
        val ms = 100L * 1000 // 100 seconds
        val distanceM = 400
        val result = PaceCalculator.calculatePace(ms, distanceM)
        assertEquals("4:10", result)
    }

    @Test
    fun calculatePace_zeroDistance_returns0_00() {
        val ms = 1000L
        val distanceM = 0
        val result = PaceCalculator.calculatePace(ms, distanceM)
        assertEquals("0:00", result)
    }

    @Test
    fun calculatePace_verySlow_returnsCorrectPace() {
        // 100m in 2 minutes -> 20 min/km
        val ms = 2L * 60 * 1000
        val distanceM = 100
        val result = PaceCalculator.calculatePace(ms, distanceM)
        assertEquals("20:00", result)
    }

    @Test
    fun calculatePace_veryFast_returnsCorrectPace() {
        // 1000m in 2 minutes -> 2 min/km
        val ms = 2L * 60 * 1000
        val distanceM = 1000
        val result = PaceCalculator.calculatePace(ms, distanceM)
        assertEquals("2:00", result)
    }

    @Test
    fun calculatePace_Delson_returnsCorrectPace() {
        // 1000m in 2 minutes -> 2 min/km
        val ms = 2L * 60 * 1000
        val distanceM = 370
        val result = PaceCalculator.calculatePace(ms, distanceM)
        assertEquals("5:24", result)
    }
}
