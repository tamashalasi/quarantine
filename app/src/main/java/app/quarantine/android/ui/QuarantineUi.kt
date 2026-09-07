package app.quarantine.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.quarantine.android.R
import app.quarantine.android.core.HoldTimer
import app.quarantine.android.core.formatDuration
import app.quarantine.android.data.UsageSnapshot
import kotlin.math.ceil

private val LightColors = lightColorScheme(primary = Color(0xFF42684A), secondary = Color(0xFF52634F))
private val DarkColors = darkColorScheme(primary = Color(0xFFA7D2AD), secondary = Color(0xFFBACCB4))

@Composable
fun QuarantineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
fun UsageSummary(usage: UsageSnapshot) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your past 7 days", style = MaterialTheme.typography.titleMedium)
            when {
                usage.loading -> Text("Loading screen time…")
                usage.error != null -> Text(usage.error)
                !usage.hasAccess -> Text("Allow usage access to see your screen time and top apps.")
                !usage.hasHistory -> Text("Android has no screen-time history available yet.")
                else -> {
                    Text(formatDuration(usage.totalMillis), style = MaterialTheme.typography.headlineMedium)
                    Text("Screen time · today and the previous 6 days", style = MaterialTheme.typography.bodySmall)
                    if (usage.topApps.isEmpty()) Text("No app usage recorded.")
                    usage.topApps.forEach { app ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(app.name, Modifier.weight(1f))
                            Text(formatDuration(usage.millis[app.packageName] ?: 0L))
                        }
                    }
                    Text("Android estimates for apps in this profile.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun HoldToUnlock(label: String, seconds: Int, enabled: Boolean = true, onUnlocked: () -> Unit) {
    var pointerHeld by remember { mutableStateOf(false) }
    var keyHeld by remember { mutableStateOf(false) }
    var remaining by remember(seconds) { mutableLongStateOf(seconds * 1000L) }
    val focused = LocalWindowInfo.current.isWindowFocused
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = enabled && focused && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val callback by rememberUpdatedState(onUnlocked)
    val held = pointerHeld || keyHeld

    LaunchedEffect(active) { if (!active) { pointerHeld = false; keyHeld = false } }
    LaunchedEffect(held, active, seconds) {
        val timer = HoldTimer(seconds * 1000L)
        remaining = seconds * 1000L
        if (held && active) {
            timer.start(withFrameNanos { it / 1_000_000 })
            while (true) {
                val now = withFrameNanos { it / 1_000_000 }
                remaining = timer.remaining(now)
                if (timer.complete(now)) {
                    pointerHeld = false
                    keyHeld = false
                    callback()
                    break
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .semantics {
                    role = Role.Button
                    contentDescription = "$label. Hold continuously for $seconds seconds."
                    stateDescription = "${ceil(remaining / 1000.0).toInt()} seconds remaining"
                    if (!active) disabled()
                }
                .onFocusChanged { if (!it.isFocused) keyHeld = false }
                .onPreviewKeyEvent {
                    if (active && (it.key == Key.Spacebar || it.key == Key.Enter || it.key == Key.DirectionCenter)) {
                        keyHeld = it.type == KeyEventType.KeyDown
                        true
                    } else false
                }
                .focusable(enabled = active)
                .pointerInput(active, seconds) {
                    if (active) detectTapGestures(onPress = {
                        pointerHeld = true
                        try { tryAwaitRelease() } finally { pointerHeld = false }
                    })
                }.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = remaining.toFloat(), onValueChange = {}, enabled = false,
            valueRange = 0f..(seconds * 1000f),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Unlock time remaining" },
            colors = SliderDefaults.colors(disabledThumbColor = MaterialTheme.colorScheme.primary,
                disabledActiveTrackColor = MaterialTheme.colorScheme.primary),
        )
        Text("${ceil(remaining / 1000.0).toInt()} seconds remaining", style = MaterialTheme.typography.bodyMedium)
        Text("Keep holding. Releasing early resets the timer.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun UnlockScreen(
    title: String, seconds: Int, usage: UsageSnapshot, enabled: Boolean = true,
    onUnlocked: () -> Unit, footer: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp).widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(painterResource(R.drawable.ic_quarantine), contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            UsageSummary(usage)
            HoldToUnlock(title, seconds, enabled, onUnlocked)
            footer()
        }
    }
}
