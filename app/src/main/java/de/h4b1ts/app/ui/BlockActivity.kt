package de.h4b1ts.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.R
import de.h4b1ts.app.block.BlockMethod
import de.h4b1ts.app.block.ServiceHealth
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.PixelIcon
import de.h4b1ts.app.ui.theme.H4Colors
import de.h4b1ts.app.ui.theme.H4Typography
import kotlinx.coroutines.delay

/**
 * Full screen shield, used when the overlay permission is missing. On most ROMs
 * OverlayBlocker takes over; see BlockPresenter.
 *
 * Deliberately monochrome on pure black and deliberately not a wall: the way out
 * exists, but costs waiting time and a conscious decision. That is "make it
 * difficult", not "make it impossible" — and the absence of any accent is the
 * point, because a blocked state must offer nothing to look at.
 */
class BlockActivity : ComponentActivity() {

    private var blockedPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlockRepository.init(applicationContext)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = labelOf(blockedPackage)

        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            BlockScreen(
                appLabel = label,
                onDismiss = { goHome() },
                onBypass = {
                    BlockRepository.grantBypass(blockedPackage, BYPASS_DURATION_MS)
                    openApp(blockedPackage)
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        // Recorded here rather than at startActivity: this is the moment the
        // shield is actually on screen, which is the latency the user feels.
        ServiceHealth.recordBlock(blockedPackage, BlockMethod.ACTIVITY)
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
    }

    private fun labelOf(packageName: String): String = runCatching {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    }.getOrDefault(packageName)

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun openApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "extra_package"
        private const val BYPASS_DURATION_MS = 60_000L

        /**
         * Read by BlockPresenter to check whether the activity really came up.
         * Some ROMs drop background activity launches without reporting an error.
         */
        @Volatile
        var isVisible: Boolean = false
            private set

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun BlockScreen(
    appLabel: String,
    onDismiss: () -> Unit,
    onBypass: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(FRICTION_SECONDS) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }

    // The shield sets its colours by hand and so never ran through H4b1tsTheme.
    // Without this wrapper it would be the one screen still in Material's font.
    MaterialTheme(typography = H4Typography) {
        Surface(modifier = Modifier.fillMaxSize(), color = H4Colors.Void) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.setup_eyebrow),
                        color = H4Colors.Mid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = appLabel,
                        color = H4Colors.TextPrimary,
                        fontSize = 34.sp,
                        // Long app names wrap here, and the inherited 26sp line
                        // box would overlap them.
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.block_locked),
                        color = H4Colors.TextMuted,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(48.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = H4Colors.Line,
                            contentColor = H4Colors.TextPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.block_dismiss),
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBypass, enabled = remaining == 0) {
                        // The only mark on the screen, and it earns its place: it
                        // says the wait is the point. It leaves with the countdown
                        // rather than becoming something to look at.
                        if (remaining > 0) {
                            PixelIcon(
                                icon = H4Icon.HOURGLASS,
                                size = 16.dp,
                                ink = H4Colors.Line,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (remaining > 0) {
                                stringResource(R.string.block_bypass_waiting, remaining)
                            } else {
                                stringResource(R.string.block_bypass_ready)
                            },
                            color = if (remaining > 0) H4Colors.Line else H4Colors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

private const val FRICTION_SECONDS = 5
