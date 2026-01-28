package com.example.stopwatchapp

import TrackPaceScreen
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.stopwatchapp.ui.theme.AppTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var trainingService by mutableStateOf<TrainingService?>(null)
    private var isBound = false

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

        val intent = Intent(this, TrainingService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        setContent {
            AppTheme(darkTheme = true) {
                val service = trainingService
                if (service != null) {
                    val state by service.sessionState.collectAsState()
                    
                    // Fullscreen control logic
                    LaunchedEffect(state.isUiVisible) {
                        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                        if (!state.isUiVisible) {
                            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        TrackPaceScreen(service)

                        AnimatedVisibility(
                            visible = !state.isUiVisible,
                            enter= fadeIn(),
                            exit =  fadeOut(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
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
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
