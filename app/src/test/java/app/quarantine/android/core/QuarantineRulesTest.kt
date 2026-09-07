package app.quarantine.android.core

import app.quarantine.android.data.UsageSnapshot
import org.junit.Assert.*
import org.junit.Test

class QuarantineRulesTest {
    @Test fun holdRequiresFullUninterruptedDuration() {
        val timer = HoldTimer(10_000)
        assertFalse(timer.complete(100_000))
        timer.start(500)
        assertEquals(1L, timer.remaining(10_499))
        assertFalse(timer.complete(10_499))
        assertTrue(timer.complete(10_500))
        assertEquals(0L, timer.remaining(20_000))
    }
    @Test fun releaseAndInterruptionDiscardProgress() {
        val timer = HoldTimer(10_000)
        timer.start(0)
        assertEquals(4_000L, timer.remaining(6_000))
        timer.reset()
        assertEquals(10_000L, timer.remaining(6_000))
        assertFalse(timer.complete(20_000))
        timer.start(20_000)
        assertFalse(timer.complete(29_999))
        assertTrue(timer.complete(30_000))
    }
    @Test fun repeatedDownDoesNotRestartAndClockCannotIncreaseRemaining() {
        val timer = HoldTimer(1_000)
        timer.start(50)
        timer.start(500)
        assertTrue(timer.complete(1_050))
        assertEquals(1_000L, timer.remaining(0))
    }
    @Test fun durationValidationHasExplicitBounds() {
        listOf("", "0", "301", "-5", "1.5", "999999999999").forEach { assertNull(validHoldSeconds(it)) }
        assertEquals(1, validHoldSeconds("1"))
        assertEquals(10, validHoldSeconds("10"))
        assertEquals(300, validHoldSeconds("300"))
    }
    @Test fun appGrantsAreIndependentAndNeverChangeQuarantineMembership() {
        val quarantined = setOf("firefox", "video")
        val sessions = UnlockSessions()
        assertTrue(sessions.isBlocked("firefox", quarantined))
        assertFalse(sessions.unlock("unknown", quarantined))
        assertTrue(sessions.unlock("firefox", quarantined))
        assertFalse(sessions.isBlocked("firefox", quarantined))
        assertTrue(sessions.isBlocked("video", quarantined))
        assertEquals(setOf("firefox", "video"), quarantined)
        sessions.relock("firefox")
        sessions.relock("firefox")
        assertTrue(sessions.isBlocked("firefox", quarantined))
    }
    @Test fun removedPackagesAndServiceRestartRevokeGrants() {
        val sessions = UnlockSessions()
        val packages = setOf("a", "b")
        packages.forEach { sessions.unlock(it, packages) }
        sessions.retain(setOf("b"))
        assertEquals(setOf("b"), sessions.snapshot())
        assertTrue(sessions.isBlocked("a", packages))
        sessions.clear()
        assertTrue(sessions.snapshot().isEmpty())
        assertTrue(UnlockSessions().isBlocked("b", packages))
    }
    @Test fun sortingAndFiltersUseUsageThenName() {
        val apps = listOf(AppInfo("z", "Zebra"), AppInfo("b", "Beta"), AppInfo("a", "alpha"))
        val usage = mapOf("a" to 1L, "b" to 1L, "z" to 100L)
        assertEquals(listOf("z", "a", "b"), sortedApps(apps, usage, setOf("a"), AppFilter.ALL).map { it.packageName })
        assertEquals(listOf("a"), sortedApps(apps, usage, setOf("a"), AppFilter.QUARANTINED).map { it.packageName })
        assertEquals(listOf("z", "b"), sortedApps(apps, usage, setOf("a"), AppFilter.AVAILABLE).map { it.packageName })
    }
    @Test fun totalsAndTopThreeUseSameProfileApps() {
        val apps = listOf("a", "b", "c", "d").map { AppInfo(it, it) }
        val snapshot = UsageSnapshot(apps, mapOf("a" to 10L, "b" to 40L, "c" to 20L, "d" to 30L, "hidden" to 999L))
        assertEquals(100L, snapshot.totalMillis)
        assertEquals(listOf("b", "d", "c"), snapshot.topApps.map { it.packageName })
    }
    @Test fun emptyUsageDoesNotInventTopApps() {
        assertTrue(UsageSnapshot(apps = listOf(AppInfo("a", "A"))).topApps.isEmpty())
        assertEquals(0L, UsageSnapshot().totalMillis)
    }
    @Test fun readableDurations() {
        assertEquals("0 min", formatDuration(0))
        assertEquals("<1 min", formatDuration(5_000))
        assertEquals("12 min", formatDuration(720_000))
        assertEquals("1 hr 1 min", formatDuration(3_660_000))
    }
}
