package no.boostai.sdk

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.ElementType
import no.boostai.sdk.ChatBackend.Objects.Response.Payload
import no.boostai.sdk.ChatBackend.Objects.Response.Response
import no.boostai.sdk.ChatBackend.Objects.Response.SourceType
import no.boostai.sdk.UI.ChatMessageView
import no.boostai.sdk.UI.ChatViewFragment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Where a message and its feedback row are placed relative to the conversation around them. */
@RunWith(AndroidJUnit4::class)
class MessageSpacingTest {

    private fun botAnswer(id: String, text: String) = Response(
        id,
        SourceType.BOT,
        "en-US",
        listOf(Element(Payload(text = text), ElementType.TEXT))
    )

    private fun <T> onRenderedConversation(
        responses: List<Response>,
        body: (ViewGroup) -> T
    ): T {
        var result: T? = null

        ChatBackend.messages = ArrayList<APIMessage>()
        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = ChatViewFragment()
                    fragment.startConversationOnLoad = false
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, fragment, "chat")
                        .commitNow()

                    responses.forEach {
                        fragment.render(
                            it,
                            animated = false,
                            isWelcomeMessage = false,
                            isAwaitingFiles = false
                        )
                    }
                }

                Thread.sleep(1500)

                scenario.onActivity { activity ->
                    result = body(activity.findViewById(android.R.id.content))
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
        }

        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun top(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[1]
    }

    private fun left(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[0]
    }

    private fun <T : View> findAll(root: ViewGroup, type: Class<T>): List<T> {
        val found = ArrayList<T>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (type.isInstance(child)) found.add(type.cast(child)!!)
            if (child is ViewGroup) found.addAll(findAll(child, type))
        }
        return found
    }

    private fun findById(root: ViewGroup, id: Int): List<View> {
        val found = ArrayList<View>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.id == id) found.add(child)
            if (child is ViewGroup) found.addAll(findById(child, id))
        }
        return found
    }

    /**
     * The thumbs belong to the message above them, and must read that way: the gap up to their own
     * bubble has to be smaller than the gap down to the next message.
     */
    @Test
    fun feedbackRowSitsCloserToItsOwnMessageThanToTheNext() {
        val gaps = onRenderedConversation(
            listOf(botAnswer("a-1", "First answer."), botAnswer("a-2", "Second answer."))
        ) { root ->
            val messages = findAll(root, ChatMessageView::class.java)
            assertTrue("expected two messages, got ${messages.size}", messages.size == 2)

            // The bubble's own view carries a bottom margin, so measure from the painted edge —
            // the text and its padding — rather than from the wrapper.
            val bubble = findById(messages[0], R.id.chat_message_textview).first()
            val row = findById(messages[0], R.id.chat_message_feedback_row)
                .first { it.visibility == View.VISIBLE }

            val above = top(row) - (top(bubble) + bubble.height)
            val below = top(messages[1]) - (top(row) + row.height)
            Pair(above, below)
        }

        println("feedback row gaps: ${gaps.first} px above, ${gaps.second} px below")
        assertTrue(
            "feedback row is ${gaps.first} px below its own bubble but only ${gaps.second} px " +
                "above the next message — it reads as belonging to the wrong message",
            gaps.second > gaps.first
        )
        assertTrue("feedback row overlaps its own bubble", gaps.first >= 0)
        assertTrue("feedback row is flush against the next message", gaps.second > 0)
    }

    /**
     * A message from the user hugs the right edge however short it is — it must not float in the
     * middle of the fixed-width column its bubble sits in.
     */
    @Test
    fun shortClientMessagesAreRightAligned() {
        val edges = onRenderedConversation(
            listOf(
                Response(
                    "c-1",
                    SourceType.CLIENT,
                    "en-US",
                    listOf(Element(Payload(text = "Hi"), ElementType.TEXT))
                )
            )
        ) { root ->
            val message = findAll(root, ChatMessageView::class.java).first()
            val bubble = findById(message, R.id.chat_message_textview).first()
            val column = findById(message, R.id.chat_message_parts).first()

            Pair(left(bubble) + bubble.width, left(column) + column.width)
        }

        println("right edge: client bubble ${edges.first} px, column ${edges.second} px")
        assertTrue(
            "client bubble ends ${edges.second - edges.first} px short of the right edge",
            edges.first == edges.second
        )
    }

    // The typing indicator's alignment is fixed in `chat_server_waiting.xml`, which no longer
    // carries the 10dp left margin that set it apart from the bubbles. It is deliberately not
    // covered here: measuring it meant forcing a measure/layout pass on a live view tree, which
    // wedged the instrumentation.
}
