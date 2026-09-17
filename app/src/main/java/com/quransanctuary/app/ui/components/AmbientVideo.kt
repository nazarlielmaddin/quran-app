package com.quransanctuary.app.ui.components

import android.view.SurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.quransanctuary.app.QuranApp
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Looping ambient background video — the website's atmosphere-video logic,
 * ported 1:1: only the SELECTED atmosphere's clip is ever mounted, muted,
 * cropped to fill, repeated forever, faded in once ready.
 *
 * Website parity notes (nazarlielmaddin/quran AmbientBackground):
 * - one video at a time (this composable mounts only for ambient.videoUrl),
 * - dark overlay + fallback stay in AmbientBackground underneath/above,
 * - clips are short Pixabay CDN loops (_small/_medium/_large ≈ phone sizes),
 *   streamed once then served from the 150MB disk cache.
 *
 * Navigation survival: every screen mounts its own [AmbientVideo], but they
 * all share ONE player from [AmbientVideoHolder] (app scope). Switching
 * screens (Player → Reader, …) only re-attaches the surface — the clip keeps
 * playing, never rebuffers, never flashes narrow. Without this, each
 * navigation killed the player and restarted the clip from zero.
 */
class AmbientVideoHolder(private val appContext: android.content.Context) {

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var cache: SimpleCache? = null
    private var player: ExoPlayer? = null
    private var url: String? = null
    private var boundView: SurfaceView? = null
    private var refs = 0
    private var releaseJob: Job? = null

    private fun videoCache(): SimpleCache {
        var c = cache
        if (c == null) {
            c = SimpleCache(
                File(appContext.cacheDir, "ambient-video"),
                LeastRecentlyUsedCacheEvictor(150L * 1024 * 1024),
                androidx.media3.database.StandaloneDatabaseProvider(appContext)
            )
            cache = c
        }
        return c
    }

    /** True when this exact clip is already playing and settled. */
    fun isSettled(url: String): Boolean = synchronized(lock) {
        val p = player
        p != null && this.url == url && p.playerError == null &&
            (p.isPlaying || p.playbackState == Player.STATE_READY)
    }

    fun acquire(url: String): ExoPlayer = synchronized(lock) {
        releaseJob?.cancel()
        releaseJob = null
        val existing = player
        // Dead (errored) players are never reused.
        if (existing != null && this.url == url && existing.playerError == null) {
            refs++
            return existing
        }
        releaseLocked()
        val cachedSource = CacheDataSource.Factory()
            .setCache(videoCache())
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory().setUserAgent("QuranSanctuary/1.0")
            )
        val fresh = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cachedSource))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        player = fresh
        this.url = url
        refs++
        fresh
    }

    fun attach(p: ExoPlayer, view: SurfaceView) {
        synchronized(lock) {
            if (player !== p) return
            runCatching {
                val dm = view.resources.displayMetrics
                view.holder.setFixedSize(dm.widthPixels, dm.heightPixels)
                p.setVideoSurfaceView(view)
                boundView = view
            }
        }
    }

    fun detach(p: ExoPlayer, view: SurfaceView) {
        synchronized(lock) {
            // Clear only our own surface: during screen transitions the new
            // screen attaches first — the old screen must not blank it.
            if (player === p && boundView === view) {
                runCatching { p.clearVideoSurfaceView(view) }
                boundView = null
            }
        }
    }

    fun releaseRef() {
        synchronized(lock) {
            refs = (refs - 1).coerceAtLeast(0)
            if (refs != 0) return
            releaseJob?.cancel()
            // Grace period: a navigating screen re-acquires within a frame or
            // two — releasing instantly would restart the clip on every switch.
            releaseJob = scope.launch {
                delay(1500)
                synchronized(lock) {
                    if (refs == 0) releaseLocked()
                    releaseJob = null
                }
            }
        }
    }

    fun setPaused(paused: Boolean) {
        val p = synchronized(lock) { player } ?: return
        runCatching { p.playWhenReady = !paused }
    }

    private fun releaseLocked() {
        releaseJob?.cancel()
        releaseJob = null
        val p = player
        player = null
        url = null
        boundView = null
        if (p != null) {
            scope.launch(Dispatchers.Main.immediate) {
                runCatching { p.release() }
            }
        }
    }
}

@Composable
fun AmbientVideo(
    url: String,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as QuranApp
    val holder = app.container.ambientVideo
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val player = remember(url) { holder.acquire(url) }
    // Attach-once: re-setting the same surface re-primes the decoder output
    // and micro-freezes the clip — attach only when the player or the view
    // actually changed hands.
    var boundTo by remember(url) { mutableStateOf<Pair<ExoPlayer, SurfaceView>?>(null) }
    fun attachIfNeeded(view: SurfaceView) {
        val current = boundTo
        if (current?.first === player && current.second === view) return
        holder.attach(player, view)
        boundTo = player to view
    }
    var ready by remember(url) { mutableStateOf(holder.isSettled(url)) }
    // Settle detection: every video-size event bumps sizeVersion, which
    // restarts the settle timer below. The view appears only after the size
    // has been stable for 700ms AND a real frame was drawn — no squeezed or
    // narrow transient can ever flash, on any clip.
    var sizeVersion by remember(url) { mutableIntStateOf(0) }
    var framed by remember(url) { mutableStateOf(false) }
    // If the clip fails, unmount the surface entirely so the gradient
    // fallback stays visible (a dead SurfaceView would paint black over it).
    var failed by remember(url) { mutableStateOf(false) }

    DisposableEffect(player, url) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) sizeVersion++
            }

            override fun onRenderedFirstFrame() {
                framed = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { runCatching { player.removeListener(listener) } }
    }

    // Show only after size settled (700ms without changes) + first frame.
    // Restarts automatically on every size event; the 4.5s fallback guarantees
    // no permanent black if events stall on an exotic device.
    // Already-settled (shared, still playing) players show instantly.
    LaunchedEffect(url, sizeVersion, framed) {
        if (!ready && sizeVersion > 0 && framed) {
            kotlinx.coroutines.delay(700)
            ready = true
        }
    }
    LaunchedEffect(url) {
        kotlinx.coroutines.delay(4500)
        if (!failed) ready = true
    }

    DisposableEffect(lifecycle, player, url) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.setPaused(false)
                Lifecycle.Event.ON_PAUSE -> holder.setPaused(true)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            runCatching { lifecycle.removeObserver(observer) }
            holder.releaseRef()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(1200),
        label = "ambientVideoFade"
    )
    if (!failed) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { view ->
                    attachIfNeeded(view)
                }
            },
            update = { view ->
                attachIfNeeded(view)
            },
            onRelease = { view ->
                holder.detach(player, view)
                if (boundTo?.second === view) boundTo = null
            },
            modifier = modifier
                .fillMaxSize()
                .alpha(alpha)
        )
    }
}
