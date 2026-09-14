package no.boostai.sdk

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.boostai.sdk.ChatBackend.Objects.File
import no.boostai.sdk.UI.FileUploadFragment
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileUploadRestoreTest {

    /** FileUploadFragment had no zero-argument constructor, so recreating the activity while an
     *  upload chip was on screen crashed with Fragment.InstantiationException. */
    @Test
    fun recreatingWithAnUploadChipOnScreenDoesNotCrash() {
        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .add(
                        android.R.id.content,
                        FileUploadFragment(File("a.pdf", "application/pdf", "", true)),
                        "chip"
                    )
                    .commitNow()
            }

            // Pre-fix this recreate crashed the process while instantiating the fragment
            scenario.recreate()

            scenario.onActivity { activity ->
                assertNotNull(
                    "the restored chip exists (ChatViewFragment discards its own separately)",
                    activity.supportFragmentManager.findFragmentByTag("chip")
                )
            }
        }
    }
}
