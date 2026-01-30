package com.example.stopwatchapp

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val trackUiVisible: Flow<Boolean> = _sessionState.map { it.isUiVisible }

    private var timerJob: Job? = null
    private var uiHideJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): TrainingService = this@TrainingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        dataStoreManager = DataStoreManager(applicationContext)
        tts = TextToSpeech(this, this)

        createNotificationChannel()
    }

    fun showUi() {
        Timber.d("TRLOG showUi")

        _sessionState.update { it.copy(isUiVisible = true) }
        uiHideJob?.cancel()

        // Only start the auto-hide timer if the session is currently running
        if (_sessionState.value.isRunning) {
            uiHideJob = serviceScope.launch {
                delay(4000)
                hideUi()
            }
        }
    }

    fun hideUi() {
        _sessionState.update { it.copy(isUiVisible = false) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isTtsReady = true
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
        return START_STICKY
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var lastTick = SystemClock.elapsedRealtime()

            while (isActive) {
                delay(100) // Smaller delay for smoother UI if showing hundredths

                val currentTick = SystemClock.elapsedRealtime()
                val timeDelta = currentTick - lastTick
                lastTick = currentTick

                _sessionState.update { it.copy(elapsedTime = it.elapsedTime + timeDelta) }
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
        if (_sessionState.value.isRunning) {
            addLap()
        }

        _sessionState.update {
            val newState = it.copy(isRunning = !it.isRunning)
            if (newState.isRunning) {
                startForeground(NOTIFICATION_ID, createNotification())
                startTimer()
            } else {
                stopTimer()
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            newState
        }

        showUi()
    }

    fun addLap() {
        showUi()
        _sessionState.update { current ->
            if (!current.isRunning) {
                return@update current
            }

            val durationMs = current.elapsedTime
            val distanceM = current.trackDistanceM
            val paceMinKm = PaceCalculator.calculatePace(durationMs, distanceM)
            val newLap = Lap(current.laps.size + 1, durationMs, distanceM, paceMinKm)
            speakPace(paceMinKm)
            current.copy(elapsedTime = 0, laps = (listOf(newLap) + current.laps).toPersistentList())
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

    private fun saveState() {
        val stateToSave = _sessionState.value
        serviceScope.launch { dataStoreManager.saveSessionState(stateToSave) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Training Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TrackPace Active")
            .setContentText("Timer: ${formatTime(_sessionState.value.elapsedTime)}")
            .setSmallIcon(R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        return builder.build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification())
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
        tts?.stop()
        tts?.shutdown()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "training_channel"
        private const val NOTIFICATION_ID = 1
    }
}
