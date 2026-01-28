package com.example.stopwatchapp

import TrackPaceScreen
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.stopwatchapp.ui.theme.StopWatchAppTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var trainingService by mutableStateOf<TrainingService?>(null)
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TrainingService.LocalBinder
            trainingService = binder.getService()

            Timber.d("TRLOG MainActivity onServiceConnected trainingService=$trainingService")

            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {

            Timber.d("TRLOG MainActivity onServiceDisconnected")

            isBound = false
            trainingService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intent = Intent(this, TrainingService::class.java)
        // Start the service to ensure it has a lifecycle independent of the Activity binding
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        setContent {
            StopWatchAppTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    TrackPaceScreen(trainingService)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                trainingService?.toggleStartStop()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
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
