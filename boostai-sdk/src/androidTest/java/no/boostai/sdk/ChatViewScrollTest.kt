package no.boostai.sdk

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.Response.*
import no.boostai.sdk.UI.ChatMessageView
import no.boostai.sdk.UI.ChatViewFragment
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A new message must bring the conversation back to the bottom — unless its newest part is taller
 * than the screen, which must then be shown from its top.
 */
@RunWith(AndroidJUnit4::class)
class ChatViewScrollTest {

    private fun response(index: Int) = Response(
        id = "r-$index",
        source = SourceType.BOT,
        language = "en-US",
        elements = listOf(
            Element(Payload(text = "Message number $index. ".repeat(8)), ElementType.TEXT),
            Element(Payload(text = "Second element of message $index."), ElementType.TEXT)
        )
    )

    private fun distanceFromBottom(fragment: ChatViewFragment): Int {
        val scrollView = fragment.requireView()
            .findViewById<ScrollView>(R.id.chat_messages_scrollview)
        val content = fragment.requireView().findViewById<LinearLayout>(R.id.chat_messages)
        return content.bottom - (scrollView.scrollY + scrollView.height)
    }

    private fun overflows(fragment: ChatViewFragment): Boolean {
        val scrollView = fragment.requireView()
            .findViewById<ScrollView>(R.id.chat_messages_scrollview)
        val content = fragment.requireView().findViewById<LinearLayout>(R.id.chat_messages)
        return content.height > scrollView.height
    }

    /** Pixels left below the fold — only meaningful when the conversation overflows the screen. */
    private fun runScrollScenario(animated: Boolean): Int {
        var remaining = -1
        var contentOverflows = false

        // Enough history that the conversation overflows the screen
        ChatBackend.messages = ArrayList((0 until 15).map { APIMessage(response = response(it)) })

        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                lateinit var fragment: ChatViewFragment
                scenario.onActivity { activity ->
                    fragment = ChatViewFragment()
                    fragment.startConversationOnLoad = false
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, fragment, "chat").commitNow()
                    fragment.startOrResumeConversation()
                }
                Thread.sleep(1500)

                // A new message arrives
                scenario.onActivity {
                    val next = response(99)
                    fragment.responses.add(next)
                    fragment.render(next, animated, isWelcomeMessage = false, isAwaitingFiles = false)
                }
                Thread.sleep(3000)

                scenario.onActivity {
                    remaining = distanceFromBottom(fragment)
                    contentOverflows = overflows(fragment)
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
        }

        // On a screen tall enough to show the whole conversation the distance is negative and
        // the assertion would pass without proving anything
        assumeTrue("Conversation does not overflow this screen", contentOverflows)
        return remaining
    }

    @Test
    fun anIncomingMessageScrollsToTheBottom() {
        val remaining = runScrollScenario(animated = true)
        println("SCROLL animated: $remaining px left below the fold")
        assertTrue("Not scrolled to the bottom — $remaining px remain below", remaining <= 8)
    }

    @Test
    fun aReplayedMessageScrollsToTheBottom() {
        val remaining = runScrollScenario(animated = false)
        println("SCROLL not animated: $remaining px left below the fold")
        assertTrue("Not scrolled to the bottom — $remaining px remain below", remaining <= 8)
    }

    /** A part taller than the screen, such as a carousel above an open keyboard, must not be
     *  scrolled past: its top (the image and title) has to stay in view. */
    @Test
    fun aTallPartIsShownFromItsTop() {
        ChatBackend.messages = ArrayList((0 until 5).map { APIMessage(response = response(it)) })
        var topInView = Int.MIN_VALUE
        var partTallerThanScreen = false
        val tall = Response(
            id = "r-tall",
            source = SourceType.BOT,
            language = "en-US",
            elements = listOf(
                Element(Payload(text = "Short intro."), ElementType.TEXT),
                Element(Payload(text = "A very tall part. ".repeat(300)), ElementType.TEXT)
            )
        )

        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                lateinit var fragment: ChatViewFragment
                scenario.onActivity { activity ->
                    fragment = ChatViewFragment()
                    fragment.startConversationOnLoad = false
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, fragment, "chat").commitNow()
                    fragment.startOrResumeConversation()
                }
                Thread.sleep(1500)

                scenario.onActivity {
                    fragment.responses.add(tall)
                    fragment.render(tall, true, isWelcomeMessage = false, isAwaitingFiles = false)
                }
                Thread.sleep(5000)

                scenario.onActivity {
                    val scrollView = fragment.requireView()
                        .findViewById<ScrollView>(R.id.chat_messages_scrollview)
                    val message = fragment.requireView()
                        .findViewWithTag<ChatMessageView>("r-tall")
                    val part = message.latestPart
                    assertNotNull(part)
                    partTallerThanScreen = part!!.height > scrollView.height

                    val rect = android.graphics.Rect()
                    part.getDrawingRect(rect)
                    scrollView.offsetDescendantRectToMyCoords(part, rect)
                    topInView = rect.top - scrollView.scrollY
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
        }

        assumeTrue("The part fits on this screen", partTallerThanScreen)
        println("SCROLL tall part: top is $topInView px below the top of the scroll view")
        assertTrue("The top of the tall part was scrolled out of view ($topInView px)", topInView >= 0)
        assertTrue("Scrolled short of the tall part's top ($topInView px)", topInView <= 100)
    }
}
