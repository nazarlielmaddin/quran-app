package com.quransanctuary.app.data.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.quransanctuary.app.data.local.LibraryDatabase
import com.quransanctuary.app.data.local.QuranRepository
import com.quransanctuary.app.data.local.SettingsRepository
import com.quransanctuary.app.data.model.RepeatMode
import com.quransanctuary.app.domain.activeVerseIndex
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlayerUiState(
    val reciterId: String = "yasir-al-dawsari",
    val surahId: Int = 1,
    val quranPlaying: Boolean = false,
    val quranLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeAyah: Int = 0,
    val timings: List<Long>? = null,
    val hasSync: Boolean = false,
    val ambientId: String? = null,
    val ambientPlaying: Boolean = false,
    val ambientLoading: Boolean = false,
    val quranVolume: Float = 0.9f,
    val ambientVolume: Float = 0.6f,
    val quranEnabled: Boolean = true,
    val repeat: RepeatMode = RepeatMode.OFF,
    val autoNext: Boolean = true,
    val loopAmbient: Boolean = true,
    val error: String? = null,
    val sleepMinutesLeft: Int? = null,
    val hasSelection: Boolean = false
)

/**
 * Application-scoped dual audio engine.
 * Mirrors the website's PlayerProvider: an independent Quran track + an independent ambient layer.
 */
