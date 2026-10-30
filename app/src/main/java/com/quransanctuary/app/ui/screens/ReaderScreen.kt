package com.quransanctuary.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.quransanctuary.app.data.local.BookmarkEntity
import com.quransanctuary.app.data.local.QuranRepository
import com.quransanctuary.app.data.model.DisplayMode
import com.quransanctuary.app.data.model.VerseTexts
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.AmbientBackground
import com.quransanctuary.app.ui.components.GoldButton
import com.quransanctuary.app.ui.components.PlayerBarOrNothing
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.AmiriFamily
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft
import com.quransanctuary.app.ui.theme.arabicTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(vm: AppViewModel, nav: NavController, surahId: Int) {
    val container = vm.container
    val repo = container.repository
    val engine = container.audioEngine
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val player by vm.player.collectAsState()
    val displayMode by vm.displayMode.collectAsState()
    val fontScale by vm.arabicFontScale.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val surah = remember(surahId) { repo.getSurah(surahId) }
    var verses by remember(surahId) { mutableStateOf<VerseTexts?>(null) }
    var versesMissing by remember(surahId) { mutableStateOf(false) }
    LaunchedEffect(surahId) {
        verses = null
        versesMissing = false
        verses = repo.getVerses(surahId)
        if (verses == null) versesMissing = true
    }

    val bookmarks by container.database.bookmarks().observe().collectAsState(initial = emptyList())
    val bookmarkedAyahs = remember(bookmarks, surahId) {
        bookmarks.filter { it.surah == surahId }.map { it.ayah }.toSet()
    }

    if (surah == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.surahs.noneFound)
        }
        return
    }

    val isCurrentSurah = player.surahId == surahId
    val showArabic = displayMode == DisplayMode.ARABIC || displayMode == DisplayMode.BOTH
    val showTranslit = displayMode == DisplayMode.TRANSLITERATION || displayMode == DisplayMode.BOTH
    // Key the list state by surah: without this, scrolling to item 31 in a long surah
    // and then opening an 18-ayah surah (49/64) reuses the stale scroll position and
    // the internal Lazy/SlotTable asks for index=31 in a length=18 table -> AIOOBE.
    val listState: LazyListState = remember(surahId) { LazyListState() }
    val loadedVerses = verses

    // Reset stale scroll whenever the surah changes.
    LaunchedEffect(surahId) {
        runCatching { listState.scrollToItem(0) }
    }

    // Follow playback: prev/next from the mini player (or auto-next) moves
    // the open reader to the surah that is actually playing — but only when
    // playback MOVES. Opening a different surah to browse while listening
    // must never yank the reader away (tapVerse joins explicitly instead).
    var lastSeenPlaying by remember { mutableStateOf(player.surahId) }
    LaunchedEffect(player.surahId) {
        if (player.surahId != lastSeenPlaying) {
            lastSeenPlaying = player.surahId
            if (player.surahId != surahId) {
                runCatching {
                    nav.navigate(Route.Reader(player.surahId)) {
                        popUpTo(Route.Reader(surahId)) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // Header layout: header card (1) + mode switcher (1) + optional bismillah (0/1).
    // Verse `index` != Lazy item index — the offset must be added or auto-scroll
    // lands on the wrong row and can request an out-of-bounds index.
    val headerCount = 2 + if (QuranRepository.showsBismillah(surahId)) 1 else 0
    // Auto-scroll to the active verse while reciting (paused while the user scrolls).
    LaunchedEffect(player.activeAyah, player.quranPlaying, surahId, loadedVerses) {
        if (isCurrentSurah && player.quranPlaying && player.timings != null && loadedVerses != null) {
            if (!listState.isScrollInProgress) {
                try {
                    val verseCount = loadedVerses.arabic.size
                    if (verseCount <= 0) return@LaunchedEffect
                    val safeAyah = player.activeAyah.coerceIn(0, verseCount - 1)
                    val totalItems = listState.layoutInfo.totalItemsCount
                    // layoutInfo may be empty on first composition — fall back to known structure.
                    val itemCount = if (totalItems > 0) totalItems else headerCount + verseCount + 1
                    val target = (headerCount + safeAyah).coerceIn(0, itemCount - 1)
                    if (target in 0 until itemCount) {
                        runCatching { listState.animateScrollToItem(target) }
                    }
                } catch (_: Exception) {
                    // Never crash playback because of auto-scroll.
                }
            }
        }
    }

    fun tapVerse(index: Int) {
        scope.launch {
            if (!isCurrentSurah) {
                engine.playSurah(surahId, autoplay = true)
                // Wait for the new surah's timings, then jump.
                var tries = 0
                while (tries < 20) {
                    delay(300)
                    val t = engine.state.value.timings
                    if (engine.state.value.surahId == surahId && t != null && index in t.indices) {
                        engine.seekTo(t[index])
                        return@launch
                    }
                    tries++
                }
            } else {
                val t = engine.state.value.timings
                if (t != null && index in t.indices) {
                    if (!engine.state.value.quranPlaying) engine.toggleQuran()
                    engine.seekTo(t[index])
                } else {
                    if (!engine.state.value.quranPlaying) {
                        engine.playSurah(surahId, autoplay = true)
                    }
                }
            }
        }
    }

    fun toggleBookmark(ayah: Int) {
        scope.launch {
            val dao = container.database.bookmarks()
            if (dao.find(surahId, ayah) != null) dao.delete(surahId, ayah)
            else dao.insert(BookmarkEntity(surah = surahId, ayah = ayah))
        }
    }

    val ambient = repo.getAmbient(player.ambientId)
    AmbientBackground(ambient = ambient) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "${surah.number}. ${surah.transliteratedName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        val rec = repo.getRecitation(player.reciterId, surahId)
                        IconButton(onClick = {
                            if (rec != null) {
                                container.downloader.enqueue(
                                    "${player.reciterId}:$surahId", rec.audioUrl,
                                    "${surah.transliteratedName} — ${repo.getReciter(player.reciterId)?.localizedName(locale.id) ?: ""}",
                                    player.reciterId, surahId
                                )
                            }
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = strings.surahs.duration)
                        }
                        IconButton(onClick = {
                            if (isCurrentSurah) engine.toggleQuran()
                            else engine.playSurah(surahId, autoplay = true)
                        }) {
                            Icon(
                                if (isCurrentSurah && player.quranPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = strings.player.play,
                                tint = Gold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                PlayerBarOrNothing(vm = vm, nav = nav)
            },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            if (verses == null && !versesMissing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Gold)
                        Spacer(Modifier.height(12.dp))
                        Text(strings.recitation.loading, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Scaffold
            }
            if (versesMissing || verses == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(strings.recitation.unavailable, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }
            val v = verses!!
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header card
                item {
                    ReaderHeader(
                        surahNumber = surah.number,
                        transliterated = surah.transliteratedName,
                        arabicName = surah.arabicName,
                        meta = "${surah.verses} ${strings.surahs.ayahs} · " +
                            if (surah.revelationType == "Meccan") strings.surahs.meccan else strings.surahs.medinan
                    )
                }
                // Display mode switchers (transliteration is always the correct reading)
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ModeChip(
                            selected = displayMode == DisplayMode.TRANSLITERATION,
                            label = strings.recitation.displayModes.transliteration,
                            onClick = { vm.setDisplayMode(DisplayMode.TRANSLITERATION) }
                        )
                        ModeChip(
                            selected = displayMode == DisplayMode.ARABIC,
                            label = strings.recitation.displayModes.arabic,
                            onClick = { vm.setDisplayMode(DisplayMode.ARABIC) }
                        )
                        ModeChip(
                            selected = displayMode == DisplayMode.BOTH,
                            label = strings.recitation.displayModes.both,
                            onClick = { vm.setDisplayMode(DisplayMode.BOTH) }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.recitation.tapVerse,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Bismillah header — background-free so the ambient video
                // stays visible behind it.
                if (QuranRepository.showsBismillah(surahId)) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isCurrentSurah) engine.playSurah(surahId, autoplay = true)
                                    engine.seekTo(0)
                                    if (!engine.state.value.quranPlaying) engine.toggleQuran()
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                    text = QuranRepository.BISMILLAH,
                                    style = arabicTextStyle(fontScale),
                                    color = GoldSoft,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Bismillahir-rəhmanir-rəhim",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldSoft,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                // Verses — stable keys keep SlotTable identity across recompositions
                // (translit style switch + 400ms progress updates). Without keys, rapid
                // recompose + parallel animateScrollToItem is a known AIOOBE source.
                itemsIndexed(v.arabic, key = { index, _ -> index }) { index, arabic ->
                    val active = isCurrentSurah && player.quranPlaying && player.activeAyah == index &&
                        player.timings != null
                    VerseCard(
                        ayahNumber = index + 1,
                        arabic = if (QuranRepository.showsBismillah(surahId) && index == 0) {
                            QuranRepository.stripBismillahPrefix(arabic)
                        } else arabic,
                        transliteration = v.transliteration.getOrNull(index) ?: "",
                        translation = v.translationAz.getOrNull(index) ?: "",
                        showArabic = showArabic,
                        showTranslit = showTranslit,
                        active = active,
                        bookmarked = bookmarkedAyahs.contains(index + 1),
                        fontScale = fontScale,
                        onTap = { tapVerse(index) },
                        onToggleBookmark = { toggleBookmark(index + 1) }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GoldButton(
                            text = if (isCurrentSurah && player.quranPlaying) strings.player.pause else strings.player.play,
                            onClick = {
                                if (isCurrentSurah) engine.toggleQuran()
                                else engine.playSurah(surahId, autoplay = true)
                            }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ReaderHeader(
    surahNumber: Int,
    transliterated: String,
    arabicName: String,
    meta: String
) {
    // Background-free: floats over the ambient video. Legibility comes from
    // soft text shadows instead of a card.
    val veil = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.7f),
        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
        blurRadius = 8f
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = arabicName,
            style = arabicTextStyle(1.4f).copy(shadow = veil),
            color = GoldSoft,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        com.quransanctuary.app.ui.components.GoldDivider()
        Spacer(Modifier.height(10.dp))
        Text(
            text = "$surahNumber. $transliterated",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                shadow = veil
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = meta,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = veil),
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ModeChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Gold.copy(alpha = 0.25f),
            selectedLabelColor = GoldSoft
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) Gold else MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun VerseCard(
    ayahNumber: Int,
    arabic: String,
    transliteration: String,
    translation: String,
    showArabic: Boolean,
    showTranslit: Boolean,
    active: Boolean,
    bookmarked: Boolean,
    fontScale: Float,
    onTap: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            // Frosted glass: translucent enough for the ambient video to glow
            // through, solid enough for the verse to stay legible.
            containerColor = if (active) Gold.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f)
        ),
        border = BorderStroke(
            1.dp,
            if (active) Gold.copy(alpha = 0.65f)
            else Color.White.copy(alpha = 0.14f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onTap, onLongClick = onToggleBookmark)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (active) Gold else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ayahNumber.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (bookmarked) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (showArabic) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = arabic,
                    style = arabicTextStyle(fontScale),
                    color = if (active) GoldSoft else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showTranslit && transliteration.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = transliteration,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = AmiriFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (translation.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}
