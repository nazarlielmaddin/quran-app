package com.quransanctuary.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.quransanctuary.app.data.model.Reciter
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.GlassCard
import com.quransanctuary.app.ui.components.GlassSearchField
import com.quransanctuary.app.ui.components.PlayerBarOrNothing
import com.quransanctuary.app.ui.components.SanctuaryBackdrop
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.strings.RedesignStrings
import com.quransanctuary.app.ui.strings.redesignStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft

/**
 * Reciter discovery: search, favorites filter, one calm list with generous
 * touch targets. Tapping a row selects the reciter and moves to the surah
 * picker; the play button starts the current surah immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecitersScreen(vm: AppViewModel, nav: NavController) {
    val repo = vm.container.repository
    val engine = vm.container.audioEngine
    val appStrings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val strings = remember(locale) { redesignStrings(locale) }
    val player by vm.player.collectAsState()
    val favorites by vm.favorites.collectAsState()

    val reciters = remember { repo.reciters }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }

    val favoriteCount = remember(favorites, reciters) {
        reciters.count { it.id in favorites }
    }
    val filtered = remember(query, favoritesOnly, favorites, reciters) {
        val q = query.trim().lowercase()
        reciters.filter { reciter ->
            val matches = q.isEmpty() || buildString {
                append(reciter.name).append(' ')
                append(reciter.nameAz).append(' ')
                append(reciter.arabicName).append(' ')
                append(reciter.origin).append(' ')
                append(reciter.originAz)
            }.lowercase().contains(q)
            matches && (!favoritesOnly || reciter.id in favorites)
        }
    }

    fun choose(reciter: Reciter) {
        engine.selectReciter(reciter.id)
        nav.navigate(Route.Surahs) {
            popUpTo(nav.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
    }

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = strings.recitersTitle,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.recitersSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    GlassSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = strings.searchReciters,
                        clearLabel = strings.clearFilters
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !favoritesOnly,
                            onClick = { favoritesOnly = false },
                            label = {
                                Text(
                                    text = "${strings.filterAll} · ${reciters.size}",
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.18f),
                                selectedLabelColor = GoldSoft,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (favoritesOnly) Icons.Filled.Favorite
                                    else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${strings.filterFavorites} · $favoriteCount",
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold.copy(alpha = 0.18f),
                                selectedLabelColor = GoldSoft,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        RecitersEmptyState(
                            favoritesOnly = favoritesOnly,
                            strings = strings,
                            onClear = {
                                query = ""
                                favoritesOnly = false
                            }
                        )
                    }
                } else {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column {
                                filtered.forEachIndexed { index, reciter ->
                                    ReciterSelectRow(
                                        reciter = reciter,
                                        locale = locale.id,
                                        selected = reciter.id == player.reciterId,
                                        playing = reciter.id == player.reciterId &&
                                            player.quranPlaying,
                                        favorite = reciter.id in favorites,
                                        strings = strings,
                                        onSelect = { choose(reciter) },
                                        onFavorite = { vm.toggleFavorite(reciter.id) },
                                        onPlay = {
                                            if (reciter.id == player.reciterId &&
                                                player.quranPlaying
                                            ) {
                                                engine.toggleQuran()
                                            } else {
                                                engine.playSurah(
                                                    player.surahId,
                                                    reciter.id,
                                                    autoplay = true
                                                )
                                                nav.navigate(Route.Reader(player.surahId))
                                            }
                                        },
                                        onBio = { nav.navigate(Route.ReciterDetail(reciter.id)) }
                                    )
                                    if (index < filtered.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                                .copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun ReciterSelectRow(
    reciter: Reciter,
    locale: String,
    selected: Boolean,
    playing: Boolean,
    favorite: Boolean,
    strings: RedesignStrings,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
    onBio: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
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
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Gold else Gold.copy(alpha = 0.30f),
                        shape = CircleShape
                    )
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(Gold)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = strings.selectedLabel,
                        tint = Color(0xFF17120A),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reciter.localizedName(locale),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (selected) GoldSoft else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reciter.localizedOrigin(locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (reciter.localizedOrigin(locale).isNotBlank()) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = strings.bio,
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldSoft,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBio)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Favorite
                else Icons.Filled.FavoriteBorder,
                contentDescription = strings.filterFavorites,
                tint = if (favorite) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (playing) Gold
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .border(
                    1.dp,
                    if (playing) Gold else MaterialTheme.colorScheme.outlineVariant
                        .copy(alpha = 0.32f),
                    CircleShape
                )
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = strings.listen,
                tint = if (playing) Color(0xFF17120A)
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun RecitersEmptyState(
    favoritesOnly: Boolean,
    strings: RedesignStrings,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 42.dp, horizontal = 20.dp),
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
                imageVector = if (favoritesOnly) Icons.Filled.FavoriteBorder
                else Icons.Filled.SearchOff,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (favoritesOnly) strings.noFavoritesTitle else strings.noResultsTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (favoritesOnly) strings.noFavoritesText else strings.noResultsText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onClear) {
            Text(strings.clearFilters, color = GoldSoft)
        }
    }
}
