package com.djportablepro.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.djportablepro.app.audio.DjPlayerController
import com.djportablepro.app.ui.debug.BpmInspectorLabView
import com.djportablepro.app.ui.debug.KymatikBpmLaboratoryView
import com.djportablepro.app.ui.deck.DeckPanel
import com.djportablepro.app.ui.deck.JogWheelView
import com.djportablepro.app.ui.grid.FullScreenGridEditOverlay
import com.djportablepro.app.ui.debug.SyncValidationLabView
import com.djportablepro.app.ui.library.LibraryBrowserComponent
import kotlinx.coroutines.delay
import java.util.Locale

// --- GESTOR DE TEMAS Y APARIENCIA VISUAL ---
enum class DjTheme(val displayName: String, val description: String) {
    NIGHT("Modo Noche", "Fondo oscuro de hardware para clubes y poca luz"),
    DAY("Modo Día / Brillo", "Fondo blanco de alto contraste para eventos en exteriores"),
    PRO_GRADIENT("Modo Pro Gris", "Fondo gris degradado metálico estilo consola Pro")
}

data class DjThemeColors(
    val bg: Color,
    val surface: Color,
    val container: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val buttonBg: Color
)

object DjThemeManager {
    var currentTheme by mutableStateOf(DjTheme.NIGHT)

