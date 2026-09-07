package app.quarantine.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.quarantine.android.core.AppInfo
import app.quarantine.android.core.QuarantineSettings
import app.quarantine.android.data.UsageSnapshot
import app.quarantine.android.ui.HoldToUnlock
import app.quarantine.android.ui.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class QuarantineUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continuousHoldUnlocksExactlyOnce() {
        var unlocks = 0
        compose.setContent { MaterialTheme { HoldToUnlock("Unlock", 1) { unlocks++ } } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Unlock").performTouchInput { down(center) }
        advanceHoldTime(1_200)
        val tree = compose.onRoot().printToString()
        compose.runOnIdle { assertEquals(tree, 1, unlocks) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(1, unlocks) }
        compose.onNodeWithText("Unlock").performTouchInput { up() }
    }

    // Android's input dispatcher and Compose's frame clock are distinct in Robolectric.
    // Drain input/recomposition between clock advances, just as a device's event loop does.
    private fun advanceHoldTime(millis: Int) {
        compose.waitForIdle()
        repeat(millis / 100) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    @Test fun earlyReleaseResetsAndTapCannotUnlock() {
        var unlocks = 0
        compose.setContent { MaterialTheme { HoldToUnlock("Unlock", 2) { unlocks++ } } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Unlock").performTouchInput { down(center) }
        advanceHoldTime(1_000)
        compose.onNodeWithText("1 seconds remaining").assertExists()
        compose.onNodeWithText("Unlock").performTouchInput { up() }
        advanceHoldTime(100)
        compose.onNodeWithText("2 seconds remaining").assertExists()
        compose.onNodeWithText("Unlock").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertEquals(0, unlocks) }
    }

    @Test fun disablingMidHoldCancelsProgress() {
        var enabled by mutableStateOf(true)
        var unlocks = 0
        compose.setContent { MaterialTheme { HoldToUnlock("Unlock", 2, enabled) { unlocks++ } } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Unlock").performTouchInput { down(center) }
        advanceHoldTime(1_000)
        compose.onNodeWithText("1 seconds remaining").assertExists()
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertEquals(0, unlocks) }
        compose.onNodeWithText("2 seconds remaining").assertExists()
        compose.onNodeWithText("Unlock").performTouchInput { up() }
    }

    @Test fun listCanToggleAndFilterQuarantine() {
        var settings by mutableStateOf(QuarantineSettings())
        val usage = UsageSnapshot(apps = listOf(AppInfo("a", "Firefox"), AppInfo("b", "Video")),
            millis = mapOf("a" to 60_000L, "b" to 120_000L), hasAccess = true, hasHistory = true, loading = false)
        compose.setContent { MaterialTheme {
            SettingsScreen(settings, usage, {}, { pkg -> settings = settings.copy(packages = settings.packages + pkg) }, {}, {}, null, {})
        } }
        // The summary repeats app names; select the row by its unique usage label.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("1 min in the past 7 days"))
        compose.onNodeWithText("1 min in the past 7 days", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(setOf("a"), settings.packages) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Show: All"))
        compose.onNodeWithText("Show: All").performClick()
        compose.onNodeWithText("In quarantine").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("1 min in the past 7 days"))
        compose.onNodeWithText("1 min in the past 7 days", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("2 min in the past 7 days", useUnmergedTree = true).assertDoesNotExist()
    }
}
