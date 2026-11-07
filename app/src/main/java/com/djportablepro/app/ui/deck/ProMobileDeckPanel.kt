package com.djportablepro.app.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.djportablepro.app.audio.DjPlayerController

@Composable
fun ProMobileDeckPanel(
    deckIndex: Int = 0,
    deckLabel: String = "A",
    trackTitle: String = "120 - SIN TI - NATUSHA - [ DJ L...",
    currentTime: String = "00:25",
    isPlaying: Boolean = false,
    isVinylActive: Boolean = true,
    isCueActive: Boolean = false,
    isSyncActive: Boolean = true,
    onPlayClick: () -> Unit = {},
    onCueClick: () -> Unit = {},
    onSyncClick: () -> Unit = { DjPlayerController.toggleSync(deckIndex) },
    onVinylClick: () -> Unit = {},
    onLoadClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Pure clean black background as in user design
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left Side: Pitch Fader & Reset Button (Minimalist Flat Style)
        Column(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Sync Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E222B))
                    .clickable(onClick = onSyncClick)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SYNC",
                    color = Color(0xFF8A93A0),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Vertical Pitch Fader Track
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141820)),
                contentAlignment = Alignment.Center
            ) {
                // Hardware 3D Metallic Pitch Fader Cap / Thumb (Larger 42dp x 30dp for high touch accessibility)
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF4A5262),
                                    Color(0xFF2A303C),
                                    Color(0xFF181B22)
                                )
                            )
                        )
                        .border(1.5.dp, Color(0xFF5B6476), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Center Tactile Grip Line Notch
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.70f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (deckLabel == "A") Color(0xFFFF9100) else Color(0xFF00E5FF))
                    )
                }
            }

            // RESET Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF141820))
                    .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(4.dp))
                    .clickable { }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RESET",
                    color = Color(0xFF3B82F6),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Center/Right: Track Info, Clean Jog Wheel & Transport
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Track Info & Load Button (Flat Minimalist)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackTitle,
                        color = Color(0xFFEF4444), // Red accent matching user design
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTime,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Load Music Note Button (Blue tint matching user design)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E3A8A))
                        .clickable(onClick = onLoadClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎵", fontSize = 14.sp)
                }
            }

            // Clean Flat Vinyl Jog Wheel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111318))
                        .border(3.dp, Color(0xFF252D3D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Center spindle / Badge A/B
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F2937)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = deckLabel,
                            color = Color(0xFF3B82F6),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Transport Bar (Flat Minimalist Buttons matching user design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // VINYL Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E222B))
                        .clickable(onClick = onVinylClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VINYL",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // CUE Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E222B))
                        .clickable(onClick = onCueClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CUE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // PLAY / PAUSE Button (Blue pill matching user design)
                Box(
                    modifier = Modifier
                        .weight(1.8f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0xFF00A8FF))
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(name = "Pro Mobile Deck Panel Clean Preview", widthDp = 380, heightDp = 240)
@Composable
fun ProMobileDeckPanelPreview() {
    ProMobileDeckPanel()
}