    val colors: DjThemeColors
        get() = when (currentTheme) {
            DjTheme.NIGHT -> DjThemeColors(
                bg = Color(0xFF050507),
                surface = Color(0xFF0D0D11),
                container = Color(0xFF14141A),
                border = Color(0xFF22222A),
                textPrimary = Color(0xFFF0F0F5),
                textSecondary = Color(0xFF8E8E9A),
                buttonBg = Color(0xFF1A1A22)
            )
            DjTheme.DAY -> DjThemeColors(
                bg = Color(0xFFEFEFF4),
                surface = Color(0xFFFFFFFF),
                container = Color(0xFFE5E5EA),
                border = Color(0xFFC7C7CC),
                textPrimary = Color(0xFF1C1C1E),
                textSecondary = Color(0xFF6C6C70),
                buttonBg = Color(0xFFD1D1D6)
            )
            DjTheme.PRO_GRADIENT -> DjThemeColors(
                bg = Color(0xFF22222E),
                surface = Color(0xFF2C2C3A),
                container = Color(0xFF363648),
                border = Color(0xFF4A4A60),
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFA0A0B0),
                buttonBg = Color(0xFF3A3A4D)
            )
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            TraktorProDjTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DjThemeManager.colors.bg
                ) {
                    AudioPermissionHandler {
                        MainDjCanvas()
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPermissionHandler(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val permissionsToRequest = remember {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list.toTypedArray()
    }

    var hasPermissions by remember {
        mutableStateOf(
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(permissionsToRequest)
        }
    }

    if (hasPermissions) {
        content()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DjThemeManager.colors.bg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(DjThemeManager.colors.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, DjThemeManager.colors.border, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "⚠️ Advertencia de Audio y Multimedia",
                    color = Color(0xFFFF9100),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "DJ Portable Pro requiere permisos de grabación de audio y acceso a archivos multimedia para reproducir música, analizar pistas y utilizar el motor de audio profesional. Por favor, concede los permisos para continuar.",
                    color = DjThemeManager.colors.textPrimary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { launcher.launch(permissionsToRequest) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9100),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Permitir",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TraktorProDjTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DjThemeManager.colors.bg,
            surface = DjThemeManager.colors.surface,
            primary = Color(0xFFFF9100)
        ),
        content = content
    )
}

fun formatTrackTitle(title: String, maxChars: Int = 28): String {
    return if (title.length > maxChars) {
        title.take(maxChars - 3) + "..."
    } else {
        title
    }
}

@Composable
fun MainDjCanvas() {
    val context = LocalContext.current
    val themeColors = DjThemeManager.colors

    val pitchTrackColor = when (DjThemeManager.currentTheme) {
        DjTheme.NIGHT -> android.graphics.Color.parseColor("#FFFFFF")
        DjTheme.DAY -> android.graphics.Color.parseColor("#8E8E9A")
        DjTheme.PRO_GRADIENT -> android.graphics.Color.parseColor("#0F0F18")
    }

    LaunchedEffect(Unit) {
        DjPlayerController.initialize(context)
    }

    val deck1State by DjPlayerController.deck1State.collectAsState()
    val deck2State by DjPlayerController.deck2State.collectAsState()
    val physicalLibraryDeckState by DjPlayerController.activeLibraryDeckState.collectAsState()

    var activeLibraryDeck by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(physicalLibraryDeckState) {
        if (physicalLibraryDeckState != null) {
            activeLibraryDeck = physicalLibraryDeckState
        }
    }
    var isDeck1EqOpen by remember { mutableStateOf(false) }
    var isDeck2EqOpen by remember { mutableStateOf(false) }
    var isDeck1LoopOpen by remember { mutableStateOf(false) }
    var isDeck2LoopOpen by remember { mutableStateOf(false) }
    var isDeck1FxOpen by remember { mutableStateOf(false) }
    var isDeck2FxOpen by remember { mutableStateOf(false) }
    var isMenuOpen by remember { mutableStateOf(false) }
    var isGridEditOpen by remember { mutableStateOf(false) }
    var isSyncLabOpen by remember { mutableStateOf(false) }
    var isBpmInspectorOpen by remember { mutableStateOf(false) }
    var isKymatikLabOpen by remember { mutableStateOf(false) }

    var deck1WaveformMode by remember { mutableStateOf(WaveformMode.OVERVIEW) }
    var deck2WaveformMode by remember { mutableStateOf(WaveformMode.OVERVIEW) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.bg)
            .padding(6.dp)
    ) {
        // --- CONTENEDOR PRINCIPAL DECKS Y WAVEFORMS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(bottom = 38.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // --- DECK 1 FULL CONTAINER ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Deck1WaveformHeader(
                        trackTitle = deck1State.trackTitle,
                        currentPositionMs = deck1State.currentPositionMs,
                        durationMs = deck1State.durationMs,
                        waveformBytes = deck1State.waveformBytes,
                        bpm = deck1State.bpm,
                        firstBeatMs = deck1State.firstBeatMs,
                        mode = deck1WaveformMode,
                        onModeChange = { deck1WaveformMode = it },
                        onSeek = { progress ->
                            if (deck1State.durationMs > 0) {
                                val targetMs = (progress * deck1State.durationMs).toLong()
                                DjPlayerController.seekTo(0, targetMs)
                            }
                        },
                        onFolderClick = { activeLibraryDeck = "DECK 1" }
                    )
                    AndroidView(
                        factory = { ctx ->
                            DeckPanel(ctx, isRightSide = false).apply {
                                val label = if (deck1State.bpm > 0f) String.format(Locale.US, "%.1f", deck1State.bpm * deck1State.playbackRate) else "0.00"
                                setDeckLabel(label)
                                setAccentColor(android.graphics.Color.parseColor("#FF9100"))
                                setPlaying(deck1State.isPlaying)
                                setAlbumArt(deck1State.coverArtBitmap)
                                setPanelBackgroundColor(themeColors.bg.toArgb())
                                setPitchTrackColor(pitchTrackColor)
                                setOnFxClickListener { isDeck1FxOpen = true }
                                setOnEqClickListener { isDeck1EqOpen = true }
                                setOnLoopClickListener { isDeck1LoopOpen = true }
                                setOnPitchChangeListener { rate, _ ->
                                    DjPlayerController.setPlaybackRate(0, rate, isManualUserAction = true)
                                }
                                setOnJogTouchListener(object : JogWheelView.OnJogTouchListener {
                                    override fun onTouchDown() { DjPlayerController.setPlatterTouched(0, true) }
                                    override fun onScratch(deltaAngle: Float) { DjPlayerController.scratch(0, deltaAngle / 10.0f) }
                                    override fun onTouchUp() { DjPlayerController.setPlatterTouched(0, false) }
                                })
                                DjPlayerController.onJogRotateListenerDeck1 = { deltaAngle ->
                                    rotateBy(deltaAngle)
                                }
                            }
                        },
                        update = { panel ->
                            val label = if (deck1State.bpm > 0f) String.format(Locale.US, "%.1f", deck1State.bpm * deck1State.playbackRate) else "0.00"
                            panel.setDeckLabel(label)
                            panel.setPlaying(deck1State.isPlaying)
                            panel.setActive(deck1State.isPlatterTouched)
                            panel.setAlbumArt(deck1State.coverArtBitmap)
                            panel.setPanelBackgroundColor(themeColors.bg.toArgb())
                            panel.setPitchTrackColor(pitchTrackColor)
                            panel.setPlaybackRate(deck1State.playbackRate)
                            panel.setLoopActive(deck1State.isLoopActive, android.graphics.Color.parseColor("#FF9100"))
                            if (deck1State.durationMs > 0) {
                                val progress = deck1State.currentPositionMs.toFloat() / deck1State.durationMs.toFloat()
                                val remainingMs = maxOf(0L, deck1State.durationMs - deck1State.currentPositionMs)
                                panel.setTrackProgress(progress, remainingMs)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Overlay EQ Deck 1
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isDeck1EqOpen,
                        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Deck1EqOverlayPanel(onClose = { isDeck1EqOpen = false })
                    }
                }

                // Overlay LOOP Deck 1
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isDeck1LoopOpen,
                        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Deck1LoopOverlayPanel(onClose = { isDeck1LoopOpen = false })
                    }
                }

                // Overlay FX Deck 1
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isDeck1FxOpen,
                        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Deck1FxOverlayPanel(onClose = { isDeck1FxOpen = false })
                    }
                }
            }

            // --- DECK 2 FULL CONTAINER ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Deck2WaveformHeader(
                        trackTitle = deck2State.trackTitle,
                        currentPositionMs = deck2State.currentPositionMs,
                        durationMs = deck2State.durationMs,
                        waveformBytes = deck2State.waveformBytes,
                        bpm = deck2State.bpm,
                        firstBeatMs = deck2State.firstBeatMs,
                        mode = deck2WaveformMode,
                        onModeChange = { deck2WaveformMode = it },
                        onSeek = { progress ->
                            if (deck2State.durationMs > 0) {
                                val targetMs = (progress * deck2State.durationMs).toLong()
                                DjPlayerController.seekTo(1, targetMs)
                            }
                        },
                        onFolderClick = { activeLibraryDeck = "DECK 2" }
                    )
                    AndroidView(
                        factory = { ctx ->
                            DeckPanel(ctx, isRightSide = true).apply {
                                val label = if (deck2State.bpm > 0f) String.format(Locale.US, "%.1f", deck2State.bpm * deck2State.playbackRate) else "0.00"
                                setDeckLabel(label)
                                setAccentColor(android.graphics.Color.parseColor("#00E5FF"))
                                setPlaying(deck2State.isPlaying)
                                setAlbumArt(deck2State.coverArtBitmap)
                                setPanelBackgroundColor(themeColors.bg.toArgb())
                                setPitchTrackColor(pitchTrackColor)
                                setOnFxClickListener { isDeck2FxOpen = true }
                                setOnEqClickListener { isDeck2EqOpen = true }
                                setOnLoopClickListener { isDeck2LoopOpen = true }
                                setOnPitchChangeListener { rate, _ ->
                                    DjPlayerController.setPlaybackRate(1, rate, isManualUserAction = true)
                                }
                                setOnJogTouchListener(object : JogWheelView.OnJogTouchListener {
                                    override fun onTouchDown() { DjPlayerController.setPlatterTouched(1, true) }
                                    override fun onScratch(deltaAngle: Float) { DjPlayerController.scratch(1, deltaAngle / 10.0f) }
                                    override fun onTouchUp() { DjPlayerController.setPlatterTouched(1, false) }
                                })
                                DjPlayerController.onJogRotateListenerDeck2 = { deltaAngle ->
                                    rotateBy(deltaAngle)
                                }
                            }
                        },
                        update = { panel ->
                            val label = if (deck2State.bpm > 0f) String.format(Locale.US, "%.1f", deck2State.bpm * deck2State.playbackRate) else "0.00"
                            panel.setDeckLabel(label)
                            panel.setPlaying(deck2State.isPlaying)
                            panel.setActive(deck2State.isPlatterTouched)
                            panel.setAlbumArt(deck2State.coverArtBitmap)
                            panel.setPanelBackgroundColor(themeColors.bg.toArgb())
                            panel.setPitchTrackColor(pitchTrackColor)
                            panel.setPlaybackRate(deck2State.playbackRate)
                            panel.setLoopActive(deck2State.isLoopActive, android.graphics.Color.parseColor("#00E5FF"))
                            if (deck2State.durationMs > 0) {
                                val progress = deck2State.currentPositionMs.toFloat() / deck2State.durationMs.toFloat()
                                val remainingMs = maxOf(0L, deck2State.durationMs - deck2State.currentPositionMs)
                                panel.setTrackProgress(progress, remainingMs)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Overlay EQ Deck 2
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isDeck2EqOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Deck2EqOverlayPanel(onClose = { isDeck2EqOpen = false })
                    }
                }

                // Overlay LOOP Deck 2
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isDeck2LoopOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Deck2LoopOverlayPanel(onClose = { isDeck2LoopOpen = false })
                    }
                }

                // Overlay FX Deck 2
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isDeck2FxOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Deck2FxOverlayPanel(onClose = { isDeck2FxOpen = false })
                    }
                }
            }
        }

        // --- INFERIOR: BARRA DE TRANSPORTE Y CROSSFADER ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(themeColors.surface, RoundedCornerShape(4.dp))
                .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Deck 1 Transport
            Row(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DjTransportButton(
                    text = "CUE",
                    textColor = Color(0xFFFF9100),
                    modifier = Modifier.weight(1f)
                )
                DjTransportButton(
                    text = if (deck1State.isPlaying) "❚❚" else "▶",
                    textColor = Color(0xFFFF9100),
                    modifier = Modifier.weight(1.4f),
                    isBlinking = deck1State.isLoaded && !deck1State.isPlaying,
                    onClick = { DjPlayerController.togglePlayPause(0) }
                )
                DjTransportButton(
                    text = "SYNC",
                    textColor = if (deck1State.isSyncActive) Color.Black else themeColors.textPrimary,
                    isActive = deck1State.isSyncActive,
                    activeContainerColor = Color(0xFF39FF14),
                    isBlinking = false,
                    modifier = Modifier.weight(1f),
                    onClick = { DjPlayerController.toggleSync(0) }
                )
            }

            // Crossfader
            val crossfaderPos by DjPlayerController.crossfaderPosition.collectAsState()
            Box(
                modifier = Modifier.weight(1.2f),
                contentAlignment = Alignment.Center
            ) {
                DjContinuousCrossfader(
                    position = crossfaderPos,
                    onPositionChange = { pos -> DjPlayerController.setCrossfader(pos) }
                )
            }

            // Deck 2 Transport
            Row(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DjTransportButton(
                    text = "SYNC",
                    textColor = if (deck2State.isSyncActive) Color.Black else themeColors.textPrimary,
                    isActive = deck2State.isSyncActive,
                    activeContainerColor = Color(0xFF39FF14),
                    isBlinking = false,
                    modifier = Modifier.weight(1f),
                    onClick = { DjPlayerController.toggleSync(1) }
                )
                DjTransportButton(
                    text = if (deck2State.isPlaying) "❚❚" else "▶",
                    textColor = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1.4f),
                    isBlinking = deck2State.isLoaded && !deck2State.isPlaying,
                    onClick = { DjPlayerController.togglePlayPause(1) }
                )
                DjTransportButton(
                    text = "CUE",
                    textColor = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- BOTÓN DE PADS (FLOTANTE ABAJO EN EL MEDIO) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {},
                modifier = Modifier.size(44.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.buttonBg
                ),
                shape = RoundedCornerShape(50)
            ) {
                PadsIcon()
            }
        }

        // --- BOTÓN DE MENÚ (FLOTANTE ARRIBA EN EL MEDIO - SOLO VISIBLE SI NO HAY PANELES ABIERTOS) ---
        if (!isMenuOpen && activeLibraryDeck == null && !isSyncLabOpen && !isBpmInspectorOpen) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .zIndex(10f),
                color = themeColors.buttonBg,
                shape = RoundedCornerShape(4.dp),
                onClick = { isMenuOpen = true }
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "☰",
                        color = themeColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- OVERLAY DEL MENÚ PRINCIPAL (Despliega de arriba hacia abajo) ---
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(20f)
        ) {
            MenuOverlayPanel(
                onClose = { isMenuOpen = false },
                onOpenGridEditor = {
                    isGridEditOpen = true
                    isMenuOpen = false
                }
            )
        }

        // --- OVERLAY PANTALLA COMPLETA DE EDICIÓN DE REJILLA (BEATGRID EDIT & PARALLEL WAVEFORMS) ---
        if (isGridEditOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(45f)
                    .background(Color(0xFF0F1217)),
                contentAlignment = Alignment.Center
            ) {
                FullScreenGridEditOverlay(
                    deck1State = deck1State,
                    deck2State = deck2State,
                    onClose = { isGridEditOpen = false }
                )
            }
        }

        // --- OVERLAY DEL LABORATORIO DE AUDITORÍA KYMATIK BPM NDK ---
        if (isKymatikLabOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
                    .background(themeColors.bg),
                contentAlignment = Alignment.Center
            ) {
                KymatikBpmLaboratoryView(
                    onCloseClick = { isKymatikLabOpen = false }
                )
            }
        }

        // --- OVERLAY DEL INSPECTOR DE BPM ---
        if (isBpmInspectorOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(35f)
                    .background(themeColors.bg),
                contentAlignment = Alignment.Center
            ) {
                BpmInspectorLabView(
                    onCloseClick = { isBpmInspectorOpen = false }
                )
            }
        }

        // --- OVERLAY DEL LABORATORIO DE SINCRONIZACIÓN ---
        if (isSyncLabOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(30f)
                    .background(themeColors.bg),
                contentAlignment = Alignment.Center
            ) {
                SyncValidationLabView(
                    onCloseClick = { isSyncLabOpen = false }
                )
            }
        }

        // --- OVERLAY DE LA BIBLIOTECA DE MÚSICA ---
        if (activeLibraryDeck != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(40f)
                    .background(themeColors.bg),
                contentAlignment = Alignment.Center
            ) {
                LibraryBrowserComponent(
                    targetDeck = activeLibraryDeck!!,
                    onTrackSelected = { _ ->
                        activeLibraryDeck = null
                        DjPlayerController.activeLibraryDeckState.value = null
                    },
                    onCloseClick = {
                        activeLibraryDeck = null
                        DjPlayerController.activeLibraryDeckState.value = null
                    }
                )
            }
        }
    }
}

