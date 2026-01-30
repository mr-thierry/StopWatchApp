package com.example.stopwatchapp

import java.util.Locale

object PaceCalculator {
    fun calculatePace(ms: Long, distanceM: Int): String {
        if (distanceM <= 0) return "0:00"
        val seconds = ms / 1000.0
        val paceDecimal = (seconds * 1000.0) / (60.0 * distanceM)
        val minutes = paceDecimal.toInt()
        val secs = ((paceDecimal - minutes) * 60).toInt()
        return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }
}
