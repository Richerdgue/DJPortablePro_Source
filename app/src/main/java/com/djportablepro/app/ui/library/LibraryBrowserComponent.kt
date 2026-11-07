package com.djportablepro.app.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djportablepro.app.analysis.BpmAnalysisService
import com.djportablepro.app.audio.DjPlayerController
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import com.djportablepro.app.data.AppDatabase
import com.djportablepro.app.data.TrackEntity
import com.djportablepro.app.core.Deck
import com.djportablepro.app.library.TrackAnalysisManager
import com.djportablepro.app.library.TrackDatabase
import com.djportablepro.app.ui.main.DjThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class FolderItem(
    val id: String,
    val name: String
)

data class TrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: String,
    val key: String,
    val duration: String,
    val analysisVersion: Int = 0
)

/**
 * Gestor de Memoria Inteligente de Biblioteca.
 */
object LibraryMemoryManager {
    var lastOpenedFolder: FolderItem? = null
}

/**
 * Controlador de Análisis de Lote en Segundo Plano (Persistent Background Batch Analyzer).
 * Continúa analizando las canciones en segundo plano aunque salgas de la biblioteca,
 * actualizando SQLite (DB v1) en tiempo real con opción de cancelación ('✕').
 */
object GlobalBatchAnalyzer {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null

    var isAnalyzing by mutableStateOf(false)
        private set

    var currentFolderId by mutableStateOf<String?>(null)
        private set

    var currentlyAnalyzingTitle by mutableStateOf("")
        private set

    var analyzedCount by mutableStateOf(0)
        private set

    var totalCount by mutableStateOf(0)
        private set

    var progressFloat by mutableStateOf(0f)
        private set

    val updatedBpms = mutableStateMapOf<String, String>()