@Composable
fun MenuOverlayPanel(
    onClose: () -> Unit,
    onOpenGridEditor: () -> Unit = {}
) {
    val themeColors = DjThemeManager.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .background(themeColors.bg)
            .border(1.5.dp, themeColors.border, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header del Menú
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(themeColors.surface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "☰", color = Color(0xFFFF9100), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "MENÚ PRINCIPAL & CONFIGURACIÓN",
                        color = themeColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = themeColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // HERRAMIENTAS PRINCIPALES
            Text(
                text = "HERRAMIENTAS DE EDICIÓN Y AJUSTE:",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            // Botón Edición de Rejilla Pantalla Completa (GRID EDIT & PARALLEL WAVEFORMS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00A3FF).copy(alpha = 0.2f))
                    .border(1.5.dp, Color(0xFF00A3FF), RoundedCornerShape(6.dp))
                    .clickable { onOpenGridEditor() }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🎛️ EDICIÓN DE REJILLA Y ONDAS PARALELAS (GRID EDIT)",
                        color = Color(0xFF00A3FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ajuste manual < GRID >, Tap Tempo, Set Beat 1 y ondas paralelas en pantalla completa",
                        color = Color.LightGray,
                        fontSize = 9.sp
                    )
                }
                Text(text = "▶", color = Color(0xFF00A3FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "SELECCIONAR APARIENCIA / TEMA VISUAL:",
                color = Color(0xFFFF9100),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

                DjTheme.entries.forEach { themeOption ->
                    val isSelected = DjThemeManager.currentTheme == themeOption

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFFFF9100).copy(alpha = 0.25f) else themeColors.surface)
                            .border(
                                1.5.dp,
                                if (isSelected) Color(0xFFFF9100) else themeColors.border,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { DjThemeManager.currentTheme = themeOption }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Swatch Preview Color Box
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        when (themeOption) {
                                            DjTheme.NIGHT -> Color(0xFF050507)
                                            DjTheme.DAY -> Color(0xFFFFFFFF)
                                            DjTheme.PRO_GRADIENT -> Color(0xFF2C2C3A)
                                        },
                                        CircleShape
                                    )
                                    .border(1.dp, Color.White, CircleShape)
                            )

                            Column {
                                Text(
                                    text = themeOption.displayName,
                                    color = themeColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = themeOption.description,
                                    color = themeColors.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (isSelected) {
                            Text(text = "✓", color = Color(0xFFFF9100), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

fun formatTimeMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

fun upsampleWaveform(input: ByteArray, targetPoints: Int = 12000): ByteArray {
    val inPoints = input.size / 3
    if (inPoints == 0) return input
    val output = ByteArray(targetPoints * 3)

    for (i in 0 until targetPoints) {
        val srcPos = (i.toFloat() / (targetPoints - 1).toFloat()) * (inPoints - 1)
        val idx0 = srcPos.toInt().coerceIn(0, inPoints - 1)
        val idx1 = (idx0 + 1).coerceIn(0, inPoints - 1)
        val frac = srcPos - idx0

        val r0 = (input[idx0 * 3].toInt() and 0xFF)
        val g0 = (input[idx0 * 3 + 1].toInt() and 0xFF)
        val b0 = (input[idx0 * 3 + 2].toInt() and 0xFF)

        val r1 = (input[idx1 * 3].toInt() and 0xFF)
        val g1 = (input[idx1 * 3 + 1].toInt() and 0xFF)
        val b1 = (input[idx1 * 3 + 2].toInt() and 0xFF)

        output[i * 3] = (r0 + (r1 - r0) * frac).toInt().coerceIn(0, 255).toByte()
        output[i * 3 + 1] = (g0 + (g1 - g0) * frac).toInt().coerceIn(0, 255).toByte()
        output[i * 3 + 2] = (b0 + (b1 - b0) * frac).toInt().coerceIn(0, 255).toByte()
    }
    return output
}

fun downsampleWaveform(input: ByteArray, targetPoints: Int = 1200): ByteArray {
    val inPoints = input.size / 3
    if (inPoints <= targetPoints || inPoints == 0) return input
    val output = ByteArray(targetPoints * 3)

    for (i in 0 until targetPoints) {
        val srcStart = (i * inPoints) / targetPoints
        val srcEnd = ((i + 1) * inPoints) / targetPoints

        var maxR = 0
        var maxG = 0
        var maxB = 0

        for (j in srcStart until srcEnd.coerceAtMost(inPoints)) {
            val r = input[j * 3].toInt() and 0xFF
            val g = input[j * 3 + 1].toInt() and 0xFF
            val b = input[j * 3 + 2].toInt() and 0xFF

            if (r > maxR) maxR = r
            if (g > maxG) maxG = g
            if (b > maxB) maxB = b
        }

        output[i * 3] = maxR.toByte()
        output[i * 3 + 1] = maxG.toByte()
        output[i * 3 + 2] = maxB.toByte()
    }
    return output
}

enum class WaveformMode(val label: String) {
    OVERVIEW("OVERVIEW"),
    SPECTRUM_THREAD("M-COLOR THREAD"),
    RGB_ZOOM("SINGLE COLOR")
}

/**
 * Renderizador Individual Ultra-Rápido para el Modo Visión Completa (OVERVIEW).
 * Optimizado a 1,200 puntos max para renderizado instantáneo a 60-120 FPS sin caídas de rendimiento.
 */
@Composable
fun OverviewWaveformCanvas(
    waveformBytes: ByteArray?,
    currentPositionMs: Long,
    durationMs: Long,
    bpm: Float = 0f,
    firstBeatMs: Double = 0.0,
    accentColor: Color,
    onSeek: (Float) -> Unit
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val progress = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    var zoomScale by remember { mutableStateOf(1.0f) }
    var panOffsetFactor by remember { mutableStateOf(0.0f) }

    // Auto-Scroll Continuo al reproducir la canción cuando hay Zoom activado
    LaunchedEffect(progress, zoomScale) {
        if (zoomScale > 1.0f) {
            val targetPan = (progress - (0.5f / zoomScale)).coerceIn(0.0f, 1.0f - 1.0f / zoomScale)
            panOffsetFactor = targetPan
        }
    }

    val overviewWaveform = remember(waveformBytes) {
        if (waveformBytes == null || waveformBytes.isEmpty()) null
        else if (waveformBytes.size <= 3600) waveformBytes
        else downsampleWaveform(waveformBytes, 1200)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(1.0f, 8.0f)
                    if (zoomScale > 1.0f) {
                        panOffsetFactor = (panOffsetFactor - pan.x / (size.width * zoomScale)).coerceIn(0.0f, 1.0f - 1.0f / zoomScale)
                    } else {
                        panOffsetFactor = 0.0f
                    }
                }
            }
            .pointerInput(durationMs, zoomScale, panOffsetFactor) {
                detectTapGestures { offset ->
                    val clickedProgressOnVisibleWidth = offset.x / size.width.toFloat()
                    val globalProgress = (panOffsetFactor + clickedProgressOnVisibleWidth / zoomScale).coerceIn(0f, 1f)
                    currentOnSeek(globalProgress)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        if (overviewWaveform == null || overviewWaveform.isEmpty()) {
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.4f),
                start = Offset(0f, centerY),
                end = Offset(w, centerY),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        val pointCount = overviewWaveform.size / 3
        if (pointCount == 0) return@Canvas

        val startPointIdx = (panOffsetFactor * pointCount).toInt().coerceIn(0, pointCount - 1)
        val visiblePointCount = (pointCount / zoomScale).toInt().coerceAtLeast(10)
        val endPointIdx = (startPointIdx + visiblePointCount).coerceAtMost(pointCount)

        val barWidth = w / visiblePointCount.toFloat()

        // 1. Draw Played Region Background Tint Shading
        val playheadGlobalRatio = progress
        if (playheadGlobalRatio >= panOffsetFactor && playheadGlobalRatio <= panOffsetFactor + 1.0f / zoomScale) {
            val playheadX = (playheadGlobalRatio - panOffsetFactor) * zoomScale * w
            drawRect(
                color = accentColor.copy(alpha = 0.12f),
                topLeft = Offset(0f, 0f),
                size = Size(playheadX, h)
            )
        } else if (playheadGlobalRatio > panOffsetFactor + 1.0f / zoomScale) {
            drawRect(
                color = accentColor.copy(alpha = 0.12f),
                topLeft = Offset(0f, 0f),
                size = Size(w, h)
            )
        }

        // 2. Beat Grid Ticks Overlay in Overview
        if (bpm > 40f && durationMs > 0L) {
            val beatIntervalMs = (60000.0f / bpm).toDouble()
            var beatTimeMs = if (firstBeatMs > 0.0) firstBeatMs else 0.0
            val totalDurationMsDouble = durationMs.toDouble()
            var beatIndex = 0

            while (beatTimeMs < totalDurationMsDouble) {
                val beatProgress = (beatTimeMs / totalDurationMsDouble).toFloat()
                if (beatProgress >= panOffsetFactor && beatProgress <= panOffsetFactor + 1.0f / zoomScale) {
                    val tickX = (beatProgress - panOffsetFactor) * zoomScale * w
                    val isDownbeat = (beatIndex % 4 == 0)
                    val tickColor = if (isDownbeat) Color(0x66FF5252) else Color(0x33FFFFFF)
                    val strokeW = if (isDownbeat) 1.5f else 1.0f
                    drawLine(
                        color = tickColor,
                        start = Offset(tickX, 0f),
                        end = Offset(tickX, h),
                        strokeWidth = strokeW
                    )
                }
                beatTimeMs += beatIntervalMs
                beatIndex++
            }
        }

        // 3. Render VirtualDJ / Rekordbox Style Smooth Cubic Spline Waveform (Monotone Cubic To Curve)
        val ribbonPath = Path()
        val threadPath = Path()

        var isFirstPoint = true
        var prevX = 0f
        var prevTopY = centerY

        for (i in startPointIdx until endPointIdx) {
            val r = (overviewWaveform[i * 3].toInt() and 0xFF) / 255f
            val g = (overviewWaveform[i * 3 + 1].toInt() and 0xFF) / 255f
            val b = (overviewWaveform[i * 3 + 2].toInt() and 0xFF) / 255f

            val x = (i - startPointIdx) * barWidth

            // High Dynamic Range: Exponent 1.35 keeps valleys thin & beat hits razor-sharp
            val bassSpike = Math.pow(r.toDouble(), 1.35).toFloat() * 1.10f
            val midSpike = Math.pow(g.toDouble(), 1.60).toFloat() * 0.25f
            val amplitude = (bassSpike + midSpike).coerceIn(0.015f, 0.95f)
            val curTopY = centerY - (amplitude * (h * 0.38f))

            if (isFirstPoint) {
                ribbonPath.moveTo(x, curTopY)
                threadPath.moveTo(x, centerY)
                isFirstPoint = false
            } else {
                val cx = prevX + (x - prevX) / 2f
                ribbonPath.cubicTo(cx, prevTopY, cx, curTopY, x, curTopY)
                threadPath.cubicTo(cx, centerY, cx, centerY, x, centerY)
            }
            prevX = x
            prevTopY = curTopY
        }

        var prevBottomY = centerY
        var isFirstBottom = true
        for (i in (endPointIdx - 1) downTo startPointIdx) {
            val r = (overviewWaveform[i * 3].toInt() and 0xFF) / 255f
            val g = (overviewWaveform[i * 3 + 1].toInt() and 0xFF) / 255f
            val b = (overviewWaveform[i * 3 + 2].toInt() and 0xFF) / 255f

            val x = (i - startPointIdx) * barWidth

            val bassSpike = Math.pow(r.toDouble(), 1.35).toFloat() * 1.10f
            val midSpike = Math.pow(g.toDouble(), 1.60).toFloat() * 0.25f
            val amplitude = (bassSpike + midSpike).coerceIn(0.015f, 0.95f)
            val curBottomY = centerY + (amplitude * (h * 0.38f))

            if (isFirstBottom) {
                ribbonPath.lineTo(x, curBottomY)
                isFirstBottom = false
            } else {
                val cx = prevX - (prevX - x) / 2f
                ribbonPath.cubicTo(cx, prevBottomY, cx, curBottomY, x, curBottomY)
            }
            prevX = x
            prevBottomY = curBottomY
        }
        ribbonPath.close()

        // Draw Ribbon Envelope
        drawPath(
            path = ribbonPath,
            color = accentColor.copy(alpha = 0.60f)
        )

        // 4. Draw Playhead Needle with Triangular Head Pointers
        if (playheadGlobalRatio >= panOffsetFactor && playheadGlobalRatio <= panOffsetFactor + 1.0f / zoomScale) {
            val playheadX = (playheadGlobalRatio - panOffsetFactor) * zoomScale * w

            // Needle Line
            drawLine(
                color = Color.White,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, h),
                strokeWidth = 2.dp.toPx()
            )

            // Top Triangle Pointer
            val triSize = 5.dp.toPx()
            val topTriPath = Path().apply {
                moveTo(playheadX - triSize, 0f)
                lineTo(playheadX + triSize, 0f)
                lineTo(playheadX, triSize * 1.2f)
                close()
            }
            drawPath(topTriPath, color = Color.White)

            // Bottom Triangle Pointer
            val bottomTriPath = Path().apply {
                moveTo(playheadX - triSize, h)
                lineTo(playheadX + triSize, h)
                lineTo(playheadX, h - triSize * 1.2f)
                close()
            }
            drawPath(bottomTriPath, color = Color.White)
        }
    }
}

/**
 * Renderizador de Forma de Onda Zoom de Alta Definición (Pioneer CDJ-3000 / Rekordbox 3-Band Mode).
 * Ultra-fluido a 60-120 FPS con 160 puntos visuales sub-píxel y capas de silueta continua de 3 bandas:
 * - Rojo Coral (#FF3D00) para Sub-Bass Kick (Bombos).
 * - Verde Esmeralda (#00E676) para Punch/Snare (Cajas/Tambora/Claps).
 * - Cían Eléctrico (#00E5FF) para Hi-Hats/Percusión (Platos/Güira).
 */
@Composable
fun RgbZoomWaveformCanvas(
    waveformBytes: ByteArray?,
    currentPositionMs: Long,
    durationMs: Long,
    bpm: Float = 0f,
    firstBeatMs: Double = 0.0,
    accentColor: Color
) {
    val progress = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val denseWaveform = remember(waveformBytes) {
        if (waveformBytes == null || waveformBytes.isEmpty()) null
        else if (waveformBytes.size >= 36000) waveformBytes
        else upsampleWaveform(waveformBytes, 12000)
    }

    Canvas(
        modifier = Modifier.fillMaxSize().clipToBounds()
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val centerPlayheadX = w / 2f

        if (denseWaveform == null || denseWaveform.isEmpty()) {
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.4f),
                start = Offset(0f, centerY),
                end = Offset(w, centerY),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        val pointCount = denseWaveform.size / 3
        if (pointCount == 0) return@Canvas

        val visiblePoints = 160
        val barWidth = w / visiblePoints.toFloat()

        val floatIndex = progress * pointCount
        val baseIndex = floatIndex.toInt()
        val subPixelFraction = floatIndex - baseIndex

        val halfVisible = visiblePoints / 2
        val startIndex = baseIndex - halfVisible
        val maxHalfH = h * 0.44f

        // 1. Baseline Zero-Crossing Line
        drawLine(
            color = Color(0xFF1E222D),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Dynamic Pioneer CDJ-3000 Beat Grid Overlay
        if (bpm > 40f && durationMs > 0L) {
            val beatIntervalMs = (60000.0f / bpm).toDouble()
            val windowStartMs = currentPositionMs.toDouble() - (halfVisible.toDouble() / pointCount.toDouble()) * durationMs.toDouble()
            val windowEndMs = currentPositionMs.toDouble() + (halfVisible.toDouble() / pointCount.toDouble()) * durationMs.toDouble()

            var beatTimeMs = if (firstBeatMs > 0.0) firstBeatMs else 0.0
            var beatIndex = 0

            if (beatTimeMs < windowStartMs) {
                val skippedBeats = ((windowStartMs - beatTimeMs) / beatIntervalMs).toInt()
                beatTimeMs += skippedBeats * beatIntervalMs
                beatIndex += skippedBeats
            }

            while (beatTimeMs <= windowEndMs) {
                if (beatTimeMs >= windowStartMs) {
                    val beatFractionInWindow = (beatTimeMs - windowStartMs) / (windowEndMs - windowStartMs)
                    val tickX = (beatFractionInWindow * w).toFloat()
                    val isDownbeat = (beatIndex % 4 == 0)

                    if (isDownbeat) {
                        drawLine(
                            color = Color(0xAAFF3D00),
                            start = Offset(tickX, 0f),
                            end = Offset(tickX, h),
                            strokeWidth = 1.8f
                        )
                    } else {
                        drawLine(
                            color = Color(0x44FFFFFF),
                            start = Offset(tickX, 0f),
                            end = Offset(tickX, h),
                            strokeWidth = 1.0f
                        )
                    }
                }
                beatTimeMs += beatIntervalMs
                beatIndex++
            }
        }

        // 3. Render VirtualDJ / Rekordbox Style Smooth Cubic Spline Waveform (Monotone Cubic To Curve)
        val ribbonPath = Path()
        val threadPath = Path()

        var isFirstZoomPoint = true
        var prevZoomX = 0f
        var prevZoomTopY = centerY

        for (i in 0 until visiblePoints) {
            val pIdx0 = (startIndex + i).coerceIn(0, pointCount - 1)
            val pIdx1 = (startIndex + i + 1).coerceIn(0, pointCount - 1)

            val r0 = (denseWaveform[pIdx0 * 3].toInt() and 0xFF) / 255f
            val g0 = (denseWaveform[pIdx0 * 3 + 1].toInt() and 0xFF) / 255f
            val b0 = (denseWaveform[pIdx0 * 3 + 2].toInt() and 0xFF) / 255f

            val r1 = (denseWaveform[pIdx1 * 3].toInt() and 0xFF) / 255f
            val g1 = (denseWaveform[pIdx1 * 3 + 1].toInt() and 0xFF) / 255f
            val b1 = (denseWaveform[pIdx1 * 3 + 2].toInt() and 0xFF) / 255f

            val r = (r0 + (r1 - r0) * subPixelFraction).coerceIn(0f, 1f)
            val g = (g0 + (g1 - g0) * subPixelFraction).coerceIn(0f, 1f)
            val b = (b0 + (b1 - b0) * subPixelFraction).coerceIn(0f, 1f)

            val x = (i - subPixelFraction) * barWidth
            val amplitude = maxOf(0.025f, (r * 0.50f + g * 0.35f + b * 0.15f))
            val curZoomTopY = centerY - (amplitude * maxHalfH)

            if (isFirstZoomPoint) {
                ribbonPath.moveTo(x, curZoomTopY)
                threadPath.moveTo(x, centerY)
                isFirstZoomPoint = false
            } else {
                val cx = prevZoomX + (x - prevZoomX) / 2f
                ribbonPath.cubicTo(cx, prevZoomTopY, cx, curZoomTopY, x, curZoomTopY)
                threadPath.cubicTo(cx, centerY, cx, centerY, x, centerY)
            }
            prevZoomX = x
            prevZoomTopY = curZoomTopY
        }

        var prevZoomBottomY = centerY
        var isFirstZoomBottom = true
        for (i in (visiblePoints - 1) downTo 0) {
            val pIdx0 = (startIndex + i).coerceIn(0, pointCount - 1)
            val pIdx1 = (startIndex + i + 1).coerceIn(0, pointCount - 1)

            val r0 = (denseWaveform[pIdx0 * 3].toInt() and 0xFF) / 255f
            val g0 = (denseWaveform[pIdx0 * 3 + 1].toInt() and 0xFF) / 255f
            val b0 = (denseWaveform[pIdx0 * 3 + 2].toInt() and 0xFF) / 255f

            val r1 = (denseWaveform[pIdx1 * 3].toInt() and 0xFF) / 255f
            val g1 = (denseWaveform[pIdx1 * 3 + 1].toInt() and 0xFF) / 255f
            val b1 = (denseWaveform[pIdx1 * 3 + 2].toInt() and 0xFF) / 255f

            val r = (r0 + (r1 - r0) * subPixelFraction).coerceIn(0f, 1f)
            val g = (g0 + (g1 - g0) * subPixelFraction).coerceIn(0f, 1f)
            val b = (b0 + (b1 - b0) * subPixelFraction).coerceIn(0f, 1f)

            val x = (i - subPixelFraction) * barWidth
            val amplitude = maxOf(0.025f, (r * 0.50f + g * 0.35f + b * 0.15f))
            val curZoomBottomY = centerY + (amplitude * maxHalfH)

            if (isFirstZoomBottom) {
                ribbonPath.lineTo(x, curZoomBottomY)
                isFirstZoomBottom = false
            } else {
                val cx = prevZoomX - (prevZoomX - x) / 2f
                ribbonPath.cubicTo(cx, prevZoomBottomY, cx, curZoomBottomY, x, curZoomBottomY)
            }
            prevZoomX = x
            prevZoomBottomY = curZoomBottomY
        }
        ribbonPath.close()

        // 1. Draw Ribbon Fill
        drawPath(
            path = ribbonPath,
            color = accentColor.copy(alpha = 0.65f)
        )

        // 4. Center Playhead Needle with Glow and Head Pointers
        // Outer Glow Line
        drawLine(
            color = accentColor.copy(alpha = 0.45f),
            start = Offset(centerPlayheadX, 0f),
            end = Offset(centerPlayheadX, h),
            strokeWidth = 5.dp.toPx()
        )
        // Main Needle Line
        drawLine(
            color = Color.White,
            start = Offset(centerPlayheadX, 0f),
            end = Offset(centerPlayheadX, h),
            strokeWidth = 2.dp.toPx()
        )

        // Top Neon Pointer
        val triSize = 6.dp.toPx()
        val topTriPath = Path().apply {
            moveTo(centerPlayheadX - triSize, 0f)
            lineTo(centerPlayheadX + triSize, 0f)
            lineTo(centerPlayheadX, triSize * 1.3f)
            close()
        }
        drawPath(topTriPath, color = accentColor)

        // Bottom Neon Pointer
        val bottomTriPath = Path().apply {
            moveTo(centerPlayheadX - triSize, h)
            lineTo(centerPlayheadX + triSize, h)
            lineTo(centerPlayheadX, h - triSize * 1.3f)
            close()
        }
        drawPath(bottomTriPath, color = accentColor)
    }
}

@Composable
fun Deck1WaveformHeader(
    trackTitle: String,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    waveformBytes: ByteArray? = null,
    bpm: Float = 0f,
    firstBeatMs: Double = 0.0,
    mode: WaveformMode = WaveformMode.OVERVIEW,
    onModeChange: (WaveformMode) -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onFolderClick: () -> Unit
) {
    val themeColors = DjThemeManager.colors
    val timeDisplay = if (durationMs > 0L) {
        val remainingMs = maxOf(0L, durationMs - currentPositionMs)
        formatTimeMs(remainingMs)
    } else {
        formatTimeMs(currentPositionMs)
    }

    val isZoomMode = mode == WaveformMode.RGB_ZOOM

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(themeColors.surface, RoundedCornerShape(4.dp))
            .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isZoomMode) {
                // Folder Button (Shown in Overview Mode)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(themeColors.buttonBg, RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(4.dp))
                        .clickable(onClick = onFolderClick),
                    contentAlignment = Alignment.Center
                ) {
                    FolderIcon(tint = Color(0xFFFF9100))
                }

                // Track Title (Shown in Overview Mode)
                Text(
                    text = formatTrackTitle(trackTitle, 22),
                    color = Color(0xFFFF9100),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Mode Toggle Button: [ OVERVIEW / RGB ZOOM ]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E222B))
                    .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(3.dp))
                    .clickable {
                        val nextMode = when (mode) {
                            WaveformMode.OVERVIEW -> WaveformMode.SPECTRUM_THREAD
                            WaveformMode.SPECTRUM_THREAD -> WaveformMode.RGB_ZOOM
                            WaveformMode.RGB_ZOOM -> WaveformMode.OVERVIEW
                        }
                        onModeChange(nextMode)
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    color = Color(0xFFFF9100),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Time Countdown Display
            Text(
                text = timeDisplay,
                color = Color(0xFFFF9100),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Waveform Render Box con Clip de Seguridad Estricto
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(themeColors.container)
                .border(1.dp, themeColors.border, RoundedCornerShape(2.dp))
                .clickable {
                    val nextMode = if (mode == WaveformMode.OVERVIEW) WaveformMode.RGB_ZOOM else WaveformMode.OVERVIEW
                    onModeChange(nextMode)
                }
        ) {
            if (mode == WaveformMode.OVERVIEW) {
                OverviewWaveformCanvas(
                    waveformBytes = waveformBytes,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    bpm = bpm,
                    firstBeatMs = firstBeatMs,
                    accentColor = Color(0xFFFF9100),
                    onSeek = onSeek
                )
            } else {
                RgbZoomWaveformCanvas(
                    waveformBytes = waveformBytes,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    bpm = bpm,
                    firstBeatMs = firstBeatMs,
                    accentColor = Color(0xFFFF9100)
                )
            }
        }
    }
}

@Composable
fun Deck2WaveformHeader(
    trackTitle: String,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    waveformBytes: ByteArray? = null,
    bpm: Float = 0f,
    firstBeatMs: Double = 0.0,
    mode: WaveformMode = WaveformMode.OVERVIEW,
    onModeChange: (WaveformMode) -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onFolderClick: () -> Unit
) {
    val themeColors = DjThemeManager.colors
    val timeDisplay = if (durationMs > 0L) {
        val remainingMs = maxOf(0L, durationMs - currentPositionMs)
        formatTimeMs(remainingMs)
    } else {
        formatTimeMs(currentPositionMs)
    }

    val isZoomMode = mode == WaveformMode.RGB_ZOOM

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(themeColors.surface, RoundedCornerShape(4.dp))
            .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Countdown Display
            Text(
                text = timeDisplay,
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Mode Toggle Button: [ OVERVIEW / RGB ZOOM ]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E222B))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(3.dp))
                    .clickable {
                        val nextMode = when (mode) {
                            WaveformMode.OVERVIEW -> WaveformMode.SPECTRUM_THREAD
                            WaveformMode.SPECTRUM_THREAD -> WaveformMode.RGB_ZOOM
                            WaveformMode.RGB_ZOOM -> WaveformMode.OVERVIEW
                        }
                        onModeChange(nextMode)
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    color = Color(0xFF00E5FF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isZoomMode) {
                // Track Title (Shown in Overview Mode)
                Text(
                    text = formatTrackTitle(trackTitle, 22),
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )

                // Folder Button (Shown in Overview Mode)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(themeColors.buttonBg, RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                        .clickable(onClick = onFolderClick),
                    contentAlignment = Alignment.Center
                ) {
                    FolderIcon(tint = Color(0xFF00E5FF))
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Waveform Render Box con Clip de Seguridad Estricto
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(themeColors.container)
                .border(1.dp, themeColors.border, RoundedCornerShape(2.dp))
                .clickable {
                    val nextMode = if (mode == WaveformMode.OVERVIEW) WaveformMode.RGB_ZOOM else WaveformMode.OVERVIEW
                    onModeChange(nextMode)
                }
        ) {
            if (mode == WaveformMode.OVERVIEW) {
                OverviewWaveformCanvas(
                    waveformBytes = waveformBytes,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    bpm = bpm,
                    firstBeatMs = firstBeatMs,
                    accentColor = Color(0xFF00E5FF),
                    onSeek = onSeek
                )
            } else {
                RgbZoomWaveformCanvas(
                    waveformBytes = waveformBytes,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    bpm = bpm,
                    firstBeatMs = firstBeatMs,
                    accentColor = Color(0xFF00E5FF)
                )
            }
        }
    }
}

@Composable
fun Deck1EqOverlayPanel(onClose: () -> Unit) {
    val themeColors = DjThemeManager.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .background(themeColors.surface)
            .border(1.5.dp, Color(0xFFFF9100), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(themeColors.container, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK 1 - EQ & MIXER",
                    color = Color(0xFFFF9100),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = themeColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                EqDeckFadersView(accentColor = Color(0xFFFF9100), isRightDeck = false, deckIndex = 0)
            }
        }
    }
}

@Composable
fun Deck2EqOverlayPanel(onClose: () -> Unit) {
    val themeColors = DjThemeManager.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .background(themeColors.surface)
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(themeColors.container, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK 2 - EQ & MIXER",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = themeColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                EqDeckFadersView(accentColor = Color(0xFF00E5FF), isRightDeck = true, deckIndex = 1)
            }
        }
    }
}

