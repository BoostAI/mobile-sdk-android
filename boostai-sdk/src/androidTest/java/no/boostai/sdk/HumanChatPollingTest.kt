package no.boostai.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.ChatBackend.Objects.Response.ChatStatus
import no.boostai.sdk.ChatBackend.Objects.Response.ConversationResult
import no.boostai.sdk.ChatBackend.Objects.Response.ConversationState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * The human-chat poll loop end to end: a response that puts the conversation in human chat must
 * start the timer, and the timer must keep firing every pollInterval until polling is stopped.
 * The domain resolves nowhere, so every poll surfaces as an observer onFailure — which is the
 * observable signal that a POLL was actually attempted.
 */
@RunWith(AndroidJUnit4::class)
class HumanChatPollingTest {

    private fun humanChatMessage() = APIMessage(
        conversation = ConversationResult(
            id = "c-1",
            reference = null,
            state = ConversationState(chatStatus = ChatStatus.ASSIGNED_TO_HUMAN, poll = true)
        )
    )

    @Test
    fun humanChatKeepsPollingUntilStopped() {
        val domain = ChatBackend.domain
        ChatBackend.messages = ArrayList()
        // A domain unique to this test: failures published by ChatBackend carry no origin, so a
        // straggling request from an earlier test failing inside our window would otherwise be
        // counted as a poll and could mask a dead timer. UnknownHostException messages contain
        // the host name, which is what the filter keys on.
        ChatBackend.domain = "human-chat-poll-test.invalid"

        val pollAttempts = AtomicInteger()
        val observer = object : ChatBackend.MessageObserver {
            override fun onMessageReceived(backend: ChatBackend, message: APIMessage) {}
            override fun onFailure(backend: ChatBackend, error: Exception) {
                if (error.message?.contains("human-chat-poll-test.invalid") == true) {
                    pollAttempts.incrementAndGet()
                }
            }
        }
        ChatBackend.addMessageObserver(observer)

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                // What onResume does when the chat is visible, then the server's human-chat reply
                ChatBackend.allowPolling()
                ChatBackend.handleApiMessage(humanChatMessage())
            }

            // Two firings at the default 2.5 s interval fit comfortably inside this window
            Thread.sleep(7000)

            assertTrue(
                "expected repeated poll attempts, saw ${pollAttempts.get()} — " +
                        "the human-chat poll timer is not running",
                pollAttempts.get() >= 2
            )
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ChatBackend.stopPolling()
                ChatBackend.resetConversationState()
                ChatBackend.allowPolling()
            }
            // Let a poll fired just before the stop fail inside this test rather than surface
            // as a stray banner in whichever test runs next
            Thread.sleep(500)
            ChatBackend.removeMessageObserver(observer)
            ChatBackend.domain = domain
            ChatBackend.messages = ArrayList()
        }
    }
}
