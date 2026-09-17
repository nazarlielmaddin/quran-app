package com.quransanctuary.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quransanctuary.app.data.model.AmbientSound
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft

fun ambientIcon(id: String): ImageVector = when (id) {
    "rain", "heavyRain" -> Icons.Filled.WaterDrop
    "thunder" -> Icons.Filled.Thunderstorm
    "wind" -> Icons.Filled.Air
    "ocean" -> Icons.Filled.Waves
    "river", "water" -> Icons.Filled.Water
    "forest", "birds" -> Icons.Filled.Forest
    "night" -> Icons.Filled.Bedtime
    "fireplace" -> Icons.Filled.LocalFireDepartment
    "cat" -> Icons.Filled.Pets
    "room" -> Icons.Filled.Weekend
    else -> Icons.Filled.Cloud
}

fun parseHex(hex: String): Color = runCatching {
    val clean = hex.removePrefix("#")
    val full = if (clean.length == 6) "FF$clean" else clean
    require(full.length == 8)
    Color(
        red = full.substring(2, 4).toInt(16) / 255f,
        green = full.substring(4, 6).toInt(16) / 255f,
        blue = full.substring(6, 8).toInt(16) / 255f,
        alpha = full.substring(0, 2).toInt(16) / 255f
    )
}.getOrDefault(Color(0xFF141420))

/**
 * Website AmbientSelector card: gradient/image cover, center icon fallback,
 * play/pause FAB and waveform while playing.
 */
@Composable
fun AmbientCard(
    sound: AmbientSound,
    locale: String,
    selected: Boolean,
    playing: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onLongPressStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gradient = sound.gradient
    val c1 = gradient.getOrNull(0)?.let(::parseHex) ?: Color(0xFF141420)
    val c2 = gradient.getOrNull(1)?.let(::parseHex) ?: Color(0xFF0A0A10)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (selected) Gold.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Brush.radialGradient(listOf(c1, c2)))
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (sound.imageUrl != null) {
                    AsyncImage(
                        model = sound.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = 0.6f
                    )
                } else {
                    Icon(
                        imageVector = ambientIcon(sound.id),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (playing) Gold else Color.Black.copy(alpha = 0.45f))
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading && selected) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = sound.localizedName(locale),
                            tint = if (playing) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (playing) {
                    Waveform(
                        bars = 4,
                        playing = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }
            Text(
                text = sound.localizedName(locale),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) GoldSoft else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}