@Composable
fun Deck1LoopOverlayPanel(onClose: () -> Unit) {
    val themeColors = DjThemeManager.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .background(themeColors.surface)
            .border(1.5.dp, Color(0xFFFF9100), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(themeColors.container, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK 1 - LOOPS & PADS",
                    color = Color(0xFFFF9100),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = themeColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                LoopDeckPadsView(accentColor = Color(0xFFFF9100), deckIndex = 0)
            }
        }
    }
}

@Composable
fun Deck2LoopOverlayPanel(onClose: () -> Unit) {
    val themeColors = DjThemeManager.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .background(themeColors.surface)
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(themeColors.container, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK 2 - LOOPS & PADS",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = themeColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                LoopDeckPadsView(accentColor = Color(0xFF00E5FF), deckIndex = 1)
            }
        }
    }
}

@Composable
fun Deck1FxOverlayPanel(onClose: () -> Unit) {
    val themeColors = DjThemeManager.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .background(themeColors.surface)
            .border(1.5.dp, Color(0xFFFF9100), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(themeColors.container, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK 1 - STUDIO FX SUITE",
                    color = Color(0xFFFF9100),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = themeColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                FxDeckProcessorView(accentColor = Color(0xFFFF9100), deckIndex = 0)
            }
        }
    }
}

