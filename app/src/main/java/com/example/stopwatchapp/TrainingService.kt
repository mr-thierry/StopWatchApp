package com.example.stopwatchapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

class TrainingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var dataStoreManager: DataStoreManager

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var timerJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): TrainingService = this@TrainingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(applicationContext)
        createNotificationChannel()
        
        serviceScope.launch {
            dataStoreManager.sessionState.collect { savedState ->
                if (_sessionState.value == SessionState()) {
                    _sessionState.value = savedState
                    if (savedState.isRunning) {
                        startTimer()
                    }
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(100)
                _sessionState.update { it.copy(elapsedTime = it.elapsedTime + 100) }
                updateNotification()
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        saveState()
    }

    fun toggleStartStop() {
        _sessionState.update { 
            val newState = it.copy(isRunning = !it.isRunning)
            if (newState.isRunning) {
                startTimer()
                startForeground(NOTIFICATION_ID, createNotification())
            } else {
                stopTimer()
            }
            newState
        }
    }

    fun addLap() {
        _sessionState.update { current ->
            if (!current.isRunning) return@update current
            
            val durationMs = current.elapsedTime
            val distanceM = current.trackDistanceM
            val paceMinKm = calculatePace(durationMs, distanceM)
            
            val newLap = Lap(
                lapNumber = current.laps.size + 1,
                durationMs = durationMs,
                distanceM = distanceM,
                paceMinKm = paceMinKm
            )
            
            current.copy(
                elapsedTime = 0,
                laps = listOf(newLap) + current.laps
            )
        }
        saveState()
    }

    fun resetSession() {
        stopTimer()
        _sessionState.value = SessionState(trackDistanceM = _sessionState.value.trackDistanceM)
        saveState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun setTrackDistance(distanceM: Int) {
        _sessionState.update { it.copy(trackDistanceM = distanceM) }
        saveState()
    }

    private fun calculatePace(ms: Long, distanceM: Int): String {
        if (distanceM == 0) return "0:00"
        val seconds = ms / 1000.0
        val paceDecimal = (seconds * 1000.0) / (60.0 * distanceM)
        val minutes = paceDecimal.toInt()
        val secs = ((paceDecimal - minutes) * 60).toInt()
        return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }

    private fun saveState() {
        serviceScope.launch {
            dataStoreManager.saveSessionState(_sessionState.value)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Training Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TrackPace Active")
            .setContentText("Timer: ${formatTime(_sessionState.value.elapsedTime)}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        val hundredths = (ms % 1000) / 10
        return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "training_channel"
        private const val NOTIFICATION_ID = 1
    }
}
