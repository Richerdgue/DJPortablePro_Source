package com.djportablepro.app.ui.debug

import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djportablepro.app.audio.DjPlayerController
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import com.djportablepro.app.core.Deck
import com.djportablepro.app.kymatik.domain.KymatikResult
import com.djportablepro.app.kymatik.service.KymatikPipeline
import com.djportablepro.app.library.AnalysisResult
import com.djportablepro.app.library.TrackAnalysisManager
import com.djportablepro.app.library.TrackDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Professional Kymatik BPM & Audio DSP Laboratory Audit View.
 * Provides live folder browsing, single/batch NDK DSP analysis,
 * real-time SQLite database inspection, and direct deck loading.
 */
@Composable
fun KymatikBpmLaboratoryView(
    modifier: Modifier = Modifier,
    onCloseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val audioPlayer = remember { NativeAudioPlayer(context) }
    val kymatikPipeline = remember { KymatikPipeline(context) }
    val trackDb = remember { TrackDatabase(context) }

    var localTracks by remember { mutableStateOf<List<File>>(emptyList()) }
    var availableFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<File?>(null) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var isBatchAnalyzing by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<KymatikResult?>(null) }
    var currentSqliteRecord by remember { mutableStateOf<AnalysisResult?>(null) }

    var batchResults by remember { mutableStateOf<Map<String, KymatikResult>>(emptyMap()) }

    // Load available local audio tracks and folders on init
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

    fun inspectSqliteRecord(file: File) {
        scope.launch(Dispatchers.IO) {
            val rec = trackDb.getAnalysis(file.absolutePath)
            currentSqliteRecord = rec
        }
    }

    fun runAnalysisForFile(file: File, forceReanalyze: Boolean = true) {
        selectedFile = file
        isAnalyzing = true
        currentResult = null

        scope.launch {
            val result = kymatikPipeline.processTrack(file.absolutePath, forceReanalyze = forceReanalyze)
            currentResult = result
            isAnalyzing = false
            inspectSqliteRecord(file)
        }
    }

    fun runBatchAnalysisForFolder() {
        val targetFolder = selectedFolder ?: return
        val filesInFolder = localTracks.filter { it.parentFile?.absolutePath == targetFolder.absolutePath }
        if (filesInFolder.isEmpty()) return

        isBatchAnalyzing = true
        scope.launch {
            val resultMap = mutableMapOf<String, KymatikResult>()
            for (file in filesInFolder) {
                val res = kymatikPipeline.processTrack(file.absolutePath, forceReanalyze = true)
                resultMap[file.absolutePath] = res
            }
            batchResults = resultMap
            isBatchAnalyzing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
            .border(1.dp, Color(0xFF1E293B))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔬 ", fontSize = 16.sp)
                    Text(
                        text = "LABORATORIO & AUDITORÍA DE BPM NDK (KYMATIK)",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E293B))
                        .clickable(onClick = onCloseClick)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = "✕ CERRAR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Top Status Panel
            val result = currentResult
            val sqliteRec = currentSqliteRecord
            val isBpmValid = result != null && result.bpm >= 50.0 && result.bpm <= 250.0

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0B132B))
                    .border(1.dp, if (isBpmValid) Color(0xFF10B981) else Color(0xFF334155), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // File Name & Path
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFile?.name ?: "SELECCIONAR CANCIÓN DE LA LISTA",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedFile?.absolutePath ?: "",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }

                    if (isAnalyzing) {
                        Text(text = "ANALIZANDO NDK...", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else if (isBpmValid) {
                        Text(text = "🟢 BPM NDK EXACTO", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        Text(text = "TEMPO KYMATIK NDK", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                            text = result?.key ?: "8A / Am",
                            color = Color(0xFF38BDF8),
                            fontSize = 15.sp,
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

                    // SQLite Inspection Status
                    Column {
                        Text(text = "REGISTRO EN SQLITE DB", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (sqliteRec != null) String.format(Locale.US, "GUARDADO (%.2f BPM)", sqliteRec.bpm) else "NO GUARDADO",
                            color = if (sqliteRec != null) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action Buttons: Load to Deck 1 / Deck 2 & Force Reanalyze
                if (selectedFile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { runAnalysisForFile(selectedFile!!, forceReanalyze = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White),
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(text = "🔄 RE-ANALIZAR NDK", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        if (result != null && isBpmValid) {
                            Button(
                                onClick = {
                                    DjPlayerController.loadTrack(0, selectedFile!!.absolutePath, selectedFile!!.name, result.bpm.toFloat())
                                    DjPlayerController.handleLoadAction(Deck.DECK_A)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100), contentColor = Color.Black),
                                modifier = Modifier.weight(1f).height(34.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(text = "CARGAR EN DECK 1", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    DjPlayerController.loadTrack(1, selectedFile!!.absolutePath, selectedFile!!.name, result.bpm.toFloat())
                                    DjPlayerController.handleLoadAction(Deck.DECK_B)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                modifier = Modifier.weight(1f).height(34.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(text = "CARGAR EN DECK 2", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Lower Panel: Folder Selector & Track List
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { runBatchAnalysisForFolder() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(text = if (isBatchAnalyzing) "ANALIZANDO LOTE..." else "⚡ PROBAR TODA LA CARPETA", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF334155))
                                .clickable { selectedFolder = null }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(text = "✕ VER TODAS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
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

            // Track List with Live NDK Results & SQLite Verification
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
                    val batchRes = batchResults[file.absolutePath]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF090D16))
                            .clickable { runAnalysisForFile(file) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                color = if (isSelected) Color(0xFF38BDF8) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            val fileSizeMb = file.length() / (1024.0 * 1024.0)
                            Text(
                                text = String.format(Locale.US, "%.1f MB", fileSizeMb),
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        }

                        if (batchRes != null) {
                            Text(
                                text = String.format(Locale.US, "%.2f BPM", batchRes.bpm),
                                color = Color(0xFFFF9100),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isSelected) "▶ PROBANDO NDK" else "PROBAR",
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
