package com.quransanctuary.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quransanctuary.app.R
import com.quransanctuary.app.data.model.AmbientSound
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft

/** Website header replica: logo + brand, blurred when scrolled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanctuaryTopBar(
    onBrandClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val strings = LocalStrings.current
    CenterAlignedTopAppBar(
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        navigationIcon = {},
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    )
}

@Composable
fun SanctuaryBottomNav(
    current: String,
    onNavigate: (String) -> Unit,
    homeLabel: String,
    readerLabel: String,
    libraryLabel: String,
    settingsLabel: String
) {
    // No bar, no edges: icons + labels float on a soft transparent grounding.
    // A whisper of a gradient only — mist, with no visible boundary.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.22f)
                    )
                )
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            SanctuaryNavItem("home", current == "home", onNavigate, Icons.Filled.Home, homeLabel)
            SanctuaryNavItem("reader", current == "reader", onNavigate, Icons.AutoMirrored.Filled.MenuBook, readerLabel)
            SanctuaryNavItem("library", current == "library", onNavigate, Icons.AutoMirrored.Filled.LibraryBooks, libraryLabel)
            SanctuaryNavItem("settings", current == "settings", onNavigate, Icons.Filled.Settings, settingsLabel)
        }
    }
}

@Composable
private fun RowScope.SanctuaryNavItem(
    route: String,
    selected: Boolean,
    onNavigate: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = { onNavigate(route) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = GoldSoft,
            indicatorColor = Gold.copy(alpha = 0.28f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Website AmbientBackground replica: ambient gradient (or image) washed under a scrim,
 * so the whole app breathes with the selected atmosphere.
 */
@Composable
fun AmbientBackground(
    ambient: AmbientSound?,
    modifier: Modifier = Modifier,
    video: Boolean = true,
    content: @Composable () -> Unit
) {
    // No atmosphere selected: exactly one flat color, no gradients, no glows.
    if (ambient == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        // Website parity: only the selected atmosphere's clip is ever mounted
        // (muted, cropped to fill, repeated). Nothing else paints here — no
        // washes, no photo thumbs, no blobs: single calm color + smooth scrim.
        if (video) {
            val clip = ambient?.videoUrl
            if (clip != null) {
                key(clip) {
                    AmbientVideo(url = clip, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f)
                        )
                    )
                )
        )
        content()
    }
}

/** Shared scaffold: ambient backdrop + top bar + mini-player-aware content + bottom nav. */
@Composable
fun SanctuaryScaffold(
    currentTab: String,
    onTab: (String) -> Unit,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    ambient: AmbientSound? = null,
    homeLabel: String = "",
    readerLabel: String = "",
    libraryLabel: String = "",
    settingsLabel: String = "",
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    AmbientBackground(ambient = ambient) {
        Scaffold(
            topBar = topBar,
            bottomBar = {
                bottomBar()
                SanctuaryBottomNav(
                    current = currentTab,
                    onNavigate = onTab,
                    homeLabel = homeLabel,
                    readerLabel = readerLabel,
                    libraryLabel = libraryLabel,
                    settingsLabel = settingsLabel
                )
            },
            containerColor = Color.Transparent,
            content = content
        )
    }
}
