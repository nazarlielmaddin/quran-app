package com.quransanctuary.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.GlassSearchField
import com.quransanctuary.app.ui.components.PlayerBarOrNothing
import com.quransanctuary.app.ui.components.ReciterChip
import com.quransanctuary.app.ui.components.SanctuaryBackdrop
import com.quransanctuary.app.ui.components.SurahGlassRow
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.strings.redesignStrings
import com.quransanctuary.app.ui.theme.Gold

/**
 * Surah picker: the current reciter is always visible and switchable, so
 * choosing a voice and choosing a surah never hide each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahPickerScreen(vm: AppViewModel, nav: NavController) {
    val repo = vm.container.repository
    val engine = vm.container.audioEngine
    val appStrings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val strings = remember(locale) { redesignStrings(locale) }
    val player by vm.player.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) { repo.searchSurahs(query) }
    val reciter = remember(player.reciterId) { repo.getReciter(player.reciterId) }

    SanctuaryBackdrop(
        ambient = repo.getAmbient(player.ambientId),
        photo = true,
        veil = 0.88f
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = appStrings.brand,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            bottomBar = { PlayerBarOrNothing(vm = vm, nav = nav) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 2.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = strings.surahsScreenTitle,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.surahsScreenSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.currentReciter,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(8.dp))
                            if (reciter != null) {
                                ReciterChip(
                                    reciter = reciter,
                                    locale = locale.id,
                                    onClick = { nav.navigate(Route.Reciters) }
                                )
                            }
                        }
                    }
                }

                item {
                    GlassSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = appStrings.surahs.searchPlaceholder.ifBlank {
                            strings.searchReciters
                        },
                        clearLabel = appStrings.surahs.clear.ifBlank { strings.clearFilters }
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.SearchOff,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = appStrings.surahs.noneFound.ifBlank {
                                    strings.noResultsTitle
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = { query = "" }) {
                                Text(
                                    text = appStrings.surahs.clear.ifBlank {
                                        strings.clearFilters
                                    },
                                    color = Gold
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.number }) { surah ->
                        SurahGlassRow(
                            surah = surah,
                            ayahsLabel = appStrings.surahs.ayahs,
                            meccanLabel = appStrings.surahs.meccan,
                            medinanLabel = appStrings.surahs.medinan,
                            onOpen = { nav.navigate(Route.Reader(surah.number)) },
                            onPlay = {
                                engine.playSurah(
                                    surah.number,
                                    reciter?.id ?: player.reciterId,
                                    autoplay = true
                                )
                                nav.navigate(Route.Reader(surah.number))
                            }
                        )
                    }
                }
            }
        }
    }
}
