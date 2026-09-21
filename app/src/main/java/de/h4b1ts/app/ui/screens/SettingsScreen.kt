package de.h4b1ts.app.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.data.SettingsRepository
import de.h4b1ts.app.ui.components.H4Card
import de.h4b1ts.app.ui.components.SectionTitle
import de.h4b1ts.app.ui.theme.AccentMode
import de.h4b1ts.app.ui.theme.H4Colors
import de.h4b1ts.app.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    accent: AccentMode,
    theme: ThemeMode,
    onAccentChange: (AccentMode) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onRedoScorecard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = theme.isDark()
    var cooldown by remember { mutableStateOf(SettingsRepository.cooldownMinutes) }
    var pendingCooldown by remember { mutableStateOf(SettingsRepository.pendingCooldownMinutes) }
    @Suppress("UNUSED_VARIABLE")
    var instant by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Appearance", icon = H4Icon.GEAR) }

        item {
            H4Card {
                Label("Theme")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { candidate ->
                        Choice(
                            label = candidate.label,
                            selected = candidate == theme,
                            onClick = { onThemeChange(candidate) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            H4Card {
                Label("Accent")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Colour marks what you earned. Everything else stays monochrome.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                AccentMode.entries.forEach { candidate ->
                    AccentRow(
                        mode = candidate,
                        dark = dark,
                        selected = candidate == accent,
                        onClick = { onAccentChange(candidate) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (dark) {
                        "Full neon on the dark ground."
                    } else {
                        "Darkened on white: no neon reaches readable contrast there."
                    },
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
            }
        }

        item {
            H4Card {
                Label("The lock screen")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Always pure black, in both themes. Black is not a surface " +
                        "here, it is the blocked state — and a blocked state should " +
                        "have nothing to look at.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(H4Colors.Void, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "H4B1TS",
                        color = H4Colors.Mid,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        item {
            H4Card {
                Label("Cooling-off")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Blocking something takes effect at once. Unblocking it, or " +
                        "ending a focus session early, waits this long first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20, 60).forEach { minutes ->
                        Choice(
                            label = "$minutes",
                            selected = minutes == cooldown,
                            onClick = {
                                instant = SettingsRepository.setCooldownMinutes(minutes)
                                cooldown = SettingsRepository.cooldownMinutes
                                pendingCooldown = SettingsRepository.pendingCooldownMinutes
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                pendingCooldown?.let { target ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        // Lowering it is a loosening like any other, or the whole
                        // scheme would be one tap deep: set the wait to zero,
                        // then unblock everything.
                        text = "Shortening to $target minutes is itself waiting out the " +
                            "current $cooldown. Tap the current value to call it off.",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        item {
            H4Card {
                Label("Habit scorecard")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Take stock of what you do on a normal day and mark each line. " +
                        "Worth repeating — what serves you changes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .clickable(onClick = onRedoScorecard)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Run it again",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "H4b1ts 0.4.0",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AccentRow(
    mode: AccentMode,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).background(mode.accentFor(dark), CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            text = mode.label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        // Selection is carried by the word, not only by the ring.
        Text(
            text = if (selected) "in use" else "",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
    }
}
