package com.djportablepro.app.ui.deck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djportablepro.app.audio.DjPlayerController
import kotlin.random.Random

@Composable
fun DjDeckView(
    deckIndex: Int = 0,
    deckLabel: String = "DECK A",
    trackTitle: String = "El Malilla - Ñero Session 13",
    artistName: String = "Alu Mix",
    bpm: String = "128.00",
    currentTime: String = "01:24",
    remainingTime: String = "-02:15",
    isSyncActive: Boolean = true,
    isCueActive: Boolean = false,
    isCupActive: Boolean = false,
    isPlaying: Boolean = false,
    onSyncClick: () -> Unit = { DjPlayerController.toggleSync(deckIndex) },
    onCueClick: () -> Unit = {},
    onCupClick: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14171F))
            .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. COMPACT HEADER (Title, Artist, BPM, Time)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0D1017))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Deck Badge & Track Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (deckLabel.contains("A")) Color(0xFF00BCD4) else Color(0xFFFF4081))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = deckLabel,
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artistName,
                        color = Color(0xFF8A93A0),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // BPM & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$bpm BPM",
                        color = Color(0xFFFF9800), // Traktor Orange
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currentTime,
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = remainingTime,
                        color = Color(0xFF8A93A0),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // 2. CENTRAL WAVEFORM SECTION (Canvas Simulated Audio Waveform)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0D14))
                .border(1.dp, Color(0xFF1E2533), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            WaveformCanvas()
        }

        // 3. BOTTOM TRANSPORT BUTTONS (CUE, CUP, SYNC, PLAY)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TransportButton(
                text = "CUE",
                isActive = isCueActive,
                activeColor = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
                onClick = onCueClick
            )
            TransportButton(
                text = "CUP",
                isActive = isCupActive,
                activeColor = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
                onClick = onCupClick
            )
            TransportButton(
                text = "SYNC",
                isActive = isSyncActive,
                activeColor = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
                onClick = onSyncClick
            )
            TransportButton(
                text = "PLAY",
                isActive = isPlaying,
                activeColor = Color(0xFF00E676), // Active play green or traktor orange
                modifier = Modifier.weight(1.5f),
                onClick = onPlayClick
            )
        }
    }
}

@Composable
fun WaveformCanvas() {
    val barHeights = remember {
        List(60) { Random.nextFloat() * 0.8f + 0.2f }
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val barWidth = width / barHeights.size

        barHeights.forEachIndexed { index, amp ->
            val x = index * barWidth
            val barH = amp * centerY
            val color = if (index < barHeights.size / 2) Color(0xFF00BCD4) else Color(0xFFFF9800)

            drawLine(
                color = color,
                start = Offset(x, centerY - barH),
                end = Offset(x, centerY + barH),
                strokeWidth = barWidth * 0.6f
            )
        }

        // Playhead center line
        drawLine(
            color = Color.Red,
            start = Offset(width / 2f, 0f),
            end = Offset(width / 2f, height),
            strokeWidth = 2f
        )
    }
}

@Composable
fun TransportButton(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) activeColor else Color(0xFF1E222B))
            .border(1.dp, if (isActive) activeColor else Color(0xFF2E384A), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else Color(0xFFC5CBD3),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(name = "DjDeckView Preview", widthDp = 400, heightDp = 180)
@Composable
fun DjDeckViewPreview() {
    DjDeckView()
}
