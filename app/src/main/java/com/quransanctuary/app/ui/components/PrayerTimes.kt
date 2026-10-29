package com.quransanctuary.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransanctuary.app.data.prayer.PrayerCities
import com.quransanctuary.app.data.prayer.PrayerCity
import com.quransanctuary.app.data.prayer.PrayerDay
import com.quransanctuary.app.domain.NextPrayer
import com.quransanctuary.app.domain.Prayer
import com.quransanctuary.app.domain.nextPrayer
import com.quransanctuary.app.ui.strings.RedesignStrings
import com.quransanctuary.app.ui.theme.Gold
import com.quransanctuary.app.ui.theme.GoldSoft
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun LocalTime.clock(): String = format(clockFormat)

private fun remainingText(
    now: LocalDateTime,
    next: NextPrayer,
    strings: RedesignStrings
): String {
    var target = LocalDateTime.of(now.toLocalDate(), next.time)
    if (next.tomorrow || target.isBefore(now)) target = target.plusDays(1)
    val minutes = Duration.between(now, target).toMinutes().coerceAtLeast(0L)
    return strings.remaining(minutes / 60, minutes % 60)
}

/**
 * Compact prayer-times card: the next prayer is the hero, all five times stay
 * visible in a single quiet row. Tap the card for the full schedule.
 */
@Composable
fun PrayerTimesCard(
    day: PrayerDay?,
    cityId: String,
    cityLabel: String,
    now: LocalDateTime,
    strings: RedesignStrings,
    onCityClick: () -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val next = day?.let {
        nextPrayer(it.times, now.toLocalTime(), it.tomorrowFajr)
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
        onClick = onOpenSchedule
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = strings.prayerTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Gold
                    )
                    Spacer(Modifier.size(7.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onCityClick)
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (cityId == "gps") Icons.Filled.MyLocation
                            else Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = GoldSoft,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = cityLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = strings.prayerCityPicker,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(onClick = onOpenSchedule) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = strings.prayerScheduleTitle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.size(10.dp))

            if (day == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Gold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.prayerLoading,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (next == null) {
                Text(
                    text = strings.prayerUnavailable,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = strings.prayerNext,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.prayerName(next.prayer),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = GoldSoft
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = next.time.clock(),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (next.tomorrow) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = strings.prayerTomorrow,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Gold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Gold.copy(alpha = 0.28f))
                    ) {
                        Text(
                            text = remainingText(now, next, strings),
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldSoft,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.size(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Prayer.daily.forEach { prayer ->
                        val time = day.times[prayer] ?: return@forEach
                        val highlighted = next.prayer == prayer && !next.tomorrow
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(4.dp),
                                shape = CircleShape,
                                color = if (highlighted) Gold else Color.Transparent
                            ) {}
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = strings.prayerName(prayer),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (highlighted) GoldSoft
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                text = time.clock(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (highlighted) FontWeight.SemiBold
                                    else FontWeight.Normal
                                ),
                                color = if (highlighted) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                day.tahajjud?.let { tahajjud ->
                    Spacer(Modifier.size(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    Spacer(Modifier.size(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(Gold.copy(alpha = 0.75f), CircleShape)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = strings.prayerTahajjud,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = tahajjud.clock(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = GoldSoft,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScheduleSheet(
    day: PrayerDay?,
    cityLabel: String,
    dateLabel: String,
    now: LocalDateTime,
    strings: RedesignStrings,
    onDismiss: () -> Unit,
    onChangeCity: () -> Unit
) {
    val next = day?.let { nextPrayer(it.times, now.toLocalTime(), it.tomorrowFajr) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = strings.prayerScheduleTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = "$cityLabel · $dateLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    day?.let {
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = strings.sourceLabel(it.source),
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldSoft.copy(alpha = 0.85f)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = strings.prayerClose,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(12.dp))

            if (day == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Gold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.prayerLoading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Prayer.entries.forEachIndexed { index, prayer ->
                    val time = day.times[prayer] ?: return@forEachIndexed
                    val highlighted = next != null &&
                        next.prayer == prayer &&
                        !next.tomorrow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.prayerName(prayer),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (highlighted) GoldSoft
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = time.clock(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (highlighted) GoldSoft
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (highlighted) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (index < Prayer.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }

                day.tahajjud?.let { tahajjud ->
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = strings.prayerTahajjud,
                                style = MaterialTheme.typography.bodyLarge,
                                color = GoldSoft
                            )
                            Text(
                                text = strings.prayerTahajjudHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = tahajjud.clock(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = GoldSoft
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            OutlinedButton(
                onClick = onChangeCity,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.prayerChangeCity)
            }
        }
    }
}

@Composable
fun CityPickerDialog(
    cityId: String,
    strings: RedesignStrings,
    onDismiss: () -> Unit,
    onSelectCity: (PrayerCity) -> Unit,
    onUseLocation: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = strings.prayerCityPicker,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onUseLocation),
                    shape = RoundedCornerShape(14.dp),
                    color = Gold.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings.prayerUseLocation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoldSoft
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(PrayerCities, key = { it.id }) { city ->
                        val selected = city.id == cityId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectCity(city) }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = city.localizedName(strings.locale.id),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) GoldSoft
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.prayerClose, color = GoldSoft)
            }
        }
    )
}
