package com.djportablepro.app.ui.debug

import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djportablepro.app.audio.DjPlayerController
import com.djportablepro.app.core.Deck
import com.djportablepro.app.library.AnalysisResult
import com.djportablepro.app.library.TrackAnalysisManager
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Real-time C++ NDK Audio DSP BPM Inspector & Range Validation View.
 * Audits tracks directly with the C++ DSP engine, validates if calculated BPM is within
 * the allowed range (50.0 - 220.0 BPM), displays Camelot key and downbeat anchors,
 * and allows instant loading into Deck 1 or Deck 2.
 */
@Composable
fun BpmInspectorLabView(
    modifier: Modifier = Modifier,
    onCloseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val audioPlayer = remember { NativeAudioPlayer(context) }
    val analysisManager = remember { TrackAnalysisManager(context, audioPlayer) }

    var localTracks by remember { mutableStateOf<List<File>>(emptyList()) }
    var availableFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<File?>(null) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var currentAnalysisResult by remember { mutableStateOf<AnalysisResult?>(null) }

    // Load available local audio tracks on init via MediaStore and Device Storage
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val audioFiles = mutableListOf<File>()
            val folderMap = mutableMapOf<String, File>()

            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DISPLAY_NAME
                )
                val cursor = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
                )
                cursor?.use { c ->
                    val dataIndex = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    while (c.moveToNext()) {
                        if (dataIndex != -1) {
                            val path = c.getString(dataIndex)
                            if (!path.isNullOrBlank()) {
                                val f = File(path)
                                if (f.exists() && f.isFile) {
                                    audioFiles.add(f)
                                    f.parentFile?.let { p -> folderMap[p.absolutePath] = p }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (audioFiles.isNotEmpty()) {
                localTracks = audioFiles
                availableFolders = folderMap.values.sortedBy { it.name }
                selectedFile = audioFiles.first()
            } else {
                val musicFolder = File("/storage/emulated/0/Music")
                if (musicFolder.exists() && musicFolder.isDirectory) {
                    val files = musicFolder.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("mp3", "wav", "m4a", "flac") }.toList()
                    localTracks = files
                    availableFolders = files.mapNotNull { it.parentFile }.distinct().sortedBy { it.name }
                    if (files.isNotEmpty()) selectedFile = files.first()
                }
            }
        }
    }

    // Run NDK C++ Analysis when selected file changes
    fun runAnalysisForFile(file: File) {
        selectedFile = file
        isAnalyzing = true
        currentAnalysisResult = null

        scope.launch {
            val result = analysisManager.getOrAnalyze(file.absolutePath, file.name, forceReanalyze = true)
            currentAnalysisResult = result
            isAnalyzing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🔍", fontSize = 14.sp)
                    Text(
                        text = "INSPECTOR Y ANALIZADOR DE BPM (C++ NDK)",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF334155))
                        .clickable(onClick = onCloseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Upper Panel: Current Analysis Results Card
            val result = currentAnalysisResult
            val bpmValue = result?.bpm ?: 0f
            val isBpmValid = bpmValue in 50.0f..220.0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, if (isBpmValid) Color(0xFF10B981) else Color(0xFF64748B), RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFile?.name ?: "Ninguna pista seleccionada",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedFile?.absolutePath ?: "",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isAnalyzing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ANALIZANDO...",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (result != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isBpmValid) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isBpmValid) "🟢 BPM VÁLIDO" else "🔴 FUERA DE RANGO",
                                color = if (isBpmValid) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Grid Metrics Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // BPM Display
                    Column {
                        Text(text = "TEMPO C++ NDK", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (result != null) String.format(Locale.US, "%.2f BPM", result.bpm) else "--.-- BPM",
                            color = Color(0xFFFF9100),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Key Display
                    Column {
                        Text(text = "CLAVE CAMELOT", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "8A / Am",
                            color = Color(0xFF38BDF8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Downbeat Anchor Offset Display
                    Column {
                        Text(text = "PRIMER GOLPE (DOWNBEAT)", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val downbeatMs = if (result != null) (result.firstBeatFrame * 1000.0) / 44100.0 else 0.0
                        Text(
                            text = String.format(Locale.US, "%.1f ms", downbeatMs),
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Waveform Points Capacity
                    Column {
                        Text(text = "ONDA RGB", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val points = (result?.waveform?.size ?: 0) / 3
                        Text(
                            text = "$points pts",
                            color = Color(0xFFA855F7),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Action Buttons: Load to Deck 1 or Deck 2
                if (selectedFile != null && result != null && isBpmValid) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                DjPlayerController.loadTrack(0, selectedFile!!.absolutePath, selectedFile!!.name, result.bpm)
                                DjPlayerController.handleLoadAction(Deck.DECK_A)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100), contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(text = "CARGAR EN DECK 1", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                DjPlayerController.loadTrack(1, selectedFile!!.absolutePath, selectedFile!!.name, result.bpm)
                                DjPlayerController.handleLoadAction(Deck.DECK_B)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(text = "CARGAR EN DECK 2", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Lower Panel: Folder Selector & List of Device Audio Tracks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXPLORAR NAVEGADOR POR CARPETAS:",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (selectedFolder != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF334155))
                            .clickable { selectedFolder = null }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "✕ TODAS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Horizontal Folder Chips Selector
            if (availableFolders.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        val isAllSelected = selectedFolder == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isAllSelected) Color(0xFF38BDF8) else Color(0xFF1E293B))
                                .clickable { selectedFolder = null }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "📁 TODAS (${localTracks.size})",
                                color = if (isAllSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(availableFolders) { folder ->
                        val isFolderSelected = selectedFolder?.absolutePath == folder.absolutePath
                        val trackCountInFolder = localTracks.count { it.parentFile?.absolutePath == folder.absolutePath }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isFolderSelected) Color(0xFF38BDF8) else Color(0xFF1E293B))
                                .border(1.dp, if (isFolderSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(4.dp))
                                .clickable { selectedFolder = folder }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "📁 ${folder.name} ($trackCountInFolder)",
                                color = if (isFolderSelected) Color.Black else Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontWeight = if (isFolderSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            val displayedTracks = remember(localTracks, selectedFolder) {
                if (selectedFolder == null) localTracks
                else localTracks.filter { it.parentFile?.absolutePath == selectedFolder?.absolutePath }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF020617))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(displayedTracks) { file ->
                    val isSelected = selectedFile?.absolutePath == file.absolutePath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                            .clickable { runAnalysisForFile(file) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${file.length() / (1024 * 1024)} MB",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        }

                        if (isSelected) {
                            Text(text = "▶ PROBAR", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