@Composable
fun Deck2FxOverlayPanel(onClose: () -> Unit) {
    val themeColors = DjThemeManager.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .background(themeColors.surface)
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(themeColors.container, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK 2 - STUDIO FX SUITE",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = themeColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                FxDeckProcessorView(accentColor = Color(0xFF00E5FF), deckIndex = 1)
            }
        }
    }
}

@Composable
fun FxDeckProcessorView(accentColor: Color, deckIndex: Int = 0) {
    var selectedBeatFraction by remember { mutableStateOf(0.50f) }
    var echoWet by remember { mutableStateOf(0.0f) }
    var echoFeedback by remember { mutableStateOf(0.45f) }
    var reverbWet by remember { mutableStateOf(0.0f) }

    val beatFractions = listOf(0.125f to "1/8", 0.25f to "1/4", 0.5f to "1/2", 1.0f to "1", 2.0f to "2")

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. PING-PONG BEAT ECHO
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "BEAT ECHO", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                beatFractions.forEach { (frac, label) ->
                    val isSel = selectedBeatFraction == frac
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSel) accentColor else Color(0xFF1E222B))
                            .clickable {
                                selectedBeatFraction = frac
                                DjPlayerController.setDelayParams(deckIndex, frac, echoFeedback, echoWet, false)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            EqVerticalFaderItem("WET", echoWet, maxVal = 1.0f, accentColor = accentColor) { newWet ->
                echoWet = newWet
                DjPlayerController.setDelayParams(deckIndex, selectedBeatFraction, echoFeedback, newWet, false)
            }
        }

        // 2. REVERB
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "REVERB", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

            EqVerticalFaderItem("SPACE", reverbWet, maxVal = 1.0f, accentColor = accentColor) { newWet ->
                reverbWet = newWet
                DjPlayerController.setReverbParams(deckIndex, 0.70f, newWet)
            }
        }
    }
}

