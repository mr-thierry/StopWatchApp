package com.example.stopwatchapp

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import com.example.stopwatchapp.ui.Overlay
import com.example.stopwatchapp.ui.TrackPaceScreen
import com.example.stopwatchapp.ui.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.random.Random
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private var trainingService by mutableStateOf<TrainingService?>(null)
    private var isBound = false
    private var toneGenerator: ToneGenerator? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TrainingService.LocalBinder
            trainingService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            trainingService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Allow showing over lockscreen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        val intent = Intent(this, TrainingService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        setContent {
            AppTheme(darkTheme = true) {
                val service = trainingService
                if (service != null) {
                    val trackUiVisible by service.trackUiVisible.collectAsState(true)

                    // Fullscreen control and luminosity logic
                    LaunchedEffect(trackUiVisible) {
                        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                        val layoutParams = window.attributes
                        if (trackUiVisible) {
                            windowInsetsController.show(systemBars())
                            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        } else {
                            windowInsetsController.hide(systemBars())
                            windowInsetsController.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            layoutParams.screenBrightness = 0.01f
                        }
                        window.attributes = layoutParams
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        val state by service.sessionState.collectAsState()
                        if (trackUiVisible) {
                            TrackPaceScreen(
                                isRunning = state.isRunning,
                                elapsedTime = { state.elapsedTime },
                                laps = state.laps,
                                trackDistanceM = state.trackDistanceM,
                                onResetClick = { service.resetSession() },
                                onToggleStartPauseClick = { service.toggleStartPause() },
                                onSplitLastLapClick = { service.splitLastLap() },
                                onDeleteLapClick = { lapNumber -> service.deleteLap(lapNumber) },
                                onAddLapClick = { service.addLap() },
                                selectTrack = { distance -> service.setTrackDistance(distance) },
                            )
                        }

                        AnimatedVisibility(
                            visible = !trackUiVisible,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Overlay(
                                laps = state.laps,
                                onOverlayClick = {
                                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                                    service.showUi()
                                }
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        Text("Connecting to service...", color = Color.White)
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                trainingService?.showUi()
                trainingService?.addLap()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
