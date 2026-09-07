package app.quarantine.android.blocking

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.quarantine.android.ProtectionStatus
import app.quarantine.android.QuarantineApplication
import app.quarantine.android.R
import app.quarantine.android.core.QuarantineSettings
import app.quarantine.android.core.UnlockSessions
import app.quarantine.android.data.UsageSnapshot
import app.quarantine.android.ui.QuarantineTheme
import app.quarantine.android.ui.UnlockScreen
import java.util.UUID
import kotlinx.coroutines.*

class QuarantineService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as QuarantineApplication
    private val sessions = UnlockSessions()
    private val tokens = mutableMapOf<String, String>()
    private var settings by mutableStateOf(QuarantineSettings())
    private var usage by mutableStateOf(UsageSnapshot())
    private var loaded = false
    private var foreground: String? = null
    private var overlayTarget: String? = null
    private var overlay: ComposeView? = null
    private var overlayOwner: OverlayOwner? = null
    private var refreshJob: Job? = null
    private var receiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) hideOverlay()
            else if (intent.action == Intent.ACTION_USER_PRESENT) evaluate()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        sessions.clear()
        tokens.clear()
        getSystemService(NotificationManager::class.java).cancelAll()
        publishSessions()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Unlocked apps", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tap an unlocked app's notification to put it back in quarantine."
                setShowBadge(false)
            },
        )
        ContextCompat.registerReceiver(this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) },
            ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        scope.launch {
            try {
                app.settings.settings.collect { next ->
                    settings = next
                    sessions.retain(next.packages)
                    tokens.keys.toList().filter { it !in next.packages }.forEach {
                        tokens.remove(it)
                        getSystemService(NotificationManager::class.java).cancel(it, NOTIFICATION_ID)
                    }
                    loaded = true
                    ProtectionStatus.mutableConnected.value = true
                    publishSessions()
                    evaluate()
                }
            } catch (_: java.io.IOException) {
                loaded = false
                ProtectionStatus.mutableConnected.value = false
                relockAll()
                hideOverlay()
            }
        }
        refreshUsage()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !loaded) return
        // Only package identifiers are inspected. No node text or user input is read.
        val visible = visibleApplication()
        if (visible != null) foreground = visible
        else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val candidate = event.packageName?.toString()
            if (candidate != null && usage.apps.any { it.packageName == candidate }) foreground = candidate
        }
        evaluate()
    }

    private fun visibleApplication(): String? = windows
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        .let { appWindows -> appWindows.firstOrNull { it.isFocused || it.isActive }
            ?: if (overlay != null) appWindows.firstOrNull() else null }
        ?.root?.packageName?.toString()

    private fun evaluate() {
        if (!loaded || !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            hideOverlay()
            return
        }
        visibleApplication()?.let { foreground = it }
        val target = foreground
        val protected = target == packageName || target == "com.android.settings" || target == "com.android.systemui" ||
            usage.apps.any { it.packageName == target && !it.canQuarantine }
        if (target == null || protected || !sessions.isBlocked(target, settings.packages)) hideOverlay()
        else if (overlayTarget != target) showOverlay(target)
    }

    private fun showOverlay(target: String) {
        hideOverlay()
        overlayTarget = target
        val owner = OverlayOwner()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuarantineTheme {
                    UnlockScreen("Unlock quarantine for ${appName(target)}", settings.holdSeconds, usage,
                        onUnlocked = { unlock(target) }) {
                        Text("This app stays unlocked until you relock it from its notification or Quarantine.")
                        TextButton(onClick = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            hideOverlay()
                        }) { Text("Go home") }
                    }
                }
            }
        }
        overlayOwner = owner
        overlay = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE,
        ).apply { title = "Quarantine unlock" }
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            owner.resume()
            refreshUsage()
        } catch (_: WindowManager.BadTokenException) {
            hideOverlay()
            ProtectionStatus.mutableConnected.value = false
        }
    }

    private fun hideOverlay() {
        overlayOwner?.destroy()
        overlay?.let {
            if (it.isAttachedToWindow) getSystemService(WindowManager::class.java).removeViewImmediate(it)
            it.disposeComposition()
        }
        overlay = null
        overlayOwner = null
        overlayTarget = null
    }

    private fun unlock(target: String) {
        val visible = visibleApplication()
        if (overlayTarget != target || visible != target ||
            !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            evaluate()
            return
        }
        if (sessions.unlock(target, settings.packages)) {
            tokens[target] = UUID.randomUUID().toString()
            publishSessions()
            notifyUnlocked(target)
        }
        evaluate()
    }

    fun relock(target: String, token: String? = null) {
        if (token != null && tokens[target] != token) return
        val isForeground = (visibleApplication() ?: foreground) == target
        sessions.relock(target)
        tokens.remove(target)
        getSystemService(NotificationManager::class.java).cancel(target, NOTIFICATION_ID)
        publishSessions()
        evaluate() // Cover the app before sending Home.
        if (isForeground) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun relockAll() {
        val grants = sessions.snapshot()
        grants.forEach { relock(it) }
    }

    fun refreshNotifications() { sessions.snapshot().forEach(::notifyUnlocked) }

    // The tap is an immediate relock command, not a notification trampoline to an Activity.
    @android.annotation.SuppressLint("LaunchActivityFromNotification")
    private fun notifyUnlocked(target: String) {
        val token = tokens[target] ?: return
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(this, RelockReceiver::class.java).apply {
            action = ACTION_RELOCK
            data = Uri.Builder().scheme("quarantine").authority("lock").appendPath(target).appendPath(token).build()
            putExtra(EXTRA_PACKAGE, target)
            putExtra(EXTRA_TOKEN, token)
        }
        val pending = PendingIntent.getBroadcast(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lock ${appName(target)} in quarantine")
            .setContentText("Tap to relock. If this app is open, you’ll return to Home.")
            .setContentIntent(pending)
            .addAction(R.drawable.ic_notification, "Lock now", pending)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { manager.notify(target, NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* In-app relocking remains available after permission revocation. */ }
    }

    private fun appName(target: String): String = usage.apps.firstOrNull { it.packageName == target }?.name
        ?: try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { target }

    private fun publishSessions() { ProtectionStatus.mutableUnlocked.value = sessions.snapshot() }
    private fun refreshUsage() {
        refreshJob?.cancel()
        refreshJob = scope.launch { usage = app.usage.load() }
    }

    override fun onInterrupt() { hideOverlay() }
    override fun onDestroy() {
        hideOverlay()
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        scope.cancel()
        sessions.clear()
        tokens.clear()
        getSystemService(NotificationManager::class.java).cancelAll()
        publishSessions()
        ProtectionStatus.mutableConnected.value = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        var instance: QuarantineService? = null
            private set
        private const val CHANNEL = "unlocked_apps"
        private const val NOTIFICATION_ID = 1
        const val ACTION_RELOCK = "app.quarantine.android.RELOCK"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_TOKEN = "token"
    }
}
