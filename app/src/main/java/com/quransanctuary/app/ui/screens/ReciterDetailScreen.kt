package com.quransanctuary.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.components.AmbientBackground
import com.quransanctuary.app.ui.components.GoldButton
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.GoldSoft

/** Reciter biography sheet — website card bio expanded with full bioLong text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterDetailScreen(vm: AppViewModel, nav: NavController, reciterId: String) {
    val repo = vm.container.repository
    val engine = vm.container.audioEngine
    val strings = LocalStrings.current
    val locale by vm.locale.collectAsState()
    val player by vm.player.collectAsState()
    val reciter = repo.getReciter(reciterId)

    if (reciter == null) {
        nav.popBackStack()
        return
    }
    val ambient = repo.getAmbient(player.ambientId)
    AmbientBackground(ambient = ambient) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(reciter.localizedName(locale.id)) },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(0.7f)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("file:///android_asset/reciters/${reciter.assetFileName()}")
                            .crossfade(true)
                            .build(),
                        contentDescription = reciter.localizedName(locale.id),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = reciter.arabicName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = reciter.localizedName(locale.id),
                    style = MaterialTheme.typography.titleLarge,
                    color = GoldSoft,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = reciter.localizedOrigin(locale.id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = reciter.localizedBioLong(locale.id),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                GoldButton(
                    text = strings.reciters.listen,
                    onClick = {
                        engine.playSurah(player.surahId, reciter.id, autoplay = true)
                        nav.navigate(Route.Reader(player.surahId))
                    },
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
