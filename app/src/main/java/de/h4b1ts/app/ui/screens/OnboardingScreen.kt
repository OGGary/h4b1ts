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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.data.SettingsRepository
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.habits.Polarity
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.PixelIcon
import de.h4b1ts.app.ui.components.PixelIconDecoration
import de.h4b1ts.app.ui.components.Eyebrow
import de.h4b1ts.app.ui.components.GroupLabel
import de.h4b1ts.app.ui.components.H4Card
import org.json.JSONArray
import org.json.JSONObject

/**
 * How the entry in the scorecard was judged.
 *
 * The inventory comes before any change: list what you already do and mark each
 * line good, neutral or bad — you cannot improve a habit you have not noticed.
 * Here that inventory is also the fastest way into the app: the plus lines become
 * positive habits, the minus lines become negative ones, and the minus lines are
 * exactly the ones worth blocking apps for.
 */
/**
 * The marks used to be typographic: +, = and a real minus sign. Pixelbasel has no
 * U+2212, so that last one would have rendered as a missing-glyph box — and a
 * scorecard whose worst option is an empty square is not a scorecard. Faces say
 * the same three things without depending on a glyph being present.
 */
private enum class Mark(val icon: H4Icon, val label: String) {
    GOOD(H4Icon.FACE_ANGEL, "serves me"),
    NEUTRAL(H4Icon.FACE_NEUTRAL, "neutral"),
    BAD(H4Icon.FACE_DEMON, "costs me"),
}

private data class ScoreEntry(val text: String, val mark: Mark)

/**
 * The half-filled scorecard, kept across a back gesture.
 *
 * Marks are stored by name, so R8 must not rename them — the keep rule in
 * proguard-rules.pro covers every enum in this package, [Mark] included.
 * An unreadable or half-written draft is simply dropped: losing a draft is
 * bad, but starting the app on a crash would be worse.
 */
private fun List<ScoreEntry>.toJson(): String =
    JSONArray().apply {
        forEach { put(JSONObject().put("text", it.text).put("mark", it.mark.name)) }
    }.toString()

private fun parseDraft(raw: String?): List<ScoreEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val text = item.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mark = runCatching { Mark.valueOf(item.optString("mark")) }.getOrNull()
                ?: return@mapNotNull null
            ScoreEntry(text, mark)
        }
    }.getOrDefault(emptyList())
}

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        mutableStateListOf<ScoreEntry>().apply { addAll(parseDraft(SettingsRepository.scorecardDraft)) }
    }
    var draft by remember { mutableStateOf("") }

    val good = entries.count { it.mark == Mark.GOOD }
    val bad = entries.count { it.mark == Mark.BAD }
    val willCreate = good + bad

    // Written on every change rather than on the way out: there is no reliable
    // "on the way out" here, since back finishes the activity.
    fun persist() {
        SettingsRepository.scorecardDraft = if (entries.isEmpty()) null else entries.toJson()
    }

    fun add(mark: Mark) {
        val text = draft.trim()
        if (text.isBlank()) return
        entries.add(ScoreEntry(text, mark))
        draft = ""
        persist()
    }

    /** The scorecard has served its purpose; it should not outlive the screen. */
    fun finishAndClear() {
        SettingsRepository.scorecardDraft = null
        onFinish()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Eyebrow("FIRST", color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Habit scorecard",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Write down what you already do on a normal day — not what you " +
                    "want to do. Then mark each line: does it serve the person you want " +
                    "to be, or cost you?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            H4Card {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            text = "Check my phone in bed",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 17.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mark.entries.forEach { mark ->
                        MarkButton(
                            mark = mark,
                            enabled = draft.isNotBlank(),
                            onClick = { add(mark) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (entries.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                GroupLabel("Your day so far (${entries.size})")
            }
            itemsIndexed(entries) { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            entries.removeAt(index)
                            persist()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarkDot(entry.mark)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = entry.text,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = entry.mark.label,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    entries.forEach { entry ->
                        // Neutral lines are noticed and then left alone. That is
                        // the point of marking them at all.
                        val polarity = when (entry.mark) {
                            Mark.GOOD -> Polarity.POSITIVE
                            Mark.BAD -> Polarity.NEGATIVE
                            Mark.NEUTRAL -> return@forEach
                        }
                        HabitStore.add(
                            identity = entry.text.replaceFirstChar { it.lowercase() },
                            name = entry.text.replaceFirstChar { it.uppercase() },
                            polarity = polarity,
                        )
                    }
                    finishAndClear()
                },
                enabled = willCreate > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (willCreate == 0) {
                        "Mark at least one line"
                    } else {
                        "Create $willCreate habit${if (willCreate == 1) "" else "s"}"
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { finishAndClear() }, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (bad > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "The $bad line${if (bad == 1) "" else "s"} you marked as costing " +
                        "you become habits you are breaking. Those are the ones worth " +
                        "locking an app behind.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun MarkButton(
    mark: Mark,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = markColor(mark)
    Box(
        modifier = modifier
            .border(
                1.dp,
                if (enabled) tint else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIconDecoration(
                icon = mark.icon,
                size = 18.dp,
                ink = if (enabled) tint else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = mark.label,
                color = if (enabled) tint else MaterialTheme.colorScheme.outline,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MarkDot(mark: Mark) {
    // Drawn straight in the mark colour rather than knocked out of a filled
    // circle: at 16 px the linework needs the whole badge to stay readable.
    PixelIcon(
        icon = mark.icon,
        size = 22.dp,
        ink = markColor(mark),
        contentDescription = mark.label,
    )
}

/**
 * Good and bad never sit next to each other as red against green — that pair
 * collapses into the same olive under deuteranopia. The symbol and the label
 * carry the meaning; colour only confirms it.
 */
@Composable
private fun markColor(mark: Mark): Color = when (mark) {
    Mark.GOOD -> MaterialTheme.colorScheme.primary
    Mark.NEUTRAL -> MaterialTheme.colorScheme.outline
    Mark.BAD -> MaterialTheme.colorScheme.tertiary
}
