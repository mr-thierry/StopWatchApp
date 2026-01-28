package com.example.stopwatchapp

import kotlinx.serialization.Serializable

@Serializable
data class Lap(
    val lapNumber: Int,
    val durationMs: Long,
    val distanceM: Int,
    val paceMinKm: String
)

@Serializable
data class SessionState(
    val isRunning: Boolean = false,
    val startTime: Long = 0L,
    val elapsedTime: Long = 0L,
    val laps: List<Lap> = emptyList(),
    val trackDistanceM: Int = 370 // Default to Delson
)