@Composable
fun LoopDeckPadsView(accentColor: Color, deckIndex: Int = 0) {
    val themeColors = DjThemeManager.colors
    val initialDeckState = remember {
        if (deckIndex == 0) DjPlayerController.deck1State.value else DjPlayerController.deck2State.value
    }

    var loopSize by remember { mutableStateOf(initialDeckState.loopSize) }
    var isLoopActive by remember { mutableStateOf(initialDeckState.isLoopActive) }
    var activePadIndex by remember { mutableStateOf<Int?>(initialDeckState.activePadIndex) }
    var isSlipMode by remember { mutableStateOf(initialDeckState.isSlipMode) }

    val loopSizes = listOf(1, 2, 4, 8, 16, 32)

    LaunchedEffect(loopSize, isLoopActive, activePadIndex, isSlipMode) {
        DjPlayerController.setLoopState(deckIndex, loopSize, isLoopActive, activePadIndex, isSlipMode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { isSlipMode = !isSlipMode },
                    modifier = Modifier
                        .height(30.dp)
                        .widthIn(min = 52.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSlipMode) accentColor else themeColors.buttonBg
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "SLIP",
                        color = if (isSlipMode) Color.Black else themeColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { isLoopActive = false },
                    modifier = Modifier.size(height = 30.dp, width = 36.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.buttonBg
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "✕",
                        color = themeColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 42.dp)
                        .background(themeColors.container, RoundedCornerShape(6.dp))
                        .border(1.5.dp, themeColors.border, RoundedCornerShape(6.dp))
                        .clickable {
                            val currentIndex = loopSizes.indexOf(loopSize)
                            if (currentIndex > 0) {
                                loopSize = loopSizes[currentIndex - 1]
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "◀", color = themeColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .widthIn(min = 110.dp)
                        .background(
                            if (isLoopActive) accentColor else themeColors.container,
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.5.dp, accentColor, RoundedCornerShape(6.dp))
                        .clickable { isLoopActive = !isLoopActive }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↺  $loopSize",
                        color = if (isLoopActive) Color.Black else themeColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 42.dp)
                        .background(themeColors.container, RoundedCornerShape(6.dp))
                        .border(1.5.dp, themeColors.border, RoundedCornerShape(6.dp))
                        .clickable {
                            val currentIndex = loopSizes.indexOf(loopSize)
                            if (currentIndex < loopSizes.size - 1) {
                                loopSize = loopSizes[currentIndex + 1]
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "▶", color = themeColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (pad in 1..4) {
                    val isActive = activePadIndex == pad
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (isActive) accentColor else themeColors.container,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isActive) Color.White else themeColors.border,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                activePadIndex = if (isActive) null else pad
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$pad",
                            color = if (isActive) Color.Black else themeColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (pad in 5..8) {
                    val isActive = activePadIndex == pad
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (isActive) accentColor else themeColors.container,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isActive) Color.White else themeColors.border,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                activePadIndex = if (isActive) null else pad
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$pad",
                            color = if (isActive) Color.Black else themeColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveGainKnob(
    label: String = "GAIN",
    accentColor: Color,
    initialValue: Float = 1.0f,
    onValueChange: (Float) -> Unit = {}
) {
    val themeColors = DjThemeManager.colors
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var knobValue by remember(initialValue) { mutableStateOf(initialValue) }
    LaunchedEffect(initialValue) {
        knobValue = initialValue
    }
    // Gain initialValue is 0.0f..2.0f, center is 1.0f
    val normalizedGain = (knobValue / 2.0f).coerceIn(0f, 1f)
    val angleDegrees = (normalizedGain - 0.5f) * 270f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            color = themeColors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(themeColors.container, CircleShape)
                .border(1.5.dp, themeColors.border, CircleShape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaNorm = -dragAmount / 120f
                        val newNorm = ((knobValue / 2.0f) + deltaNorm).coerceIn(0f, 1f)
                        knobValue = newNorm * 2.0f
                        currentOnValueChange(knobValue)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 2.dp.toPx()
                val angleRad = Math.toRadians((angleDegrees - 90).toDouble())
                val endX = center.x + radius * Math.cos(angleRad).toFloat()
                val endY = center.y + radius * Math.sin(angleRad).toFloat()

                drawLine(
                    color = accentColor,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun EqDeckFadersView(accentColor: Color, isRightDeck: Boolean = false, deckIndex: Int = 0) {
    val deckStateFlow = if (deckIndex == 0) DjPlayerController.deck1State else DjPlayerController.deck2State
    val deckState by deckStateFlow.collectAsState()

    val volValue = deckState.volume
    val highValue = deckState.high
    val midValue = deckState.mid
    val lowValue = deckState.low
    val filterValue = deckState.filter
    val gainValue = deckState.gain

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EqVerticalFaderItem("GAIN", gainValue, maxVal = 2.0f, accentColor = accentColor) { newGain ->
            DjPlayerController.setGain(deckIndex, newGain)
        }
        EqVerticalFaderItem("HIGH", highValue, maxVal = 2.0f, accentColor = accentColor) { newHigh ->
            DjPlayerController.setEqHigh(deckIndex, newHigh)
        }
        EqVerticalFaderItem("MID", midValue, maxVal = 2.0f, accentColor = accentColor) { newMid ->
            DjPlayerController.setEqMid(deckIndex, newMid)
        }
        EqVerticalFaderItem("LOW", lowValue, maxVal = 2.0f, accentColor = accentColor) { newLow ->
            DjPlayerController.setEqLow(deckIndex, newLow)
        }
        EqVerticalFaderItem("FILTER", filterValue, maxVal = 1.0f, accentColor = accentColor) { newFilter ->
            DjPlayerController.setFilter(deckIndex, newFilter)
        }
        EqVerticalFaderItem("VOL", volValue, maxVal = 1.0f, accentColor = accentColor) { newVol ->
            DjPlayerController.setDeckVolume(deckIndex, newVol)
        }
    }
}

@Composable
fun EqVerticalFaderItem(
    label: String,
    value: Float,
    maxVal: Float = 2.0f,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    val themeColors = DjThemeManager.colors
    val normalizedVal = (if (maxVal > 1.0f) value / maxVal else value).coerceIn(0f, 1f)
    val isCentered = Math.abs(normalizedVal - 0.5f) < 0.05f
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(if (isCentered) accentColor.copy(alpha = 0.35f) else themeColors.container, RoundedCornerShape(3.dp))
                .border(1.dp, if (isCentered) accentColor else themeColors.border, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isCentered && DjThemeManager.currentTheme == DjTheme.DAY) Color.Black else themeColors.textPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .width(36.dp)
                .background(themeColors.container, RoundedCornerShape(3.dp))
                .border(1.dp, if (isCentered) accentColor.copy(alpha = 0.5f) else themeColors.border, RoundedCornerShape(3.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val trackHeight = size.height.toFloat()
                            if (trackHeight > 0f) {
                                val norm = (1f - (offset.y / trackHeight)).coerceIn(0f, 1f)
                                val targetVal = if (maxVal > 1.0f) norm * maxVal else norm
                                currentOnValueChange(targetVal)
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val trackHeight = size.height.toFloat()
                            if (trackHeight > 0f) {
                                val norm = (1f - (change.position.y / trackHeight)).coerceIn(0f, 1f)
                                val targetVal = if (maxVal > 1.0f) norm * maxVal else norm
                                currentOnValueChange(targetVal)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Track line background
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF08080E), RoundedCornerShape(1.dp))
            )

            // Center mark line
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .background(if (isCentered) accentColor else themeColors.textSecondary)
            )

            // Fader thumb button
            val verticalBias = (1.0f - 2.0f * normalizedVal).coerceIn(-1.0f, 1.0f)
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = BiasAlignment(0f, verticalBias)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 22.dp)
                        .background(
                            if (isCentered) accentColor.copy(alpha = 0.35f) else themeColors.buttonBg,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = if (isCentered) 1.5.dp else 1.dp,
                            color = if (isCentered) accentColor else themeColors.border,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(2.5.dp)
                            .background(if (isCentered) Color.White else accentColor)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderIcon(tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val folderPath = Path().apply {
            moveTo(0f, h * 0.3f)
            lineTo(w * 0.38f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.45f)
            lineTo(w, h * 0.45f)
            lineTo(w, h * 0.9f)
            lineTo(0f, h * 0.9f)
            close()
        }
        drawPath(folderPath, color = tint)
    }
}

@Composable
fun PadsIcon() {
    val themeColors = DjThemeManager.colors
    Column(
        modifier = Modifier.size(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(4.5.dp).background(themeColors.textPrimary, RoundedCornerShape(1.dp)))
            Box(modifier = Modifier.size(4.5.dp).background(themeColors.textPrimary, RoundedCornerShape(1.dp)))
            Box(modifier = Modifier.size(4.dp).background(themeColors.textPrimary, RoundedCornerShape(1.dp)))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(4.5.dp).background(themeColors.textPrimary, RoundedCornerShape(1.dp)))
            Box(modifier = Modifier.size(4.dp).background(themeColors.textPrimary, RoundedCornerShape(1.dp)))
            Box(modifier = Modifier.size(4.dp).background(themeColors.textPrimary, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
fun DjTransportButton(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    isBlinking: Boolean = false,
    isActive: Boolean = false,
    activeContainerColor: Color = Color(0xFF39FF14),
    activeTextColor: Color = Color.Black,
    onClick: () -> Unit = {}
) {
    val themeColors = DjThemeManager.colors
    var isBlinkVisible by remember { mutableStateOf(true) }

    if (isBlinking) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                isBlinkVisible = !isBlinkVisible
            }
        }
    } else {
        isBlinkVisible = true
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isBlinking && isBlinkVisible -> textColor.copy(alpha = 0.35f)
                isActive -> activeContainerColor
                else -> themeColors.buttonBg
            }
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = when {
                isBlinking && !isBlinkVisible -> themeColors.textSecondary
                isActive -> activeTextColor
                else -> textColor
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DjContinuousCrossfader(
    position: Float = 0.5f,
    onPositionChange: (Float) -> Unit = {}
) {
    val themeColors = DjThemeManager.colors
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    var faderPosition by remember(position) { mutableStateOf(position) }
    LaunchedEffect(position) {
        faderPosition = position
    }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(themeColors.container, RoundedCornerShape(4.dp))
            .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val thumbWidthPx = with(density) { 46.dp.toPx() }
                    val trackWidth = size.width.toFloat() - thumbWidthPx
                    if (trackWidth > 0f) {
                        val delta = dragAmount / trackWidth
                        faderPosition = (faderPosition + delta).coerceIn(0f, 1f)
                        currentOnPositionChange(faderPosition)
                    }
                }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFF08080A), RoundedCornerShape(2.dp))
        )
        
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(18.dp)
                .background(themeColors.textSecondary)
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = BiasAlignment((faderPosition - 0.5f) * 2f, 0f)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 32.dp)
                    .background(themeColors.buttonBg, RoundedCornerShape(4.dp))
                    .border(1.5.dp, Color(0xFFFF9100), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(Color(0xFFFF9100))
                )
            }
        }
    }
}
