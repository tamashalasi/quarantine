package app.quarantine.android.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import app.quarantine.android.core.AppInfo
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UsageSnapshot(
    val apps: List<AppInfo> = emptyList(),
    val millis: Map<String, Long> = emptyMap(),
    val hasAccess: Boolean = false,
    val hasHistory: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val totalMillis: Long get() = apps.sumOf { millis[it.packageName] ?: 0L }
    val topApps: List<AppInfo> get() = apps.filter { (millis[it.packageName] ?: 0L) > 0 }
        .sortedWith(compareByDescending<AppInfo> { millis[it.packageName] ?: 0L }.thenBy { it.name }).take(3)
}

class UsageRepository(private val context: Context) {
    @Suppress("DEPRECATION") // Available across the entire supported API range.
    fun hasAccess(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED

    @Suppress("DEPRECATION")
    suspend fun load(): UsageSnapshot = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homePackages = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        ).map { it.activityInfo.packageName }.toSet()
        val protected = homePackages + setOf(context.packageName, "com.android.settings", "com.android.systemui")
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.activityInfo.packageName !in protected) }
        if (!hasAccess()) return@withContext UsageSnapshot(apps = apps, loading = false)
        try {
            val start = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val stats = context.getSystemService(UsageStatsManager::class.java)
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis()).orEmpty()
            val millis = stats.groupBy { it.packageName }
                .mapValues { (_, rows) -> rows.sumOf { it.totalTimeInForeground.coerceAtLeast(0) } }
            UsageSnapshot(apps, millis, hasAccess = true, hasHistory = stats.isNotEmpty(), loading = false)
        } catch (_: SecurityException) {
            UsageSnapshot(apps = apps, loading = false, error = "Usage access is unavailable. Enable it in Android Settings.")
        } catch (_: RuntimeException) {
            UsageSnapshot(apps = apps, hasAccess = true, loading = false, error = "Screen time could not be read. Try refreshing.")
        }
    }
}
