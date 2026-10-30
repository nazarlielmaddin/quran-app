package com.quransanctuary.app.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextDirectionHeuristics
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.quransanctuary.app.R
import com.quransanctuary.app.data.local.QuranRepository
import com.quransanctuary.app.data.model.AmbientSound
import com.quransanctuary.app.data.model.Reciter
import com.quransanctuary.app.data.model.Surah
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.Locale
import java.util.Random
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * Turns one ayah into a shareable, cinematic 9:16 MP4.
 *
 * The video is intentionally generated on-device: the ayah recitation is clipped
 * from the reciter's surah audio at the exact QUL timestamp, the selected
 * atmosphere is looped underneath, and a premium gold-on-glass frame is
 * composited over it.
 */
@OptIn(UnstableApi::class)
class ShareVideoGenerator(
    private val context: Context,
    private val repo: QuranRepository
) {
    companion object {
        private const val TAG = "ShareVideo"
        const val WIDTH = 1080
        const val HEIGHT = 1920
        private const val FRAME_RATE = 30
        private const val AUDIO_SAMPLE_RATE = 44_100
        private const val AYAH_GAIN = 0.94f
        private const val AMBIENT_GAIN = 0.18f
        private const val MIN_CLIP_MS = 900L
        /**
         * Bump when the rendered frame design changes so old cached MP4s are
         * ignored. v4 also guarantees a true 1080x1920 file without rotation
         * metadata; v3 files must never be reused.
         */
        private const val OVERLAY_VERSION = 5
    }

    data class Request(
        val surah: Surah,
        val reciter: Reciter,
        val ambient: AmbientSound?,
        /** Zero-based ayah indices included in this video (sorted, unique). */
        val ayahIndices: List<Int>,
        val arabic: String,
        val transliteration: String,
        val translation: String,
        /** User's in-app ambient volume (0..1), mirrored into the exported mix. */
        val ambientVolume: Float = 0.6f
    ) {
        /** First ayah, kept for cache/compatibility helpers. */
        val ayahIndex: Int get() = ayahIndices.firstOrNull() ?: 0

        /** Human-readable ayah range, e.g. "1", "2-5", "2, 4, 5". */
        val ayahRangeLabel: String
            get() {
                val sorted = ayahIndices.distinct().sorted()
                if (sorted.isEmpty()) return "1"
                if (sorted.size == 1) return "${sorted.first() + 1}"
                val contiguous = sorted.last() - sorted.first() + 1 == sorted.size
                return if (contiguous) {
                    "${sorted.first() + 1}-${sorted.last() + 1}"
                } else {
                    sorted.joinToString(", ") { "${it + 1}" }
                }
            }
    }

    data class Result(
        val file: File,
        val durationMs: Long,
        /** Non-null when the primary audio failed and a mirror was used. */
        val warning: String? = null
    )

    enum class Stage { PREPARING, DOWNLOADING, ENCODING, DONE }

    data class Progress(
        val stage: Stage,
        val fraction: Float? = null,
        val message: String = ""
    )

    private val cacheRoot = File(context.cacheDir, "share-video").apply { mkdirs() }

    private fun outputFile(request: Request): File {
        val ambient = request.ambient?.id ?: "none"
        return File(
            cacheRoot,
            "out/${request.reciter.id}-${request.surah.number}-${request.ayahRangeLabel}-$ambient-v$OVERLAY_VERSION-${WIDTH}x${HEIGHT}.mp4"
        )
    }

    fun cachedOutput(request: Request): File? {
        val out = outputFile(request)
        return out.takeIf { it.exists() && it.length() > 20_000L }
    }

    suspend fun generate(
        request: Request,
        onProgress: (Progress) -> Unit = {}
    ): Result {
        onProgress(Progress(Stage.PREPARING, 0f, "Ayə vaxtları yoxlanılır…"))
        val recitation = repo.getRecitation(request.reciter.id, request.surah.number)
            ?: throw IllegalStateException("Bu surə üçün audio mənbəyi tapılmadı.")
        val timings = repo.getTimings(request.reciter.id, request.surah.number)
            ?: throw IllegalStateException("Bu hafiz üçün ayə vaxtları yoxdur.")
        val selectedIndicesForValidation = request.ayahIndices.distinct().sorted()
        if (selectedIndicesForValidation.isEmpty() || selectedIndicesForValidation.any { it !in timings.indices }) {
            throw IllegalStateException("Seçilmiş ayələr üçün vaxt məlumatı tapılmadı.")
        }

        val cached = cachedOutput(request)
        if (cached != null) {
            val duration = probeDurationMs(cached) ?: 0L
            onProgress(Progress(Stage.DONE, 1f, "Video hazırdır."))
            return Result(cached, duration)
        }

        val totalMs = (recitation.durationSeconds?.toLong()?.times(1000L))
            ?.takeIf { it > 0L }
            ?: probeRemoteOrLocalDurationMs(request.reciter.id, request.surah.number)
            ?: throw IllegalStateException("Audio müddəti müəyyən edilə bilmədi.")

        val selectedIndices = request.ayahIndices.distinct().sorted()
        if (selectedIndices.isEmpty() || selectedIndices.any { it !in timings.indices }) {
            throw IllegalStateException("Seçilmiş ayələr üçün vaxt məlumatı tapılmadı.")
        }

        // Build one clipped audio range per selected ayah so non-contiguous
        // selections do not drag unselected verses into the soundtrack.
        val firstIndex = selectedIndices.first()
        val lastIndex = selectedIndices.last()
        val startMs = timings[firstIndex].coerceAtLeast(0L)
        val isLastAyah = lastIndex == timings.lastIndex
        val rawEnd = timings.getOrNull(lastIndex + 1)
            ?: (totalMs - if (isLastAyah) 400L else 0L).coerceAtLeast(startMs + MIN_CLIP_MS)
        val endMs = rawEnd.coerceAtMost(totalMs).coerceAtLeast(startMs + MIN_CLIP_MS)
            .coerceAtMost(totalMs)
        if (endMs - startMs < MIN_CLIP_MS) {
            throw IllegalStateException("Seçilmiş ayələrin audio aralığı çox qısadır.")
        }
        val totalDurationMs = endMs - startMs

        // The ayah audio can be streamed from the source with clipping. If a
        // downloaded copy already exists we use it so the export is offline and
        // faster. Mirrors are only tried when the primary source fails.
        val localAudio = localQuranFile(request.reciter.id, request.surah.number)
        val primaryAudio: Uri = if (localAudio.exists() && localAudio.length() > 0L) {
            Uri.fromFile(localAudio)
        } else {
            Uri.parse(recitation.audioUrl)
        }
        val fallbackAudio = recitation.fallbackAudioUrl
            ?.takeIf { it.isNotBlank() && it != recitation.audioUrl }
            ?.let(Uri::parse)

        onProgress(Progress(Stage.DOWNLOADING, 0.02f, "Atmosfer hazırlanır…"))
        val background = prepareBackground(request.ambient, onProgress)

        onProgress(Progress(Stage.DOWNLOADING, 0.15f, "Atmosfer səsi hazırlanır…"))
        val ambientAudio = prepareAmbientAudio(request.ambient, onProgress)

        val overlay = renderOverlay(request)

        val output = outputFile(request)
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        onProgress(Progress(Stage.ENCODING, 0.22f, "Video yığılır…"))
        var warning: String? = null

        suspend fun exportWithAmbient(audioUri: Uri) {
            try {
                runExport(
                    request = request,
                    audioUri = audioUri,
                    audioDurationMs = totalMs,
                    startMs = startMs,
                    endMs = endMs,
                    totalDurationMs = totalDurationMs,
                    background = background,
                    overlay = overlay,
                    ambientUri = ambientAudio,
                    output = output,
                    onProgress = onProgress
                )
            } catch (exportError: Throwable) {
                if (exportError is CancellationException) throw exportError
                if (ambientAudio == null) throw exportError
                // Some devices fail to decode a particular ambient asset; retry
                // without it instead of failing the whole video.
                warning = listOfNotNull(
                    warning,
                    "Atmosfer səsi əlavə edilə bilmədi; video atmosfersiz yaradıldı."
                ).joinToString(" ")
                Log.w(TAG, "Export with ambient failed, retrying without ambient", exportError)
                if (output.exists()) output.delete()
                runExport(
                    request = request,
                    audioUri = audioUri,
                    audioDurationMs = totalMs,
                    startMs = startMs,
                    endMs = endMs,
                    totalDurationMs = totalDurationMs,
                    background = background,
                    overlay = overlay,
                    ambientUri = null,
                    output = output,
                    onProgress = onProgress
                )
            }
        }

        try {
            try {
                exportWithAmbient(primaryAudio)
            } catch (primaryError: Throwable) {
                if (primaryError is CancellationException) throw primaryError
                if (fallbackAudio == null) throw primaryError
                warning = "Əsas audio mənbəyi əlçatmaz oldu; ehtiyat mənbə istifadə edildi. Vaxtlar təxmini ola bilər."
                Log.w(TAG, "Primary audio failed, trying fallback", primaryError)
                if (output.exists()) output.delete()
                onProgress(Progress(Stage.ENCODING, 0.22f, "Ehtiyat audio ilə yenidən yığılır…"))
                exportWithAmbient(fallbackAudio)
            }

            // Media3 exports portrait compositions as landscape H.264 with a
            // 90° rotation flag. Some galleries ignore that flag and show the
            // clip sideways, so bake in a real 1080x1920 portrait stream.
            onProgress(Progress(Stage.ENCODING, 0.985f, "Portret formatına çevrilir…"))
            val truePortrait = PortraitVideoRotator.rotate(output, output, WIDTH, HEIGHT)
            if (!truePortrait) {
                warning = listOfNotNull(
                    warning,
                    "Video bəzi cihazlarda 90° fırlana bilər."
                ).joinToString(" ")
            }
        } finally {
            overlay.recycle()
        }

        val duration = probeDurationMs(output) ?: totalDurationMs
        Log.i(TAG, "Generated ${output.absolutePath} (${output.length()} bytes, ${duration}ms)")
        onProgress(Progress(Stage.DONE, 1f, "Video hazırdır."))
        return Result(output, duration, warning)
    }

    // ---------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------

    private suspend fun runExport(
        request: Request,
        audioUri: Uri,
        audioDurationMs: Long,
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long,
        background: Background,
        overlay: Bitmap,
        ambientUri: File?,
        output: File,
        onProgress: (Progress) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        val videoEffects = buildList<Effect> {
            add(
                Presentation.createForWidthAndHeight(
                    WIDTH, HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            )
            add(OverlayEffect(ImmutableList.of(BitmapOverlay.createStaticBitmapOverlay(overlay))))
        }

        val videoItemBuilder = EditedMediaItem.Builder(
            if (background.isImage) {
                MediaItem.Builder()
                    .setUri(background.uri)
                    .setImageDurationMs(totalDurationMs.coerceAtLeast(1L))
                    .build()
            } else {
                MediaItem.fromUri(background.uri)
            }
        )
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), videoEffects))
        if (background.isImage) {
            videoItemBuilder
                .setDurationUs(totalDurationMs * 1000L)
                .setFrameRate(FRAME_RATE)
        }
        val videoSequence = EditedMediaItemSequence(listOf(videoItemBuilder.build()), true)

        val ayahItem = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(audioUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
        )
            .setRemoveVideo(true)
            .setDurationUs(audioDurationMs * 1000L)
            .setEffects(Effects(normalizedAudioProcessors(AYAH_GAIN), emptyList()))
            .build()
        val ayahSequence = EditedMediaItemSequence(listOf(ayahItem), false)

        val ambientGain = request.ambientVolume.coerceIn(0f, 1f)
        val sequences = mutableListOf(videoSequence, ayahSequence)
        if (ambientUri != null) {
            val ambientItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(ambientUri)))
                .setRemoveVideo(true)
                .setEffects(Effects(normalizedAudioProcessors(ambientGain), emptyList()))
                .build()
            sequences.add(EditedMediaItemSequence(listOf(ambientItem), true))
        }

        val composition = Composition.Builder(sequences).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        val progressJob: Job = launch {
            val holder = ProgressHolder()
            while (isActive) {
                val state = runCatching { transformer.getProgress(holder) }.getOrDefault(-1)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val encodeFraction = (holder.progress / 100f).coerceIn(0f, 1f)
                    onProgress(
                        Progress(
                            Stage.ENCODING,
                            0.22f + encodeFraction * 0.76f,
                            "Video yığılır… ${(encodeFraction * 100).roundToInt()}%"
                        )
                    )
                }
                delay(250)
            }
        }

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                }
                transformer.addListener(listener)
                continuation.invokeOnCancellation {
                    runCatching { transformer.removeListener(listener) }
                    runCatching { transformer.cancel() }
                }
                try {
                    transformer.start(composition, output.absolutePath)
                } catch (t: Throwable) {
                    transformer.removeListener(listener)
                    if (continuation.isActive) continuation.resumeWithException(t)
                }
            }
        } finally {
            progressJob.cancel()
        }
    }

    /**
     * All audio sequences in a Composition must expose the same AudioFormat.
     * Tarteel and Pixabay files differ in sample rate/channel count, so both
     * are normalized to 44.1 kHz stereo PCM before they are mixed.
     */
    private fun normalizedAudioProcessors(gain: Float): List<AudioProcessor> = listOf(
        SonicAudioProcessor().apply { setOutputSampleRateHz(AUDIO_SAMPLE_RATE) },
        ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2))
            putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2))
        },
        GainAudioProcessor(gain)
    )

    // ---------------------------------------------------------------------
    // Background / audio assets
    // ---------------------------------------------------------------------

    private data class Background(val uri: Uri, val isImage: Boolean)

    private suspend fun prepareBackground(
        ambient: AmbientSound?,
        onProgress: (Progress) -> Unit
    ): Background = withContext(Dispatchers.IO) {
        if (ambient == null) {
            return@withContext Background(Uri.fromFile(makeGradientBackground(null)), true)
        }
        val videoUrl = ambient.videoUrl
        if (videoUrl != null) {
            try {
                val video = downloadCached(
                    url = videoUrl,
                    dir = File(cacheRoot, "ambient"),
                    extension = "mp4"
                ) { read, total ->
                    onProgress(
                        Progress(
                            Stage.DOWNLOADING,
                            0.02f + 0.10f * fraction(read, total),
                            "Atmosfer videosu yüklənir… ${(fraction(read, total) * 100).roundToInt()}%"
                        )
                    )
                }
                return@withContext Background(Uri.fromFile(video), false)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(TAG, "Ambient video unavailable, trying image", t)
            }
        }
        val imageUrl = ambient.imageUrl
        if (imageUrl != null) {
            try {
                val image = downloadCached(
                    url = imageUrl,
                    dir = File(cacheRoot, "ambient"),
                    extension = "jpg"
                )
                return@withContext Background(Uri.fromFile(image), true)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(TAG, "Ambient image unavailable, using gradient", t)
            }
        }
        Background(Uri.fromFile(makeGradientBackground(ambient)), true)
    }

    private suspend fun prepareAmbientAudio(
        ambient: AmbientSound?,
        onProgress: (Progress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        if (ambient == null || ambient.audioUrl.isBlank()) return@withContext null
        try {
            downloadCached(
                url = ambient.audioUrl,
                dir = File(cacheRoot, "ambient"),
                extension = "mp3"
            ) { read, total ->
                onProgress(
                    Progress(
                        Stage.DOWNLOADING,
                        0.15f + 0.06f * fraction(read, total),
                        "Atmosfer səsi yüklənir… ${(fraction(read, total) * 100).roundToInt()}%"
                    )
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w(TAG, "Ambient audio unavailable; exporting ayah only", t)
            null
        }
    }

    private suspend fun downloadCached(
        url: String,
        dir: File,
        extension: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val fileName = "a-${abs(url.hashCode()).toString(16)}.$extension"
        val target = File(dir, fileName)
        if (target.exists() && target.length() > 1024L) return@withContext target
        val temp = File(dir, "$fileName.part")
        if (temp.exists()) temp.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "QuranSanctuary/1.18")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            var readTotal = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        readTotal += n
                        onProgress(readTotal, total)
                    }
                }
            }
            if (temp.length() < 1024L) error("Downloaded file is too small")
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    private fun fraction(read: Long, total: Long): Float =
        if (total > 0L) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    private fun makeGradientBackground(ambient: AmbientSound?): File {
        val out = File(cacheRoot, "ambient/gradient-${ambient?.id ?: "default"}.png")
        if (out.exists() && out.length() > 1024L) return out
        out.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c1 = ambient?.gradient?.getOrNull(0)?.let(::parseColor) ?: Color.rgb(20, 28, 44)
        val c2 = ambient?.gradient?.getOrNull(1)?.let(::parseColor) ?: Color.rgb(4, 6, 12)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            intArrayOf(c1, c2, Color.BLACK),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = RadialGradient(
            WIDTH * 0.5f, HEIGHT * 0.42f, HEIGHT * 0.55f,
            intArrayOf(Color.argb(130, 200, 169, 124), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 96, it) }
        bitmap.recycle()
        return out
    }

    private fun parseColor(value: String): Int = runCatching {
        Color.parseColor(if (value.startsWith("#")) value else "#$value")
    }.getOrDefault(Color.BLACK)

    // ---------------------------------------------------------------------
    // Timing helpers
    // ---------------------------------------------------------------------

    private fun localQuranFile(reciterId: String, surahId: Int): File =
        File(context.filesDir, "audio-qul-v1/$reciterId/$surahId.mp3")

    private fun probeDurationMs(file: File): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private suspend fun probeRemoteOrLocalDurationMs(reciterId: String, surahId: Int): Long? =
        withContext(Dispatchers.IO) {
            val local = localQuranFile(reciterId, surahId)
            if (local.exists() && local.length() > 0L) return@withContext probeDurationMs(local)
            val rec = repo.getRecitation(reciterId, surahId) ?: return@withContext null
            rec.durationSeconds?.toLong()?.times(1000L)?.takeIf { it > 0L }
        }

    // ---------------------------------------------------------------------
    // Frame design
    // ---------------------------------------------------------------------

    private fun renderOverlay(request: Request): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = WIDTH.toFloat()
        val h = HEIGHT.toFloat()
        val gold = Color.rgb(226, 205, 166)
        val paper = Color.rgb(255, 248, 235)

        // Cinematic scrim: keeps the atmosphere visible while the translation stays legible.
        val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
        scrim.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.argb(226, 3, 4, 8),
                Color.argb(100, 3, 4, 8),
                Color.argb(55, 3, 4, 8),
                Color.argb(135, 3, 4, 8),
                Color.argb(242, 3, 4, 8)
            ),
            floatArrayOf(0f, 0.18f, 0.46f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, scrim)

        val vignette = Paint(Paint.ANTI_ALIAS_FLAG)
        vignette.shader = RadialGradient(
            w / 2f, h * 0.44f, h * 0.68f,
            intArrayOf(Color.TRANSPARENT, Color.argb(158, 0, 0, 0)),
            floatArrayOf(0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, vignette)

        // Subtle gold dust.
        val random = Random(7_317L)
        val dust = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(46) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val r = 0.8f + random.nextFloat() * 2.2f
            dust.color = Color.argb(18 + random.nextInt(48), 226, 205, 166)
            canvas.drawCircle(x, y, r, dust)
        }

        // Thin frame.
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            color = Color.argb(90, 226, 205, 166)
        }
        canvas.drawRoundRect(RectF(24f, 24f, w - 24f, h - 24f), 34f, 34f, frame)

        // Logo + wordmark.
        val logo = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_logo)
        }.getOrNull()
        if (logo != null) {
            val size = 92f
            val left = w / 2f - size / 2f
            val top = 76f
            val dst = RectF(left, top, left + size, top + size)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(150, 226, 205, 166)
            }
            canvas.drawCircle(w / 2f, top + size / 2f, size / 2f + 10f, ring)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 248 })
            logo.recycle()
        }
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 26f
            color = gold
            letterSpacing = 0.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("AZANIFY", w / 2f, 222f, brand)
        val brandLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 226, 205, 166)
            strokeWidth = 1.2f
        }
        canvas.drawLine(w / 2f - 108f, 244f, w / 2f + 108f, 244f, brandLine)
        canvas.drawCircle(w / 2f, 244f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })

        // Warm glow behind the glass panel.
        val panelGlow = Paint(Paint.ANTI_ALIAS_FLAG)
        panelGlow.shader = RadialGradient(
            w / 2f, 910f, 560f,
            intArrayOf(Color.argb(24, 226, 205, 166), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w / 2f, 910f, 560f, panelGlow)

        // Glass translation panel.
        val panel = RectF(74f, 326f, w - 74f, 1582f)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(78, 5, 6, 12)
            setShadowLayer(40f, 0f, 20f, Color.argb(172, 0, 0, 0))
        }
        canvas.drawRoundRect(panel, 46f, 46f, panelPaint)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG)
        glass.shader = LinearGradient(
            panel.left, panel.top, panel.left, panel.bottom,
            intArrayOf(
                Color.argb(36, 255, 255, 255),
                Color.argb(8, 255, 255, 255),
                Color.argb(22, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(panel, 46f, 46f, glass)
        val panelBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(100, 184, 151, 104)
        }
        canvas.drawRoundRect(panel, 46f, 46f, panelBorder)
        drawCornerAccents(canvas, panel, Color.argb(145, 226, 205, 166))

        // Small section label and ornament.
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 21f
            color = gold
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
            textLocale = Locale("az")
        }
        canvas.drawText("AYƏ TƏRCÜMƏSİ", w / 2f, 388f, labelPaint)
        drawOrnament(canvas, 418f, Color.argb(150, 226, 205, 166), 148f)

        // Azerbaijani translation — the only verse text in the video.
        val textWidth = 820
        val areaTop = 458f
        val areaBottom = 1326f
        val areaHeight = (areaBottom - areaTop).toInt()
        val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            textSize = 60f
            color = paper
            // StaticLayout already centers each line; CENTER here would shift
            // every line left by half its width and push long lines off-screen.
            textAlign = Paint.Align.LEFT
            textLocale = Locale("az")
            setShadowLayer(10f, 0f, 4f, Color.argb(200, 0, 0, 0))
        }
        val translation = request.translation.trim().ifBlank { "Tərcümə mövcud deyil." }
        val translationLayout = fitTranslationLayout(
            translation,
            translationPaint,
            textWidth,
            areaHeight
        )
        val translationTop = areaTop +
            ((areaHeight - translationLayout.height) / 2f).coerceAtLeast(0f)
        canvas.save()
        canvas.translate((w - textWidth) / 2f, translationTop)
        translationLayout.draw(canvas)
        canvas.restore()

        // Gold ornament above the reference.
        drawOrnament(canvas, 1364f, Color.argb(150, 226, 205, 166), 132f)

        // Surah / ayah / reciter.
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 29f
            color = Color.argb(232, 255, 248, 235)
            textAlign = Paint.Align.CENTER
            textLocale = Locale("az")
        }
        val ayahLabel = request.ayahIndices.distinct().sorted().let { idx ->
            if (idx.size <= 1) {
                ordinalAz((idx.firstOrNull() ?: 0) + 1)
            } else {
                val first = idx.first() + 1
                val last = idx.last() + 1
                if (last - first + 1 == idx.size) {
                    "$first-${ordinalAz(last)}"
                } else {
                    idx.joinToString(", ") { ordinalAz(it + 1) }
                }
            }
        }
        val surahLine = "${request.surah.azerbaijaniName} surəsi  ·  $ayahLabel ayə"
        canvas.drawText(surahLine, w / 2f, 1418f, metaPaint)
        val reciterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 24f
            color = gold
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
            textLocale = Locale("az")
        }
        canvas.drawText("Oxuyur: ${request.reciter.localizedName("az")}", w / 2f, 1464f, reciterPaint)

        val mottoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 18f
            color = Color.argb(135, 226, 205, 166)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
            textLocale = Locale("az")
        }
        canvas.drawText("HƏR AYƏ BİR HƏYAT", w / 2f, 1542f, mottoPaint)

        // Footer.
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 20f
            color = Color.argb(150, 255, 248, 235)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        canvas.drawText("QURANSANCTUARY.APP", w / 2f, 1818f, footer)
        val footerLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(75, 226, 205, 166)
            strokeWidth = 1f
        }
        canvas.drawLine(w / 2f - 150f, 1840f, w / 2f + 150f, 1840f, footerLine)

        return bmp
    }

    /** Renders the final overlay once so the share sheet can show an exact live preview. */
    fun previewOverlay(request: Request): Bitmap = renderOverlay(request)

    private fun drawOrnament(canvas: Canvas, y: Float, color: Int, halfWidth: Float) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 1.4f
        }
        canvas.drawLine(WIDTH / 2f - halfWidth, y, WIDTH / 2f - 22f, y, line)
        canvas.drawLine(WIDTH / 2f + 22f, y, WIDTH / 2f + halfWidth, y, line)
        val diamond = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.save()
        canvas.rotate(45f, WIDTH / 2f, y)
        canvas.drawRect(WIDTH / 2f - 5f, y - 5f, WIDTH / 2f + 5f, y + 5f, diamond)
        canvas.restore()
    }

    private fun drawCornerAccents(canvas: Canvas, rect: RectF, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val inset = 24f
        val length = 48f
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.left + inset + length, rect.top + inset, paint)
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.left + inset, rect.top + inset + length, paint)
        canvas.drawLine(rect.right - inset, rect.top + inset, rect.right - inset - length, rect.top + inset, paint)
        canvas.drawLine(rect.right - inset, rect.top + inset, rect.right - inset, rect.top + inset + length, paint)
        canvas.drawLine(rect.left + inset, rect.bottom - inset, rect.left + inset + length, rect.bottom - inset, paint)
        canvas.drawLine(rect.left + inset, rect.bottom - inset, rect.left + inset, rect.bottom - inset - length, paint)
        canvas.drawLine(rect.right - inset, rect.bottom - inset, rect.right - inset - length, rect.bottom - inset, paint)
        canvas.drawLine(rect.right - inset, rect.bottom - inset, rect.right - inset, rect.bottom - inset - length, paint)
    }

    /** Azerbaijani ordinal suffix, e.g. 1-ci, 3-cü, 10-cu, 20-ci. */
    private fun ordinalAz(number: Int): String {
        val suffix = if (number % 10 == 0) {
            when ((number / 10) % 10) {
                1 -> "cu"
                2 -> "ci"
                3 -> "cu"
                4 -> "cı"
                5 -> "ci"
                6 -> "cı"
                7 -> "ci"
                8 -> "ci"
                9 -> "cı"
                else -> "cü"
            }
        } else {
            when (number % 10) {
                1, 2, 5, 7, 8 -> "ci"
                3, 4 -> "cü"
                6 -> "cı"
                9 -> "cu"
                else -> "ci"
            }
        }
        return "$number-$suffix"
    }

    private fun fitTranslationLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxHeight: Int
    ): StaticLayout {
        var size = 60f
        var candidate: StaticLayout? = null
        while (size >= 28f) {
            paint.textSize = size
            val maxLines = (maxHeight / (size * 1.38f)).toInt().coerceAtLeast(1)
            candidate = translationLayout(text, paint, width, maxLines)
            val fullyVisible = candidate.getLineEnd(candidate.lineCount - 1) >= text.length
            if (fullyVisible && candidate.height <= maxHeight) return candidate
            size -= 2f
        }
        paint.textSize = 28f
        val maxLines = (maxHeight / (28f * 1.38f)).toInt().coerceAtLeast(1)
        return candidate ?: translationLayout(text, paint, width, maxLines)
    }

    private fun translationLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.38f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
}

/**
 * Simple 16-bit PCM gain stage. Kept local so the feature has no dependency on
 * newer Media3 gain APIs.
 */
@OptIn(UnstableApi::class)
private class GainAudioProcessor(private val gain: Float) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val out = replaceOutputBuffer(inputBuffer.remaining())
        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.short.toInt()
            val scaled = (sample * gain).roundToInt().coerceIn(-32768, 32767)
            out.putShort(scaled.toShort())
        }
        out.flip()
    }
}
