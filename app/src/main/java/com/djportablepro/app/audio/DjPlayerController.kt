package com.djportablepro.app.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.djportablepro.app.analysis.BeatSyncEngine
import com.djportablepro.app.analysis.BeatSyncService
import com.djportablepro.app.analysis.BpmAnalysisService
import com.djportablepro.app.audio.engine.NativeAudioPlayer
import com.djportablepro.app.core.Deck
import com.djportablepro.app.library.TrackDatabase
import com.djportablepro.app.logging.DjLogger
import com.djportablepro.app.midi.decoder.MidiProcessor
import com.djportablepro.app.midi.mapping.HerculesImpulse500Profile
import com.djportablepro.app.midi.usb.UsbMidiManager
import com.djportablepro.app.state.DjEventBus
import com.djportablepro.app.state.DjStateManager
import com.djportablepro.app.ui.library.GlobalBatchAnalyzer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

enum class SyncRole {
    NONE,           // Sync is OFF
    FOLLOWER,       // Follows the Master/Leader deck
    LEADER_SOFT,    // Auto-assigned Master/Leader (chosen automatically by engine)
    LEADER_EXPLICIT // Explicitly assigned Master/Leader by user
}

data class DeckAudioState(
    val isLoaded: Boolean = false,
    val trackTitle: String = "No Track Loaded",
    val coverArtBitmap: Bitmap? = null,
    val bpm: Float = 0.0f,
    val waveformBytes: ByteArray? = null,
    val firstBeatMs: Double = 0.0,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackRate: Float = 1.0f,
    val volume: Float = 1.0f,
    val gain: Float = 1.0f,
    val high: Float = 1.0f,
    val mid: Float = 1.0f,
    val low: Float = 1.0f,
    val filter: Float = 0.5f,
    val loopSize: Int = 4,
    val isLoopActive: Boolean = false,
    val activePadIndex: Int? = null,
    val isSlipMode: Boolean = false,
    val isSyncActive: Boolean = false,
    val syncRole: SyncRole = SyncRole.NONE,
    val userOffsetMs: Double = 0.0,
    val isPlatterTouched: Boolean = false
)

/**
 * Controladora desacoplada de audio.
 * Carga la carátula real de la música directamente dentro del centro del Jog Wheel.
 */
object DjPlayerController {
    private const val TAG = "DjPlayerController"

    private var usbMidiManager: UsbMidiManager? = null
    private var midiProcessor: MidiProcessor? = null
    private var stateManager: DjStateManager? = null
    private var nativePlayer: NativeAudioPlayer? = null
    private var appContext: Context? = null
    private var isNativeAvailable = false

    private var mediaPlayerDeck1: MediaPlayer? = null
    private var mediaPlayerDeck2: MediaPlayer? = null
    private var equalizerDeck1: Equalizer? = null
    private var equalizerDeck2: Equalizer? = null

    // Estado persistente del Crossfader (0.0 = Left, 0.5 = Center, 1.0 = Right)
    private var currentCrossfaderPosition = 0.5f
    private val _crossfaderPosition = MutableStateFlow(0.5f)
    val crossfaderPosition: StateFlow<Float> = _crossfaderPosition.asStateFlow()

    private val beatSyncService = BeatSyncService()
    private val beatSyncEngine = BeatSyncEngine()

    private val _deck1State = MutableStateFlow(DeckAudioState())
    val deck1State: StateFlow<DeckAudioState> = _deck1State.asStateFlow()

