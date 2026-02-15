package com.example.stopwatchapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.stopwatchapp.Lap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val formatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun Overlay(
    laps: ImmutableList<Lap>,
    onOverlayClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onOverlayClick() }
    ) {
        var iconOffset by remember { mutableStateOf(IntOffset.Zero) }
        val iconSize = 256.dp
        val density = LocalDensity.current

        LaunchedEffect(Unit) {
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }
            val iconSizePx = with(density) { iconSize.roundToPx() }

            while (true) {
                val maxX = (maxWidthPx - iconSizePx).coerceAtLeast(0F)
                val maxY = (maxHeightPx - iconSizePx).coerceAtLeast(0F)
                iconOffset = IntOffset(
                    x = Random.nextInt(maxX.toInt() + 1),
                    y = Random.nextInt(maxY.toInt() + 1)
                )
                delay(10000)
            }
        }

        Column(
            modifier = Modifier
                .size(iconSize)
                .offset { iconOffset },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            val current = LocalDateTime.now()

            Text(
                text = "${current.format(formatter)}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${laps.size} laps",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${laps.sumOf { it.distanceM }}m",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text("")
        }
    }
}


@Composable
@Preview
fun OverlayPreview() {
    Overlay(
        laps = persistentListOf(
            Lap(1, 45000, 370, "2:01"),
            Lap(2, 42000, 370, "1:53"),
        ),
        onOverlayClick = { }
    )
}
