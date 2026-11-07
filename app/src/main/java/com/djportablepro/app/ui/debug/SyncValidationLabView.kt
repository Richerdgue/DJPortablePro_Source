package com.djportablepro.app.ui.debug

import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djportablepro.app.analysis.BeatSyncService
import com.djportablepro.app.audio.DjPlayerController
import com.djportablepro.app.ui.library.LibraryBrowserComponent
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/**
 * Senior DJ Audio DSP Smart Sync Validation Lab View.
 * Connects directly to active playing audio decks (Deck 1 and Deck 2) via [DjPlayerController].
 * Evaluates live audio streams to verify if Kick drums coincide perfectly in real time.
 */
@Composable
fun SyncValidationLabView(
    modifier: Modifier = Modifier,
    onCloseClick: () -> Unit = {}
) {
    val deck1State by DjPlayerController.deck1State.collectAsState()
    val deck2State by DjPlayerController.deck2State.collectAsState()

    var activeLibraryDeck by remember { mutableStateOf<String?>(null) }
    val syncService = remember { BeatSyncService() }

    // Quantization & Metronome State
    var quantizationBars by remember { mutableStateOf(1) } // 0: 1 Beat, 1: 1 Bar, 2: 2 Bars, 3: 4 Bars
    var isPlayingMetronome by remember { mutableStateOf(false) }
    var beatFlash by remember { mutableStateOf(false) }
    val toneGen = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (_: Throwable) { null }
    }

    val metronomeBpm = if (deck1State.bpm > 0f) deck1State.bpm.toDouble() else 128.0
    val effectiveMetronomeBpm = metronomeBpm * deck1State.playbackRate.toDouble()

    LaunchedEffect(isPlayingMetronome, effectiveMetronomeBpm) {
        if (isPlayingMetronome && effectiveMetronomeBpm > 0.0) {
            val intervalMs = (60000.0 / effectiveMetronomeBpm).toLong().coerceAtLeast(50L)
            while (isPlayingMetronome) {
                beatFlash = true
                try {
                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
                } catch (_: Throwable) {}
                delay(60L)
                beatFlash = false
                delay((intervalMs - 60L).coerceAtLeast(10L))
            }
        }
    }

    val masterBpm = if (deck1State.bpm > 0f) deck1State.bpm.toDouble() else 128.0
    val slaveBaseBpm = if (deck2State.bpm > 0f) deck2State.bpm.toDouble() else 128.0

    val masterEffectiveBpm = masterBpm * deck1State.playbackRate.toDouble()
    val slaveEffectiveBpm = slaveBaseBpm * deck2State.playbackRate.toDouble()

    val phaseErrorMs = if (deck1State.isLoaded && deck2State.isLoaded) {
        syncService.calculatePhaseErrorMs(
            masterPositionMs = deck1State.currentPositionMs.toDouble(),
            masterBpm = masterBpm,
            slavePositionMs = deck2State.currentPositionMs.toDouble(),
            slaveBpm = slaveBaseBpm,
            slaveFirstBeatOffsetMs = deck2State.firstBeatMs,
            masterFirstBeatOffsetMs = deck1State.firstBeatMs,
            useBeatWrapping = true,
            masterEffectiveBpm = masterEffectiveBpm
        )
    } else {
        0.0
    }

    val phaseErrorAbs = abs(phaseErrorMs)

    val meterColor = when {
        !deck1State.isLoaded || !deck2State.isLoaded -> Color.Gray
        phaseErrorAbs <= 8.0 -> Color(0xFF10B981) // Emerald Green (< 8ms = Kick Match 100%)
        phaseErrorAbs <= 25.0 -> Color(0xFFF59E0B) // Amber Yellow (8 - 25ms)
        else -> Color(0xFFEF4444) // Red (> 25ms = Misaligned)
    }

    val kickMatchStatus = when {
        !deck1State.isLoaded || !deck2State.isLoaded -> "CARGA MÚSICA EN AMBOS DECKS PARA VERIFICAR"
        phaseErrorAbs <= 8.0 -> "🟢 BOMBOS COINCIDEN PERFECTAMENTE (KICK MATCH 100%)"
        phaseErrorAbs <= 25.0 -> "🟡 LEVE DESFASE (10ms - 25ms)"
        else -> "🔴 BOMBOS DESFASADOS / A DESTIEMPO"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "⚡ LABORATORIO INTELIGENTE DE SINCRONIZACIÓN ⚡",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Auditoría en vivo de coincidencia de bombos con canciones reales",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF334155))
                        .clickable(onClick = onCloseClick)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕ VOLVER AL DJ",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Real Audio Decks Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Deck A Real Monitor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "DECK A (MÁSTER)", color = Color(0xFFFF9100), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF0284C7))
                                .clickable { activeLibraryDeck = "DECK 1" }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "🎵 CARGAR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = deck1State.trackTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "BPM Base: ${if (deck1State.bpm > 0) String.format(Locale.US, "%.1f", deck1State.bpm) else "--"}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "BPM Efectivo: ${if (deck1State.bpm > 0) String.format(Locale.US, "%.1f", masterEffectiveBpm) else "--"}",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Posición: ${formatTimeMs(deck1State.currentPositionMs)}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    // Controls Deck A: Play/Pause + SYNC
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (deck1State.isPlaying) Color(0xFF15803D) else Color(0xFF334155))
                                .clickable { DjPlayerController.togglePlayPause(0) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (deck1State.isPlaying) "⏸ PAUSAR A" else "▶ REPRODUCIR A",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (deck1State.isSyncActive) Color(0xFF16A34A) else Color(0xFF2563EB))
                                .clickable { DjPlayerController.toggleSync(0) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (deck1State.isSyncActive) "⚡ SYNC A (ON)" else "⚡ SYNC A",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Deck B Real Monitor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "DECK B (ESCLAVO)", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF0284C7))
                                .clickable { activeLibraryDeck = "DECK 2" }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "🎵 CARGAR", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = deck2State.trackTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "BPM Base: ${if (deck2State.bpm > 0) String.format(Locale.US, "%.1f", deck2State.bpm) else "--"}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "BPM Efectivo: ${if (deck2State.bpm > 0) String.format(Locale.US, "%.1f", slaveEffectiveBpm) else "--"}",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Posición: ${formatTimeMs(deck2State.currentPositionMs)}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    // Controls Deck B: Play/Pause + SYNC
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (deck2State.isPlaying) Color(0xFF15803D) else Color(0xFF334155))
                                .clickable { DjPlayerController.togglePlayPause(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (deck2State.isPlaying) "⏸ PAUSAR B" else "▶ REPRODUCIR B",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (deck2State.isSyncActive) Color(0xFF16A34A) else Color(0xFF2563EB))
                                .clickable { DjPlayerController.toggleSync(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (deck2State.isSyncActive) "⚡ SYNC B (ON)" else "⚡ SYNC B",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Real-Time Live Kick Collision / Kick-Match Indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.5.dp, meterColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (deck1State.isLoaded && deck2State.isLoaded)
                        "DESFASE EN VIVO: ${String.format(Locale.US, "%.2f", phaseErrorMs)} ms"
                    else "DESFASE EN VIVO: -- ms",
                    color = meterColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = kickMatchStatus,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Active SYNC States
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DECK A SYNC: ${if (deck1State.isSyncActive) "🟢 ACTIVO" else "⚪ INACTIVO"}",
                        color = if (deck1State.isSyncActive) Color(0xFF4ADE80) else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "DECK B SYNC: ${if (deck2State.isSyncActive) "🟢 ACTIVO" else "⚪ INACTIVO"}",
                        color = if (deck2State.isSyncActive) Color(0xFF4ADE80) else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Visual Meter Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF0F172A))
                ) {
                    val fillFraction = if (deck1State.isLoaded && deck2State.isLoaded) {
                        (1.0 - (phaseErrorAbs / 50.0).coerceIn(0.0, 1.0)).coerceAtLeast(0.05).toFloat()
                    } else 0.05f

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fillFraction)
                            .clip(RoundedCornerShape(5.dp))
                            .background(meterColor)
                    )
                }
            }

            // Dual SYNC Action Trigger Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (deck1State.isSyncActive) Color(0xFF15803D) else Color(0xFF2563EB))
                        .clickable {
                            DjPlayerController.syncDeck(0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ SYNC DECK A (A ➔ B)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (deck2State.isSyncActive) Color(0xFF15803D) else Color(0xFF2563EB))
                        .clickable {
                            DjPlayerController.syncDeck(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ SYNC DECK B (B ➔ A)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- METRÓNOMO PROFESIONAL (ESTILO WUTOOLS) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, if (beatFlash) Color(0xFFFF9100) else Color(0xFF334155), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎵 METRÓNOMO DE VERIFICACIÓN (ESTILO WUTOOLS)",
                        color = Color(0xFFFF9100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (beatFlash) Color(0xFFFF9100) else Color(0xFF475569))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BPM Analizado: ${String.format(Locale.US, "%.2f", effectiveMetronomeBpm)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPlayingMetronome) Color(0xFFDC2626) else Color(0xFF2563EB))
                            .clickable { isPlayingMetronome = !isPlayingMetronome }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPlayingMetronome) "⏸ DETENER CLIC" else "▶ REPRODUCIR CLIC",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- CUANTIZACIÓN DE SINCRONIZACIÓN (SNAP / BAR SIZE) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "⚙️ CUANTIZACIÓN DE FASE Y COMPÁS (QUANTIZE SNAP)",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val options = listOf("1 Beat", "1 Bar", "2 Bars", "4 Bars")
                    options.forEachIndexed { index, label ->
                        val isSelected = quantizationBars == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF334155))
                                .clickable { quantizationBars = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Music Browser Overlay inside Lab
        if (activeLibraryDeck != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                LibraryBrowserComponent(
                    targetDeck = activeLibraryDeck!!,
                    onTrackSelected = { _ ->
                        activeLibraryDeck = null
                    },
                    onCloseClick = {
                        activeLibraryDeck = null
                    }
                )
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

@Preview(name = "Smart Sync Validation Lab Preview", widthDp = 380, heightDp = 420)
@Composable
fun SyncValidationLabPreview() {
    SyncValidationLabView()
}
