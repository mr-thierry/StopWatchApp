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
import com.example.stopwatchapp.TrainingService
import java.util.Locale
import com.example.stopwatchapp.SessionState
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPaceScreen(service: TrainingService?) {
    val state by service?.sessionState?.collectAsState() ?: remember { mutableStateOf(SessionState()) }
    var showResetDialog by remember { mutableStateOf(false) }

    Timber.d("TRLOG TrackPaceScreen state=$state service:$service")

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
                    Text("Reset", color = MaterialTheme.colorScheme.error)
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TRACK PACE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                TrackSelectionToggle(state.trackDistanceM) { distance ->
                    service?.setTrackDistance(distance)
                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "LAP", value = "${state.laps.size + 1}")
                VerticalDivider(modifier = Modifier.height(40.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StatItem(label = "TOTAL DIST", value = "${state.laps.size * state.trackDistanceM}m")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val lastPace = state.laps.firstOrNull()?.paceMinKm ?: "--:--"
                    Text(
                        text = lastPace,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "MIN/KM LAST LAP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = formatTime(state.elapsedTime),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 64.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { service?.toggleStartStop() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (state.isRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1.2f).height(64.dp)
                ) {
                    Text(
                        if (state.isRunning) "STOP" else "START",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = { service?.addLap() },
                    enabled = state.isRunning,
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Text("LAP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                ) {
                    Text("RST", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lap List Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LAP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                Text("TIME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                Text("PACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            }

            // Lap List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(state.laps) { lap ->
                    LapRow(lap)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LapRow(lap: Lap) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%02d", lap.lapNumber),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = formatTime(lap.durationMs),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = lap.paceMinKm,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun TrackSelectionToggle(currentDistance: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackOption("Delson (370m)", currentDistance == 370, Modifier.weight(1f)) { onSelect(370) }
        TrackOption("Dix30 (630m)", currentDistance == 630, Modifier.weight(1f)) { onSelect(630) }
    }
}

@Composable
fun TrackOption(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (isSelected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text = label, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text = label, maxLines = 1)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    val hundredths = (ms % 1000) / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, hundredths)
}