    fun startBatchAnalysis(
        context: Context,
        folderId: String,
        tracks: List<TrackItem>,
        forceReanalyze: Boolean = false
    ) {
        if (isAnalyzing || tracks.isEmpty()) return

        isAnalyzing = true
        currentFolderId = folderId
        totalCount = tracks.size
        analyzedCount = 0
        progressFloat = 0f
        currentlyAnalyzingTitle = ""

        currentJob = scope.launch(Dispatchers.IO) {
            val appDatabase = AppDatabase.getInstance(context)
            val trackDao = appDatabase.trackDao()
            val nativeAudio = NativeAudioPlayer(context)
            val analysisManager = TrackAnalysisManager(context, nativeAudio)

            tracks.forEachIndexed { index, track ->
                if (!isActive) return@forEachIndexed

                // Yield and delay 20ms to leave CPU headroom for 60 FPS UI rendering & audio playback
                delay(20L)

                withContext(Dispatchers.Main) {
                    currentlyAnalyzingTitle = track.title
                    analyzedCount = index + 1
                    progressFloat = (index + 1).toFloat() / tracks.size.toFloat()
                }

                try {
                    val result = analysisManager.getOrAnalyze(
                        path = track.id,
                        title = track.title,
                        forceReanalyze = forceReanalyze
                    )
                    if (result != null && result.bpm > 0f) {
                        val formattedBpm = String.format(Locale.US, "%.1f", result.bpm)

                        val now = System.currentTimeMillis()
                        val trackEntity = TrackEntity(
                            filePath = track.id,
                            title = track.title,
                            artist = track.artist,
                            durationMs = 0L,
                            bpm = result.bpm.toDouble(),
                            firstBeatFrame = result.firstBeatFrame,
                            confidence = 0.95,
                            beatDurationMs = 60000.0 / result.bpm.toDouble(),
                            waveformBytes = result.waveform,
                            isAnalyzed = true,
                            lastAnalyzedTimestamp = now
                        )
                        trackDao.insertOrReplace(trackEntity)

                        withContext(Dispatchers.Main) {
                            updatedBpms[track.id] = formattedBpm
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                isAnalyzing = false
                currentFolderId = null
                currentlyAnalyzingTitle = ""
                progressFloat = 0f
                analyzedCount = 0
            }
        }
    }

    fun cancelAnalysis() {
        currentJob?.cancel()
        currentJob = null
        isAnalyzing = false
        currentFolderId = null
        currentlyAnalyzingTitle = ""
        progressFloat = 0f
        analyzedCount = 0
    }
}

@Composable
fun LibraryBrowserComponent(
    targetDeck: String = "DECK 1",
    onTrackSelected: (TrackItem) -> Unit = {},
    onCloseClick: () -> Unit = {}
) {
    val themeColors = DjThemeManager.colors
    var currentFolder by remember { mutableStateOf(LibraryMemoryManager.lastOpenedFolder) }
    
    val context = LocalContext.current
    var deviceFolders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var deviceTracks by remember { mutableStateOf<List<TrackItem>>(emptyList()) }

    val trackDatabase = remember { TrackDatabase(context) }
    val bpmService = remember { BpmAnalysisService() }

    val listState = rememberLazyListState()
    var selectedIndex by remember(currentFolder) { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(currentFolder, deviceFolders.size, deviceTracks.size, targetDeck) {
        DjPlayerController.onBrowseEncoderListener = { delta ->
            val maxIndex = if (currentFolder == null) deviceFolders.size - 1 else deviceTracks.size - 1
            if (maxIndex >= 0) {
                selectedIndex = (selectedIndex + delta).coerceIn(0, maxIndex)
                coroutineScope.launch {
                    listState.animateScrollToItem(selectedIndex)
                }
            }
        }

        DjPlayerController.onBrowsePushListener = {
            if (currentFolder == null) {
                if (selectedIndex in deviceFolders.indices) {
                    currentFolder = deviceFolders[selectedIndex]
                    LibraryMemoryManager.lastOpenedFolder = currentFolder
                    selectedIndex = 0
                }
            } else {
                if (selectedIndex in deviceTracks.indices) {
                    val track = deviceTracks[selectedIndex]
                    val deckIdx = if (targetDeck.contains("1") || targetDeck.contains("A")) 0 else 1
                    val trackBpm = track.bpm.toFloatOrNull() ?: 0.0f
                    DjPlayerController.loadTrack(deckIdx, track.id, track.title, trackBpm)
                    onTrackSelected(track)
                }
            }
        }

        DjPlayerController.onLoadDeckListener = { deck ->
            val loadTargetDeck = if (deck == Deck.DECK_A) "DECK 1" else "DECK 2"
            if (currentFolder != null && selectedIndex in deviceTracks.indices) {
                val track = deviceTracks[selectedIndex]
                val deckIdx = if (deck == Deck.DECK_A) 0 else 1
                val trackBpm = track.bpm.toFloatOrNull() ?: 0.0f
                DjPlayerController.loadTrack(deckIdx, track.id, track.title, trackBpm)
                onTrackSelected(track)
            } else {
                DjPlayerController.activeLibraryDeckState.value = loadTargetDeck
            }
        }

        onDispose {
            DjPlayerController.onBrowseEncoderListener = null
            DjPlayerController.onBrowsePushListener = null
            DjPlayerController.onLoadDeckListener = null
        }
    }

    // Cargar carpetas raíz
    LaunchedEffect(Unit) {
        deviceFolders = queryDeviceMusicFolders(context)
    }

    // Cargar canciones filtradas ÚNICAMENTE por la carpeta seleccionada y verificar caché de base de datos
    LaunchedEffect(currentFolder, GlobalBatchAnalyzer.updatedBpms.size) {
        if (currentFolder != null) {
            LibraryMemoryManager.lastOpenedFolder = currentFolder
            val rawTracks = queryDeviceTracksForFolder(context, currentFolder!!.id)
            
            // Consultar análisis real C++ almacenado en SQLite DB v2 o en memoria viva
            deviceTracks = withContext(Dispatchers.IO) {
                rawTracks.map { track ->
                    val liveBpm = GlobalBatchAnalyzer.updatedBpms[track.id]
                    if (liveBpm != null) {
                        track.copy(bpm = liveBpm, analysisVersion = 2)
                    } else {
                        val cached = trackDatabase.getAnalysis(track.id)
                        val isVersionValid = cached != null && cached.version >= 2 && cached.bpm > 0f
                        val finalBpmStr = if (isVersionValid) {
                            String.format(Locale.US, "%.1f", cached!!.bpm)
                        } else {
                            "0.0"
                        }
                        track.copy(
                            bpm = finalBpmStr,
                            analysisVersion = if (isVersionValid) cached!!.version else 0
                        )
                    }
                }
            }
        } else {
            LibraryMemoryManager.lastOpenedFolder = null
            deviceFolders = queryDeviceMusicFolders(context)
        }
    }

    // Color correspondiente al deck (Naranja para Deck 1, Cian para Deck 2)
    val deckColor = if (targetDeck.contains("1") || targetDeck.contains("A")) Color(0xFFFF9100) else Color(0xFF00E5FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.bg),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- 1. TOP BAR CON BARRA DE PROGRESO Y TÍTULO DE CANCIÓN EN ANÁLISIS ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(deckColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentFolder != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    currentFolder = null
                                    LibraryMemoryManager.lastOpenedFolder = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "←", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        PremiumFolderIcon(modifier = Modifier.size(22.dp), tintColor = Color.Black)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentFolder != null) currentFolder!!.name else "Carpetas del Teléfono",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (GlobalBatchAnalyzer.isAnalyzing) "⚡ Analizando (${GlobalBatchAnalyzer.analyzedCount}/${GlobalBatchAnalyzer.totalCount}): ${GlobalBatchAnalyzer.currentlyAnalyzingTitle.take(18)}..." else "Loading to $targetDeck",
                            color = Color.DarkGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // BOTÓN DE ANÁLISIS CANCELABLE CON ICONO DE CANCELAR (✕)
                    if (currentFolder != null) {
                        if (GlobalBatchAnalyzer.isAnalyzing) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "⚡ Analizando...",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    // BOTÓN CANCELAR X
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF333333))
                                            .clickable { GlobalBatchAnalyzer.cancelAnalysis() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "✕",
                                            color = Color(0xFFFF5252),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            val areAllAnalyzed = deviceTracks.isNotEmpty() && deviceTracks.all {
                                it.bpm.isNotBlank() && it.bpm != "0.0" && it.bpm != "0.00"
                            }
                            Button(
                                onClick = {
                                    GlobalBatchAnalyzer.startBatchAnalysis(
                                        context = context,
                                        folderId = currentFolder!!.id,
                                        tracks = deviceTracks,
                                        forceReanalyze = areAllAnalyzed
                                    )
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (areAllAnalyzed) "⚡ Reanalizar" else "⚡ Analysis",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Close Button (✕)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onCloseClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // BARRITA DE PROGRESO DE ANÁLISIS EN LA PARTE SUPERIOR
            if (GlobalBatchAnalyzer.isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(GlobalBatchAnalyzer.progressFloat)
                            .background(Color.Black)
                    )
                }
            }
        }

        // --- 2. MAIN CONTENT (Smart Folders or Filtered Tracks) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(themeColors.bg)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (currentFolder == null) {
                if (deviceFolders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No se encontraron carpetas de música en el dispositivo",
                            color = themeColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(deviceFolders) { index, folder ->
                            val isHighlighted = index == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isHighlighted) deckColor.copy(alpha = 0.25f) else themeColors.surface)
                                    .border(
                                        width = if (isHighlighted) 2.dp else 1.dp,
                                        color = if (isHighlighted) deckColor else themeColors.border,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        selectedIndex = index
                                        currentFolder = folder
                                        LibraryMemoryManager.lastOpenedFolder = folder
                                    }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    PremiumFolderIcon(modifier = Modifier.size(24.dp), tintColor = Color(0xFFFFB300))
                                    Text(
                                        text = folder.name,
                                        color = themeColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(text = "›", color = themeColors.textSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                if (deviceTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No hay canciones en esta carpeta",
                            color = themeColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(deviceTracks) { index, track ->
                            val isHighlighted = index == selectedIndex
                            TrackRowItem(
                                track = track,
                                deckAccentColor = deckColor,
                                isHighlighted = isHighlighted,
                                onClick = {
                                    selectedIndex = index
                                    val deckIdx = if (targetDeck.contains("1") || targetDeck.contains("A")) 0 else 1
                                    val trackBpm = track.bpm.toFloatOrNull() ?: 0.0f
                                    DjPlayerController.loadTrack(deckIdx, track.id, track.title, trackBpm)
                                    onTrackSelected(track)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumFolderIcon(
    modifier: Modifier = Modifier.size(24.dp),
    tintColor: Color = Color(0xFFFFB300)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val tabPath = Path().apply {
            moveTo(w * 0.1f, h * 0.25f)
            lineTo(w * 0.42f, h * 0.25f)
            lineTo(w * 0.52f, h * 0.38f)
            lineTo(w * 0.9f, h * 0.38f)
            lineTo(w * 0.9f, h * 0.85f)
            lineTo(w * 0.1f, h * 0.85f)
            close()
        }
        drawPath(
            path = tabPath,
            color = tintColor.copy(alpha = 0.40f)
        )

        val bodyPath = Path().apply {
            moveTo(w * 0.08f, h * 0.38f)
            lineTo(w * 0.92f, h * 0.38f)
            lineTo(w * 0.92f, h * 0.85f)
            lineTo(w * 0.08f, h * 0.85f)
            close()
        }
        drawPath(
            path = bodyPath,
            color = tintColor
        )

        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(w * 0.08f, h * 0.38f),
            end = Offset(w * 0.92f, h * 0.38f),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun TrackRowItem(
    track: TrackItem,
    deckAccentColor: Color = Color(0xFFFF9100),
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val themeColors = DjThemeManager.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHighlighted) deckAccentColor.copy(alpha = 0.25f) else themeColors.container)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) deckAccentColor else themeColors.border,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrackAlbumArtThumbnail(audioPath = track.id, accentColor = deckAccentColor)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = themeColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = track.artist,
                        color = themeColors.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Badge de Versión de Análisis SQLite
                    if (track.analysisVersion > 0) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                .border(0.8.dp, Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "DB v${track.analysisVersion}",
                                color = Color(0xFF00E5FF),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visualizar el BPM real analizado y almacenado
            if (track.bpm != "0.00" && track.bpm.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFF9100).copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                        .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${track.bpm} BPM",
                        color = Color(0xFFFF9100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = track.duration,
                color = themeColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TrackAlbumArtThumbnail(
    audioPath: String,
    accentColor: Color,
    modifier: Modifier = Modifier.size(40.dp)
) {
    val themeColors = DjThemeManager.colors
    val context = LocalContext.current
    var albumArtBitmap by remember(audioPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(audioPath) {
        withContext(Dispatchers.IO) {
            albumArtBitmap = loadAlbumArt(context, audioPath)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(themeColors.surface)
            .border(1.dp, themeColors.border, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtBitmap != null) {
            Image(
                bitmap = albumArtBitmap!!.asImageBitmap(),
                contentDescription = "Carátula de Álbum",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultVinylThumbnail(accentColor = accentColor, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun DefaultVinylThumbnail(accentColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f

        drawCircle(color = Color(0xFF14141C), radius = radius)
        drawCircle(color = Color(0xFF2A2A38), radius = radius, style = Stroke(width = 1.dp.toPx()))

        drawCircle(color = Color(0xFF222230), radius = radius * 0.75f, style = Stroke(width = 0.8f.dp.toPx()))
        drawCircle(color = Color(0xFF222230), radius = radius * 0.55f, style = Stroke(width = 0.8f.dp.toPx()))

        drawCircle(color = accentColor, radius = radius * 0.35f)
        drawCircle(color = Color(0xFF0A0A0E), radius = radius * 0.12f)
    }
}

// --- MediaMetadataRetriever Helper for Cover Art ---
fun loadAlbumArt(context: Context, audioPath: String): Bitmap? {
    return try {
        val mmr = MediaMetadataRetriever()
        if (audioPath.startsWith("content://")) {
            mmr.setDataSource(context, Uri.parse(audioPath))
        } else {
            mmr.setDataSource(audioPath)
        }
        val artBytes = mmr.embeddedPicture
        mmr.release()
        if (artBytes != null) {
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
        } else null
    } catch (_: Exception) {
        null
    }
}

// --- MediaStore Query Helpers ---
fun queryDeviceMusicFolders(context: Context): List<FolderItem> {
    val folders = mutableMapOf<String, FolderItem>()
    val projection = arrayOf(
        MediaStore.Audio.Media.BUCKET_ID,
        MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
    )
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    try {
        context.contentResolver.query(uri, projection, null, null, MediaStore.Audio.Media.DATE_ADDED + " DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_ID)
            val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val bucketId = if (idCol != -1) cursor.getString(idCol) ?: "0" else "0"
                val bucketName = if (nameCol != -1) cursor.getString(nameCol) ?: "Audio" else "Audio"
                if (!folders.containsKey(bucketId)) {
                    folders[bucketId] = FolderItem(bucketId, bucketName)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (folders.isEmpty()) {
        folders["1"] = FolderItem("1", "Música del Teléfono")
    }

    return folders.values.toList()
}

fun queryDeviceTracksForFolder(context: Context, bucketId: String): List<TrackItem> {
    val tracks = mutableListOf<TrackItem>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.BUCKET_ID} = ?"
    val selectionArgs = arrayOf(bucketId)

    try {
        context.contentResolver.query(uri, projection, selection, selectionArgs, MediaStore.Audio.Media.TITLE + " ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val dataPath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                val id = if (idCol != -1) cursor.getString(idCol) ?: dataPath else dataPath
                val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Pista Desconocida" else "Pista Desconocida"
                val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "Artista Desconocido" else "Artista Desconocido"
                val durationMs = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                val minutes = durationMs / 1000 / 60
                val seconds = (durationMs / 1000) % 60
                val durationStr = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)

                tracks.add(TrackItem(dataPath.ifEmpty { id }, title, artist, "0.00", "4A", durationStr))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return tracks
}