    private val _deck2State = MutableStateFlow(DeckAudioState())
    val deck2State: StateFlow<DeckAudioState> = _deck2State.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(100)
                updatePlaybackProgress(0)
                updatePlaybackProgress(1)
            }
        }
        startContinuousSyncLoop()
    }

    var onJogRotateListenerDeck1: ((Float) -> Unit)? = null
    var onJogRotateListenerDeck2: ((Float) -> Unit)? = null

    // Navigation & Library Control Flow for physical MIDI
    val activeLibraryDeckState = MutableStateFlow<String?>(null)
    var onBrowseEncoderListener: ((Int) -> Unit)? = null
    var onBrowsePushListener: (() -> Unit)? = null
    var onLoadDeckListener: ((Deck) -> Unit)? = null

    fun handleLoadAction(deck: Deck) {
        scope.launch(Dispatchers.Main) {
            if (onLoadDeckListener != null) {
                onLoadDeckListener?.invoke(deck)
            } else {
                val targetName = if (deck == Deck.DECK_A) "DECK 1" else "DECK 2"
                activeLibraryDeckState.value = targetName
            }
        }
    }

    fun handleBrowsePush() {
        scope.launch(Dispatchers.Main) {
            if (onBrowsePushListener != null) {
                onBrowsePushListener?.invoke()
            } else {
                if (activeLibraryDeckState.value == null) {
                    activeLibraryDeckState.value = "DECK 1"
                }
            }
        }
    }

    fun handleBrowseEncoder(delta: Int) {
        scope.launch(Dispatchers.Main) {
            if (onBrowseEncoderListener != null) {
                onBrowseEncoderListener?.invoke(delta)
            } else {
                if (activeLibraryDeckState.value == null) {
                    activeLibraryDeckState.value = "DECK 1"
                }
            }
        }
    }

    private fun updatePlaybackProgress(deckIndex: Int) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        if (!state.isLoaded) return

        if (isNativeAvailable) {
            val frames = nativePlayer?.getPosition(deckIndex) ?: 0L
            val posMs = (frames * 1000L) / 44100L
            var durMs = state.durationMs
            if (durMs <= 0L) {
                val totalFrames = nativePlayer?.getDuration(deckIndex) ?: 0L
                if (totalFrames > 0L) {
                    durMs = (totalFrames * 1000L) / 44100L
                }
            }
            val update: (DeckAudioState) -> DeckAudioState = {
                it.copy(
                    currentPositionMs = posMs,
                    durationMs = if (durMs > 0L) durMs else it.durationMs
                )
            }
            if (deckIndex == 0) _deck1State.update(update) else _deck2State.update(update)
        } else {
            val player = if (deckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
            if (player != null && state.isPlaying) {
                val posMs = player.currentPosition.toLong()
                val durMs = player.duration.toLong()
                val update: (DeckAudioState) -> DeckAudioState = {
                    it.copy(
                        currentPositionMs = posMs,
                        durationMs = if (durMs > 0L) durMs else it.durationMs
                    )
                }
                if (deckIndex == 0) _deck1State.update(update) else _deck2State.update(update)
            }
        }
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        try {
            nativePlayer = NativeAudioPlayer(appContext!!).apply {
                if (initialize(0)) {
                    start()
                    isNativeAvailable = true
                }
            }
            DjLogger.i(TAG, "Native audio engine initialized successfully")
        } catch (e: Throwable) {
            DjLogger.e(TAG, "Failed to initialize native audio engine: ${e.message}")
            isNativeAvailable = false
        }

        // Initialize USB MIDI Controller Management (Hercules Inpulse 500 & Universal MIDI)
        try {
            val eventBus = DjEventBus()
            val processor = MidiProcessor(eventBus)
            processor.activeProfile = HerculesImpulse500Profile()
            midiProcessor = processor

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            stateManager = DjStateManager(eventBus, nativePlayer, scope)

            val manager = UsbMidiManager(appContext!!, processor)
            manager.startDiscovery()
            usbMidiManager = manager

            DjLogger.i(TAG, "[USB_MIDI_INIT] USB MIDI Controller discovery started successfully.")
        } catch (e: Throwable) {
            DjLogger.e(TAG, "[USB_MIDI_ERROR] Failed to initialize USB MIDI: ${e.message}")
        }
    }

    fun loadTrack(deckIndex: Int, path: String, title: String, bpm: Float = 0.0f) {
        val ctx = appContext ?: return
        DjLogger.i(TAG, "[DECK_LOAD] Deck $deckIndex, Path: $path, Title: $title, BPM: $bpm")

        // Immediately mark deck as paused/loading in state
        val pauseStateUpdate: (DeckAudioState) -> DeckAudioState = { it.copy(isPlaying = false, currentPositionMs = 0L) }
        if (deckIndex == 0) _deck1State.update(pauseStateUpdate) else _deck2State.update(pauseStateUpdate)

        scope.launch(Dispatchers.IO) {
            // 1. Extraer la carátula real en hilo secundario (Background I/O)
            val coverArt = extractCoverArt(ctx, path)

            // 2. Cargar el archivo de audio en C++ NDK / MediaPlayer en hilo secundario
            if (isNativeAvailable) {
                try {
                    nativePlayer?.pause(deckIndex)
                    nativePlayer?.load(deckIndex, path)
                } catch (e: Throwable) {
                    DjLogger.e(TAG, "Native load failed for deck $deckIndex: ${e.message}")
                }
            } else {
                try {
                    withContext(Dispatchers.Main) {
                        if (deckIndex == 0) {
                            try { equalizerDeck1?.release() } catch (_: Exception) {}
                            equalizerDeck1 = null
                            try { mediaPlayerDeck1?.stop() } catch (_: Exception) {}
                            try { mediaPlayerDeck1?.release() } catch (_: Exception) {}
                            mediaPlayerDeck1 = null

                            mediaPlayerDeck1 = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .build()
                                )
                                if (path.startsWith("content://") || path.startsWith("http")) {
                                    setDataSource(ctx, Uri.parse(path))
                                } else {
                                    setDataSource(path)
                                }
                                prepare()
                            }

                            try {
                                equalizerDeck1 = Equalizer(0, mediaPlayerDeck1!!.audioSessionId).apply {
                                    enabled = true
                                }
                            } catch (eqEx: Exception) {
                                eqEx.printStackTrace()
                            }
                        } else {
                            try { equalizerDeck2?.release() } catch (_: Exception) {}
                            equalizerDeck2 = null
                            try { mediaPlayerDeck2?.stop() } catch (_: Exception) {}
                            try { mediaPlayerDeck2?.release() } catch (_: Exception) {}
                            mediaPlayerDeck2 = null

                            mediaPlayerDeck2 = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .build()
                                )
                                if (path.startsWith("content://") || path.startsWith("http")) {
                                    setDataSource(ctx, Uri.parse(path))
                                } else {
                                    setDataSource(path)
                                }
                                prepare()
                            }

                            try {
                                equalizerDeck2 = Equalizer(0, mediaPlayerDeck2!!.audioSessionId).apply {
                                    enabled = true
                                }
                            } catch (eqEx: Exception) {
                                eqEx.printStackTrace()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // 3. Extraer duración con MediaMetadataRetriever en hilo secundario
            var durationMs = 0L
            try {
                val mmr = MediaMetadataRetriever()
                if (path.startsWith("content://")) {
                    mmr.setDataSource(ctx, Uri.parse(path))
                } else {
                    mmr.setDataSource(path)
                }
                val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                mmr.release()
                durationMs = durStr?.toLongOrNull() ?: 0L
            } catch (_: Exception) { }

            if (durationMs <= 0L) {
                val player = if (deckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
                val frames = if (isNativeAvailable) (nativePlayer?.getDuration(deckIndex) ?: 0L) else 0L
                durationMs = if (frames > 0L) (frames * 1000L) / 44100L else (player?.duration?.toLong() ?: 0L)
            }

            // 4. Consultar SQLite DB v2 en hilo secundario o analizar dinámicamente
            val trackDb = TrackDatabase(ctx)
            var cachedAnalysis = trackDb.getAnalysis(path)
            var firstBeatFrame = cachedAnalysis?.firstBeatFrame ?: 0L

            if ((cachedAnalysis == null || cachedAnalysis.version < 2 || firstBeatFrame == 0L || cachedAnalysis.bpm <= 0.0f) && isNativeAvailable) {
                try {
                    val resolvedPath = resolveAudioPathOrFd(ctx, path)
                    val freshResult = nativePlayer?.analyzeTrack(resolvedPath, 0.0f)
                    if (freshResult != null && freshResult.bpm > 0.0f) {
                        firstBeatFrame = freshResult.firstBeatFrame
                        trackDb.saveAnalysis(path, freshResult.bpm, firstBeatFrame, freshResult.waveform, freshResult.version)
                        cachedAnalysis = trackDb.getAnalysis(path)
                    }
                } catch (e: Throwable) {
                    DjLogger.e(TAG, "Native analyzeTrack failed during load: ${e.message}")
                }
            }

            val finalBpm = when {
                cachedAnalysis != null && cachedAnalysis.bpm > 0.0f -> cachedAnalysis.bpm
                bpm > 0.0f -> bpm
                else -> {
                    val filenameBpm = BpmAnalysisService().extractBpmHint(path).toFloat()
                    if (filenameBpm > 0.0f) filenameBpm else 0.0f
                }
            }

            // Actualizar memoria viva para reflejar el BPM analizado en la biblioteca
            if (finalBpm > 0.0f) {
                GlobalBatchAnalyzer.updatedBpms[path] = String.format(Locale.US, "%.1f", finalBpm)
            }

            val waveformData = cachedAnalysis?.waveform
            val calculatedFirstBeatMs = (firstBeatFrame * 1000.0) / 44100.0

            originalBpmMap[deckIndex] = finalBpm
            originalFirstBeatMap[deckIndex] = calculatedFirstBeatMs

            if (isNativeAvailable) {
                nativePlayer?.setBeatGrid(deckIndex, finalBpm, firstBeatFrame)
            }

            val stateUpdate: (DeckAudioState) -> DeckAudioState = {
                it.copy(
                    isLoaded = true,
                    trackTitle = title,
                    coverArtBitmap = coverArt,
                    bpm = finalBpm,
                    waveformBytes = waveformData,
                    firstBeatMs = calculatedFirstBeatMs,
                    isPlaying = false,
                    durationMs = durationMs,
                    currentPositionMs = 0L,
                    volume = 1.0f,
                    gain = 1.0f,
                    high = 1.0f,
                    mid = 1.0f,
                    low = 1.0f,
                    filter = 0.5f,
                    isLoopActive = false,
                    activePadIndex = null
                )
            }

            withContext(Dispatchers.Main) {
                if (deckIndex == 0) _deck1State.update(stateUpdate) else _deck2State.update(stateUpdate)
                updateCrossfaderVolumes()
            }
        }
    }

    fun seekTo(deckIndex: Int, targetPositionMs: Long) {
        val targetFrame = (targetPositionMs * 44100L) / 1000L
        try {
            if (isNativeAvailable) {
                nativePlayer?.seek(deckIndex, targetFrame)
            } else {
                val player = if (deckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
                player?.seekTo(targetPositionMs.toInt())
            }
            if (deckIndex == 0) {
                _deck1State.update { it.copy(currentPositionMs = targetPositionMs) }
            } else {
                _deck2State.update { it.copy(currentPositionMs = targetPositionMs) }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun resolveAudioPathOrFd(context: Context, path: String): String {
        if (!path.startsWith("content://")) return path

        try {
            val uri = Uri.parse(path)
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataIndex != -1) {
                        val realPath = cursor.getString(dataIndex)
                        if (!realPath.isNullOrBlank() && File(realPath).exists()) {
                            return realPath
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(path), "r")
            if (pfd != null) {
                val fd = pfd.detachFd()
                return "fd:$fd"
            }
        } catch (_: Exception) {}

        return path
    }

    private fun extractCoverArt(context: Context, audioPath: String): Bitmap? {
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
        } catch (_: Throwable) {
            null
        }
    }

    fun togglePlayPause(deckIndex: Int) {
        val currentState = if (deckIndex == 0) _deck1State.value else _deck2State.value
        DjLogger.i(TAG, "[DECK_TOGGLE] Deck $deckIndex, isLoaded=${currentState.isLoaded}, isPlaying=${currentState.isPlaying}")
        if (!currentState.isLoaded) return

        val isPlayingNow = currentState.isPlaying

        updateCrossfaderVolumes()

        if (!isPlayingNow && currentState.isSyncActive) {
            val otherDeckIndex = if (deckIndex == 0) 1 else 0
            val otherState = if (otherDeckIndex == 0) _deck1State.value else _deck2State.value
            if (otherState.isPlaying && isNativeAvailable) {
                val masterPhase = nativePlayer?.getBeatPhase(otherDeckIndex) ?: 0.0
                nativePlayer?.alignPhase(deckIndex, masterPhase)
            }
        }

        if (isNativeAvailable) {
            try {
                if (isPlayingNow) {
                    nativePlayer?.pause(deckIndex)
                } else {
                    nativePlayer?.play(deckIndex)
                }
            } catch (e: Throwable) {
                DjLogger.e(TAG, "Native play/pause failed: ${e.message}")
            }
        } else {
            val player = if (deckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
            try {
                if (isPlayingNow) {
                    player?.pause()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player != null) {
                        val params = player.playbackParams
                        params.speed = currentState.playbackRate
                        player.playbackParams = params
                    }
                    player?.start()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        val update: (DeckAudioState) -> DeckAudioState = { it.copy(isPlaying = !isPlayingNow) }
        if (deckIndex == 0) _deck1State.update(update) else _deck2State.update(update)
        reevaluateLeaderRoles()
    }

    fun setPlaybackRate(deckIndex: Int, rate: Float, isManualUserAction: Boolean = false) {
        val clampedRate = rate.coerceIn(0.5f, 2.0f)
        val currentState = if (deckIndex == 0) _deck1State.value else _deck2State.value

        if (isManualUserAction && currentState.isSyncActive) {
            deactivateSync(deckIndex)
        }

        if (isNativeAvailable) {
            try {
                nativePlayer?.setPlaybackRate(deckIndex, clampedRate)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } else {
            val player = if (deckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
            try {
                if (player != null && currentState.isPlaying) {
                    val params = player.playbackParams
                    params.speed = clampedRate
                    player.playbackParams = params
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        val update: (DeckAudioState) -> DeckAudioState = { it.copy(playbackRate = clampedRate) }
        if (deckIndex == 0) _deck1State.update(update) else _deck2State.update(update)

        // --- CROSS DJ / VIRTUAL DJ MASTER PITCH TRACKING ---
        // If the OTHER deck has SYNC enabled, update its rate dynamically to follow the Master!
        val otherDeckIndex = if (deckIndex == 0) 1 else 0
        val otherState = if (otherDeckIndex == 0) _deck1State.value else _deck2State.value

        if (otherState.isSyncActive && otherState.isLoaded && currentState.isLoaded) {
            val masterEffectiveBpm = (currentState.bpm * clampedRate).toDouble()
            val slaveBaseBpm = otherState.bpm.toDouble().takeIf { it > 0.0 } ?: 128.0
            if (masterEffectiveBpm > 0.0 && slaveBaseBpm > 0.0) {
                val newSlaveRate = (masterEffectiveBpm / slaveBaseBpm).toFloat().coerceIn(0.5f, 2.0f)

                if (isNativeAvailable) {
                    nativePlayer?.setPlaybackRate(otherDeckIndex, newSlaveRate)
                    val masterPhase = nativePlayer?.getBeatPhase(deckIndex) ?: 0.0
                    nativePlayer?.alignPhase(otherDeckIndex, masterPhase)
                } else {
                    val player = if (otherDeckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
                    try {
                        if (player != null && otherState.isPlaying) {
                            val params = player.playbackParams
                            params.speed = newSlaveRate
                            player.playbackParams = params
                        }
                    } catch (_: Exception) { }
                }

                val updateOther: (DeckAudioState) -> DeckAudioState = { it.copy(playbackRate = newSlaveRate) }
                if (otherDeckIndex == 0) _deck1State.update(updateOther) else _deck2State.update(updateOther)
            }
        }
    }

    private fun startContinuousSyncLoop() {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(33) // 30 FPS continuous sub-bass & beatgrid phase lock loop
                try {
                    val d1 = _deck1State.value
                    val d2 = _deck2State.value

                    if ((d1.isPlaying || d2.isPlaying) && (d1.isSyncActive || d2.isSyncActive) && isNativeAvailable) {
                        reevaluateLeaderRoles()
                        val updatedD1 = _deck1State.value
                        val updatedD2 = _deck2State.value

                        val (followerIdx, followerState) = when {
                            updatedD1.syncRole == SyncRole.FOLLOWER -> Pair(0, updatedD1)
                            updatedD2.syncRole == SyncRole.FOLLOWER -> Pair(1, updatedD2)
                            else -> Pair(-1, updatedD1)
                        }

                        if (followerIdx != -1 && !followerState.isPlatterTouched && followerState.isLoaded) {
                            val slaveFirstBeat = (followerState.firstBeatMs * 44.1).toLong()
                            val adj = nativePlayer?.calculateSyncAdjustment(
                                slaveDeckIndex = followerIdx,
                                slaveBaseBpm = followerState.bpm.toDouble().takeIf { it > 0.0 } ?: 128.0,
                                slaveFirstBeatFrame = slaveFirstBeat,
                                isSyncTriggered = false
                            )
                            if (adj != null) {
                                nativePlayer?.setPlaybackRate(followerIdx, adj.finalPlaybackRate)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private var scratchResetJobDeck1: Job? = null
    private var scratchResetJobDeck2: Job? = null
    private var platterTouchKeepAliveJobDeck1: Job? = null
    private var platterTouchKeepAliveJobDeck2: Job? = null

    fun setPlatterTouched(deckIndex: Int, isTouched: Boolean) {
        val update: (DeckAudioState) -> DeckAudioState = { it.copy(isPlatterTouched = isTouched) }
        if (deckIndex == 0) {
            _deck1State.update(update)
            if (!isTouched) {
                platterTouchKeepAliveJobDeck1?.cancel()
                scratchResetJobDeck1?.cancel()
            }
        } else {
            _deck2State.update(update)
            if (!isTouched) {
                platterTouchKeepAliveJobDeck2?.cancel()
                scratchResetJobDeck2?.cancel()
            }
        }

        if (isNativeAvailable) {
            try {
                if (!isTouched) {
                    nativePlayer?.setScratchVelocity(deckIndex, 0.0f)
                }
                nativePlayer?.setScratchMode(deckIndex, isTouched)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun keepPlatterTouchAlive(deckIndex: Int) {
        setPlatterTouched(deckIndex, true)
        if (deckIndex == 0) {
            platterTouchKeepAliveJobDeck1?.cancel()
            platterTouchKeepAliveJobDeck1 = scope.launch {
                delay(150)
                setPlatterTouched(0, false)
            }
        } else {
            platterTouchKeepAliveJobDeck2?.cancel()
            platterTouchKeepAliveJobDeck2 = scope.launch {
                delay(150)
                setPlatterTouched(1, false)
            }
        }
    }

    fun onPhysicalJogMoved(deckIndex: Int, delta: Int) {
        val stepAngle = delta * 3.5f
        scope.launch(Dispatchers.Main) {
            if (deckIndex == 0) {
                onJogRotateListenerDeck1?.invoke(stepAngle)
            } else {
                onJogRotateListenerDeck2?.invoke(stepAngle)
            }
        }
    }

    fun scratch(deckIndex: Int, velocity: Float) {
        onPhysicalJogMoved(deckIndex, (velocity * 3.0f).toInt())
        if (isNativeAvailable) {
            try {
                nativePlayer?.setScratchVelocity(deckIndex, velocity)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // Timer de auto-reseteo a 0.0f de velocidad de Scratch si el jog físico deja de girar
        if (deckIndex == 0) {
            scratchResetJobDeck1?.cancel()
            scratchResetJobDeck1 = scope.launch {
                delay(60)
                if (isNativeAvailable) {
                    try { nativePlayer?.setScratchVelocity(0, 0.0f) } catch (_: Exception) {}
                }
            }
        } else {
            scratchResetJobDeck2?.cancel()
            scratchResetJobDeck2 = scope.launch {
                delay(60)
                if (isNativeAvailable) {
                    try { nativePlayer?.setScratchVelocity(1, 0.0f) } catch (_: Exception) {}
                }
            }
        }
    }

    private var jogNudgeJobDeck1: Job? = null
    private var jogNudgeJobDeck2: Job? = null

    fun jogNudge(deckIndex: Int, delta: Int) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        if (!state.isLoaded) return

        if (!state.isPlaying) {
            // Si la pista está PAUSADA, mover el jog hace búsqueda/fleteo frame por frame sin iniciar reproducción
            val targetMs = (state.currentPositionMs + (delta * 20L)).coerceIn(0L, state.durationMs)
            seekTo(deckIndex, targetMs)
            return
        }

        val baseRate = state.playbackRate
        val nudgeFactor = (delta / 200.0f).coerceIn(-0.15f, 0.15f)
        val temporaryRate = (baseRate * (1.0f + nudgeFactor)).coerceIn(0.2f, 2.0f)

        if (isNativeAvailable) {
            try {
                nativePlayer?.setPlaybackRate(deckIndex, temporaryRate)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } else {
            val player = if (deckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
            try {
                if (player != null && state.isPlaying) {
                    val params = player.playbackParams
                    params.speed = temporaryRate
                    player.playbackParams = params
                }
            } catch (_: Exception) { }
        }

        if (deckIndex == 0) {
            jogNudgeJobDeck1?.cancel()
            jogNudgeJobDeck1 = scope.launch {
                delay(150)
                val currentBaseRate = _deck1State.value.playbackRate
                if (isNativeAvailable) {
                    nativePlayer?.setPlaybackRate(0, currentBaseRate)
                } else {
                    mediaPlayerDeck1?.let { p ->
                        try {
                            val params = p.playbackParams
                            params.speed = currentBaseRate
                            p.playbackParams = params
                        } catch (_: Exception) { }
                    }
                }
            }
        } else {
            jogNudgeJobDeck2?.cancel()
            jogNudgeJobDeck2 = scope.launch {
                delay(150)
                val currentBaseRate = _deck2State.value.playbackRate
                if (isNativeAvailable) {
                    nativePlayer?.setPlaybackRate(1, currentBaseRate)
                } else {
                    mediaPlayerDeck2?.let { p ->
                        try {
                            val params = p.playbackParams
                            params.speed = currentBaseRate
                            p.playbackParams = params
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    fun setDeckVolume(deckIndex: Int, volume: Float) {
        val update: (DeckAudioState) -> DeckAudioState = { it.copy(volume = volume) }
        if (deckIndex == 0) _deck1State.update(update) else _deck2State.update(update)
        updateCrossfaderVolumes()
    }

    enum class CrossfaderMode {
        SMOOTH,  // Mezcla progresiva / potencia constante
        SCRATCH, // Corte rápido para Turntablism / Hip-Hop
        THRU     // Desactivado / Ambos decks a 100%
    }

    var currentCrossfaderMode: CrossfaderMode = CrossfaderMode.SMOOTH
        private set

    fun setCrossfaderMode(mode: CrossfaderMode) {
        currentCrossfaderMode = mode
        updateCrossfaderVolumes()
    }

    fun setCrossfader(position: Float) {
        val pos = position.coerceIn(0f, 1f)
        currentCrossfaderPosition = pos
        _crossfaderPosition.value = pos
        updateCrossfaderVolumes()
    }

    private fun updateCrossfaderVolumes() {
        val baseVol1 = (_deck1State.value.volume * _deck1State.value.gain).coerceIn(0f, 2f)
        val baseVol2 = (_deck2State.value.volume * _deck2State.value.gain).coerceIn(0f, 2f)

        val (xfGain1, xfGain2) = when (currentCrossfaderMode) {
            CrossfaderMode.SMOOTH -> {
                val pos = currentCrossfaderPosition
                val g1 = if (pos <= 0.5f) 1.0f else (1.0f - (pos - 0.5f) * 2.0f)
                val g2 = if (pos >= 0.5f) 1.0f else (pos * 2.0f)
                g1 to g2
            }
            CrossfaderMode.SCRATCH -> {
                val pos = currentCrossfaderPosition
                val g1 = if (pos >= 0.95f) 0.0f else 1.0f
                val g2 = if (pos <= 0.05f) 0.0f else 1.0f
                g1 to g2
            }
            CrossfaderMode.THRU -> {
                1.0f to 1.0f
            }
        }

        val finalVol1 = baseVol1 * xfGain1
        val finalVol2 = baseVol2 * xfGain2

        if (isNativeAvailable) {
            try {
                nativePlayer?.setVolume(0, finalVol1)
                nativePlayer?.setVolume(1, finalVol2)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } else {
            try {
                mediaPlayerDeck1?.setVolume(finalVol1.coerceIn(0f, 1f), finalVol1.coerceIn(0f, 1f))
                mediaPlayerDeck2?.setVolume(finalVol2.coerceIn(0f, 1f), finalVol2.coerceIn(0f, 1f))
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun setEqLow(deckIndex: Int, low: Float) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        setDspParams(deckIndex, low = low, mid = state.mid, high = state.high, filter = state.filter, gain = state.gain)
    }

    fun setEqMid(deckIndex: Int, mid: Float) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        setDspParams(deckIndex, low = state.low, mid = mid, high = state.high, filter = state.filter, gain = state.gain)
    }

    fun setEqHigh(deckIndex: Int, high: Float) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        setDspParams(deckIndex, low = state.low, mid = state.mid, high = high, filter = state.filter, gain = state.gain)
    }

    fun setFilter(deckIndex: Int, filter: Float) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        setDspParams(deckIndex, low = state.low, mid = state.mid, high = state.high, filter = filter, gain = state.gain)
    }

    fun setGain(deckIndex: Int, gain: Float) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        setDspParams(deckIndex, low = state.low, mid = state.mid, high = state.high, filter = state.filter, gain = gain)
    }

    fun setDelayParams(deckIndex: Int, beatFraction: Float, feedback: Float, wet: Float, freeze: Boolean) {
        if (isNativeAvailable) {
            try {
                nativePlayer?.setDelayParams(deckIndex, beatFraction, feedback, wet, freeze)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun setReverbParams(deckIndex: Int, roomSize: Float, wet: Float) {
        if (isNativeAvailable) {
            try {
                nativePlayer?.setReverbParams(deckIndex, roomSize, wet)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun setDspParams(deckIndex: Int, low: Float, mid: Float, high: Float, filter: Float, gain: Float) {
        val updateDsp: (DeckAudioState) -> DeckAudioState = {
            it.copy(low = low, mid = mid, high = high, filter = filter, gain = gain)
        }
        if (deckIndex == 0) _deck1State.update(updateDsp) else _deck2State.update(updateDsp)
        updateCrossfaderVolumes()

        if (isNativeAvailable) {
            try {
                nativePlayer?.setDspParams(deckIndex, low, mid, high, filter, gain)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } else {
            val eq = if (deckIndex == 0) equalizerDeck1 else equalizerDeck2

            try {
                eq?.let { equalizer ->
                    val numBands = equalizer.numberOfBands.toInt()
                    if (numBands > 0) {
                        val minEQ = equalizer.bandLevelRange[0]
                        val maxEQ = equalizer.bandLevelRange[1]

                        fun toMillibels(valFactor: Float): Short {
                            val level = minEQ + (valFactor * (maxEQ - minEQ))
                            return level.toInt().coerceIn(minEQ.toInt(), maxEQ.toInt()).toShort()
                        }

                        val effectiveLow = if (filter > 0.5f) {
                            (low * (1f - (filter - 0.5f) * 2f)).coerceIn(0f, 1f)
                        } else {
                            low
                        }

                        val effectiveHigh = if (filter < 0.5f) {
                            (high * (filter * 2f)).coerceIn(0f, 1f)
                        } else {
                            high
                        }

                        equalizer.setBandLevel(0.toShort(), toMillibels(effectiveLow))
                        if (numBands > 2) equalizer.setBandLevel((numBands / 2).toShort(), toMillibels(mid))
                        if (numBands > 1) equalizer.setBandLevel((numBands - 1).toShort(), toMillibels(effectiveHigh))
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun setLoopState(deckIndex: Int, loopSize: Int, isLoopActive: Boolean, activePadIndex: Int?, isSlipMode: Boolean) {
        val updateLoop: (DeckAudioState) -> DeckAudioState = {
            it.copy(
                loopSize = loopSize,
                isLoopActive = isLoopActive,
                activePadIndex = activePadIndex,
                isSlipMode = isSlipMode
            )
        }
        if (deckIndex == 0) _deck1State.update(updateLoop) else _deck2State.update(updateLoop)

        if (isNativeAvailable) {
            try {
                val pos = nativePlayer?.getPosition(deckIndex) ?: 0L
                val endFrame = pos + (loopSize * 44100L)
                nativePlayer?.setLoop(deckIndex, pos, endFrame, isLoopActive)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } else {
            val player = if (deckIndex == 0) mediaPlayerDeck1 else mediaPlayerDeck2
            try {
                player?.isLooping = isLoopActive
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun reevaluateLeaderRoles() {
        val d1 = _deck1State.value
        val d2 = _deck2State.value

        if (!d1.isSyncActive && !d2.isSyncActive) {
            if (d1.syncRole != SyncRole.NONE) _deck1State.update { it.copy(syncRole = SyncRole.NONE) }
            if (d2.syncRole != SyncRole.NONE) _deck2State.update { it.copy(syncRole = SyncRole.NONE) }
            return
        }

        if (d1.isSyncActive && !d2.isSyncActive) {
            if (d1.syncRole != SyncRole.LEADER_SOFT && d1.syncRole != SyncRole.LEADER_EXPLICIT) {
                _deck1State.update { it.copy(syncRole = SyncRole.LEADER_SOFT) }
            }
            if (d2.syncRole != SyncRole.NONE) _deck2State.update { it.copy(syncRole = SyncRole.NONE) }
            return
        }

        if (!d1.isSyncActive && d2.isSyncActive) {
            if (d1.syncRole != SyncRole.NONE) _deck1State.update { it.copy(syncRole = SyncRole.NONE) }
            if (d2.syncRole != SyncRole.LEADER_SOFT && d2.syncRole != SyncRole.LEADER_EXPLICIT) {
                _deck2State.update { it.copy(syncRole = SyncRole.LEADER_SOFT) }
            }
            return
        }

        // Both decks have isSyncActive = true: Assign LeaderSoft based on Audible Master (Mixxx 2.5.6 Sync Engine)
        val vol1 = d1.volume * d1.gain
        val vol2 = d2.volume * d2.gain
        val isAudible1 = d1.isPlaying && vol1 > 0.05f
        val isAudible2 = d2.isPlaying && vol2 > 0.05f

        if (isAudible1 && !isAudible2) {
            _deck1State.update { it.copy(syncRole = SyncRole.LEADER_SOFT) }
            _deck2State.update { it.copy(syncRole = SyncRole.FOLLOWER) }
        } else if (isAudible2 && !isAudible1) {
            _deck1State.update { it.copy(syncRole = SyncRole.FOLLOWER) }
            _deck2State.update { it.copy(syncRole = SyncRole.LEADER_SOFT) }
        } else {
            // Both playing or both stopped: Keep explicit leader if set, otherwise default Deck 1 Leader, Deck 2 Follower
            if (d2.syncRole == SyncRole.LEADER_EXPLICIT) {
                _deck1State.update { it.copy(syncRole = SyncRole.FOLLOWER) }
            } else if (d1.syncRole == SyncRole.LEADER_EXPLICIT) {
                _deck2State.update { it.copy(syncRole = SyncRole.FOLLOWER) }
            } else if (d1.syncRole == SyncRole.NONE || d2.syncRole == SyncRole.NONE) {
                _deck1State.update { it.copy(syncRole = SyncRole.LEADER_SOFT) }
                _deck2State.update { it.copy(syncRole = SyncRole.FOLLOWER) }
            }
        }
    }

    private fun determineBpmMultiplier(slaveBpm: Double, targetBpm: Double): Double {
        if (slaveBpm <= 0.0 || targetBpm <= 0.0) return 1.0
        val ratio = slaveBpm / targetBpm
        val ratioSq = ratio * ratio
        return when {
            ratioSq > 2.0 -> 2.0
            ratioSq < 0.5 -> 0.5
            else -> 1.0
        }
    }

    /**
     * Executes Pro DJ SYNC action (Mixxx 2.5.6 EngineSync Architecture).
     * Works seamlessly whether 1 or both decks are loaded or playing.
     * Guarantees Kick-on-Kick and Snare-on-Snare phase lock.
     */
    fun syncDeck(deckIndex: Int) {
        val slaveDeckIndex = deckIndex
        val masterDeckIndex = if (deckIndex == 0) 1 else 0

        val slaveState = if (slaveDeckIndex == 0) _deck1State.value else _deck2State.value
        val masterState = if (masterDeckIndex == 0) _deck1State.value else _deck2State.value

        if (!slaveState.isLoaded) return

        if (!masterState.isLoaded) {
            // Standby SYNC activation when only slave deck is loaded
            if (slaveDeckIndex == 0) {
                _deck1State.update { it.copy(isSyncActive = true, syncRole = SyncRole.LEADER_SOFT) }
            } else {
                _deck2State.update { it.copy(isSyncActive = true, syncRole = SyncRole.LEADER_SOFT) }
            }
            return
        }

        // Both decks are loaded: Execute Mixxx 2.5.6 EngineSync Phase & Tempo Match
        val masterEffectiveBpm = (masterState.bpm * masterState.playbackRate).toDouble().takeIf { it > 0.0 } ?: 128.0
        val slaveBaseBpm = slaveState.bpm.toDouble().takeIf { it > 0.0 } ?: 128.0

        val multiplier = determineBpmMultiplier(slaveBaseBpm, masterEffectiveBpm)
        val targetBpm = masterEffectiveBpm * multiplier
        val targetRate = (targetBpm / slaveBaseBpm).toFloat().coerceIn(0.5f, 2.0f)

        setPlaybackRate(slaveDeckIndex, targetRate)

        if (isNativeAvailable) {
            try {
                val masterPhase = nativePlayer?.getBeatPhase(masterDeckIndex) ?: 0.0
                nativePlayer?.alignPhase(slaveDeckIndex, masterPhase)
                nativePlayer?.setSyncActive(slaveDeckIndex, true)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        if (!slaveState.isPlaying && masterState.isPlaying) {
            togglePlayPause(slaveDeckIndex)
        }

        if (slaveDeckIndex == 0) {
            _deck1State.update { it.copy(isSyncActive = true, syncRole = SyncRole.FOLLOWER, playbackRate = targetRate) }
            _deck2State.update { it.copy(isSyncActive = true, syncRole = if (it.syncRole == SyncRole.NONE) SyncRole.LEADER_SOFT else it.syncRole) }
        } else {
            _deck2State.update { it.copy(isSyncActive = true, syncRole = SyncRole.FOLLOWER, playbackRate = targetRate) }
            _deck1State.update { it.copy(isSyncActive = true, syncRole = if (it.syncRole == SyncRole.NONE) SyncRole.LEADER_SOFT else it.syncRole) }
        }

        reevaluateLeaderRoles()
        DjLogger.i(TAG, "[PRO_DJ_SYNC] Deck $slaveDeckIndex (Follower) synced to Deck $masterDeckIndex (Leader). Multiplier: $multiplier, TargetRate: $targetRate")
    }

    fun toggleSync(deckIndex: Int) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        if (state.isSyncActive) {
            deactivateSync(deckIndex)
        } else {
            syncDeck(deckIndex)
        }
    }

    /**
     * Deactivates SYNC mode explicitly for a deck and restores pitch control.
     */
    fun deactivateSync(deckIndex: Int) {
        val turnOffSync: (DeckAudioState) -> DeckAudioState = { it.copy(isSyncActive = false, syncRole = SyncRole.NONE, playbackRate = 1.0f) }
        if (deckIndex == 0) _deck1State.update(turnOffSync) else _deck2State.update(turnOffSync)
        setPlaybackRate(deckIndex, 1.0f)
        if (isNativeAvailable) {
            try {
                nativePlayer?.setSyncActive(deckIndex, false)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        reevaluateLeaderRoles()
        DjLogger.i(TAG, "[SYNC_DEACTIVATED] Sync deactivated for Deck $deckIndex")
    }

    // --- MANUAL BEATGRID CONTROL & TAP TEMPO (PARTE 1) ---
    private val tapHistoryMap = mutableMapOf<Int, MutableList<Long>>()
    val originalBpmMap = mutableMapOf<Int, Float>()
    val originalFirstBeatMap = mutableMapOf<Int, Double>()

    fun nudgeBeatgrid(deckIndex: Int, offsetMs: Double) {
        val updateState: (DeckAudioState) -> DeckAudioState = { state ->
            val newOffset = state.userOffsetMs + offsetMs
            val newFirstBeat = (state.firstBeatMs + offsetMs).coerceAtLeast(0.0)
            state.copy(userOffsetMs = newOffset, firstBeatMs = newFirstBeat)
        }
        if (deckIndex == 0) _deck1State.update(updateState) else _deck2State.update(updateState)
        
        if (isNativeAvailable) {
            val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
            val firstBeatFrame = ((state.firstBeatMs / 1000.0) * 44100.0).toLong()
            try {
                nativePlayer?.setBeatGrid(deckIndex, state.bpm, firstBeatFrame)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        DjLogger.d(TAG, "Nudged Beatgrid Deck $deckIndex by ${offsetMs}ms")
    }

    fun setBeat1AtCurrentPosition(deckIndex: Int) {
        val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
        val currentPos = state.currentPositionMs.toDouble()
        val updateState: (DeckAudioState) -> DeckAudioState = { s ->
            s.copy(firstBeatMs = currentPos, userOffsetMs = currentPos - (originalFirstBeatMap[deckIndex] ?: 0.0))
        }
        if (deckIndex == 0) _deck1State.update(updateState) else _deck2State.update(updateState)
        
        if (isNativeAvailable) {
            val firstBeatFrame = ((currentPos / 1000.0) * 44100.0).toLong()
            try {
                nativePlayer?.setBeatGrid(deckIndex, state.bpm, firstBeatFrame)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        DjLogger.d(TAG, "Set Beat 1 at current position ${currentPos}ms for Deck $deckIndex")
    }

    fun processTapTempo(deckIndex: Int) {
        val now = System.currentTimeMillis()
        val history = tapHistoryMap.getOrPut(deckIndex) { mutableListOf() }
        
        if (history.isNotEmpty() && (now - history.last()) > 2000) {
            history.clear()
        }
        
        history.add(now)
        if (history.size > 8) history.removeAt(0)

        if (history.size >= 4) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until history.size) {
                intervals.add(history[i] - history[i - 1])
            }
            val avgInterval = intervals.average()
            if (avgInterval > 0) {
                val tappedBpm = (60000.0 / avgInterval).toFloat().coerceIn(60.0f, 200.0f)
                val roundedBpm = (Math.round(tappedBpm * 10.0) / 10.0).toFloat()
                
                val updateState: (DeckAudioState) -> DeckAudioState = { s ->
                    s.copy(bpm = roundedBpm)
                }
                if (deckIndex == 0) _deck1State.update(updateState) else _deck2State.update(updateState)
                
                if (isNativeAvailable) {
                    val state = if (deckIndex == 0) _deck1State.value else _deck2State.value
                    val firstBeatFrame = ((state.firstBeatMs / 1000.0) * 44100.0).toLong()
                    try {
                        nativePlayer?.setBeatGrid(deckIndex, roundedBpm, firstBeatFrame)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                DjLogger.d(TAG, "Tap Tempo calculated for Deck $deckIndex: $roundedBpm BPM")
            }
        }
    }

    fun resetBeatgrid(deckIndex: Int) {
        val origBpm = originalBpmMap[deckIndex]
        val origFirstBeat = originalFirstBeatMap[deckIndex]
        
        val updateState: (DeckAudioState) -> DeckAudioState = { state ->
            state.copy(
                bpm = origBpm ?: state.bpm,
                firstBeatMs = origFirstBeat ?: state.firstBeatMs,
                userOffsetMs = 0.0
            )
        }
        if (deckIndex == 0) _deck1State.update(updateState) else _deck2State.update(updateState)
        tapHistoryMap[deckIndex]?.clear()
        
        if (isNativeAvailable && origBpm != null && origFirstBeat != null) {
            val firstBeatFrame = ((origFirstBeat / 1000.0) * 44100.0).toLong()
            try {
                nativePlayer?.setBeatGrid(deckIndex, origBpm, firstBeatFrame)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        DjLogger.d(TAG, "Reset Beatgrid for Deck $deckIndex to original NDK state")
    }

    fun release() {
        try {
            usbMidiManager?.release()
            nativePlayer?.stop()
            nativePlayer?.release()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        equalizerDeck1?.release()
        equalizerDeck2?.release()
        mediaPlayerDeck1?.release()
        mediaPlayerDeck2?.release()
        equalizerDeck1 = null
        equalizerDeck2 = null
        mediaPlayerDeck1 = null
        mediaPlayerDeck2 = null
        nativePlayer = null
        appContext = null
    }
}
