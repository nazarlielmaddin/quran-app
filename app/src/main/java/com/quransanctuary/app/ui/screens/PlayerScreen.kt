package com.quransanctuary.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quransanctuary.app.data.model.RepeatMode
import com.quransanctuary.app.domain.formatTimeMs
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.GlassCard
import com.quransanctuary.app.ui.components.SanctuaryBackdrop
import com.quransanctuary.app.ui.components.SettingSwitch
import com.quransanctuary.app.ui.components.VolumeRow
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft

/** Premium now-playing: gallery artwork, gold seek, glass control cards. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, nav: NavController) {
    val container = vm.container
    val repo = container.repository
    val engine = container.audioEngine
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val player by vm.player.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Control cards live in a separate expandable section (tune button).
    var controlsOpen by rememberSaveable { mutableStateOf(false) }

    val surah = repo.getSurah(player.surahId)
    val reciter = repo.getReciter(player.reciterId)
    val ambient = repo.getAmbient(player.ambientId)

    player.error?.let { err ->
        LaunchedEffect(err) {
            snackbar.showSnackbar(if (err == "ambient") strings.errors.ambient else strings.errors.audio)
            engine.clearError()
        }
    }

    SanctuaryBackdrop(ambient = ambient) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            strings.nav.nowPlaying,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.player.dismiss,
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { controlsOpen = !controlsOpen }) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = strings.player.repeat,
                                tint = if (controlsOpen) GoldSoft else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!controlsOpen) {
                Spacer(Modifier.height(8.dp))
                // ---- Artwork: photo disc + strong breathing golden aura ----
                // Aura + rim are pure radial/vector drawing (never shadow APIs:
                // shadow layers facet into octagons on some GPUs). The glow
                // pulses continuously — never a static line.
                val glowTransition = rememberInfiniteTransition(label = "artGlow")
                val glow by glowTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2200),
                        repeatMode = AnimRepeatMode.Reverse
                    ),
                    label = "artGlowAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Tight breathing aura: peak glued to the disc rim (0.875),
                    // fully dissolved by 0.97 — glow hugs the photo, never
                    // sprawls across the screen.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    0f to Color.Transparent,
                                    0.80f to Color.Transparent,
                                    0.875f to Gold.copy(alpha = 0.30f + glow * 0.50f),
                                    0.97f to Color.Transparent,
                                    1f to Color.Transparent
                                ),
                                shape = CircleShape
                            )
                    )
                    Card(
                        shape = CircleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Gold.copy(alpha = 0.35f + glow * 0.50f)
                        ),
                        modifier = Modifier
                            .fillMaxSize(0.88f)
                            .aspectRatio(1f)
                    ) {
                        if (reciter != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("file:///android_asset/reciters/${reciter.assetFileName()}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = reciter.localizedName(locale.id),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    // ---- Titles ----
                    Text(
                        text = surah?.let { "${it.number}. ${it.transliteratedName}" } ?: "",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = reciter?.localizedName(locale.id) ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldSoft,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (surah != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${surah.azerbaijaniName} · ${surah.verses} ${strings.surahs.ayahs}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    // ---- Live ayah pill (stays visible on pause too) ----
                    if (player.hasSync && surah != null) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = CircleShape,
                            color = Gold.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Filled.FiberManualRecord,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(8.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${strings.recitation.verse} ${player.activeAyah + 1} / ${surah.verses}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = GoldSoft
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    // ---- Seek ----
                    var dragging by remember(player.surahId, player.reciterId) { mutableStateOf(false) }
                    var dragValue by remember(player.surahId, player.reciterId) { mutableFloatStateOf(0f) }
                    val progress = runCatching {
                        val dur = player.durationMs
                        val pos = player.positionMs
                        if (dur > 0 && pos >= 0) {
                            (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f).let {
                                if (it.isFinite()) it else 0f
                            }
                        } else 0f
                    }.getOrDefault(0f)
                    Slider(
                        value = (if (dragging) dragValue else progress).coerceIn(0f, 1f),
                        onValueChange = { dragging = true; dragValue = it.coerceIn(0f, 1f) },
                        onValueChangeFinished = {
                            try {
                                val dur = player.durationMs.coerceAtLeast(0L)
                                val target = (dragValue.coerceIn(0f, 1f) * dur.toFloat())
                                    .let { if (it.isFinite()) it.toLong() else 0L }
                                    .coerceIn(0L, dur)
                                engine.seekTo(target)
                            } catch (_: Exception) {
                            } finally {
                                dragging = false
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Gold,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTimeMs(player.positionMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTimeMs(player.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    // ---- Transport ----
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        IconButton(onClick = { engine.prevSurah() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = strings.player.previous, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(38.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { engine.toggleQuran() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (player.quranLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
                            } else {
                                Icon(
                                    if (player.quranPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (player.quranPlaying) strings.player.pause else strings.player.play,
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        IconButton(onClick = { engine.nextSurah() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = strings.player.next, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(38.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = strings.recitation.tapVerse,
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldSoft,
                        modifier = Modifier.clickable { nav.navigate(Route.Reader(player.surahId)) }
                    )
                } else {
                    // ---- Control cards view (tune button): only the cards ----
                    Spacer(Modifier.height(12.dp))
                // ---- Playback card ----
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SectionCaption(strings.player.repeat)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = { engine.cycleRepeat() }) {
                                Icon(
                                    if (player.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                    contentDescription = strings.player.repeat,
                                    tint = if (player.repeat == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else Gold,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (player.repeat) {
                                        RepeatMode.OFF -> strings.repeatStatus.off
                                        RepeatMode.ONE -> strings.repeatStatus.one
                                        RepeatMode.ALL -> strings.repeatStatus.all
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    when (player.repeat) {
                                        RepeatMode.OFF -> strings.repeats.off
                                        RepeatMode.ONE -> strings.repeats.one
                                        RepeatMode.ALL -> strings.repeats.all
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldSoft
                                )
                            }
                        }
                        SettingSwitch(
                            checked = player.autoNext,
                            onChange = { engine.setAutoNext(it) },
                            label = strings.player.autoNext
                        )
                        SettingSwitch(
                            checked = player.quranEnabled,
                            onChange = { engine.setQuranEnabled(it) },
                            label = "${strings.player.quran}: ${if (player.quranEnabled) strings.player.on else strings.player.off}"
                        )
                        VolumeRow(
                            label = strings.player.quranVolume,
                            value = player.quranVolume,
                            onChange = { engine.setQuranVolume(it) },
                            icon = if (player.quranVolume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // ---- Ambient card ----
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SectionCaption("${strings.player.ambient}: ${ambient?.localizedName(locale.id) ?: strings.player.none}")
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = player.ambientId == null,
                                onClick = { engine.stopAmbient() },
                                label = { Text(strings.player.none) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Gold.copy(alpha = 0.25f),
                                    selectedLabelColor = GoldSoft
                                )
                            )
                            repo.ambients.forEach { sound ->
                                FilterChip(
                                    selected = player.ambientId == sound.id,
                                    onClick = { engine.toggleAmbient(sound.id) },
                                    label = { Text(sound.localizedName(locale.id)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold.copy(alpha = 0.25f),
                                        selectedLabelColor = GoldSoft
                                    )
                                )
                            }
                        }
                        if (player.ambientId != null) {
                            Spacer(Modifier.height(8.dp))
                            SettingSwitch(
                                checked = player.loopAmbient,
                                onChange = { engine.setLoopAmbient(it) },
                                label = strings.player.loopAmbient
                            )
                            VolumeRow(
                                label = strings.player.ambientVolume,
                                value = player.ambientVolume,
                                onChange = { engine.setAmbientVolume(it) },
                                icon = if (player.ambientVolume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // ---- Sleep timer card ----
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Gold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Sleep timer" + (player.sleepMinutesLeft?.let { " · $it min" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f)
                            )
                            if (player.sleepMinutesLeft != null) {
                                Text(
                                    text = "✕",
                                    color = GoldSoft,
                                    modifier = Modifier
                                        .clickable { engine.cancelSleepTimer() }
                                        .padding(8.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(15, 30, 45, 60).forEach { min ->
                                FilterChip(
                                    selected = player.sleepMinutesLeft == min,
                                    onClick = { engine.startSleepTimer(min) },
                                    label = { Text("$min min") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold.copy(alpha = 0.25f),
                                        selectedLabelColor = GoldSoft
                                    )
                                )
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(44.dp))
            }
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}
