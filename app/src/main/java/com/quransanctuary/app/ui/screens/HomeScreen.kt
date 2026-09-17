package com.quransanctuary.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.GlassSearchField
import com.quransanctuary.app.ui.components.GoldDivider
import com.quransanctuary.app.ui.components.GoldGradientButton
import com.quransanctuary.app.ui.components.GoldQuote
import com.quransanctuary.app.ui.components.PlayerBarOrNothing
import com.quransanctuary.app.ui.components.ReciterCell
import com.quransanctuary.app.ui.components.ReciterChip
import com.quransanctuary.app.ui.components.SanctuaryBackdrop
import com.quransanctuary.app.ui.components.StepHeader
import com.quransanctuary.app.ui.components.SurahGlassRow
import com.quransanctuary.app.ui.components.WizardTopBar
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.data.model.AppLocale

fun tabLabels(locale: AppLocale): Array<String> = when (locale) {
    AppLocale.AZ -> arrayOf("Əsas", "Oxunuş", "Kitabxana", "Parametrlər")
    AppLocale.EN -> arrayOf("Home", "Reader", "Library", "Settings")
    AppLocale.TR -> arrayOf("Ana Sayfa", "Okuma", "Kütüphane", "Ayarlar")
    AppLocale.AR -> arrayOf("الرئيسية", "القراءة", "المكتبة", "الإعدادات")
}

