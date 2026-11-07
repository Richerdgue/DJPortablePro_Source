package com.djportablepro.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun SettingsMenuComponent(
    onCloseClick: () -> Unit = {}
) {
    var selectedSkin by remember { mutableStateOf("Traktor Pro") }
    var audioBuffer by remember { mutableStateOf("128 frames (Ultra Low Latency)") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1017))
            .border(width = 1.dp, color = Color(0xFF222834), shape = RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Settings Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFF6D00))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SETTINGS",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "DJPortablePro Preferences",
                    color = Color(0xFF8A93A0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Close Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E2430))
                    .clickable(onClick = onCloseClick)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CLOSE",
                    color = Color(0xFFFF6D00),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Settings Sections (LazyColumn)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SettingsCategoryTitle("AUDIO ENGINE (OBOE)")
                SettingsToggleItem(title = "Audio Buffer Size", value = audioBuffer, onClick = {
                    audioBuffer = if (audioBuffer.contains("128")) "256 frames (Stable)" else "128 frames (Ultra Low Latency)"
                })
                SettingsToggleItem(title = "PFL / Headphone Split", value = "Enabled (Split Mono)", onClick = {})
            }

            item {
                SettingsCategoryTitle("INTERFACE SKIN & THEME")
                SettingsToggleItem(title = "DJ Layout Skin", value = selectedSkin, onClick = {
                    selectedSkin = if (selectedSkin == "Traktor Pro") "Virtual DJ" else "Traktor Pro"
                })
                SettingsToggleItem(title = "High Contrast Waveform", value = "3-Band (RGB)", onClick = {})
            }

            item {
                SettingsCategoryTitle("MIDI CONTROLLER")
                SettingsToggleItem(title = "Connected Device", value = "Hercules Inpulse 500", onClick = {})
                SettingsToggleItem(title = "MIDI Learn Mode", value = "Tap to configure", onClick = {})
            }
        }
    }
}

@Composable
fun SettingsCategoryTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF00BCD4),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp),
        letterSpacing = 1.sp
    )
}

@Composable
fun SettingsToggleItem(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141A23))
            .border(1.dp, Color(0xFF202836), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E2430))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                color = Color(0xFFFF6D00),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(name = "Traktor Settings Menu Preview", widthDp = 400, heightDp = 260)
@Composable
fun SettingsMenuComponentPreview() {
    SettingsMenuComponent()
}
