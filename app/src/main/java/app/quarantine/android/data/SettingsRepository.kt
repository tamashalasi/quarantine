package app.quarantine.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.quarantine.android.core.QuarantineSettings
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("quarantine")

class SettingsRepository(private val context: Context) {
    private val secondsKey = intPreferencesKey("hold_seconds")
    private val packagesKey = stringSetPreferencesKey("quarantined_packages")
    val settings = context.settingsStore.data.map {
        QuarantineSettings((it[secondsKey] ?: 10).coerceIn(1, 300), it[packagesKey] ?: emptySet())
    }
    suspend fun setDuration(seconds: Int) {
        require(seconds in 1..300)
        context.settingsStore.edit { it[secondsKey] = seconds }
    }
    suspend fun toggle(packageName: String) {
        context.settingsStore.edit {
            val packages = it[packagesKey] ?: emptySet()
            it[packagesKey] = if (packageName in packages) packages - packageName else packages + packageName
        }
    }
}
