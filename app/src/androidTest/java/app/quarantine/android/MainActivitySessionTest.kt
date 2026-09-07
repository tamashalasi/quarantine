package app.quarantine.android

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySessionTest {
    @Test fun settingsRelockWhenActivityPauses() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].settingsUnlocked.value = true
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                assertFalse(ViewModelProvider(activity)[MainViewModel::class.java].settingsUnlocked.value)
            }
        }
    }
}
