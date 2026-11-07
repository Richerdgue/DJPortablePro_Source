package com.djportablepro.app.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.djportablepro.app.audio.DjPlayerController

@Composable
fun DeckPanelComponent(
    deckIndex: Int = 0,
    deckLabel: String = "DECK A",
    trackTitle: String = "El Malilla - Ñero Session 13",
    artistName: String = "Alu Mix",
    bpm: String = "128.00",
    key: String = "8B",
    isPlaying: Boolean = false,
    isSyncActive: Boolean = true,
    isCueActive: Boolean = false,
    onPlayClick: () -> Unit = {},
    onCueClick: () -> Unit = {},
    onSyncClick: () -> Unit = { DjPlayerController.toggleSync(deckIndex) },
    onLoadClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12151B))
            .border(width = 1.dp, color = Color(0xFF222834), shape = RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Deck Header (Label, Key, Sync)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (deckLabel == "DECK A") Color(0xFF00BCD4) else Color(0xFFFF4081))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = deckLabel,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Camelot Key Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1E2430))
                        .border(1.dp, Color(0xFF333B4D), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = key,
                        color = Color(0xFFFFD700), // Gold Key
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sync Button
            DeckStatusButton(
                text = "SYNC",
                active = isSyncActive,
                activeColor = Color(0xFF00E676),
                onClick = onSyncClick
            )
        }

        // 2. Track Info & Load Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF181D26))
                .border(1.dp, Color(0xFF272F3D), RoundedCornerShape(4.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Load Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A3447))
                    .clickable(onClick = onLoadClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LOAD",
                    color = Color(0xFF00BCD4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. Jog Wheel / Waveform Display Area (Traktor Style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0D1017))
                .border(1.dp, Color(0xFF1E2530), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$bpm BPM",
                    color = Color(0xFFFF6D00),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "JOG WHEEL / WAVEFORM",
                    color = Color(0xFF4A5568),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 4. Transport Controls (CUE & PLAY)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // CUE Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isCueActive) Color(0xFFFFD700) else Color(0xFF2A2012))
                    .border(1.dp, Color(0xFFFFAB00), RoundedCornerShape(4.dp))
                    .clickable(onClick = onCueClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CUE",
                    color = if (isCueActive) Color.Black else Color(0xFFFFAB00),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // PLAY Button
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isPlaying) Color(0xFF00E676) else Color(0xFF12281D))
                    .border(1.dp, Color(0xFF00E676), RoundedCornerShape(4.dp))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPlaying) "PAUSE" else "PLAY",
                    color = if (isPlaying) Color.Black else Color(0xFF00E676),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DeckStatusButton(text: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) activeColor else Color(0xFF1E2430))
            .border(1.dp, if (active) activeColor else Color(0xFF333B4D), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) Color.Black else Color(0xFF8A93A0),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(name = "Traktor Deck Panel Preview", widthDp = 340, heightDp = 260)
@Composable
fun DeckPanelComponentPreview() {
    DeckPanelComponent()
}
