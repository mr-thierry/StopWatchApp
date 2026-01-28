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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stopwatchapp.Lap
import com.example.stopwatchapp.ui.theme.StopWatchAppTheme
import java.util.Locale

@Composable
fun TrackPaceScreen(service: TrainingService?) {
    val state by service?.sessionState?.collectAsState() ?: remember { mutableStateOf(SessionState()) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Session?") },
            text = { Text("This will clear all lap data.") },
            confirmButton = {
                TextButton(onClick = {
                    service?.resetSession()
                    showResetDialog = false
                }) {
                    Text("Reset", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        bottomBar = {
            TrackSelectionToggle(state.trackDistanceM) { distance ->
                service?.setTrackDistance(distance)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "LAP", value = "${state.laps.size + 1}")
                StatItem(label = "TOTAL DIST", value = "${state.laps.size * state.trackDistanceM}m")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Last Lap Pace (Highlight)
            val lastPace = state.laps.firstOrNull()?.paceMinKm ?: "--:--"
            Text(
                text = lastPace,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Green
                )
            )
            Text(
                text = "MIN/KM (LAST LAP)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Current Timer
            Text(
                text = formatTime(state.elapsedTime),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 60.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { service?.toggleStartStop() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) Color.DarkGray else Color.Blue
                    ),
                    modifier = Modifier.weight(1f).padding(8.dp)
                ) {
                    Text(if (state.isRunning) "STOP" else "START")
                }

                Button(
                    onClick = { service?.addLap() },
                    enabled = state.isRunning,
                    modifier = Modifier.weight(1f).padding(8.dp)
                ) {
                    Text("LAP")
                }

                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.weight(1f).padding(8.dp)
                ) {
                    Text("RESET", color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lap List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(state.laps) { lap ->
                    LapRow(lap)
                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                }
            }
        }
    }
}


@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@Composable
fun LapRow(lap: Lap) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "L${lap.lapNumber}", color = Color.Gray)
        Text(text = formatTime(lap.durationMs), color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(text = lap.paceMinKm, color = Color.Green, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TrackSelectionToggle(currentDistance: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .padding(16.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackOption("Delson (370m)", currentDistance == 370) { onSelect(370) }
        Spacer(modifier = Modifier.width(24.dp))
        TrackOption("Dix30 (630m)", currentDistance == 630) { onSelect(630) }
    }
}

@Composable
fun TrackOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    val hundredths = (ms % 1000) / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, hundredths)
}
