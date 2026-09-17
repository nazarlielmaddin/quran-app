package com.quransanctuary.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.quransanctuary.app.data.model.AppLocale
import com.quransanctuary.app.data.model.DisplayMode
import com.quransanctuary.app.data.model.ThemeMode
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.AmbientBackground
import com.quransanctuary.app.ui.components.GlassCard
import com.quransanctuary.app.ui.components.SanctuaryScaffold
import com.quransanctuary.app.ui.components.SanctuaryTopBar
import com.quransanctuary.app.ui.components.SettingSwitch
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft
import kotlinx.coroutines.launch

private fun settingsExtraLabels(locale: AppLocale): Map<String, String> = when (locale) {
    AppLocale.AZ -> mapOf(
        "language" to "Dil", "theme" to "Tema", "dark" to "Qaranlıq", "light" to "Açıq",
        "system" to "Sistem", "reading" to "Oxunuş", "displayMode" to "Görünüş rejimi",
        "arabicSize" to "Ərəb mətni ölçüsü",
        "playback" to "Oxutma", "storage" to "Yaddaş", "offlineSize" to "Oflayn fayllar",
        "clearOffline" to "Oflayn faylları sil", "about" to "Haqqında",
        "version" to "Versiya 1.0.0 · Quran. Hüzur. Diqqət."
    )
    AppLocale.TR -> mapOf(
        "language" to "Dil", "theme" to "Tema", "dark" to "Koyu", "light" to "Açık",
        "system" to "Sistem", "reading" to "Okuma", "displayMode" to "Görünüm modu",
        "arabicSize" to "Arapça metin boyutu",
        "playback" to "Oynatma", "storage" to "Depolama", "offlineSize" to "Çevrimdışı dosyalar",
        "clearOffline" to "Çevrimdışı dosyaları sil", "about" to "Hakkında",
        "version" to "Sürüm 1.0.0 · Kur'an. Huzur. Odak."
    )
    AppLocale.AR -> mapOf(
        "language" to "اللغة", "theme" to "المظهر", "dark" to "داكن", "light" to "فاتح",
        "system" to "النظام", "reading" to "القراءة", "displayMode" to "وضع العرض",
        "arabicSize" to "حجم النص العربي",
        "playback" to "التشغيل", "storage" to "التخزين", "offlineSize" to "ملفات دون اتصال",
        "clearOffline" to "حذف الملفات", "about" to "حول",
        "version" to "الإصدار 1.0.0 · قرآن. سلام. تركيز."
    )
    else -> mapOf(
        "language" to "Language", "theme" to "Theme", "dark" to "Dark", "light" to "Light",
        "system" to "System", "reading" to "Reading", "displayMode" to "Display mode",
        "arabicSize" to "Arabic text size",
        "playback" to "Playback", "storage" to "Storage", "offlineSize" to "Offline files",
        "clearOffline" to "Delete offline files", "about" to "About",
        "version" to "Version 1.0.0 · Qur'an. Peace. Focus."
    )
}

/** Website settings popover expanded into a full native settings screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val container = vm.container
    val repo = container.repository
    val engine = container.audioEngine
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val theme by vm.theme.collectAsState()
    val player by vm.player.collectAsState()
    val displayMode by vm.displayMode.collectAsState()
    val fontScale by vm.arabicFontScale.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val L = settingsExtraLabels(locale)
    val tabs = tabLabels(locale)

    val offlineBytes = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        offlineBytes.longValue = container.downloader.downloadsDirSize()
    }

    SanctuaryScaffold(
        currentTab = "settings",
        onTab = { t ->
            when (t) {
                "home" -> nav.navigate(Route.Home)
                "reader" -> nav.navigate(Route.Reader(player.surahId))
                "library" -> nav.navigate(Route.Library)
            }
        },
        ambient = repo.getAmbient(player.ambientId),
        homeLabel = tabs.getOrElse(0) { "" }, readerLabel = tabs.getOrElse(1) { "" }, libraryLabel = tabs.getOrElse(2) { "" }, settingsLabel = tabs.getOrElse(3) { "" },
        topBar = { SanctuaryTopBar(onBrandClick = {}) },
        // Player stays out of this screen: navigation panel only, so the
        // mini player can never overlap the bottom nav.
        bottomBar = {}
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.settings.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            SettingsCard(title = L["language"]!!) {
                Row {
                    listOf(AppLocale.AZ to "AZ", AppLocale.EN to "EN", AppLocale.TR to "TR", AppLocale.AR to "AR").forEach { (loc, short) ->
                        FilterChip(
                            selected = locale == loc,
                            onClick = { vm.setLocale(loc) },
                            label = { Text(short) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.25f),
                                selectedLabelColor = GoldSoft
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            SettingsCard(title = L["theme"]!!) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (theme == ThemeMode.LIGHT) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = null,
                        tint = Gold
                    )
                    Spacer(Modifier.width(8.dp))
                    listOf(
                        ThemeMode.DARK to L["dark"]!!,
                        ThemeMode.LIGHT to L["light"]!!,
                        ThemeMode.SYSTEM to L["system"]!!
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = theme == mode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.25f),
                                selectedLabelColor = GoldSoft
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            SettingsCard(title = L["reading"]!!) {
                Text(L["displayMode"]!!, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row {
                    listOf(
                        DisplayMode.TRANSLITERATION to strings.recitation.displayModes.transliteration,
                        DisplayMode.ARABIC to strings.recitation.displayModes.arabic,
                        DisplayMode.BOTH to strings.recitation.displayModes.both
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { vm.setDisplayMode(mode) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.25f),
                                selectedLabelColor = GoldSoft
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("${L["arabicSize"]!!} · ${(fontScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = fontScale,
                    onValueChange = { vm.setArabicFontScale(it) },
                    valueRange = 0.8f..1.6f,
                    steps = 7,
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
                )
            }

            SettingsCard(title = L["playback"]!!) {
                SettingSwitch(
                    checked = player.autoNext,
                    onChange = { engine.setAutoNext(it) },
                    label = strings.player.autoNext
                )
                SettingSwitch(
                    checked = player.loopAmbient,
                    onChange = { engine.setLoopAmbient(it) },
                    label = strings.player.loopAmbient
                )
                SettingSwitch(
                    checked = player.quranEnabled,
                    onChange = { engine.setQuranEnabled(it) },
                    label = strings.player.quran
                )
            }

            SettingsCard(title = L["storage"]!!) {
                Text(
                    "${L["offlineSize"]!!}: ${offlineBytes.longValue / 1024} KB",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        scope.launch {
                            container.downloader.clearAll()
                            offlineBytes.longValue = 0L
                        }
                    }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(L["clearOffline"]!!, color = MaterialTheme.colorScheme.error)
                }
            }

            SettingsCard(title = L["about"]!!) {
                Text(
                    "© 2026 ${strings.brand} · ${strings.footer.tagline}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    strings.footer.madeWith,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${strings.developedBy} Elmaddin Nazarli",
                    color = GoldSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching { uriHandler.openUri("https://www.linkedin.com/in/elinzrv/") }
                        }
                )
                Spacer(Modifier.height(4.dp))
                Text(L["version"]!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.SettingsSuggest, contentDescription = null, tint = Gold)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = GoldSoft)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
