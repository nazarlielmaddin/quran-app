package com.quransanctuary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.quransanctuary.app.data.audio.PlayerUiState
import com.quransanctuary.app.data.model.Reciter
import com.quransanctuary.app.data.model.Surah
import com.quransanctuary.app.domain.formatTimeMs
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.Ink

/**
 * The bar appears only once recitation actually starts (playing or paused
 * mid-track) — never on the hero / reciter / surah steps, never on a fresh
 * launch. Buffering alone (position 0) does NOT show it: that was the
 * appear-then-vanish flash on startup.
 */
fun PlayerUiState.hasActivePlayback(): Boolean =
    hasSelection && (quranPlaying || positionMs > 3000L)

/**
 * Drop-in bottom bar: resolves everything from the ViewModel, shows nothing
 * until playback starts. One call site per screen, zero wiring duplication.
 */
@Composable
fun PlayerBarOrNothing(
    vm: AppViewModel,
    nav: NavController,
    modifier: Modifier = Modifier,
    navigationBarsPadding: Boolean = true
) {
    val player by vm.player.collectAsState()
    if (!player.hasActivePlayback()) return
    val repo = vm.container.repository
    val engine = vm.container.audioEngine
    val locale by vm.locale.collectAsState()
    SlimPlayerBar(
        state = player,
        surah = repo.getSurah(player.surahId),
        reciter = repo.getReciter(player.reciterId),
        locale = locale.id,
        onToggle = { engine.toggleQuran() },
        onNext = { engine.nextSurah() },
        onPrev = { engine.prevSurah() },
        onSeek = { engine.seekTo(it) },
        onOpenPlayer = { nav.navigate(Route.Player) },
        modifier = modifier,
        navigationBarsPadding = navigationBarsPadding
    )
}

/** Progress in [0,1] — NaN / stale durations can never leak into the UI. */
private fun safeProgress(positionMs: Long, durationMs: Long): Float {
    return try {
        if (durationMs > 0 && positionMs >= 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f).let {
                if (it.isFinite()) it else 0f
            }
        } else 0f
    } catch (_: Exception) {
        0f
    }
}

/**
 * Slim premium mini-player: artwork, titles, transport, hairline gold
 * progress with times. Tap seeks; tap on titles opens the full player.
 */
@Composable
fun SlimPlayerBar(
    state: PlayerUiState,
    surah: Surah?,
    reciter: Reciter?,
    locale: String,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    navigationBarsPadding: Boolean = true
) {
    val strings = LocalStrings.current
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (navigationBarsPadding) Modifier.navigationBarsPadding()
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenPlayer),
                    contentAlignment = Alignment.Center
                ) {
                    if (reciter != null) {
                        AsyncImage(
                            model = "file:///android_asset/reciters/${reciter.assetFileName()}",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenPlayer)
                ) {
                    Text(
                        text = if (surah != null) "${surah.transliteratedName} № ${surah.number}" else "",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            reciter?.localizedName(locale),
                            surah?.englishName
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = strings.player.previous,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Gold)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.quranLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Ink
                        )
                    } else {
                        Icon(
                            if (state.quranPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.quranPlaying) strings.player.pause else strings.player.play,
                            tint = Ink,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = strings.player.next,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val progress = safeProgress(state.positionMs, state.durationMs)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = maxWidth
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .pointerInput(state.durationMs) {
                            detectTapGestures { offset ->
                                try {
                                    val dur = state.durationMs
                                    if (dur > 0 && width.value > 0) {
                                        val fraction = (offset.x / width.toPx()).coerceIn(0f, 1f)
                                        if (fraction.isFinite()) {
                                            onSeek((fraction * dur).toLong().coerceIn(0L, dur))
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Gold)
                            .align(Alignment.CenterStart)
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = formatTimeMs(state.positionMs),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTimeMs(state.durationMs),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
