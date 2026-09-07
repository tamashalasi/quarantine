package app.quarantine.android.core

data class QuarantineSettings(val holdSeconds: Int = 10, val packages: Set<String> = emptySet())

fun validHoldSeconds(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..300 }

/** Uses monotonic time supplied by the caller; never persists a partial hold. */
class HoldTimer(private val durationMillis: Long) {
    private var startedAt: Long? = null
    init { require(durationMillis > 0) }
    fun start(now: Long) { if (startedAt == null) startedAt = now }
    fun reset() { startedAt = null }
    fun remaining(now: Long): Long = startedAt?.let {
        (durationMillis - (now - it).coerceAtLeast(0)).coerceIn(0, durationMillis)
    } ?: durationMillis
    fun complete(now: Long): Boolean = startedAt != null && remaining(now) == 0L
}

/** Temporary grants intentionally survive foreground changes, but never process death. */
class UnlockSessions {
    private val grants = mutableSetOf<String>()
    fun unlock(packageName: String, quarantined: Set<String>): Boolean =
        packageName in quarantined && grants.add(packageName)
    fun isBlocked(packageName: String, quarantined: Set<String>): Boolean =
        packageName in quarantined && packageName !in grants
    fun relock(packageName: String) { grants.remove(packageName) }
    fun retain(quarantined: Set<String>) { grants.retainAll(quarantined) }
    fun clear() { grants.clear() }
    fun snapshot(): Set<String> = grants.toSet()
}

enum class AppFilter(val label: String) {
    ALL("All"), QUARANTINED("In quarantine"), AVAILABLE("Not in quarantine")
}

data class AppInfo(val packageName: String, val name: String, val canQuarantine: Boolean = true)

fun sortedApps(
    apps: List<AppInfo>, usage: Map<String, Long>, quarantined: Set<String>, filter: AppFilter,
): List<AppInfo> = apps.filter {
    when (filter) {
        AppFilter.ALL -> true
        AppFilter.QUARANTINED -> it.packageName in quarantined
        AppFilter.AVAILABLE -> it.packageName !in quarantined
    }
}.sortedWith(compareByDescending<AppInfo> { usage[it.packageName] ?: 0L }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.packageName })

fun formatDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return when {
        millis <= 0 -> "0 min"
        minutes == 0L -> "<1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}
