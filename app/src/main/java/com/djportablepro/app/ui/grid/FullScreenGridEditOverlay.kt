package com.djportablepro.app.ui.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djportablepro.app.audio.DeckAudioState
import com.djportablepro.app.audio.DjPlayerController
import java.util.Locale

/**
 * Full-Screen Beatgrid & Parallel Waveforms Editor Overlay.
 * Offers maximum visibility and ultra-precise touch controls for mobile & tablet screens.
 */
@Composable
fun FullScreenGridEditOverlay(
    deck1State: DeckAudioState,
    deck2State: DeckAudioState,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1217))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- 1. TOP HEADER & CLOSE BUTTON ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFF181C24), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "GRID EDIT & PARALLEL WAVEFORMS",
                    style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )

                BasicText(
                    text = String.format(Locale.US, "A: %.1f BPM | B: %.1f BPM", deck1State.bpm, deck2State.bpm),
                    style = TextStyle(color = Color(0xFF00A3FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF3366))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "CERRAR  X",
                        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 2. DECK A CONTROLS (BLUE THEME) ---
            DeckGridControlBar(
                deckIndex = 0,
                deckLabel = "DECK A",
                accentColor = Color(0xFF00A3FF),
                offsetMs = deck1State.userOffsetMs,
                bpm = deck1State.bpm
            )

            Spacer(modifier = Modifier.height(6.dp))

            // --- 3. PARALLEL WAVEFORMS CANVAS (DECK A BLUE / DECK B RED) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0C0F))
                    .border(1.dp, Color(0xFF222834), RoundedCornerShape(8.dp))
            ) {
                ParallelWaveformsCanvas(
                    deck1State = deck1State,
                    deck2State = deck2State,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- 4. DECK B CONTROLS (RED THEME) ---
            DeckGridControlBar(
                deckIndex = 1,
                deckLabel = "DECK B",
                accentColor = Color(0xFFFF3366),
                offsetMs = deck2State.userOffsetMs,
                bpm = deck2State.bpm
            )
        }
    }
}

