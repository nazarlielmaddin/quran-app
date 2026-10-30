package com.quransanctuary.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.quransanctuary.app.R
import com.quransanctuary.app.data.model.AmbientSound
import com.quransanctuary.app.data.model.Reciter
import com.quransanctuary.app.data.model.Surah
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft

// ---------------------------------------------------------------------------
// Sanctuary glass design system: translucent surfaces, hairline borders,
// soft gold glows. Theme-aware (adapts to dark / light), no image assets.
// ---------------------------------------------------------------------------

/** Translucent card with a hairline border — the base glass surface. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * Full-screen backdrop: ambient wash + soft gold glow from the top +
 * gentle bottom vignette so the player bar sits on depth.
 *
 * photo=true paints the sanctuary photograph (hero_bg) edge-to-edge with
 * cinematic scrims — top for the status/top bar, bottom for content.
 * veil adds extra black for inner steps so lists stay legible.
 */
@Composable
fun SanctuaryBackdrop(
    ambient: AmbientSound?,
    photo: Boolean = false,
    veil: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (photo) {
        Box(modifier = modifier.fillMaxSize()) {            Image(
                painter = painterResource(R.drawable.hero_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Top scrim — status bar + top bar legibility.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.62f),
                            0.30f to Color.Transparent
                        )
                    )
            )
            // Bottom scrim — button / list legibility.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.42f to Color.Transparent,
                            0.74f to Color.Black.copy(alpha = 0.52f),
                            1f to Color.Black.copy(alpha = 0.84f)
                        )
                    )
            )
            if (veil > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = veil.coerceIn(0f, 0.9f)))
                )
            }
            content()
        }
        return
    }
    AmbientBackground(ambient = ambient, modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Gold.copy(alpha = 0.13f),
                                Gold.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            radius = 700f
                        )
                    )
            )
            // Warm arch light, top-end — the quiet mosque-glow of the mockups.
            Box(
                Modifier
                    .width(280.dp)
                    .height(380.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFC98A2E).copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            radius = 600f
                        )
                    )
            )
            content()
        }
    }
}

/** Wizard top bar: brand left, overflow menu + "İndi Oxunur" pill right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardTopBar(
    showBack: Boolean,
    onBack: () -> Unit,
    libraryLabel: String,
    settingsLabel: String,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    nowPlayingLabel: String,
    onNowPlaying: () -> Unit
) {
    val strings = LocalStrings.current
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = strings.brand,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .padding(3.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.brand,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { menu = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            ) {
                DropdownMenuItem(
                    text = { Text(libraryLabel) },
                    onClick = { menu = false; onLibrary() }
                )
                DropdownMenuItem(
                    text = { Text(settingsLabel) },
                    onClick = { menu = false; onSettings() }
                )
            }
            GlassPill(text = nowPlayingLabel, onClick = onNowPlaying)
            Spacer(Modifier.width(4.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    )
}

/** Small outlined pill button (top bar "İndi Oxunur"). */
@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1
        )
    }
}

/** Centered step header: large title + muted subtitle. */
@Composable
fun StepHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/** Hero primary CTA: solid white pill, dark text (matches the mockup). */
@Composable
fun PrimaryWhiteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF141419)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

/** Hero CTA: champagne-gold gradient pill, dark text + arrow (mockup). */
@Composable
fun GoldGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF8A5A1E),
                        Color(0xFFF2D79B),
                        Color(0xFFC99B4A)
                    )
                )
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF2E2008),
                maxLines = 1
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF2E2008),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Gold italic quote block used on the hero step. */
@Composable
fun GoldQuote(
    text: String,
    cite: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\u201C $text \u201D",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = Gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = cite,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Reciter step: compact circle cell (avatar + name + play badge).
// Mockup style: large circles, gold rings, dark badge with gold rim.
// ---------------------------------------------------------------------------

/** Gold ornament divider (— ◆ —) used under step subtitles. */
@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .width(56.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Gold.copy(alpha = 0.8f))
                    )
                )
        )
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .size(7.dp)
                .background(Gold, shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width / 2f, size.height)
                    lineTo(0f, size.height / 2f)
                    close()
                })
        )
        Box(
            Modifier
                .width(56.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Gold.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun ReciterCell(
    reciter: Reciter,
    locale: String,
    selected: Boolean,
    playing: Boolean,
    avatarSize: Dp,
    nameHeight: Dp = 38.dp,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val highlighted = selected || playing
    Column(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onSelect
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fixed centered system: avatar + ring share one center, so toggling
        // play/highlight changes colors only — zero geometry shift, ever.
        // (An earlier top-anchored ring made the photo look pushed upward.)
        Box(
            modifier = Modifier.size(avatarSize + 10.dp),
            contentAlignment = Alignment.Center
        ) {
            // Base disc with initial letter (visible while the photo loads).
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = reciter.localizedName(locale).take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AsyncImage(
                model = "file:///android_asset/reciters/${reciter.assetFileName()}",
                contentDescription = reciter.localizedName(locale),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .then(
                        if (highlighted) Modifier.border(2.dp, Gold, CircleShape)
                        else Modifier.border(1.dp, Gold.copy(alpha = 0.45f), CircleShape)
                    )
            )
            // Outer glow ring: always composed, transparent when idle —
            // identical layout in every state.
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        1.dp,
                        if (highlighted) Gold.copy(alpha = 0.55f) else Color.Transparent,
                        CircleShape
                    )
            )
            // Play / pause badge: dark disc, gold rim, white glyph.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1B22))
                    .border(1.5.dp, Gold.copy(alpha = 0.9f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlay
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Fixed-height name slot: wrapping/weight swaps can never move the avatar.
        Box(
            modifier = Modifier.height(nameHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = reciter.localizedName(locale),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                ),
                color = if (highlighted) GoldSoft else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Surah step: glass list row + search field + selected-reciter chip.
// ---------------------------------------------------------------------------

@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = clearLabel)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            focusedBorderColor = Gold.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            cursorColor = Gold
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SurahGlassRow(
    surah: Surah,
    ayahsLabel: String,
    meccanLabel: String,
    medinanLabel: String,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = surah.number.toString().padStart(3, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.transliteratedName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${surah.englishName} · ${surah.azerbaijaniName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${surah.verses} $ayahsLabel · " +
                        if (surah.revelationType == "Meccan") meccanLabel else medinanLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ReciterChip(
    reciter: Reciter,
    locale: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            AsyncImage(
                model = "file:///android_asset/reciters/${reciter.assetFileName()}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = reciter.localizedName(locale),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
