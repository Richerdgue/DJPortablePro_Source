package com.djportablepro.app.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.constraintlayout.compose.ConstraintLayout

@Composable
fun MixerStripView(
    highValue: Float = 1.0f,
    midValue: Float = 1.0f,
    lowValue: Float = 1.0f,
    filterValue: Float = 0.5f,
    volumeValue: Float = 0.8f,
    crossfaderValue: Float = 0.5f
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14171F))
            .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        // Create references for ConstraintLayout
        val (header, knobHigh, knobMid, knobLow, knobFilter, volumeFader, crossfader) = createRefs()

        // 1. Mixer Header
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF0D1017))
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .constrainAs(header) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MIXER",
                color = Color(0xFFFF9800), // Traktor Orange
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // 2. EQ & Filter Knobs
        MixerKnobItem(
            label = "HI",
            modifier = Modifier.constrainAs(knobHigh) {
                top.linkTo(header.bottom, margin = 8.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        )

        MixerKnobItem(
            label = "MID",
            modifier = Modifier.constrainAs(knobMid) {
                top.linkTo(knobHigh.bottom, margin = 6.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        )

        MixerKnobItem(
            label = "LOW",
            modifier = Modifier.constrainAs(knobLow) {
                top.linkTo(knobMid.bottom, margin = 6.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        )

        MixerKnobItem(
            label = "FIL",
            isFilter = true,
            modifier = Modifier.constrainAs(knobFilter) {
                top.linkTo(knobLow.bottom, margin = 6.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        )

        // 3. Compact Volume Fader
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF0A0D14))
                .border(1.dp, Color(0xFF1E2533), RoundedCornerShape(3.dp))
                .constrainAs(volumeFader) {
                    top.linkTo(knobFilter.bottom, margin = 8.dp)
                    bottom.linkTo(crossfader.top, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E2533))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF8A93A0))
            )
        }

        // 4. Horizontal Crossfader at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0D1017))
                .border(1.dp, Color(0xFF1E2533), RoundedCornerShape(4.dp))
                .padding(4.dp)
                .constrainAs(crossfader) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "X-FADER",
                color = Color(0xFF8A93A0),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF0A0D14))
                    .border(1.dp, Color(0xFF1E2533), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1E2533))
                )
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF00BCD4))
                )
            }
        }
    }
}

@Composable
fun MixerKnobItem(label: String, isFilter: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
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
                    color = if (isFilter) Color(0xFFFF9800) else Color(0xFF2E384A),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(8.dp)
                    .offset(y = (-4).dp)
                    .background(if (isFilter) Color(0xFFFF9800) else Color(0xFF00BCD4))
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

@Preview(name = "MixerStripView Preview", widthDp = 120, heightDp = 260)
@Composable
fun MixerStripViewPreview() {
    MixerStripView()
}
