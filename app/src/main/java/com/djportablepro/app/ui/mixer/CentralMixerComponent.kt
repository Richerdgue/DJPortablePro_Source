package com.djportablepro.app.ui.mixer

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CentralMixerComponent() {
    // 0 = EQ & Filter Mode, 1 = Volume Faders & Levels Mode
    var currentMode by remember { mutableStateOf(0) }
    val modeTitles = listOf("MIXER EQ", "VOLUMES & VU")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1017))
            .border(width = 1.dp, color = Color(0xFF222834), shape = RoundedCornerShape(6.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Center Header with Left/Right Arrows for Mode Switching
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E2430))
                .padding(vertical = 4.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Arrow
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2A3447))
                    .clickable { currentMode = if (currentMode == 0) 1 else 0 },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "◄", color = Color(0xFFFF6D00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Mode Title
            Text(
                text = modeTitles[currentMode],
                color = Color(0xFFFF6D00), // Traktor Orange
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Right Arrow
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2A3447))
                    .clickable { currentMode = if (currentMode == 0) 1 else 0 },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "►", color = Color(0xFFFF6D00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 2. Dynamic Content Based on Mode
        if (currentMode == 0) {
            // --- MODE 0: EQ & FILTER VIEW (Spacious & Clean) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelEqStrip(channelLabel = "1")
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF222834)))
                ChannelEqStrip(channelLabel = "2")
            }
        } else {
            // --- MODE 1: VOLUME FADERS & VU METERS VIEW (Spacious) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelVolumeStrip(channelLabel = "1", accentColor = Color(0xFF00BCD4))
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF222834)))
                ChannelVolumeStrip(channelLabel = "2", accentColor = Color(0xFFFF4081))
            }
        }

        // 3. Crossfader Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF141A23))
                .border(1.dp, Color(0xFF272F3D), RoundedCornerShape(4.dp))
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "CROSSFADER",
                color = Color(0xFF8A93A0),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF090B10))
                    .border(1.dp, Color(0xFF202836), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF333B4D))
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF00BCD4))
                )
            }
        }
    }
}

@Composable
fun ChannelEqStrip(channelLabel: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.width(42.dp).fillMaxHeight()
    ) {
        Text(text = channelLabel, color = Color(0xFFC5CBD3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        MixerKnob(label = "GAIN")
        MixerKnob(label = "HI")
        MixerKnob(label = "MID")
        MixerKnob(label = "LOW")
        MixerKnob(label = "FIL", isFilter = true)
    }
}

@Composable
fun ChannelVolumeStrip(channelLabel: String, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(50.dp).fillMaxHeight()
    ) {
        Text(text = "CH $channelLabel VOL", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Large Spacious Volume Fader
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF090B10))
                .border(1.dp, Color(0xFF202836), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF202836))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun MixerKnob(label: String, isFilter: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isFilter) Color(0xFF261C12) else Color(0xFF181D26))
                .border(
                    width = 1.dp,
                    color = if (isFilter) Color(0xFFFF6D00) else Color(0xFF323B4C),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(8.dp)
                    .offset(y = (-4).dp)
                    .background(if (isFilter) Color(0xFFFF6D00) else Color(0xFF00BCD4))
            )
        }
        Text(
            text = label,
            color = Color(0xFF8A93A0),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(name = "Traktor Central Mixer Paged Preview", widthDp = 200, heightDp = 280)
@Composable
fun CentralMixerComponentPreview() {
    CentralMixerComponent()
}
