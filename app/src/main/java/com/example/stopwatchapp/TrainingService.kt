package com.example.stopwatchapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.Locale

class TrainingService : Service(), TextToSpeech.OnInitListener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var dataStoreManager: DataStoreManager
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var timerJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): TrainingService = this@TrainingService
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("TRLOG onBind")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        
        Timber.d("TRLOG onCreate")
        
        dataStoreManager = DataStoreManager(applicationContext)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        
        serviceScope.launch {
            dataStoreManager.sessionState.collect { savedState ->
                Timber.d("TRLOG dataStoreManager.sessionState.collect: $savedState")
                // Initial load
                if (_sessionState.value == SessionState()) {
                    Timber.d("TRLOG Initial load of session state")
                    _sessionState.value = savedState
                    if (savedState.isRunning) {
                        Timber.d("TRLOG Session was running, restarting")
                        startForeground(NOTIFICATION_ID, createNotification())
                        startTimer()
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.e("TRLOG TTS: Language not supported")
            } else {
                isTtsReady = true
                Timber.d("TRLOG TTS: Ready")
            }
        } else {
            Timber.e("TRLOG TTS: Initialization failed")
        }
    }

    private fun speakPace(pace: String) {
        if (isTtsReady) {
            val parts = pace.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].toIntOrNull() ?: 0
                val seconds = parts[1].toIntOrNull() ?: 0

                val minText = if (minutes == 1) "minute" else "minutes"
                val secText = if (seconds == 1) "second" else "seconds"

                val speechText = "$minutes $minText $seconds $secText per kilometer"
                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "PaceId")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("TRLOG onStartCommand")
        return START_STICKY
    }

    private fun startTimer() {
        Timber.d("TRLOG startTimer")
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
        Timber.d("TRLOG stopTimer")
        timerJob?.cancel()
        timerJob = null
        saveState()
    }

    fun toggleStartStop() {
        Timber.d("TRLOG toggleStartStop")

        if (_sessionState.value.isRunning) {
            addLap()
        }

        _sessionState.update { 
            val newState = it.copy(isRunning = !it.isRunning)
            Timber.d("TRLOG toggleStartStop: newState.isRunning=${newState.isRunning}")
            if (newState.isRunning) {
                startForeground(NOTIFICATION_ID, createNotification())
                startTimer()
            } else {
                stopTimer()
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            newState
        }
    }

    fun addLap() {
        Timber.d("TRLOG addLap")
        _sessionState.update { current ->
            if (!current.isRunning) {
                Timber.d("TRLOG addLap: Not running, skipping")
                return@update current
            }
            
            val durationMs = current.elapsedTime
            val distanceM = current.trackDistanceM
            val paceMinKm = calculatePace(durationMs, distanceM)
            
            val newLap = Lap(
                lapNumber = current.laps.size + 1,
                durationMs = durationMs,
                distanceM = distanceM,
                paceMinKm = paceMinKm
            )
            Timber.d("TRLOG addLap: New lap=$newLap")

            speakPace(paceMinKm)
            
            current.copy(
                elapsedTime = 0,
                laps = listOf(newLap) + current.laps
            )
        }
        saveState()
    }

    fun resetSession() {
        Timber.d("TRLOG resetSession")
        stopTimer()
        _sessionState.value = SessionState(trackDistanceM = _sessionState.value.trackDistanceM)
        saveState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun setTrackDistance(distanceM: Int) {
        Timber.d("TRLOG setTrackDistance: distanceM=$distanceM")
        _sessionState.update { it.copy(trackDistanceM = distanceM) }
        saveState()
    }

    private fun calculatePace(ms: Long, distanceM: Int): String {
        Timber.d("TRLOG calculatePace: ms=$ms, distanceM=$distanceM")
        if (distanceM == 0) return "0:00"
        val seconds = ms / 1000.0
        val paceDecimal = (seconds * 1000.0) / (60.0 * distanceM)
        val minutes = paceDecimal.toInt()
        val secs = ((paceDecimal - minutes) * 60).toInt()
        val result = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
        Timber.d("TRLOG calculatePace result: $result")
        return result
    }

    private fun saveState() {
        val stateToSave = _sessionState.value
        Timber.d("TRLOG saveState: $stateToSave")
        serviceScope.launch {
            dataStoreManager.saveSessionState(stateToSave)
        }
    }

    private fun createNotificationChannel() {
        Timber.d("TRLOG createNotificationChannel")
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
        Timber.d("TRLOG onDestroy")
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "training_channel"
        private const val NOTIFICATION_ID = 1
    }
}
