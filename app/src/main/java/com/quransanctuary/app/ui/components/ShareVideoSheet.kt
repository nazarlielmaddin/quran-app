package com.quransanctuary.app.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.quransanctuary.app.data.audio.AudioEngine
import com.quransanctuary.app.data.share.ShareVideoGenerator
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface VideoUiState {
    data class Working(val progress: ShareVideoGenerator.Progress) : VideoUiState
    data class Ready(val file: File, val warning: String?) : VideoUiState
    data class Failed(val message: String) : VideoUiState
}

/**
 * Full-screen share studio. Generation starts immediately, the finished
 * 9:16 MP4 plays in place, and the same file can be shared or saved.
 */
@Composable
fun AyahShareSheet(
    request: ShareVideoGenerator.Request,
    generator: ShareVideoGenerator,
    engine: AudioEngine,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember(request) { mutableIntStateOf(0) }
    var state by remember(request, attempt) {
        mutableStateOf<VideoUiState>(
            VideoUiState.Working(
                ShareVideoGenerator.Progress(
                    ShareVideoGenerator.Stage.PREPARING,
                    0f,
                    "Hazırlanır…"
                )
            )
        )
    }

    // The generated clip has its own soundtrack; do not fight it with the
    // in-app Quran/ambient mix.
    DisposableEffect(Unit) {
        engine.beginSharePreview()
        onDispose { engine.endSharePreview() }
    }

    LaunchedEffect(request, attempt) {
        val cached = generator.cachedOutput(request)
        if (cached != null) {
            state = VideoUiState.Ready(cached, null)
            return@LaunchedEffect
        }
        state = VideoUiState.Working(
            ShareVideoGenerator.Progress(ShareVideoGenerator.Stage.PREPARING, 0f, "Hazırlanır…")
        )
        runCatching {
            generator.generate(request) { progress ->
                state = VideoUiState.Working(progress)
            }
        }.onSuccess { result ->
            state = VideoUiState.Ready(result.file, result.warning)
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            state = VideoUiState.Failed(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Video hazırlana bilmədi. Yenidən cəhd edin."
            )
        }
    }

    val readyFile = (state as? VideoUiState.Ready)?.file
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val file = readyFile ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("OutputStream null")
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Video cihaza yükləndi." else "Video saxlanıla bilmədi.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun saveVideo(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val ok = runCatching {
                    withContext(Dispatchers.IO) { saveToGallery(context, file) }
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) "Video qalereyaya yükləndi." else "Video yüklənə bilmədi.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            saveLauncher.launch(file.name)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF07080C)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0B0D14), Color(0xFF05060A), Color(0xFF090A0F))
                        )
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Ayə videosu",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                            Text(
                                "9:16 · MP4 · Azərbaycan dilində tərcümə",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldSoft
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Bağla",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewHeight = maxHeight * 0.94f
                        val previewWidth = minOf(maxWidth * 0.78f, previewHeight * 9f / 16f)
                        Box(
                            modifier = Modifier
                                .width(previewWidth)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color.Black)
                                .border(
                                    BorderStroke(1.dp, Gold.copy(alpha = 0.55f)),
                                    RoundedCornerShape(26.dp)
                                )
                        ) {
                            when (val current = state) {
                                is VideoUiState.Ready -> GeneratedVideoPreview(current.file)
                                is VideoUiState.Working -> WorkingPreview(
                                    request = request,
                                    generator = generator
                                )
                                is VideoUiState.Failed -> FailedPreview(current.message)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    when (val current = state) {
                        is VideoUiState.Ready -> {
                            current.warning?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldSoft.copy(alpha = 0.9f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { saveVideo(current.file) },
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f))
                                ) {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null,
                                        tint = GoldSoft,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text("Yüklə", color = GoldSoft)
                                }
                                Button(
                                    onClick = { shareVideo(context, current.file) },
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text("Paylaş", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Video telefonun yaddaşında saxlanılmadan paylaşıla bilər.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.48f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is VideoUiState.Working -> {
                            val fraction = current.progress.fraction
                            if (fraction != null) {
                                LinearProgressIndicator(
                                    progress = { fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = Gold,
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = Gold,
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                current.progress.message.ifBlank { "Video hazırlanır…" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.82f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is VideoUiState.Failed -> {
                            Text(
                                current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.82f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { attempt++ },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Gold,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Yenidən cəhd et", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkingPreview(
    request: ShareVideoGenerator.Request,
    generator: ShareVideoGenerator
) {
    // The exact same overlay that will be baked into the MP4. This makes the
    // loading state trustworthy instead of a separate approximation.
    val overlay = remember(request) { generator.previewOverlay(request) }
    DisposableEffect(overlay) {
        onDispose { overlay.recycle() }
    }
    Box(Modifier.fillMaxSize()) {
        val image = request.ambient?.imageUrl
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.82f
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF151B2A), Color(0xFF06070C))
                        )
                    )
            )
        }
        Image(
            bitmap = overlay.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FailedPreview(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF171A24), Color(0xFF07080C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(28.dp)
        )
    }
}

@Composable
private fun GeneratedVideoPreview(file: File) {
    val context = LocalContext.current
    val player = remember(file.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    // New exports are true 1080x1920 files with no rotation metadata. Only the
    // legacy fallback (if a device cannot encode portrait) still carries a 90°
    // flag, and the emulator's software decoder ignores it. Compensate for that
    // fallback alone; a true portrait file is never rotated here.
    val metadataRotation = remember(file.path) { videoRotationDegrees(file) }
    val compensateOnEmulator = isEmulatorPreview() && metadataRotation % 180 != 0
    if (compensateOnEmulator) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { view ->
                        player.setVideoTextureView(view)
                    }
                },
                update = { view ->
                    player.setVideoTextureView(view)
                },
                onRelease = { view ->
                    runCatching { player.clearVideoTextureView(view) }
                },
                modifier = Modifier
                    .requiredWidth(maxHeight)
                    .requiredHeight(maxWidth)
                    .graphicsLayer { rotationZ = 90f }
            )
        }
    } else {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { view ->
                    player.setVideoTextureView(view)
                }
            },
            update = { view ->
                player.setVideoTextureView(view)
            },
            onRelease = { view ->
                runCatching { player.clearVideoTextureView(view) }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun videoRotationDegrees(file: File): Int = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
    } finally {
        retriever.release()
    }
}.getOrDefault(0)

private fun isEmulatorPreview(): Boolean {
    val hardware = Build.HARDWARE.lowercase()
    return hardware == "goldfish" ||
        hardware == "ranchu" ||
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for")
}

private fun shareVideo(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Ayə videosunu paylaş"))
    }.onFailure {
        Toast.makeText(context, "Paylaşma pəncərəsi açıla bilmədi.", Toast.LENGTH_SHORT).show()
    }
}

private fun saveToGallery(context: Context, file: File): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(
            MediaStore.Video.Media.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES + "/QuranSanctuary"
        )
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("MediaStore insert failed")
    resolver.openOutputStream(uri)?.use { output ->
        file.inputStream().use { input -> input.copyTo(output) }
    } ?: error("MediaStore output stream null")
    values.clear()
    values.put(MediaStore.Video.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return uri
}
