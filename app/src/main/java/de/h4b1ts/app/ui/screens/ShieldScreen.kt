package de.h4b1ts.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.h4b1ts.app.R
import de.h4b1ts.app.BuildConfig
import de.h4b1ts.app.block.H4b1tsAccessibilityService
import de.h4b1ts.app.block.OemSetup
import de.h4b1ts.app.block.SelfTest
import de.h4b1ts.app.block.UsageFallbackService
import de.h4b1ts.app.data.AppGroup
import de.h4b1ts.app.data.AppGroupStore
import de.h4b1ts.app.data.AppInfo
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.data.SettingsRepository
import de.h4b1ts.app.data.InstalledApps
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.GroupLabel
import de.h4b1ts.app.ui.components.H4Card
import de.h4b1ts.app.ui.components.Pill
import de.h4b1ts.app.ui.components.PixelIconDecoration
import de.h4b1ts.app.ui.components.SectionTitle
import de.h4b1ts.app.ui.components.StatusCard
import de.h4b1ts.app.ui.components.ToggleCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything about blocking in one place: proof that it works, the permissions it
 * needs, the manufacturer hurdles, and the app list.
 */
@Composable
fun ShieldScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var serviceEnabled by remember {
        mutableStateOf(
            !BuildConfig.USES_ACCESSIBILITY || H4b1tsAccessibilityService.isEnabled(context)
        )
    }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var usageGranted by remember { mutableStateOf(UsageFallbackService.hasUsagePermission(context)) }
    var fallbackOn by remember { mutableStateOf(SettingsRepository.fallbackEnabled) }
    var testState by remember { mutableStateOf<SelfTest.State>(SelfTest.state) }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var gatePickerFor by remember { mutableStateOf<String?>(null) }
    // Pending cooling-offs have to be seen running down, or the wait reads as a
    // dead end rather than a countdown.
    var tick by remember { mutableIntStateOf(0) }
    var showGranted by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<AppGroup?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    val oemSteps = remember { OemSetup.stepsFor(context) }
    val blocked: SnapshotStateList<String> = remember {
        mutableStateListOf<String>().apply { addAll(BlockRepository.blockedPackages) }
    }
    // Mirrored into snapshot state so the list recomposes when a gate changes;
    // BlockRepository itself is plain volatile state read from the service thread.
    val gates = remember {
        mutableStateMapOf<String, String>().apply {
            BlockRepository.blockedPackages.forEach { pkg ->
                BlockRepository.gateOf(pkg)?.let { put(pkg, it) }
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = !BuildConfig.USES_ACCESSIBILITY ||
                    H4b1tsAccessibilityService.isEnabled(context)
                overlayGranted = Settings.canDrawOverlays(context)
                usageGranted = UsageFallbackService.hasUsagePermission(context)
                SelfTest.collect()
                testState = SelfTest.state
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            // Finishes any wait that has run out, then redraws.
            BlockRepository.applyReadyUnblocks()
            blocked.retainAll(BlockRepository.blockedPackages)
            tick++
        }
    }

    val permissions = listOfNotNull(
        // Absent from the Play build: it declares no accessibility service, so
        // offering a switch that can never be satisfied would only confuse.
        if (!BuildConfig.USES_ACCESSIBILITY) null else Permission(
            title = stringResource(R.string.setup_accessibility_title),
            subtitle = stringResource(
                if (serviceEnabled) R.string.setup_accessibility_on
                else R.string.setup_accessibility_off
            ),
            granted = serviceEnabled,
            actionLabel = stringResource(
                if (serviceEnabled) R.string.setup_accessibility_action_on
                else R.string.setup_accessibility_action_off
            ),
            onAction = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        ),
        Permission(
            title = stringResource(R.string.setup_overlay_title),
            subtitle = stringResource(
                if (overlayGranted) R.string.setup_overlay_on else R.string.setup_overlay_off
            ),
            granted = overlayGranted,
            actionLabel = stringResource(R.string.setup_overlay_action),
            onAction = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        ),
        Permission(
            title = stringResource(R.string.setup_usage_title),
            subtitle = stringResource(
                if (usageGranted) R.string.setup_usage_on else R.string.setup_usage_off
            ),
            granted = usageGranted,
            actionLabel = stringResource(R.string.setup_usage_action),
            onAction = {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        ),
    )
    val pendingPermissions = permissions.filterNot { it.granted }
    val grantedPermissions = permissions.filter { it.granted }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("Shield", icon = H4Icon.PHONE)
            Spacer(Modifier.height(12.dp))
            GroupLabel("Does it work")
        }

        item {
            SelfTestCard(
                state = testState,
                onRun = {
                    SelfTest.start(context)
                    testState = SelfTest.state
                },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            GroupLabel("Permissions")
        }

        // Granted permissions are the ones with nothing left to do, so they stop
        // competing with the one that still needs the user. They collapse rather
        // than disappear: a permission that cannot be found again is a permission
        // that cannot be checked or revoked.
        items(pendingPermissions, key = { it.title }) { permission ->
            StatusCard(
                title = permission.title,
                subtitle = permission.subtitle,
                ok = false,
                actionLabel = permission.actionLabel,
                onAction = permission.onAction,
            )
        }

        if (grantedPermissions.isNotEmpty()) {
            item {
                GrantedSummary(
                    count = grantedPermissions.size,
                    expanded = showGranted,
                    allDone = pendingPermissions.isEmpty(),
                    onToggle = { showGranted = !showGranted },
                )
            }
        }

        if (showGranted) {
            items(grantedPermissions, key = { it.title }) { permission ->
                StatusCard(
                    title = permission.title,
                    subtitle = permission.subtitle,
                    ok = true,
                    actionLabel = permission.actionLabel,
                    onAction = permission.onAction,
                )
            }
        }

        item {
            ToggleCard(
                title = if (BuildConfig.USES_ACCESSIBILITY) {
                    stringResource(R.string.setup_fallback_title)
                } else {
                    stringResource(R.string.setup_detector_title)
                },
                subtitle = if (!BuildConfig.USES_ACCESSIBILITY) {
                    stringResource(
                        if (fallbackOn) R.string.setup_detector_on else R.string.setup_detector_off
                    )
                } else {
                    stringResource(
                        if (fallbackOn) R.string.setup_fallback_on else R.string.setup_fallback_off
                    )
                },
                checked = fallbackOn,
                enabled = usageGranted,
                onCheckedChange = { value ->
                    fallbackOn = value
                    SettingsRepository.fallbackEnabled = value
                    if (value) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        UsageFallbackService.start(context)
                    } else {
                        UsageFallbackService.stop(context)
                    }
                },
            )
        }

        if (oemSteps.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                GroupLabel(stringResource(R.string.oem_title, Build.MANUFACTURER))
            }
            items(oemSteps) { step -> OemStepCard(step) }
        }

        item {
            Spacer(Modifier.height(8.dp))
            GroupLabel("Groups")
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Block a set of apps in one move instead of hunting them " +
                    "down one at a time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
        }

        items(AppGroupStore.groups, key = { "group-${it.id}" }) { group ->
            val blockedInGroup = group.packages.count { it in blocked }
            AppGroupCard(
                group = group,
                blockedCount = blockedInGroup,
                onEdit = { editingGroup = group },
                onToggleAll = {
                    // Half-blocked counts as unblocked, so one press always has
                    // a predictable result: the whole group goes on.
                    val turnOn = blockedInGroup < group.packages.size
                    group.packages.forEach { pkg ->
                        if (turnOn) {
                            BlockRepository.block(pkg, BlockRepository.labelOf(pkg))
                            if (pkg !in blocked) blocked.add(pkg)
                        } else {
                            // Stays blocked; only the waiting starts.
                            BlockRepository.requestUnblock(pkg)
                        }
                    }
                    SelfTest.reset()
                    testState = SelfTest.state
                },
            )
        }

        item {
            OutlinedButton(
                onClick = { creatingGroup = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("New group") }
        }

        item {
            Spacer(Modifier.height(8.dp))
            GroupLabel(stringResource(R.string.setup_apps_title, blocked.size))
        }

        val list = apps
        if (list == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Blocked apps first. They are the ones the user came to check on,
            // and burying them alphabetically among two hundred others means
            // scrolling to find out what is actually switched on.
            val (blockedApps, openApps) = list.partition { it.packageName in blocked }

            if (blockedApps.isNotEmpty()) {
                item { AppListHeader("Blocked (${blockedApps.size})") }
            }
            items(blockedApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = true,
                    gateLabel = gates[app.packageName]?.let { HabitStore.byId(it)?.name },
                    pendingMs = BlockRepository.unblockRemainingMs(app.packageName) + 0L * tick,
                    onCancelPending = {
                        BlockRepository.cancelUnblock(app.packageName)
                        tick++
                    },
                    onCheckedChange = { value ->
                        // The label is stored while the app is still installed to
                        // ask; after an uninstall it cannot be resolved.
                        if (value) {
                            BlockRepository.block(app.packageName, app.label)
                            blocked.add(app.packageName)
                        } else {
                            BlockRepository.requestUnblock(app.packageName)
                        }
                        tick++
                        SelfTest.reset()
                        testState = SelfTest.state
                    },
                    onGateClick = { gatePickerFor = app.packageName },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            if (blockedApps.isNotEmpty() && openApps.isNotEmpty()) {
                item { AppListHeader("Everything else (${openApps.size})") }
            }
            items(openApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = false,
                    gateLabel = gates[app.packageName]?.let { HabitStore.byId(it)?.name },
                    pendingMs = BlockRepository.unblockRemainingMs(app.packageName) + 0L * tick,
                    onCancelPending = {
                        BlockRepository.cancelUnblock(app.packageName)
                        tick++
                    },
                    onCheckedChange = { value ->
                        // The label is stored while the app is still installed to
                        // ask; after an uninstall it cannot be resolved.
                        if (value) {
                            BlockRepository.block(app.packageName, app.label)
                            blocked.add(app.packageName)
                        } else {
                            BlockRepository.requestUnblock(app.packageName)
                        }
                        tick++
                        SelfTest.reset()
                        testState = SelfTest.state
                    },
                    onGateClick = { gatePickerFor = app.packageName },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            val missing = BlockRepository.blockedButMissing(list.map { it.packageName }.toSet())
            if (missing.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    GroupLabel("Blocked, not installed (${missing.size})")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Uninstalling is the easy way out of a block, so these stay " +
                            "on the list. Reinstall and they are blocked again.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
                items(missing, key = { "missing-$it" }) { packageName ->
                    MissingAppRow(
                        label = BlockRepository.labelOf(packageName),
                        packageName = packageName,
                        onForget = {
                            BlockRepository.requestUnblock(packageName)
                            tick++
                        },
                        pendingMs = BlockRepository.unblockRemainingMs(packageName).also { tick },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (creatingGroup) {
        AppGroupDialog(
            group = null,
            apps = apps.orEmpty(),
            onDismiss = { creatingGroup = false },
            onSave = { name, packages ->
                AppGroupStore.put(AppGroup(name = name, packages = packages))
                creatingGroup = false
            },
            onDelete = null,
        )
    }

    editingGroup?.let { group ->
        AppGroupDialog(
            group = group,
            apps = apps.orEmpty(),
            onDismiss = { editingGroup = null },
            onSave = { name, packages ->
                AppGroupStore.put(group.copy(name = name, packages = packages))
                editingGroup = null
            },
            onDelete = {
                AppGroupStore.remove(group.id)
                editingGroup = null
            },
        )
    }

    gatePickerFor?.let { packageName ->
        GatePicker(
            current = gates[packageName],
            onDismiss = { gatePickerFor = null },
            onPick = { habitId ->
                BlockRepository.setGate(packageName, habitId)
                if (habitId == null) gates.remove(packageName) else gates[packageName] = habitId
                gatePickerFor = null
            },
        )
    }
}

/**
 * The coupling, from the user's side: pick the habit that opens this app.
 *
 * "Instagram unlocks after you have walked" is the sentence the whole product is
 * built around, so it lives on the app itself rather than in a settings screen.
 */
@Composable
private fun GatePicker(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(R.string.gate_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (HabitStore.habits.isEmpty()) {
                    Text(stringResource(R.string.gate_no_habits), fontSize = 16.sp)
                }
                GateOption(
                    label = stringResource(R.string.gate_none),
                    selected = current == null,
                    onClick = { onPick(null) },
                )
                HabitStore.habits.forEach { habit ->
                    GateOption(
                        label = habit.name,
                        selected = habit.id == current,
                        onClick = { onPick(habit.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * A block that outlived its app. Kept deliberately plain — it is a record, not
 * something you interact with beyond letting it go.
 */
@Composable
private fun MissingAppRow(
    label: String,
    packageName: String,
    pendingMs: Long,
    onForget: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
            Text(packageName, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
        }
        TextButton(onClick = onForget, enabled = pendingMs <= 0) {
            Text(
                text = if (pendingMs > 0) formatWait(pendingMs) else "Forget",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun GateOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun SelfTestCard(state: SelfTest.State, onRun: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val result: Pair<String, Color>? = when (state) {
        is SelfTest.State.Idle -> null
        is SelfTest.State.Running -> stringResource(R.string.selftest_running) to MaterialTheme.colorScheme.onSurfaceVariant
        is SelfTest.State.Passed -> {
            val slow = state.latencyMs > SelfTest.GOOD_LATENCY_MS
            val id = if (slow) R.string.selftest_slow else R.string.selftest_passed
            stringResource(id, state.latencyMs, state.method.name.lowercase()) to
                if (slow) MaterialTheme.colorScheme.tertiary else accent
        }
        is SelfTest.State.Failed -> {
            val id = when (state.reason) {
                SelfTest.Reason.NO_BLOCKED_APP -> R.string.selftest_no_app
                SelfTest.Reason.NOT_LAUNCHABLE -> R.string.selftest_not_launchable
                SelfTest.Reason.NO_BLOCK_FIRED -> R.string.selftest_no_block
            }
            stringResource(id) to MaterialTheme.colorScheme.error
        }
    }

    H4Card {
        Text(
            text = stringResource(R.string.selftest_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.selftest_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
        result?.let { (text, color) ->
            Spacer(Modifier.height(10.dp))
            Text(text = text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRun) {
                Text(stringResource(R.string.selftest_action), color = accent)
            }
        }
    }
}

@Composable
private fun OemStepCard(step: OemSetup.Step) {
    val context = LocalContext.current
    H4Card {
        Text(step.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(step.body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        step.intent?.let { intent ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { runCatching { context.startActivity(intent) } }) {
                    Text(
                        text = stringResource(R.string.oem_open),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    gateLabel: String?,
    pendingMs: Long,
    onCheckedChange: (Boolean) -> Unit,
    onGateClick: () -> Unit,
    onCancelPending: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            app.icon?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
                Text(app.packageName, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }

        if (checked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = if (pendingMs > 0) onCancelPending else onGateClick)
                    .padding(start = 48.dp, top = 2.dp, bottom = 6.dp),
            ) {
                Text(
                    text = when {
                        // Tapping the countdown takes the request back, which is a
                        // tightening and therefore free.
                        pendingMs > 0 -> "Unblocks in ${formatWait(pendingMs)} — tap to keep blocked"
                        gateLabel != null -> stringResource(R.string.gate_until, gateLabel)
                        else -> stringResource(R.string.gate_always)
                    },
                    color = when {
                        pendingMs > 0 -> MaterialTheme.colorScheme.tertiary
                        gateLabel != null -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** One line in the Permissions list, so granted and pending can be told apart. */
private data class Permission(
    val title: String,
    val subtitle: String,
    val granted: Boolean,
    val actionLabel: String,
    val onAction: () -> Unit,
)

/**
 * Stands in for the permissions that are already done. It stays tappable because
 * the granted ones are exactly what you go looking for when blocking stops
 * working and you want to know what got switched off.
 */
@Composable
private fun GrantedSummary(
    count: Int,
    expanded: Boolean,
    allDone: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIconDecoration(
            icon = if (allDone) H4Icon.HAND_THUMBS_UP else H4Icon.CLIPBOARD,
            size = 16.dp,
            ink = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (allDone) {
                "All $count permissions granted"
            } else {
                "$count already granted"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (expanded) "Hide" else "Show",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
    }
}

/** A divider inside the app list, so the two halves are obviously two halves. */
@Composable
private fun AppListHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.outline,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

/**
 * One group, with the single action that makes it worth having.
 *
 * The button reads "Block all" until every app in the group is blocked, so a
 * partly-blocked group completes rather than toggling to the opposite of
 * whatever it happens to be — the result of pressing it is the same every time.
 */
@Composable
private fun AppGroupCard(
    group: AppGroup,
    blockedCount: Int,
    onEdit: () -> Unit,
    onToggleAll: () -> Unit,
) {
    val allBlocked = group.packages.isNotEmpty() && blockedCount == group.packages.size
    H4Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        group.packages.isEmpty() -> "No apps yet"
                        allBlocked -> "${group.packages.size} apps, all blocked"
                        else -> "$blockedCount of ${group.packages.size} blocked"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            if (allBlocked) {
                Pill(text = "on", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = onEdit) {
                Text("Edit", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            TextButton(onClick = onToggleAll, enabled = group.packages.isNotEmpty()) {
                Text(
                    text = if (allBlocked) "Unblock all" else "Block all",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** Name the group and tick the apps in it. */
@Composable
private fun AppGroupDialog(
    group: AppGroup?,
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onSave: (String, Set<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var picked by remember { mutableStateOf(group?.packages.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(if (group == null) "New group" else "Edit group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name", fontSize = 14.sp) },
                    placeholder = { Text("Social", fontSize = 15.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "${picked.size} selected",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
                // Its own scroller: the installed-app list is long, and the
                // name field has to stay put while it is scrolled.
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        val checked = app.packageName in picked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked = if (checked) {
                                        picked - app.packageName
                                    } else {
                                        picked + app.packageName
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = app.label,
                                color = if (checked) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (checked) {
                                Pill(text = "in", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), picked) },
                enabled = name.isNotBlank(),
            ) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}


/** mm:ss, because a cooling-off is minutes long and seconds still tick. */
private fun formatWait(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
