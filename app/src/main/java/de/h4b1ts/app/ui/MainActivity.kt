package de.h4b1ts.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.block.HealthWatchdog
import de.h4b1ts.app.data.AppGroupStore
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.data.SettingsRepository
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.AttachmentStore
import de.h4b1ts.app.productivity.NoteStore
import de.h4b1ts.app.reminders.ReminderScheduler
import de.h4b1ts.app.productivity.TaskStore
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.PixelIcon
import de.h4b1ts.app.ui.components.PixelIconDecoration
import de.h4b1ts.app.ui.screens.HabitsScreen
import de.h4b1ts.app.ui.screens.NotesScreen
import de.h4b1ts.app.ui.screens.OnboardingScreen
import de.h4b1ts.app.ui.screens.PlanScreen
import de.h4b1ts.app.ui.screens.SettingsScreen
import de.h4b1ts.app.ui.screens.ShieldScreen
import de.h4b1ts.app.ui.screens.TodayScreen
import de.h4b1ts.app.ui.theme.AccentMode
import de.h4b1ts.app.ui.theme.H4b1tsTheme
import de.h4b1ts.app.ui.theme.ThemeMode

private enum class Tab(val label: String, val icon: H4Icon) {
    TODAY("Today", H4Icon.CLOCK),
    HABITS("Habits", H4Icon.CLIPBOARD),
    PLAN("Plan", H4Icon.PIN_BOARD),
    NOTES("Notes", H4Icon.FILE),
    SHIELD("Shield", H4Icon.PHONE),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HabitStore.init(applicationContext)
        TaskStore.init(applicationContext)
        NoteStore.init(applicationContext)
        AttachmentStore.initGallery(applicationContext)
        SettingsRepository.init(applicationContext)
        BlockRepository.init(applicationContext)
        AppGroupStore.init(applicationContext)
        // Cheap, and it repairs anything the system dropped while the app was
        // not running.
        ReminderScheduler.rescheduleAll(applicationContext)
        HealthWatchdog.schedule(applicationContext)
        HealthWatchdog.check(applicationContext)

        setContent {
            var accent by remember {
                mutableStateOf(AccentMode.fromName(SettingsRepository.accentModeName))
            }
            var theme by remember {
                mutableStateOf(ThemeMode.fromName(SettingsRepository.themeModeName))
            }
            H4b1tsTheme(accent = accent, theme = theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppShell(
                        accent = accent,
                        theme = theme,
                        onAccentChange = {
                            accent = it
                            SettingsRepository.accentModeName = it.name
                        },
                        onThemeChange = {
                            theme = it
                            SettingsRepository.themeModeName = it.name
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppShell(
    accent: AccentMode,
    theme: ThemeMode,
    onAccentChange: (AccentMode) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var settingsOpen by remember { mutableStateOf(false) }
    // Existing habits mean this install has been used before, whatever the flag
    // says — reinstalling should not force the scorecard on someone again.
    var onboarded by remember {
        mutableStateOf(SettingsRepository.onboardingDone || HabitStore.habits.isNotEmpty())
    }

    if (!onboarded) {
        // Its own Scaffold, otherwise the header sits under the status bar:
        // the inset comes from Scaffold, and this branch skips the one below.
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                Header(settingsOpen = false, onToggleSettings = {})
                OnboardingScreen(
                    onFinish = {
                        SettingsRepository.onboardingDone = true
                        onboarded = true
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { candidate ->
                    NavigationBarItem(
                        // Settings sit over the tabs rather than beside them, so
                        // the bar has to close them. Without this the bar moved
                        // its highlight to a destination that stayed hidden
                        // behind settings — the app said Habits and showed the
                        // gear, and the only way out was the gear itself.
                        selected = tab == candidate && !settingsOpen,
                        onClick = {
                            settingsOpen = false
                            tab = candidate
                        },
                        icon = { TabIcon(candidate.icon, selected = tab == candidate) },
                        label = { Text(candidate.label, fontSize = 14.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedTextColor = MaterialTheme.colorScheme.outline,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Header(
                settingsOpen = settingsOpen,
                onToggleSettings = { settingsOpen = !settingsOpen },
            )
            if (settingsOpen) {
                SettingsScreen(
                    accent = accent,
                    theme = theme,
                    onAccentChange = onAccentChange,
                    onThemeChange = onThemeChange,
                    onRedoScorecard = {
                        settingsOpen = false
                        onboarded = false
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                when (tab) {
                    Tab.TODAY -> TodayScreen(
                        Modifier.weight(1f),
                        onOpenShield = { tab = Tab.SHIELD },
                    )
                    Tab.HABITS -> HabitsScreen(Modifier.weight(1f))
                    Tab.PLAN -> PlanScreen(Modifier.weight(1f))
                    Tab.NOTES -> NotesScreen(Modifier.weight(1f))
                    Tab.SHIELD -> ShieldScreen(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The wordmark is identity, not navigation, so it sits above the content rather
 * than inside it. Settings live behind the gear next to it: appearance is
 * something you set once, and it should not compete with the three things the app
 * is actually for.
 */
@Composable
private fun Header(settingsOpen: Boolean, onToggleSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "H4B1TS",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleSettings) {
            PixelIcon(
                icon = if (settingsOpen) H4Icon.ARROW_LEFT else H4Icon.GEAR,
                size = 22.dp,
                ink = if (settingsOpen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                contentDescription = if (settingsOpen) "Close settings" else "Settings",
            )
        }
    }
}

/**
 * The tabs used to be plain dots because there was no icon language to draw on.
 * Now there is, so the bar says what each destination holds instead of only where
 * you are. Selection is still carried by colour as well as by the label
 * underneath, so the icon is never the only signal.
 */
@Composable
private fun TabIcon(icon: H4Icon, selected: Boolean) {
    PixelIconDecoration(
        icon = icon,
        size = 20.dp,
        ink = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    )
}
