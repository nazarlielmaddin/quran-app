package com.quransanctuary.app.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft
import kotlinx.coroutines.delay

/** Website Hero replica: staggered entrance, title, subtitle, Anfal quote, dual CTA, credit, scroll cue. */
@Composable
fun HeroSection(
    onStartListening: () -> Unit,
    onExploreSurahs: () -> Unit,
    onScrollCue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        visible = true
    }

    @Composable
    fun Stagger(index: Int, content: @Composable () -> Unit) {
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(900, delayMillis = index * 160, easing = EaseOutCubic),
            label = "hero$index"
        )
        val dy by animateFloatAsState(
            targetValue = if (visible) 0f else 28f,
            animationSpec = tween(900, delayMillis = index * 160, easing = EaseOutCubic),
            label = "heroDy$index"
        )
        Column(
            modifier = Modifier
                .graphicsLayer { translationY = dy }
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Stagger(0) {
            Text(
                text = "${strings.hero.titleA}, ${strings.hero.titleB}…",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
        Stagger(1) {
            Text(
                text = strings.hero.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Stagger(2) {
            Text(
                text = "\u201C ${strings.hero.anfalAyah} \u201D",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = Gold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.hero.anfalCite,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(28.dp))
        Stagger(3) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GoldButton(text = strings.hero.cta, onClick = onStartListening, modifier = Modifier.fillMaxWidth(0.75f))
                Spacer(Modifier.height(12.dp))
                GhostButton(text = strings.hero.secondary, onClick = onExploreSurahs, modifier = Modifier.fillMaxWidth(0.75f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Stagger(4) {
            Text(
                text = buildString {
                    append(strings.developedBy)
                    append(" ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Elmaddin Nazarli",
                style = MaterialTheme.typography.bodySmall,
                color = GoldSoft,
                modifier = Modifier.clickable {
                    runCatching { uriHandler.openUri("https://www.linkedin.com/in/elinzrv/") }
                }
            )
        }
        Spacer(Modifier.height(36.dp))
        Stagger(5) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onScrollCue() }
            ) {
                Text(
                    text = strings.hero.scroll.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
                BounceArrow()
            }
        }
    }
}

@Composable
private fun BounceArrow() {
    var up by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            up = !up
        }
    }
    val dy by animateFloatAsState(
        targetValue = if (up) -8f else 6f,
        animationSpec = tween(700, easing = EaseOutCubic),
        label = "bounce"
    )
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .graphicsLayer { translationY = dy }
            .size(20.dp)
    )
}
