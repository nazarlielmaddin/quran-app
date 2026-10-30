package com.quransanctuary.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import com.quransanctuary.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.quransanctuary.app.data.model.AppLocale
import com.quransanctuary.app.domain.calculatePrayerTimes
import com.quransanctuary.app.data.prayer.DefaultPrayerCity
import com.quransanctuary.app.data.prayer.DeviceLocation
import com.quransanctuary.app.data.prayer.PrayerDay
import com.quransanctuary.app.data.prayer.PrayerNotificationScheduler
import com.quransanctuary.app.data.prayer.PrayerScheduleCache
import com.quransanctuary.app.data.prayer.PrayerSource
import com.quransanctuary.app.data.prayer.prayerCityById
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.CityPickerDialog
import com.quransanctuary.app.ui.components.ContinueListeningCard
import com.quransanctuary.app.ui.components.EmptyFavoritesCard
import com.quransanctuary.app.ui.components.EmptyRecentlyPlayedCard
import com.quransanctuary.app.ui.components.ExploreSurahsCard
import com.quransanctuary.app.ui.components.FavoriteRecitersRow
import com.quransanctuary.app.ui.components.GlassCard
import com.quransanctuary.app.ui.components.HomeSectionHeader
import com.quransanctuary.app.ui.components.PlayerBarOrNothing
import com.quransanctuary.app.ui.components.PrayerScheduleSheet
import com.quransanctuary.app.ui.components.PrayerTimesCard
import com.quransanctuary.app.ui.components.RecentListenItem
import com.quransanctuary.app.ui.components.RecentlyPlayedRow
import com.quransanctuary.app.ui.components.SanctuaryBackdrop
import com.quransanctuary.app.ui.components.SanctuaryBottomNav
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.strings.RedesignStrings
import com.quransanctuary.app.ui.strings.redesignStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

fun tabLabels(locale: AppLocale): Array<String> = when (locale) {
    AppLocale.AZ -> arrayOf("Əsas", "Oxunuş", "Kitabxana", "Parametrlər")
    AppLocale.EN -> arrayOf("Home", "Reader", "Library", "Settings")
    AppLocale.TR -> arrayOf("Ana Sayfa", "Okuma", "Kütüphane", "Ayarlar")
    AppLocale.AR -> arrayOf("الرئيسية", "القراءة", "المكتبة", "الإعدادات")
}

