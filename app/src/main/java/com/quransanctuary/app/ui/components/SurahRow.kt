package com.quransanctuary.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quransanctuary.app.data.model.Surah
import com.quransanctuary.app.domain.formatTime
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft

/**
 * Website SurahBrowser row: number medallion, names, meta (ayahs · Meccan/Medinan · duration),
 * offline-download affordance and a play button.
 */
@Composable
fun SurahRow(
    surah: Surah,
    isCurrent: Boolean,
    isPlaying: Boolean,
    durationSeconds: Int,
    ayahsLabel: String,
    meccanLabel: String,
    medinanLabel: String,
    playLabel: String,
    downloadState: DownloadUiState,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isCurrent) Gold.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) Gold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        text = surah.number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrent) GoldSoft else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${surah.number}. ${surah.transliteratedName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) GoldSoft else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${surah.azerbaijaniName} · ${surah.englishName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("${surah.verses} $ayahsLabel")
                        append(" · ")
                        append(if (surah.revelationType == "Meccan") meccanLabel else medinanLabel)
                        if (durationSeconds > 0) {
                            append(" · ${formatTime(durationSeconds.toLong())}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = surah.arabicName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isPlaying && isCurrent) {
                Waveform(bars = 4, playing = true, modifier = Modifier.padding(end = 8.dp))
            }
            DownloadIconButton(state = downloadState, onClick = onDownload)
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = if (isPlaying && isCurrent) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "$playLabel ${surah.transliteratedName}",
                    tint = if (isCurrent) GoldSoft else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

sealed interface DownloadUiState {
    data object Hidden : DownloadUiState
    data object Available : DownloadUiState
    data class Progress(val percent: Int) : DownloadUiState
    data object Done : DownloadUiState
}

@Composable
fun DownloadIconButton(state: DownloadUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        DownloadUiState.Hidden -> Unit
        DownloadUiState.Available -> IconButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Filled.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is DownloadUiState.Progress -> CircularProgressIndicator(
            progress = { (state.percent / 100f).coerceIn(0f, 1f) },
            modifier = modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = Gold
        )
        DownloadUiState.Done -> IconButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Filled.Download, contentDescription = "Downloaded", tint = Gold)
        }
    }
}