class AudioEngine(
    private val context: Context,
    private val repo: QuranRepository,
    private val settings: SettingsRepository,
    private val db: LibraryDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    lateinit var quranPlayer: ExoPlayer
        private set
    lateinit var ambientPlayer: ExoPlayer
        private set

    private var cache: SimpleCache? = null
    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var saveJob: Job? = null
    private var lastPersistedMs = 0L

    // Share preview is a full-screen, self-contained player. Duck the app's
    // own Quran/ambient layers while it is open, then restore exactly what was
    // playing before it opened.
    private var previewActive = false
    private var previewWasQuranPlaying = false
    private var previewWasAmbientPlaying = false

    /** One-shot mirror for the current surah and a token that invalidates stale timing loads. */
    private var currentFallbackUri: Uri? = null
    private var fallbackTried = false
    private var quranLoadToken = 0

    /** Starts the foreground audio service so playback survives backgrounding. */
    private fun ensureService() {
        // Cheap and safe to call repeatedly: the service promotes itself to
        // foreground instantly (see QuranAudioService), so the FGS timeout can
        // never fire no matter how often playback is toggled.
        runCatching {
            val intent = android.content.Intent(context, QuranAudioService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }

    @Volatile
    private var playersReady = false

    fun initialize() {
        if (playersReady && this::quranPlayer.isInitialized && this::ambientPlayer.isInitialized) return
        // If a previous release() tore the players down, drop the dead handles
        // so fresh instances are built below (lateinit alone can't express this).
        runCatching {
            // Fresh start — (re)create everything. Any half-initialized state from
            // a failed attempt (e.g. full disk during SimpleCache creation) is
            // discarded by rebuilding.
            val dbProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
            runCatching { cache?.release() }
            cache = SimpleCache(
                File(context.cacheDir, "exo"),
                LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
                dbProvider
            )
            val http = DefaultHttpDataSource.Factory().setUserAgent("QuranSanctuary/1.0")
            // DefaultDataSource also opens file:// URIs, so downloaded surahs play
            // locally instead of being sent to the HTTP-only upstream.
            val upstream = DefaultDataSource.Factory(context, http)
            val cachedSource = CacheDataSource.Factory().setCache(cache!!).setUpstreamDataSourceFactory(upstream)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            // If old (possibly released) players exist, detach listeners first.
            runCatching {
                if (this::quranPlayer.isInitialized) quranPlayer.removeListener(quranListener)
            }
            runCatching {
                if (this::ambientPlayer.isInitialized) ambientPlayer.removeListener(ambientListener)
            }
            quranPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(cachedSource))
                .setAudioAttributes(audioAttrs, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
            ambientPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(cachedSource))
                // Ambient audio is a second mix layer and must not request audio focus.
                .setAudioAttributes(audioAttrs, false)
                .build()
            quranPlayer.addListener(quranListener)
            ambientPlayer.addListener(ambientListener)
            playersReady = true
            scope.launch { restore() }
        }.onFailure {
            playersReady = false
        }
    }

    // ---------- restore ----------

    private suspend fun restore() {
        val reciter = settings.reciterId.first()
        val surah = settings.surahId.first()
        val ambient = settings.ambientId.first()
        _state.value = _state.value.copy(
            reciterId = reciter,
            surahId = surah,
            ambientId = ambient,
            quranVolume = settings.quranVolume.first(),
            ambientVolume = settings.ambientVolume.first(),
            repeat = settings.repeat.first(),
            autoNext = settings.autoNext.first(),
            loopAmbient = settings.loopAmbient.first(),
            quranEnabled = settings.quranEnabled.first()
        )
        runCatching { if (this::quranPlayer.isInitialized) quranPlayer.volume = _state.value.quranVolume }
        runCatching { if (this::ambientPlayer.isInitialized) ambientPlayer.volume = _state.value.ambientVolume }
        runCatching {
            if (this::ambientPlayer.isInitialized) {
                ambientPlayer.repeatMode = if (_state.value.loopAmbient) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        }
        applyRepeatMode()
        prepareQuran(surah, reciter, autoplay = false)
        ambient?.let { prepareAmbient(it, autoplay = false) }
    }

    // ---------- quran ----------

    fun localQuranFile(reciterId: String, surahId: Int): File =
        File(context.filesDir, "${AudioStorage.QURAN_DIR}/$reciterId/$surahId.mp3")

    private fun resolveQuranUri(reciterId: String, surahId: Int): Uri {
        val local = localQuranFile(reciterId, surahId)
        if (local.exists() && local.length() > 0) return Uri.fromFile(local)
        val rec = repo.getRecitation(reciterId, surahId)
        return Uri.parse(rec?.audioUrl ?: "")
    }

    private fun artworkUri(reciterId: String): Uri? {
        return try {
            val reciter = repo.getReciter(reciterId) ?: return null
            val out = File(context.cacheDir, "art/${reciter.assetFileName()}")
            if (!out.exists()) {
                out.parentFile?.mkdirs()
                context.assets.open("reciters/${reciter.assetFileName()}").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            Uri.fromFile(out)
        } catch (_: Exception) {
            null
        }
    }

    private fun prepareQuran(surahId: Int, reciterId: String, autoplay: Boolean) {
        // Self-heal: if a previous init failed (e.g. full disk), retry here
        // instead of crashing on an uninitialized player.
        runCatching { initialize() }
        if (!playersReady || !this::quranPlayer.isInitialized) return
        val surah = repo.getSurah(surahId) ?: return
        val reciter = repo.getReciter(reciterId) ?: return
        val rec = repo.getRecitation(reciterId, surahId)
        val uri = resolveQuranUri(reciterId, surahId)
        // Mirror only: the timings bundled with the primary file do not match it.
        val fallbackUri = rec?.fallbackAudioUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val loadToken = ++quranLoadToken
        currentFallbackUri = fallbackUri
        fallbackTried = false
        val metadata = MediaMetadata.Builder()
            .setTitle("${surah.number}. ${surah.transliteratedName}")
            .setArtist(reciter.localizedName("az"))
            .setAlbumTitle("Azanify")
            .setArtworkUri(artworkUri(reciterId))
            .build()
        quranPlayer.setMediaItem(MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build())
        quranPlayer.prepare()
        quranPlayer.playWhenReady = autoplay && _state.value.quranEnabled
        _state.value = _state.value.copy(
            surahId = surahId, reciterId = reciterId,
            quranLoading = true, error = null,
            positionMs = 0L, durationMs = 0L, activeAyah = 0,
            timings = null, hasSync = false, hasSelection = true
        )
        scope.launch {
            val t = repo.getTimings(reciterId, surahId)
            // A mirror may have taken over while this was loading — its edit is
            // different, so never attach the primary file's timings to it.
            if (loadToken != quranLoadToken) return@launch
            if (_state.value.surahId == surahId && _state.value.reciterId == reciterId) {
                _state.value = _state.value.copy(timings = t, hasSync = t != null)
            }
        }
        scope.launch {
            settings.setSurah(surahId)
            settings.setReciter(reciterId)
        }
    }

    fun playSurah(surahId: Int, reciterId: String = _state.value.reciterId, autoplay: Boolean = true) {
        ensureService()
        if (!_state.value.quranEnabled) scope.launch { settings.setQuranEnabled(true) }
        _state.value = _state.value.copy(quranEnabled = true)
        prepareQuran(surahId, reciterId, autoplay)
    }

    fun toggleQuran() {
        if (!playersReady || !this::quranPlayer.isInitialized) {
            runCatching { initialize() }
            if (!playersReady || !this::quranPlayer.isInitialized) return
            ensureService()
            _state.value = _state.value.copy(quranEnabled = true)
            scope.launch { settings.setQuranEnabled(true) }
            prepareQuran(_state.value.surahId, _state.value.reciterId, autoplay = true)
            return
        }
        if (!_state.value.quranEnabled) {
            ensureService()
            _state.value = _state.value.copy(quranEnabled = true)
            scope.launch { settings.setQuranEnabled(true) }
            if (quranPlayer.playbackState == Player.STATE_IDLE) {
                prepareQuran(_state.value.surahId, _state.value.reciterId, autoplay = true)
            } else {
                quranPlayer.play()
            }
            return
        }
        if (quranPlayer.isPlaying) quranPlayer.pause() else {
            ensureService()
            quranPlayer.play()
        }
    }

    fun setQuranEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(quranEnabled = enabled)
        scope.launch { settings.setQuranEnabled(enabled) }
        if (!enabled) runCatching {
            if (this::quranPlayer.isInitialized) quranPlayer.pause()
        }
    }

    fun nextSurah() {
        val next = if (_state.value.surahId >= 114) 1 else _state.value.surahId + 1
        playSurah(next, autoplay = true)
    }

    fun prevSurah() {
        runCatching {
            if (this::quranPlayer.isInitialized && quranPlayer.currentPosition > 5000) {
                quranPlayer.seekTo(0)
                return
            }
        }
        val prev = if (_state.value.surahId <= 1) 114 else _state.value.surahId - 1
        playSurah(prev, autoplay = true)
    }

    fun seekTo(ms: Long) {
        runCatching {
            if (!this::quranPlayer.isInitialized) return
            val dur = try {
                quranPlayer.duration
            } catch (_: Exception) {
                C.TIME_UNSET
            }
            val safe = if (dur > 0) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L)
            quranPlayer.seekTo(safe)
        }
    }

    fun seekToAyah(index: Int) {
        val t = _state.value.timings ?: return
        // Defensive: stale activeAyah from a longer surah must never index a shorter list.
        if (index in t.indices) runCatching { seekTo(t[index]) }
    }

    fun selectReciter(reciterId: String, autoplayIfPlaying: Boolean = true) {
        val wasPlaying = runCatching {
            this::quranPlayer.isInitialized && quranPlayer.isPlaying
        }.getOrDefault(false)
        prepareQuran(_state.value.surahId, reciterId, autoplay = autoplayIfPlaying && wasPlaying)
    }

    fun setQuranVolume(v: Float) {
        runCatching {
            if (this::quranPlayer.isInitialized) quranPlayer.volume = v
        }
        _state.value = _state.value.copy(quranVolume = v)
        scope.launch { settings.setQuranVolume(v) }
    }

    /** Pause the in-app mix for a self-contained share-video preview. */
    fun beginSharePreview() {
        if (previewActive) return
        previewActive = true
        previewWasQuranPlaying = runCatching {
            this::quranPlayer.isInitialized && quranPlayer.isPlaying
        }.getOrDefault(false)
        previewWasAmbientPlaying = runCatching {
            this::ambientPlayer.isInitialized && ambientPlayer.isPlaying
        }.getOrDefault(false)
        runCatching { if (this::quranPlayer.isInitialized) quranPlayer.pause() }
        runCatching { if (this::ambientPlayer.isInitialized) ambientPlayer.pause() }
    }

    /** Restore the playback state captured by [beginSharePreview]. */
    fun endSharePreview() {
        if (!previewActive) return
        previewActive = false
        if (previewWasQuranPlaying) {
            runCatching {
                if (this::quranPlayer.isInitialized) {
                    ensureService()
                    quranPlayer.play()
                }
            }
        }
        if (previewWasAmbientPlaying) {
            runCatching {
                if (this::ambientPlayer.isInitialized) ambientPlayer.play()
            }
        }
        previewWasQuranPlaying = false
        previewWasAmbientPlaying = false
    }

    fun cycleRepeat(): RepeatMode {
        val next = _state.value.repeat.next()
        _state.value = _state.value.copy(repeat = next)
        scope.launch { settings.setRepeat(next) }
        applyRepeatMode()
        return next
    }

    fun setAutoNext(v: Boolean) {
        _state.value = _state.value.copy(autoNext = v)
        scope.launch { settings.setAutoNext(v) }
    }

    private fun applyRepeatMode() {
        runCatching {
            if (!this::quranPlayer.isInitialized) return
            quranPlayer.repeatMode = if (_state.value.repeat == RepeatMode.ONE) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ---------- ambient ----------

    private fun prepareAmbient(id: String, autoplay: Boolean) {
        runCatching { initialize() }
        if (!playersReady || !this::ambientPlayer.isInitialized) return
        val sound = repo.getAmbient(id) ?: return
        val local = File(context.filesDir, "${AudioStorage.AMBIENT_DIR}/$id.mp3")
        val uri = if (local.exists() && local.length() > 0) Uri.fromFile(local) else Uri.parse(sound.audioUrl)
        runCatching {
            ambientPlayer.setMediaItem(MediaItem.fromUri(uri))
            ambientPlayer.prepare()
            ambientPlayer.playWhenReady = autoplay
        }
        _state.value = _state.value.copy(ambientId = id, ambientLoading = true, error = null)
        scope.launch { settings.setAmbient(id) }
    }

    fun toggleAmbient(id: String) {
        if (!playersReady || !this::ambientPlayer.isInitialized) {
            runCatching { initialize() }
            if (!playersReady || !this::ambientPlayer.isInitialized) return
        }
        if (_state.value.ambientId == id) {
            runCatching {
                if (ambientPlayer.isPlaying) ambientPlayer.pause() else {
                    ensureService()
                    if (ambientPlayer.playbackState == Player.STATE_IDLE) prepareAmbient(id, autoplay = true)
                    else ambientPlayer.play()
                }
            }
        } else {
            ensureService()
            prepareAmbient(id, autoplay = true)
        }
    }

    fun stopAmbient() {
        runCatching {
            if (this::ambientPlayer.isInitialized) {
                ambientPlayer.pause()
                ambientPlayer.stop()
            }
        }
        _state.value = _state.value.copy(ambientId = null, ambientPlaying = false)
        scope.launch { settings.setAmbient(null) }
    }

    fun setAmbientVolume(v: Float) {
        runCatching {
            if (this::ambientPlayer.isInitialized) ambientPlayer.volume = v
        }
        _state.value = _state.value.copy(ambientVolume = v)
        scope.launch { settings.setAmbientVolume(v) }
    }

    fun setLoopAmbient(v: Boolean) {
        _state.value = _state.value.copy(loopAmbient = v)
        runCatching {
            if (this::ambientPlayer.isInitialized) {
                ambientPlayer.repeatMode = if (v) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        }
        scope.launch { settings.setLoopAmbient(v) }
    }

    // ---------- sleep timer ----------

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _state.value = _state.value.copy(sleepMinutesLeft = minutes)
        sleepJob = scope.launch {
            var left = minutes
            while (left > 0) {
                delay(60_000)
                left--
                _state.value = _state.value.copy(sleepMinutesLeft = if (left > 0) left else null)
            }
            runCatching { if (this@AudioEngine::quranPlayer.isInitialized) quranPlayer.pause() }
            runCatching { if (this@AudioEngine::ambientPlayer.isInitialized) ambientPlayer.pause() }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _state.value = _state.value.copy(sleepMinutesLeft = null)
    }

    // ---------- listeners ----------

    private val quranListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(quranPlaying = isPlaying)
            if (isPlaying) {
                startProgress()
                recordHistory()
            } else {
                persistProgress()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    val dur = try {
                        val d = quranPlayer.duration
                        if (d == C.TIME_UNSET || d < 0) 0L else d
                    } catch (_: Exception) {
                        0L
                    }
                    _state.value = _state.value.copy(
                        quranLoading = false,
                        durationMs = dur
                    )
                }
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(quranLoading = true)
                }
                Player.STATE_ENDED -> {
                    persistProgress(clear = true)
                    when (_state.value.repeat) {
                        RepeatMode.ONE -> { /* ExoPlayer repeats automatically */ }
                        RepeatMode.ALL -> nextSurah()
                        RepeatMode.OFF -> if (_state.value.autoNext) nextSurah()
                    }
                }
                Player.STATE_IDLE -> {
                    _state.value = _state.value.copy(quranLoading = false)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val mirror = currentFallbackUri
            if (!fallbackTried && mirror != null) {
                fallbackTried = true
                // Drop the pending primary timings: the mirror is a different edit.
                quranLoadToken++
                val resume = quranPlayer.playWhenReady && _state.value.quranEnabled
                val resumeAt = runCatching {
                    maxOf(quranPlayer.currentPosition, _state.value.positionMs)
                }.getOrDefault(_state.value.positionMs).coerceAtLeast(0L)
                _state.value = _state.value.copy(
                    quranLoading = true,
                    error = null,
                    timings = null,
                    hasSync = false,
                    activeAyah = 0
                )
                runCatching {
                    val metadata = quranPlayer.mediaMetadata
                    quranPlayer.setMediaItem(
                        MediaItem.Builder().setUri(mirror).setMediaMetadata(metadata).build()
                    )
                    quranPlayer.prepare()
                    if (resumeAt > 0L) quranPlayer.seekTo(resumeAt)
                    quranPlayer.playWhenReady = resume
                    if (resume) quranPlayer.play()
                }.onFailure {
                    _state.value = _state.value.copy(quranLoading = false, error = "audio")
                }
                return
            }
            _state.value = _state.value.copy(quranLoading = false, error = "audio")
        }
    }

    private val ambientListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(ambientPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                _state.value = _state.value.copy(ambientLoading = false)
            } else if (state == Player.STATE_BUFFERING) {
                _state.value = _state.value.copy(ambientLoading = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(ambientLoading = false, error = "ambient")
        }
    }

    private fun startProgress() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                try {
                    val pos = try {
                        quranPlayer.currentPosition.coerceAtLeast(0)
                    } catch (_: Exception) {
                        _state.value.positionMs
                    }
                    val dur = try {
                        val d = quranPlayer.duration
                        if (d == C.TIME_UNSET || d < 0) 0L else d
                    } catch (_: Exception) {
                        _state.value.durationMs
                    }
                    val timings = _state.value.timings
                    // Clamp stale ayah indices from a previous (longer) surah.
                    val ayah = try {
                        activeVerseIndex(timings, pos).coerceAtLeast(0).let {
                            if (timings != null && timings.isNotEmpty()) it.coerceIn(0, timings.size - 1)
                            else 0
                        }
                    } catch (_: Exception) {
                        _state.value.activeAyah.coerceAtLeast(0)
                    }
                    _state.value = _state.value.copy(positionMs = pos, durationMs = dur, activeAyah = ayah)
                    if (pos - lastPersistedMs > 10_000) {
                        lastPersistedMs = pos
                        persistProgress()
                    }
                } catch (_: Exception) {
                    // Progress loop must never crash playback (this runs every 400ms).
                }
                delay(400)
            }
        }
    }

    private fun persistProgress(clear: Boolean = false) {
        val s = _state.value
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            if (clear) {
                settings.saveProgress(s.surahId, 0L, 0)
            } else if (s.positionMs > 3000) {
                settings.saveProgress(s.surahId, s.positionMs, s.activeAyah)
            }
        }
    }

    private fun recordHistory() {
        val s = _state.value
        scope.launch(Dispatchers.IO) {
            try {
                db.history().upsert(
                    com.quransanctuary.app.data.local.HistoryEntity(
                        surah = s.surahId,
                        ayah = s.activeAyah,
                        positionMs = s.positionMs,
                        reciterId = s.reciterId
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    fun resumeState(): kotlinx.coroutines.flow.Flow<PlayerUiState> = combine(
        settings.reciterId, settings.surahId, settings.ambientId
    ) { _, _, _ -> _state.value }

    fun release() {
        progressJob?.cancel()
        sleepJob?.cancel()
        persistProgress()
        runCatching {
            if (this::quranPlayer.isInitialized) quranPlayer.removeListener(quranListener)
        }
        runCatching {
            if (this::ambientPlayer.isInitialized) ambientPlayer.removeListener(ambientListener)
        }
        runCatching { if (this::quranPlayer.isInitialized) quranPlayer.release() }
        runCatching { if (this::ambientPlayer.isInitialized) ambientPlayer.release() }
        runCatching { cache?.release() }
        cache = null
        // Allow a later initialize() to build fresh players instead of
        // reusing the released handles above.
        playersReady = false
    }
}
