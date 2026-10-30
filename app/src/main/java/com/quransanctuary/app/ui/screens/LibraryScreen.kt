package com.quransanctuary.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.quransanctuary.app.data.model.AppLocale
import com.quransanctuary.app.domain.formatTime
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.GlassCard
import com.quransanctuary.app.ui.components.PlayerBarOrNothing
import com.quransanctuary.app.ui.components.SanctuaryScaffold
import com.quransanctuary.app.ui.components.SanctuaryTopBar
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import kotlinx.coroutines.launch

private fun libraryTabLabels(locale: AppLocale): Triple<String, String, String> = when (locale) {
    AppLocale.AZ -> Triple("Əlfəcinlər", "Endirilənlər", "Tarixçə")
    AppLocale.EN -> Triple("Bookmarks", "Downloads", "History")
    AppLocale.TR -> Triple("Yer İmleri", "İndirilenler", "Geçmiş")
    AppLocale.AR -> Triple("العلامات", "التنزيلات", "السجل")
}

/** Native library: ayah bookmarks, offline downloads, recent history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: AppViewModel, nav: NavController) {
    val container = vm.container
    val repo = container.repository
    val engine = container.audioEngine
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val player by vm.player.collectAsState()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = tabLabels(locale)
    // Defensive: tabLabels must always be 4, but never index blindly (AIOOBE guard).
    val homeTabLabel = tabs.getOrElse(0) { "" }
    val readerTabLabel = tabs.getOrElse(1) { "" }
    val libraryTabLabel = tabs.getOrElse(2) { "" }
    val settingsTabLabel = tabs.getOrElse(3) { "" }
    val (bmLabel, dlLabel, histLabel) = libraryTabLabels(locale)

    val bookmarks by container.database.bookmarks().observe().collectAsState(initial = emptyList())
    val allDownloads by container.database.downloads().observe().collectAsState(initial = emptyList())
    // Hide stale ledger rows whose file lived in the legacy `audio/` tree.
    val downloads = allDownloads.filter {
        container.downloader.isDownloaded(it.key, it.reciterId, it.surahId)
    }
    val history by container.database.history().observeRecent().collectAsState(initial = emptyList())

    SanctuaryScaffold(
        currentTab = "library",
        onTab = { t ->
            when (t) {
                "home" -> nav.navigate(Route.Home)
                "reader" -> nav.navigate(Route.Reader(player.surahId))
                "settings" -> nav.navigate(Route.Settings)
            }
        },
        ambient = repo.getAmbient(player.ambientId),
        homeLabel = homeTabLabel, readerLabel = readerTabLabel, libraryLabel = libraryTabLabel, settingsLabel = settingsTabLabel,
        topBar = { SanctuaryTopBar(onBrandClick = {}) },
        bottomBar = {
            PlayerBarOrNothing(
                vm = vm,
                nav = nav,
                navigationBarsPadding = false
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = tab.coerceIn(0, 2),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                indicator = { pos ->
                    // Guard: stale tab index must never crash the indicator.
                    val anchor = pos.getOrNull(tab.coerceIn(0, 2))
                    if (anchor != null) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(anchor),
                            color = Gold
                        )
                    }
                }
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(bmLabel) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(dlLabel) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(histLabel) })
            }
            when (tab.coerceIn(0, 2)) {
                0 -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (bookmarks.isEmpty()) {
                        item {
                            EmptyHint(
                                icon = Icons.Filled.Bookmark,
                                text = when (locale) {
                                    AppLocale.AZ -> "Hələ əlfəcin yoxdur. Ayəni əlfəcinə əlavə etmək üçün üzərinə uzun basın."
                                    AppLocale.TR -> "Henüz yer imi yok. Eklemek için ayete uzun basın."
                                    AppLocale.AR -> "لا توجد علامات بعد. اضغط مطولاً على الآية للإضافة."
                                    else -> "No bookmarks yet. Long-press a verse to add one."
                                }
                            )
                        }
                    }
                    items(bookmarks, key = { it.id }) { bm ->
                        val surah = repo.getSurah(bm.surah)
                        GlassCard(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.navigate(Route.Reader(bm.surah)) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Gold)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${surah?.number ?: bm.surah}. ${surah?.transliteratedName ?: ""} — ${strings.recitation.verse} ${bm.ayah}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        surah?.arabicName ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch { container.database.bookmarks().deleteById(bm.id) }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                1 -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (downloads.isEmpty()) {
                        item {
                            EmptyHint(
                                icon = Icons.Filled.Download,
                                text = when (locale) {
                                    AppLocale.AZ -> "Oflayn dinləmək üçün surə siyahısında yükləmə işarəsinə toxunun."
                                    AppLocale.TR -> "Çevrimdışı dinlemek için sure listesindeki indirme simgesine dokunun."
                                    AppLocale.AR -> "للاستماع دون اتصال، اضغط على أيقونة التنزيل في قائمة السور."
                                    else -> "Tap the download icon in the surah list to listen offline."
                                }
                            )
                        }
                    }
                    items(downloads, key = { it.key }) { dl ->
                        GlassCard(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, tint = Gold)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(dl.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${dl.bytes / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (dl.surahId > 0) {
                                    IconButton(onClick = {
                                        engine.playSurah(dl.surahId, dl.reciterId, autoplay = true)
                                        nav.navigate(Route.Reader(dl.surahId))
                                    }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = strings.player.play, tint = Gold)
                                    }
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        container.downloader.delete(dl.key, dl.reciterId, dl.surahId)
                                    }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                2 -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (history.isEmpty()) {
                        item {
                            EmptyHint(
                                icon = Icons.Filled.History,
                                text = when (locale) {
                                    AppLocale.AZ -> "Dinlədiyiniz surələr burada görünəcək."
                                    AppLocale.TR -> "Dinlediğiniz sureler burada görünecek."
                                    AppLocale.AR -> "ستظهر السور التي استمعت إليها هنا."
                                    else -> "Surahs you listen to will appear here."
                                }
                            )
                        }
                    }
                    items(history, key = { it.surah }) { h ->
                        val surah = repo.getSurah(h.surah)
                        GlassCard(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.navigate(Route.Reader(h.surah)) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = Gold)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "${surah?.number ?: h.surah}. ${surah?.transliteratedName ?: ""}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "${strings.recitation.verse} ${(h.ayah + 1).coerceAtLeast(1)} · ${formatTime((h.positionMs.coerceAtLeast(0L)) / 1000)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (locale) {
                                        AppLocale.AZ -> "Tarixçəni təmizlə"
                                        AppLocale.TR -> "Geçmişi temizle"
                                        AppLocale.AR -> "مسح السجل"
                                        else -> "Clear history"
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch { container.database.history().clear() }
                                        }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