/**
 * Sanctuary home wizard — Hero (0) → Reciter (1) → Surah (2) → Reader.
 * Glassmorphism throughout; the reciter grid is compact by design so all
 * reciters fit on one screen with no scrolling.
 */
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val container = vm.container
    val repo = container.repository
    val engine = container.audioEngine
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val player by vm.player.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    player.error?.let { err ->
        LaunchedEffect(err) {
            snackbar.showSnackbar(if (err == "ambient") strings.errors.ambient else strings.errors.audio)
            engine.clearError()
        }
    }

    val ambient = repo.getAmbient(player.ambientId)
    val tabs = tabLabels(locale)

    SanctuaryBackdrop(ambient = ambient, photo = true, veil = if (step == 0) 0f else 0.72f) {
        Scaffold(
            topBar = {
                WizardTopBar(
                    showBack = step > 0,
                    onBack = { step-- },
                    libraryLabel = tabs.getOrElse(2) { "" },
                    settingsLabel = tabs.getOrElse(3) { "" },
                    onLibrary = { nav.navigate(Route.Library) },
                    onSettings = { nav.navigate(Route.Settings) },
                    nowPlayingLabel = strings.nav.nowPlaying,
                    onNowPlaying = { nav.navigate(Route.Player) }
                )
            },
            bottomBar = {
                PlayerBarOrNothing(vm = vm, nav = nav)
            },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            when (step) {
                0 -> HeroStep(onStart = { step = 1 }, modifier = Modifier.padding(padding))
                1 -> ReciterStep(
                    vm = vm,
                    onSelect = { id ->
                        engine.selectReciter(id)
                        step = 2
                    },
                    onPlay = { id ->
                        engine.playSurah(player.surahId, id, autoplay = true)
                        step = 2
                    },
                    modifier = Modifier.padding(padding)
                )
                else -> SurahStep(
                    vm = vm,
                    onBackToReciters = { step = 1 },
                    onOpen = { surahId -> nav.navigate(Route.Reader(surahId)) },
                    onPlay = { surahId ->
                        engine.playSurah(surahId, autoplay = true)
                        nav.navigate(Route.Reader(surahId))
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 0 — Hero.
// ---------------------------------------------------------------------------

@Composable
private fun HeroStep(
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val titleShadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.75f),
        offset = androidx.compose.ui.geometry.Offset(0f, 3f),
        blurRadius = 8f
    )
    Box(modifier = modifier.fillMaxSize()) {
        // Readability band: darkens the middle of the photo exactly where the
        // texts sit, so no bright arch/sunset detail can ever blend with them.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.28f to Color.Black.copy(alpha = 0.55f),
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(0.85f))
            Text(
                text = "${strings.hero.titleA}, ${strings.hero.titleB}…",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = titleShadow
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = strings.hero.subtitle,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = titleShadow),
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            GoldDivider()
            Spacer(Modifier.height(16.dp))
            GoldQuote(text = strings.hero.anfalAyah, cite = strings.hero.anfalCite)
            Spacer(Modifier.height(26.dp))
        GoldGradientButton(
            text = strings.hero.cta,
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(0.74f)
        )
            Spacer(Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1 — Reciter grid (compact: circle + name + play, no scroll).
// ---------------------------------------------------------------------------

@Composable
private fun ReciterStep(
    vm: AppViewModel,
    onSelect: (String) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = vm.container.repository
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val player by vm.player.collectAsState()
    val reciters = remember { repo.reciters }

    // Pyramid rows 4-3-2-1: every row centered, every cell identical size.
    val rows = remember(reciters) {
        val pattern = listOf(4, 3, 2, 1)
        var rest = reciters
        val out = ArrayList<List<com.quransanctuary.app.data.model.Reciter>>()
        for (n in pattern) {
            if (rest.isEmpty()) break
            out.add(rest.take(n))
            rest = rest.drop(n)
        }
        if (rest.isNotEmpty()) out.add(rest)
        out
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Tiered sizing: the whole pyramid always fits, no scrolling, no shifts.
        // Avatars stay modest; air comes from gaps, not from bigger discs.
        val tier = when {
            maxHeight >= 800.dp -> 0
            maxHeight >= 700.dp -> 1
            else -> 2
        }
        val cellWidth = (maxWidth - 24.dp) / 4
        val avatarSize = listOf(64.dp, 56.dp, 48.dp)[tier]
        val nameHeight = listOf(38.dp, 36.dp, 32.dp)[tier]
        val rowGap = listOf(18.dp, 16.dp, 14.dp)[tier]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StepHeader(
                title = strings.reciters.title,
                subtitle = strings.subtitleCount(reciters.size)
            )
            Spacer(Modifier.height(12.dp))
            GoldDivider()
            Spacer(Modifier.height(16.dp))
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    row.forEach { reciter ->
                        ReciterCell(
                            reciter = reciter,
                            locale = locale.id,
                            selected = player.reciterId == reciter.id,
                            playing = player.reciterId == reciter.id && player.quranPlaying,
                            avatarSize = avatarSize,
                            nameHeight = nameHeight,
                            onSelect = { onSelect(reciter.id) },
                            onPlay = { onPlay(reciter.id) },
                            modifier = Modifier.width(cellWidth)
                        )
                    }
                }
                Spacer(Modifier.height(rowGap))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Surah picker (search + glass rows → Reader).
// ---------------------------------------------------------------------------

@Composable
private fun SurahStep(
    vm: AppViewModel,
    onBackToReciters: () -> Unit,
    onOpen: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = vm.container.repository
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val player by vm.player.collectAsState()
    var query by remember { mutableStateOf("") }

    val currentReciter = remember(player.reciterId) { repo.getReciter(player.reciterId) }
    val filtered = remember(query) { repo.searchSurahs(query) }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(4.dp))
        StepHeader(title = strings.surahs.title, subtitle = strings.surahs.subtitle)
        Spacer(Modifier.height(10.dp))
        if (currentReciter != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ReciterChip(
                    reciter = currentReciter,
                    locale = locale.id,
                    onClick = onBackToReciters
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        GlassSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = strings.surahs.searchPlaceholder,
            clearLabel = strings.surahs.clear,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Text(
                text = strings.surahs.noneFound,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.number }) { surah ->
                    SurahGlassRow(
                        surah = surah,
                        ayahsLabel = strings.surahs.ayahs,
                        meccanLabel = strings.surahs.meccan,
                        medinanLabel = strings.surahs.medinan,
                        onOpen = { onOpen(surah.number) },
                        onPlay = { onPlay(surah.number) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
