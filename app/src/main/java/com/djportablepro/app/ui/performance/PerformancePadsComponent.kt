package com.djportablepro.app.ui.performance

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
fun PerformancePadsComponent(
    deckLabel: String = "DECK A"
) {
    // Modes: 0 = Hot Cue, 1 = Slicer, 2 = Sampler, 3 = Auto Loop
    var selectedMode by remember { mutableStateOf(0) }
    val modes = listOf("HOT CUE", "SLICER", "SAMPLER", "LOOP")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1017))
            .border(width = 1.dp, color = Color(0xFF222834), shape = RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Header & Mode Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Deck Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (deckLabel == "DECK A") Color(0xFF00BCD4) else Color(0xFFFF4081))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = deckLabel,
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Mode Selector Tabs (Hot Cue, Slicer, Sampler, Loop)
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                modes.forEachIndexed { index, modeName ->
                    val isSelected = selectedMode == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) Color(0xFFFF6D00) else Color(0xFF181D26))
                            .clickable { selectedMode = index }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeName,
                            color = if (isSelected) Color.Black else Color(0xFF8A93A0),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. 8 Performance Pads Grid (4x2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val padColors = listOf(
                Color(0xFFE91E63), // Pink
                Color(0xFF9C27B0), // Purple
                Color(0xFF3F51B5), // Indigo
                Color(0xFF00BCD4), // Cyan
                Color(0xFF4CAF50), // Green
                Color(0xFFFFEB3B), // Yellow
                Color(0xFFFF9800), // Orange
                Color(0xFFFF5722)  // Deep Orange
            )

            // Row 1 (Pads 1 to 4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 1..4) {
                    PerformancePad(
                        number = i,
                        modeName = modes[selectedMode],
                        color = padColors[i - 1],
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Row 2 (Pads 5 to 8)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 5..8) {
                    PerformancePad(
                        number = i,
                        modeName = modes[selectedMode],
                        color = padColors[i - 1],
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PerformancePad(number: Int, modeName: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141A23))
            .border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$number",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(name = "Traktor Performance Pads Preview", widthDp = 300, heightDp = 160)
@Composable
fun PerformancePadsComponentPreview() {
    PerformancePadsComponent()
}
