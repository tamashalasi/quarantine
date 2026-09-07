package app.quarantine.android

import android.app.Application
import app.quarantine.android.data.SettingsRepository
import app.quarantine.android.data.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class QuarantineApplication : Application() {
    val settings by lazy { SettingsRepository(this) }
    val usage by lazy { UsageRepository(this) }
}

/** Read-only UI projection; the accessibility service alone owns temporary grants. */
object ProtectionStatus {
    internal val mutableConnected = MutableStateFlow(false)
    internal val mutableUnlocked = MutableStateFlow<Set<String>>(emptySet())
    val connected = mutableConnected.asStateFlow()
    val unlocked = mutableUnlocked.asStateFlow()
}
