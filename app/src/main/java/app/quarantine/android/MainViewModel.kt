package app.quarantine.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quarantine.android.core.QuarantineSettings
import app.quarantine.android.data.UsageSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuarantineApplication
    private val mutableSettings = MutableStateFlow<QuarantineSettings?>(null)
    val settings = mutableSettings.asStateFlow()
    private val mutableUsage = MutableStateFlow(UsageSnapshot())
    val usage = mutableUsage.asStateFlow()
    val settingsUnlocked = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            try { app.settings.settings.collect { mutableSettings.value = it } }
            catch (_: java.io.IOException) { error.value = "Settings could not be read. Restart Quarantine to retry." }
        }
        refresh()
    }
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { mutableUsage.value = app.usage.load() }
    }
    fun setDuration(seconds: Int) {
        if (!settingsUnlocked.value) return
        viewModelScope.launch {
            try { app.settings.setDuration(seconds) }
            catch (_: java.io.IOException) { error.value = "Could not save the unlock time." }
        }
    }
    fun toggle(packageName: String) {
        if (!settingsUnlocked.value || usage.value.apps.none { it.packageName == packageName && it.canQuarantine }) return
        viewModelScope.launch {
            try { app.settings.toggle(packageName) }
            catch (_: java.io.IOException) { error.value = "Could not save the app selection." }
        }
    }
}