@Composable
fun DeckGridControlBar(
    deckIndex: Int,
    deckLabel: String,
    accentColor: Color,
    offsetMs: Double,
    bpm: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF181C24), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = deckLabel,
            style = TextStyle(color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.width(52.dp)
        )

        // Control Buttons
        GridActionButton(label = "< GRID", onClick = { DjPlayerController.nudgeBeatgrid(deckIndex, -10.0) })
        GridActionButton(label = "GRID >", onClick = { DjPlayerController.nudgeBeatgrid(deckIndex, 10.0) })
        GridActionButton(label = "SET 1", onClick = { DjPlayerController.setBeat1AtCurrentPosition(deckIndex) })
        GridActionButton(label = "TAP", onClick = { DjPlayerController.processTapTempo(deckIndex) })
        GridActionButton(label = "RESET", onClick = { DjPlayerController.resetBeatgrid(deckIndex) }, isDanger = true)

        Spacer(modifier = Modifier.weight(1f))

        BasicText(
            text = String.format(Locale.US, "Offset: %+.0fms", offsetMs),
            style = TextStyle(color = Color(0xFF8A93A0), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
fun GridActionButton(
    label: String,
    onClick: () -> Unit,
    isDanger: Boolean = false
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isDanger) Color(0xFF331822) else Color(0xFF222834))
            .border(1.dp, if (isDanger) Color(0xFFFF3366) else Color(0xFF323A4B), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = if (isDanger) Color(0xFFFF3366) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun ParallelWaveformsCanvas(
    deck1State: DeckAudioState,
    deck2State: DeckAudioState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val halfHeight = height / 2.0f
        val centerX = width / 2.0f

        // 1. Background split line
        drawLine(
            color = Color(0xFF1E2430),
            start = Offset(0f, halfHeight),
            end = Offset(width, halfHeight),
            strokeWidth = 2f
        )

        // 2. Draw Deck A Waveform (Top - Blue)
        drawWaveformEnvelope(
            waveformBytes = deck1State.waveformBytes,
            color = Color(0xFF00A3FF),
            topY = 0f,
            bottomY = halfHeight,
            bpm = deck1State.bpm,
            firstBeatMs = deck1State.firstBeatMs,
            currentPosMs = deck1State.currentPositionMs,
            centerX = centerX
        )

        // 3. Draw Deck B Waveform (Bottom - Red)
        drawWaveformEnvelope(
            waveformBytes = deck2State.waveformBytes,
            color = Color(0xFFFF3366),
            topY = halfHeight,
            bottomY = height,
            bpm = deck2State.bpm,
            firstBeatMs = deck2State.firstBeatMs,
            currentPosMs = deck2State.currentPositionMs,
            centerX = centerX
        )

        // 4. Center Playhead Line (White Vertical Line)
        drawLine(
            color = Color.White,
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = 3f
        )
    }
}

private fun DrawScope.drawWaveformEnvelope(
    waveformBytes: ByteArray?,
    color: Color,
    topY: Float,
    bottomY: Float,
    bpm: Float,
    firstBeatMs: Double,
    currentPosMs: Long,
    centerX: Float
) {
    val h = bottomY - topY
    val midY = topY + (h / 2.0f)
    val width = size.width

    if (waveformBytes != null && waveformBytes.isNotEmpty()) {
        val bytesPerPixel = 4
        val totalPoints = waveformBytes.size / bytesPerPixel
        val visiblePoints = 200
        val centerIndex = ((currentPosMs.toFloat() / 180000f) * totalPoints).toInt().coerceIn(0, totalPoints - 1)

        val startIndex = (centerIndex - (visiblePoints / 2)).coerceAtLeast(0)
        val endIndex = (centerIndex + (visiblePoints / 2)).coerceAtMost(totalPoints - 1)

        val stepX = width / visiblePoints.toFloat()

        // --- VIRTUALDJ STYLE SMOOTH THREAD & RIBBON PATH (SINGLE BATCHED GPU DRAW) ---
        val ribbonPath = Path()
        val threadPath = Path()

        var isFirst = true
        for (i in startIndex until endIndex) {
            val byteIdx = i * bytesPerPixel
            if (byteIdx + 2 < waveformBytes.size) {
                val ampR = (waveformBytes[byteIdx].toInt() and 0xFF) / 255.0f
                val x = centerX + (i - centerIndex) * stepX
                val barHeight = (ampR * (h / 2.0f) * 0.88f).coerceAtLeast(1.5f)

                if (isFirst) {
                    ribbonPath.moveTo(x, midY - barHeight)
                    threadPath.moveTo(x, midY)
                    isFirst = false
                } else {
                    ribbonPath.lineTo(x, midY - barHeight)
                    threadPath.lineTo(x, midY)
                }
            }
        }

        for (i in (endIndex - 1) downTo startIndex) {
            val byteIdx = i * bytesPerPixel
            if (byteIdx + 2 < waveformBytes.size) {
                val ampR = (waveformBytes[byteIdx].toInt() and 0xFF) / 255.0f
                val x = centerX + (i - centerIndex) * stepX
                val barHeight = (ampR * (h / 2.0f) * 0.88f).coerceAtLeast(1.5f)
                ribbonPath.lineTo(x, midY + barHeight)
            }
        }
        ribbonPath.close()

        // 1. Draw Ribbon Envelope Fill
        drawPath(
            path = ribbonPath,
            color = color.copy(alpha = 0.50f)
        )
    } else {
        // Fallback placeholder thread line
        drawLine(
            color = color.copy(alpha = 0.4f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 2f
        )
    }

    // Draw Beat Ticks (Red for Beat 1, White for Beats 2, 3, 4)
    if (bpm > 50.0f) {
        val beatIntervalMs = (60000.0 / bpm)
        val visibleTimeRangeMs = 4000.0 // 4 seconds window
        val startBeatIdx = ((currentPosMs - visibleTimeRangeMs / 2 - firstBeatMs) / beatIntervalMs).toInt() - 2
        val endBeatIdx = ((currentPosMs + visibleTimeRangeMs / 2 - firstBeatMs) / beatIntervalMs).toInt() + 2

        for (b in startBeatIdx..endBeatIdx) {
            val beatTimeMs = firstBeatMs + (b * beatIntervalMs)
            val timeOffsetMs = beatTimeMs - currentPosMs
            val x = centerX + ((timeOffsetMs / (visibleTimeRangeMs / 2)) * (width / 2.0f)).toFloat()

            if (x in 0f..width) {
                val isBeat1 = (b % 4 == 0)
                drawLine(
                    color = if (isBeat1) Color(0xFFFF3366) else Color.White.copy(alpha = 0.6f),
                    start = Offset(x, topY + 4f),
                    end = Offset(x, bottomY - 4f),
                    strokeWidth = if (isBeat1) 3f else 1.5f
                )
            }
        }
    }
}
