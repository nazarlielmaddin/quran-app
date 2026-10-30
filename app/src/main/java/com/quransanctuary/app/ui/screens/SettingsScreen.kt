package com.quransanctuary.app.ui.screens

import android.media.MediaPlayer
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.quransanctuary.app.data.azan.AzanPreferences
import com.quransanctuary.app.data.model.AppLocale
import com.quransanctuary.app.data.model.DisplayMode
import com.quransanctuary.app.data.model.ThemeMode
import com.quransanctuary.app.data.prayer.PrayerNotificationScheduler
import com.quransanctuary.app.domain.Prayer
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.AmbientBackground
import com.quransanctuary.app.ui.components.GlassCard
import com.quransanctuary.app.ui.components.PlayerBarOrNothing
import com.quransanctuary.app.ui.components.SanctuaryScaffold
import com.quransanctuary.app.ui.components.SanctuaryTopBar
import com.quransanctuary.app.ui.components.SettingSwitch
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun settingsExtraLabels(locale: AppLocale): Map<String, String> = when (locale) {
    AppLocale.AZ -> mapOf(
        "language" to "Dil", "theme" to "Tema", "dark" to "Qaranlıq", "light" to "Açıq",
        "system" to "Sistem", "reading" to "Oxunuş", "displayMode" to "Görünüş rejimi",
        "arabicSize" to "Ərəb mətni ölçüsü",
        "playback" to "Oxutma", "storage" to "Yaddaş", "offlineSize" to "Oflayn fayllar",
        "clearOffline" to "Oflayn faylları sil", "about" to "Haqqında",
        "azan" to "Azan", "azanEnable" to "Azan səsini aktivləşdir",
        "azanFajr" to "Sübh və Təhəccüd", "azanFajrSound" to "Sübh azanı",
        "azanOther" to "Digər namazlar", "azanMakkah" to "Məkkə azanı",
        "azanMadinah" to "Mədinə azanı", "azanPreview" to "Dinlə", "azanStop" to "Dayandır",
        "azanTimings" to "Vaxtlar üzrə",
        "azanHint" to "Azan namaz vaxtı başlayanda oxunur.",
        "version" to "Versiya 1.0.0 · Quran, Namaz və Hüzur."
    )
    AppLocale.TR -> mapOf(
        "language" to "Dil", "theme" to "Tema", "dark" to "Koyu", "light" to "Açık",
        "system" to "Sistem", "reading" to "Okuma", "displayMode" to "Görünüm modu",
        "arabicSize" to "Arapça metin boyutu",
        "playback" to "Oynatma", "storage" to "Depolama", "offlineSize" to "Çevrimdışı dosyalar",
        "clearOffline" to "Çevrimdışı dosyaları sil", "about" to "Hakkında",
        "azan" to "Ezan", "azanEnable" to "Ezan sesini etkinleştir",
        "azanFajr" to "Sabah ve Teheccüd", "azanFajrSound" to "Sabah ezanı",
        "azanOther" to "Diğer namazlar", "azanMakkah" to "Mekke ezanı",
        "azanMadinah" to "Medine ezanı", "azanPreview" to "Dinle", "azanStop" to "Durdur",
        "azanTimings" to "Vakitlere göre",
        "azanHint" to "Ezan namaz vakti başladığında okunur.",
        "version" to "Sürüm 1.0.0 · Kur'an. Huzur. Odak."
    )
    AppLocale.AR -> mapOf(
        "language" to "اللغة", "theme" to "المظهر", "dark" to "داكن", "light" to "فاتح",
        "system" to "النظام", "reading" to "القراءة", "displayMode" to "وضع العرض",
        "arabicSize" to "حجم النص العربي",
        "playback" to "التشغيل", "storage" to "التخزين", "offlineSize" to "ملفات دون اتصال",
        "clearOffline" to "حذف الملفات", "about" to "حول",
        "azan" to "الأذان", "azanEnable" to "تفعيل صوت الأذان",
        "azanFajr" to "الفجر والتهجد", "azanFajrSound" to "أذان الفجر",
        "azanOther" to "الصلوات الأخرى", "azanMakkah" to "أذان مكة",
        "azanMadinah" to "أذان المدينة", "azanPreview" to "استمع", "azanStop" to "إيقاف",
        "azanTimings" to "حسب الوقت",
        "azanHint" to "يُشغَّل الأذان عند دخول وقت الصلاة.",
        "version" to "الإصدار 1.0.0 · قرآن. سلام. تركيز."
    )
    else -> mapOf(
        "language" to "Language", "theme" to "Theme", "dark" to "Dark", "light" to "Light",
        "system" to "System", "reading" to "Reading", "displayMode" to "Display mode",
        "arabicSize" to "Arabic text size",
        "playback" to "Playback", "storage" to "Storage", "offlineSize" to "Offline files",
        "clearOffline" to "Delete offline files", "about" to "About",
        "azan" to "Azan", "azanEnable" to "Enable azan sound",
        "azanFajr" to "Fajr and Tahajjud", "azanFajrSound" to "Fajr azan",
        "azanOther" to "Other prayers", "azanMakkah" to "Makkah azan",
        "azanMadinah" to "Madinah azan", "azanPreview" to "Preview", "azanStop" to "Stop",
        "azanTimings" to "By prayer time",
        "azanHint" to "The azan plays when the prayer time begins.",
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
    val prayerStrings = androidx.compose.runtime.remember(locale) {
        com.quransanctuary.app.ui.strings.redesignStrings(locale)
    }
    val context = LocalContext.current

    var azanEnabled by remember { mutableStateOf(AzanPreferences.isEnabled(context)) }
    var regularAzan by remember { mutableStateOf(AzanPreferences.regularSound(context)) }
    var azanPrayers by remember {
        mutableStateOf(
            Prayer.daily.associateWith { AzanPreferences.isPrayerEnabled(context, it) }
        )
    }
    var tahajjudAzan by remember { mutableStateOf(AzanPreferences.isTahajjudEnabled(context)) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewSound by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { runCatching { previewPlayer?.release() } }
    }

    fun rescheduleAzanAlarms() {
        scope.launch(Dispatchers.IO) { PrayerNotificationScheduler.rescheduleAll(context) }
    }

    fun stopPreview() {
        val player = previewPlayer
        previewPlayer = null
        previewSound = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
    }

    fun previewAzan(sound: String) {
        if (previewSound == sound) {
            stopPreview()
            return
        }
        stopPreview()
        val mp = runCatching {
            MediaPlayer.create(context, AzanPreferences.resId(sound))
        }.getOrNull() ?: return
        mp.setOnCompletionListener { finished ->
            if (previewPlayer === finished) {
                previewPlayer = null
                previewSound = null
            }
            runCatching { finished.release() }
        }
        mp.setOnErrorListener { failed, _, _ ->
            if (previewPlayer === failed) {
                previewPlayer = null
                previewSound = null
            }
            runCatching { failed.release() }
            true
        }
        previewPlayer = mp
        previewSound = sound
        runCatching { mp.start() }.onFailure { stopPreview() }
    }

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

            SettingsCard(title = L["azan"]!!) {
                SettingSwitch(
                    checked = azanEnabled,
                    onChange = { enabled ->
                        azanEnabled = enabled
                        AzanPreferences.setEnabled(context, enabled)
                        if (!enabled) stopPreview()
                        rescheduleAzanAlarms()
                    },
                    label = L["azanEnable"]!!
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L["azanTimings"]!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(modifier = Modifier.alpha(if (azanEnabled) 1f else 0.45f)) {
                    Prayer.daily.forEach { prayer ->
                        SettingSwitch(
                            checked = azanPrayers[prayer] == true,
                            onChange = { enabled ->
                                if (azanEnabled) {
                                    azanPrayers = azanPrayers.toMutableMap().apply {
                                        put(prayer, enabled)
                                    }
                                    AzanPreferences.setPrayerEnabled(context, prayer, enabled)
                                    rescheduleAzanAlarms()
                                }
                            },
                            label = prayerStrings.prayerName(prayer)
                        )
                    }
                    SettingSwitch(
                        checked = tahajjudAzan,
                        onChange = { enabled ->
                            if (azanEnabled) {
                                tahajjudAzan = enabled
                                AzanPreferences.setTahajjudEnabled(context, enabled)
                                rescheduleAzanAlarms()
                            }
                        },
                        label = prayerStrings.prayerTahajjud
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${L["azanFajr"]!!} · ${L["azanFajrSound"]!!}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { previewAzan(AzanPreferences.SOUND_FAJR) }) {
                    Text(
                        if (previewSound == AzanPreferences.SOUND_FAJR) {
                            L["azanStop"]!!
                        } else {
                            L["azanPreview"]!!
                        },
                        color = Gold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    L["azanOther"]!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    listOf(
                        AzanPreferences.SOUND_MAKKAH to L["azanMakkah"]!!,
                        AzanPreferences.SOUND_MADINAH to L["azanMadinah"]!!
                    ).forEach { (sound, label) ->
                        FilterChip(
                            selected = regularAzan == sound,
                            onClick = {
                                if (regularAzan != sound) {
                                    stopPreview()
                                    regularAzan = sound
                                    AzanPreferences.setRegularSound(context, sound)
                                    rescheduleAzanAlarms()
                                }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.25f),
                                selectedLabelColor = GoldSoft
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                TextButton(onClick = { previewAzan(regularAzan) }) {
                    Text(
                        if (previewSound == regularAzan) {
                            L["azanStop"]!!
                        } else {
                            L["azanPreview"]!!
                        },
                        color = Gold
                    )
                }
                Text(
                    L["azanHint"]!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
