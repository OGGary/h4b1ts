package de.h4b1ts.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, icon: H4Icon? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            // Decorative: the title beside it already says the same word, and a
            // screen reader announcing it twice is noise.
            PixelIconDecoration(icon = icon, size = 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
    )
}

@Composable
fun H4Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/**
 * Small outlined label. Colour never carries the meaning on its own — the text
 * inside always says the same thing.
 */
@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusCard(
    title: String,
    subtitle: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    H4Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Repeats what the subtitle already says; colour alone never carries
            // state (WCAG 1.4.1). The thumb adds a second non-colour channel, so
            // the state survives greyscale and every form of colour blindness.
            PixelIcon(
                icon = if (ok) H4Icon.HAND_THUMBS_UP else H4Icon.HAND_THUMBS_DOWN,
                size = 20.dp,
                ink = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                contentDescription = if (ok) "Granted" else "Not granted",
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel, fontSize = 15.sp) }
        }
    }
}

@Composable
fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    H4Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * Two-way switch inside a tab. Shared so Plan and Notes divide themselves the
 * same way instead of each inventing a segmented control.
 */
@Composable
fun SegmentedRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.background
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Small uppercase divider inside a long screen. */
@Composable
fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.outline,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
fun EmptyState(title: String, body: String, icon: H4Icon? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            PixelIconDecoration(
                icon = icon,
                size = 48.dp,
                ink = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
    }
}