/**
 * Home — a calm, content-first dashboard:
 * greeting → prayer times → continue listening → history → favorites → surahs.
 * No hero/poster composition, no wizard steps. The persistent mini-player and
 * bottom navigation live in the scaffold so audio remains reachable everywhere.
 */
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val container = vm.container
    val repo = container.repository
    val engine = container.audioEngine
    val settings = container.settings
    val appStrings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val strings = remember(locale) { redesignStrings(locale) }
    val player by vm.player.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val history by container.database.history().observeRecent()
        .collectAsState(initial = emptyList())

    val cityId by settings.prayerCityId.collectAsState(initial = DefaultPrayerCity.id)
    val cityLabel by settings.prayerCityLabel.collectAsState(initial = DefaultPrayerCity.nameAz)
    val latitude by settings.prayerLatitude.collectAsState(initial = DefaultPrayerCity.latitude)
    val longitude by settings.prayerLongitude.collectAsState(initial = DefaultPrayerCity.longitude)
    val zoneId by settings.prayerZoneId.collectAsState(initial = DefaultPrayerCity.zoneId)

    val zone = remember(zoneId) {
        runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    }
    val today = LocalDate.now(zone)
    val prayerDay by remember(latitude, longitude, zone, today) {
        container.prayerTimes.observe(latitude, longitude, zone, today)
    }.collectAsState(initial = null)

    LaunchedEffect(prayerDay, latitude, longitude, zone, today) {
        val day = prayerDay ?: return@LaunchedEffect
        PrayerNotificationScheduler.scheduleDay(
            context, day, today, zone, latitude, longitude
        )

        val tomorrowDate = today.plusDays(1)
        // Read before scheduling: scheduleDay persists the day it receives.
        val cachedTomorrow = PrayerScheduleCache.load(
            context, tomorrowDate, zone, latitude, longitude
        )
        val tomorrowDay = cachedTomorrow ?: run {
            val tomorrowTimes = calculatePrayerTimes(tomorrowDate, latitude, longitude, zone)
            val dayAfterFajr = calculatePrayerTimes(
                tomorrowDate.plusDays(1), latitude, longitude, zone
            )[com.quransanctuary.app.domain.Prayer.FAJR]
            PrayerDay(tomorrowTimes, PrayerSource.CALCULATED, dayAfterFajr)
        }
        PrayerNotificationScheduler.scheduleDay(
            context, tomorrowDay, tomorrowDate, zone, latitude, longitude
        )

        // Align tomorrow's alarms with the same API values the UI will show,
        // so the reminder is not a couple of minutes early or late.
        if (day.source == PrayerSource.API && cachedTomorrow?.source != PrayerSource.API) {
            val remoteTomorrow = container.prayerTimes.fetchDay(
                latitude, longitude, zone, tomorrowDate
            )
            if (remoteTomorrow != null) {
                PrayerNotificationScheduler.scheduleDay(
                    context, remoteTomorrow, tomorrowDate, zone, latitude, longitude
                )
            }
        }
    }

    var now by remember { mutableStateOf(LocalDateTime.now(zone)) }
    LaunchedEffect(zone) {
        now = LocalDateTime.now(zone)
        while (true) {
            delay(15_000)
            now = LocalDateTime.now(zone)
        }
    }

    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var showCityPicker by rememberSaveable { mutableStateOf(false) }

    val displayCity = if (cityId == "gps") strings.prayerDeviceLocation
    else prayerCityById(cityId).localizedName(locale.id).ifBlank { cityLabel }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it } || DeviceLocation.hasPermission(context)
        if (!granted) {
            scope.launch { snackbar.showSnackbar(strings.prayerLocationDenied) }
        } else {
            val location = DeviceLocation.lastKnown(context)
            if (location == null) {
                scope.launch { snackbar.showSnackbar(strings.prayerLocationFailed) }
                showCityPicker = true
            } else {
                scope.launch {
                    settings.setPrayerLocation(
                        id = "gps",
                        label = strings.prayerDeviceLocation,
                        latitude = location.first,
                        longitude = location.second,
                        zoneId = ZoneId.systemDefault().id
                    )
                    showCityPicker = false
                }
            }
        }
    }

    val requestDeviceLocation: () -> Unit = {
        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    player.error?.let { error ->
        LaunchedEffect(error) {
            snackbar.showSnackbar(
                if (error == "ambient") appStrings.errors.ambient else appStrings.errors.audio
            )
            engine.clearError()
        }
    }

    val tabs = tabLabels(locale)

    val continueHistory = history.firstOrNull()
    val continueSurah = repo.getSurah(continueHistory?.surah ?: player.surahId)
    val continueReciter = repo.getReciter(continueHistory?.reciterId ?: player.reciterId)
        ?: repo.reciters.firstOrNull()
    val continueAyah = (continueHistory?.ayah ?: 0) + 1
    val continuePosition = continueHistory?.positionMs ?: 0L
    val continueDuration = (
        repo.getRecitation(
            continueReciter?.id ?: "",
            continueSurah?.number ?: 0
        )?.durationSeconds ?: 0
        ) * 1000L
    val continueIsCurrent = continueReciter?.id == player.reciterId &&
        continueSurah?.number == player.surahId
    val continueIsPlaying = continueIsCurrent && player.quranPlaying

    val recent = remember(history) {
        history.drop(1).take(8).map {
            RecentListenItem(
                history = it,
                surah = repo.getSurah(it.surah),
                reciter = repo.getReciter(it.reciterId)
            )
        }
    }
    val favoriteReciters = remember(favorites) {
        repo.reciters.filter { it.id in favorites }
    }

    SanctuaryBackdrop(
        ambient = repo.getAmbient(player.ambientId),
        photo = true,
        veil = 0.84f
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    PlayerBarOrNothing(
                        vm = vm,
                        nav = nav,
                        navigationBarsPadding = false
                    )
                    SanctuaryBottomNav(
                        current = "home",
                        onNavigate = { tab ->
                            when (tab) {
                                "home" -> Unit
                                "reader" -> nav.navigate(Route.Reader(player.surahId))
                                "library" -> nav.navigate(Route.Library)
                                "settings" -> nav.navigate(Route.Settings)
                            }
                        },
                        homeLabel = tabs.getOrElse(0) { "" },
                        readerLabel = tabs.getOrElse(1) { "" },
                        libraryLabel = tabs.getOrElse(2) { "" },
                        settingsLabel = tabs.getOrElse(3) { "" }
                    )
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    HomeHeader(
                        strings = strings,
                        dateLabel = strings.dateLabel(today),
                        hijriLabel = strings.hijriDateLabel(today)
                    )
                }

                item {
                    PrayerTimesCard(
                        day = prayerDay,
                        cityId = cityId,
                        cityLabel = displayCity,
                        now = now,
                        strings = strings,
                        onCityClick = { showCityPicker = true },
                        onOpenSchedule = { showSchedule = true }
                    )
                }

                if (!PrayerNotificationScheduler.canScheduleExactAlarms(context)) {
                    item {
                        ExactAlarmNotice(
                            text = strings.exactAlarmNotice,
                            action = strings.exactAlarmAction,
                            onGrant = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val requested = runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    }.isSuccess
                                    if (!requested) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    ContinueListeningCard(
                        surah = continueSurah,
                        reciter = continueReciter,
                        ayah = continueAyah,
                        positionMs = continuePosition,
                        durationMs = continueDuration,
                        isPlaying = continueIsPlaying,
                        firstListen = continueHistory == null,
                        strings = strings,
                        locale = locale.id,
                        onPlay = {
                            if (continueIsCurrent) {
                                engine.toggleQuran()
                            } else {
                                val surah = continueSurah
                                val reciter = continueReciter
                                if (surah != null && reciter != null) {
                                    engine.playSurah(surah.number, reciter.id, autoplay = true)
                                    if (continuePosition > 0L) engine.seekTo(continuePosition)
                                }
                            }
                        }
                    )
                }

                when {
                    history.size > 1 -> item {
                        Column {
                            HomeSectionHeader(title = strings.recentTitle)
                            Spacer(Modifier.height(12.dp))
                            RecentlyPlayedRow(
                                items = recent,
                                strings = strings,
                                locale = locale.id,
                                onPlay = { item ->
                                    engine.playSurah(
                                        item.history.surah,
                                        item.history.reciterId,
                                        autoplay = true
                                    )
                                    if (item.history.positionMs > 0L) {
                                        engine.seekTo(item.history.positionMs)
                                    }
                                    nav.navigate(Route.Reader(item.history.surah))
                                }
                            )
                        }
                    }

                    history.isEmpty() -> item {
                        Column {
                            HomeSectionHeader(title = strings.recentTitle)
                            Spacer(Modifier.height(10.dp))
                            EmptyRecentlyPlayedCard(strings = strings)
                        }
                    }
                }

                item {
                    Column {
                        HomeSectionHeader(
                            title = strings.favoritesTitle,
                            action = if (favoriteReciters.isNotEmpty()) strings.seeAll else null,
                            onAction = {
                                nav.navigate(Route.Reciters)
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        if (favoriteReciters.isEmpty()) {
                            EmptyFavoritesCard(
                                strings = strings,
                                onChoose = { nav.navigate(Route.Reciters) }
                            )
                        } else {
                            FavoriteRecitersRow(
                                reciters = favoriteReciters,
                                selectedId = player.reciterId,
                                locale = locale.id,
                                onOpen = { reciter ->
                                    engine.selectReciter(reciter.id)
                                    nav.navigate(Route.Surahs)
                                }
                            )
                        }
                    }
                }

                item {
                    ExploreSurahsCard(
                        strings = strings,
                        onClick = { nav.navigate(Route.Surahs) }
                    )
                }

                item {
                    Text(
                        text = appStrings.footer.tagline,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    if (showSchedule) {
        PrayerScheduleSheet(
            day = prayerDay,
            cityLabel = displayCity,
            dateLabel = strings.dateLabel(today),
            now = now,
            strings = strings,
            onDismiss = { showSchedule = false },
            onChangeCity = {
                showSchedule = false
                showCityPicker = true
            }
        )
    }

    if (showCityPicker) {
        CityPickerDialog(
            cityId = cityId,
            strings = strings,
            onDismiss = { showCityPicker = false },
            onSelectCity = { city ->
                scope.launch {
                    settings.setPrayerLocation(
                        id = city.id,
                        label = city.nameAz,
                        latitude = city.latitude,
                        longitude = city.longitude,
                        zoneId = city.zoneId
                    )
                    showCityPicker = false
                }
            },
            onUseLocation = requestDeviceLocation
        )
    }
}

@Composable
private fun HomeHeader(
    strings: RedesignStrings,
    dateLabel: String,
    hijriLabel: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings.overline,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Gold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = strings.greeting,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hijriLabel.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = hijriLabel,
                style = MaterialTheme.typography.bodySmall,
                color = GoldSoft.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shown only while Android withholds exact-alarm permission. */
@Composable
private fun ExactAlarmNotice(
    text: String,
    action: String,
    onGrant: () -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = GoldSoft,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onGrant) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold
                )
            }
        }
    }
}
