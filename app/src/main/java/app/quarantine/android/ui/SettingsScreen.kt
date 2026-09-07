package app.quarantine.android.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.quarantine.android.core.*
import app.quarantine.android.data.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: QuarantineSettings, usage: UsageSnapshot, onDuration: (Int) -> Unit,
                   onToggle: (String) -> Unit, onLock: () -> Unit, onRefresh: () -> Unit,
                   error: String?, onDismissError: () -> Unit) {
    var durationText by remember { mutableStateOf(settings.holdSeconds.toString()) }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var menuOpen by remember { mutableStateOf(false) }
    val apps = remember(usage, settings.packages, filter) { sortedApps(usage.apps, usage.millis, settings.packages, filter) }
    Scaffold(topBar = { TopAppBar(title = { Text("Quarantine settings") }, actions = {
        TextButton(onClick = onLock) { Text("Lock settings") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Settings unlocked", style = MaterialTheme.typography.titleMedium)
                Text("Changes save automatically. Leaving this screen locks settings.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { text ->
                        durationText = text
                        validHoldSeconds(text)?.let(onDuration)
                    },
                    label = { Text("Hold time in seconds") },
                    supportingText = { Text("1–300 seconds · applies to settings and apps") },
                    isError = validHoldSeconds(durationText) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (error != null) item {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissError) { Text("Dismiss") }
            }
            item { UsageSummary(usage) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }) { Text("Show: ${filter.label}") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            AppFilter.entries.forEach { choice ->
                                DropdownMenuItem(text = { Text(choice.label) }, onClick = { filter = choice; menuOpen = false })
                            }
                        }
                    }
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
                Text("Most screen time first", style = MaterialTheme.typography.bodySmall)
            }
            if (apps.isEmpty()) item { Text(if (usage.loading) "Loading apps…" else "No apps in this filter.") }
            items(apps, key = { it.packageName }) { app ->
                AppRow(app, usage, app.packageName in settings.packages) { onToggle(app.packageName) }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, usage: UsageSnapshot, selected: Boolean, onToggle: () -> Unit) {
    val pm = LocalContext.current.packageManager
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            try { pm.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().toggleable(value = selected, enabled = app.canQuarantine,
            role = Role.Checkbox, onValueChange = { onToggle() }).padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                ?: Spacer(Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                Text(when {
                    !usage.hasAccess || usage.error != null -> "Screen time unavailable"
                    !usage.hasHistory -> "No screen-time history"
                    else -> "${formatDuration(usage.millis[app.packageName] ?: 0L)} in the past 7 days"
                }, style = MaterialTheme.typography.bodySmall)
                if (!app.canQuarantine) Text("Essential app · always available", style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = selected, onCheckedChange = null, enabled = app.canQuarantine)
        }
    }
}
