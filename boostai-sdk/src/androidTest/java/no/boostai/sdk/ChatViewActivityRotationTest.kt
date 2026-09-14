package no.boostai.sdk

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.UI.ChatViewActivity
import no.boostai.sdk.UI.ChatViewFragment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatViewActivityRotationTest {

    /** ChatViewActivity used to add a fresh ChatViewFragment in every onCreate, so each rotation
     *  stacked one more conversation on top of the restored one. */
    @Test
    fun recreatingChatViewActivityKeepsASingleChatFragment() {
        val domain = ChatBackend.domain
        ChatBackend.messages = ArrayList<APIMessage>()
        ChatBackend.domain = "example.invalid"

        try {
            ActivityScenario.launch(ChatViewActivity::class.java).use { scenario ->
                scenario.recreate()
                scenario.recreate()

                scenario.onActivity { activity ->
                    val chatFragments = activity.supportFragmentManager.fragments
                        .filterIsInstance<ChatViewFragment>()
                    assertEquals(
                        "two rotations must still leave exactly one chat fragment",
                        1,
                        chatFragments.size
                    )
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
            ChatBackend.domain = domain
        }
    }
}
