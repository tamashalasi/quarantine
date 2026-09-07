package app.quarantine.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.quarantine.android.blocking.QuarantineService
import app.quarantine.android.ui.*

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private var notificationsAllowed by mutableStateOf(false)
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updatePermissions()
        QuarantineService.instance?.refreshNotifications()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuarantineTheme {
                val settings by model.settings.collectAsStateWithLifecycle()
                val usage by model.usage.collectAsStateWithLifecycle()
                val unlocked by model.settingsUnlocked.collectAsStateWithLifecycle()
                val connected by ProtectionStatus.connected.collectAsStateWithLifecycle()
                val appUnlocks by ProtectionStatus.unlocked.collectAsStateWithLifecycle()
                val error by model.error.collectAsStateWithLifecycle()
                var explainAccessibility by remember { mutableStateOf(false) }
                if (unlocked && settings != null) {
                    SettingsScreen(settings!!, usage, model::setDuration, model::toggle,
                        onLock = { model.settingsUnlocked.value = false }, onRefresh = model::refresh,
                        error = error, onDismissError = { model.error.value = null })
                } else {
                    UnlockScreen("Unlock quarantine settings", settings?.holdSeconds ?: 10, usage,
                        enabled = settings != null,
                        onUnlocked = { model.settingsUnlocked.value = true }) {
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                        if (appUnlocks.isNotEmpty()) {
                            OutlinedButton(onClick = { QuarantineService.instance?.relockAll() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Lock unlocked apps (${appUnlocks.size})")
                            }
                        }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (connected) "App blocking is active" else "App blocking is off", style = MaterialTheme.typography.titleMedium)
                                Text("${settings?.packages?.size ?: 0} apps in quarantine · ${appUnlocks.size} temporarily unlocked")
                                if (!connected) {
                                    Text("Enable Accessibility access so Quarantine can show its unlock screen over selected apps.")
                                    Button(onClick = { explainAccessibility = true }) { Text("Enable app blocking") }
                                }
                                if (!usage.hasAccess) {
                                    Text("Usage access lets Quarantine read seven-day app totals. Everything stays on this device.")
                                    OutlinedButton(onClick = {
                                        openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                            "package:$packageName".toUri()))
                                    }) { Text("Allow usage access") }
                                }
                                if (!notificationsAllowed) {
                                    Text("Allow notifications to relock an unlocked app without opening Quarantine.")
                                    OutlinedButton(onClick = ::requestNotifications) { Text("Allow notifications") }
                                }
                                TextButton(onClick = model::refresh) { Text("Refresh screen time") }
                            }
                        }
                    }
                }
                if (explainAccessibility) AlertDialog(
                    onDismissRequest = { explainAccessibility = false },
                    title = { Text("Allow app blocking") },
                    text = { Text(getString(R.string.accessibility_description)) },
                    confirmButton = { TextButton(onClick = {
                        explainAccessibility = false
                        openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Agree and open settings") } },
                    dismissButton = { TextButton(onClick = { explainAccessibility = false }) { Text("Cancel") } },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        model.refresh()
        QuarantineService.instance?.refreshNotifications()
    }
    override fun onPause() {
        model.settingsUnlocked.value = false
        super.onPause()
    }
    private fun updatePermissions() { notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled() }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}
