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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import com.example.stopwatchapp.ui.TrackPaceScreen
import com.example.stopwatchapp.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

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
                    val state by service.sessionState.collectAsState()

                    // Fullscreen control and luminosity logic
                    LaunchedEffect(state.isUiVisible) {
                        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                        val layoutParams = window.attributes
                        if (!state.isUiVisible) {
                            windowInsetsController.hide(systemBars())
                            windowInsetsController.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            layoutParams.screenBrightness = 0.01f
                        } else {
                            windowInsetsController.show(systemBars())
                            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        }
                        window.attributes = layoutParams
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        TrackPaceScreen(
                            state = state,
                            onResetClick = { service.resetSession() },
                            onStopClick = { service.toggleStartStop() },
                            onAddLapClick = { service.addLap() },
                            selectTrack = { distance -> service.setTrackDistance(distance) },
                        )

                        AnimatedVisibility(
                            visible = !state.isUiVisible,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            var containerSize by remember { mutableStateOf(IntSize.Zero) }
                            var iconOffset by remember { mutableStateOf(IntOffset.Zero) }
                            val iconSize = 256.dp
                            val density = LocalDensity.current
                            val iconSizePx = with(density) { iconSize.roundToPx() }

                            LaunchedEffect(containerSize, state.isUiVisible) {
                                if (containerSize != IntSize.Zero && !state.isUiVisible) {
                                    while (true) {
                                        val maxX = (containerSize.width - iconSizePx).coerceAtLeast(0)
                                        val maxY = (containerSize.height - iconSizePx).coerceAtLeast(0)
                                        iconOffset = IntOffset(
                                            x = Random.nextInt(maxX + 1),
                                            y = Random.nextInt(maxY + 1)
                                        )
                                        delay(10000)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .onSizeChanged { containerSize = it }
                                    .clickable {
                                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                                        service.showUi()
                                    }
                            ) {
                                Image(
                                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(iconSize)
                                        .offset { iconOffset }
                                )
                            }
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
            KeyEvent.KEYCODE_VOLUME_UP -> {
                trainingService?.showUi()
                trainingService?.toggleStartStop()
                return true
            }

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
