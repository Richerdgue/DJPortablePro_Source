package com.djportablepro.app.ui.topbar

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopBarHeader(
    bpm: String = "124.00",
    cpuUsage: String = "12%",
    batteryLevel: String = "85%",
    onGridClick: () -> Unit = {},
    onBrowseClick: () -> Unit = {},
    onFxClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF0F1115))
            .border(width = 1.dp, color = Color(0xFF22262E))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: App Brand & Status LED
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF00E676)) // Pro Green Status LED
            )
            Text(
                text = "DJ PORTABLE PRO",
                color = Color(0xFFE0E0E0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        // Center: Master BPM & Telemetry
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MASTER BPM",
                    color = Color(0xFF8A93A0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = bpm,
                    color = Color(0xFFFF6D00), // Traktor Orange Accent
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "CPU:",
                    color = Color(0xFF8A93A0),
                    fontSize = 10.sp
                )
                Text(
                    text = cpuUsage,
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "BAT:",
                    color = Color(0xFF8A93A0),
                    fontSize = 10.sp
                )
                Text(
                    text = batteryLevel,
                    color = Color(0xFFE0E0E0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Right: Quick Action Buttons (GRID, Browse, FX, Settings)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopBarButton(text = "GRID", onClick = onGridClick)
            TopBarButton(text = "BROWSE", onClick = onBrowseClick)
            TopBarButton(text = "FX", onClick = onFxClick)
            TopBarButton(text = "SETTINGS", onClick = onSettingsClick)
        }
    }
}

@Composable
fun TopBarButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E232D))
            .border(width = 1.dp, color = Color(0xFF323B4C), shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFC5CBD3),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(name = "Traktor TopBar Preview", widthDp = 896, heightDp = 44)
@Composable
fun TopBarHeaderPreview() {
    TopBarHeader()
}
