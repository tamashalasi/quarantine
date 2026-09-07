package app.quarantine.android

import androidx.test.platform.app.InstrumentationRegistry
import app.quarantine.android.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test fun editsSurviveRepositoryRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepository(context)
        val original = repository.settings.first()
        val pkg = "quarantine.test.persistence"
        try {
            repository.setDuration(37)
            if (pkg !in repository.settings.first().packages) repository.toggle(pkg)
            val restored = SettingsRepository(context).settings.first()
            assertEquals(37, restored.holdSeconds)
            assertTrue(pkg in restored.packages)
        } finally {
            repository.setDuration(original.holdSeconds)
            if ((pkg in repository.settings.first().packages) != (pkg in original.packages)) repository.toggle(pkg)
        }
    }
}
