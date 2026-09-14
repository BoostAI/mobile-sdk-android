package no.boostai.sdk

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.ElementType
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.ChatBackend.Objects.Response.Payload
import no.boostai.sdk.ChatBackend.Objects.Response.Response
import no.boostai.sdk.ChatBackend.Objects.Response.SourceType
import no.boostai.sdk.UI.ChatMessageFragment
import no.boostai.sdk.UI.ChatViewFragment
import no.boostai.sdk.UI.ChatViewFragmentDelegate
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The thumbs up/down row must actually be on screen for a bot answer, whatever the answer's last
 * element happens to be. A message that ends in action links is the common case in a real
 * conversation, and is the one that regressed: the row was made visible but laid out zero pixels
 * wide.
 */
@RunWith(AndroidJUnit4::class)
class MessageFeedbackVisibilityTest {

    private val textElement = Element(Payload(text = "Here is your answer."), ElementType.TEXT)

    private val linksElement = Element(
        Payload(
            links = arrayListOf(
                Link("l-1", "Open an account", LinkType.ACTION_LINK),
                Link("l-2", "Something else", LinkType.ACTION_LINK)
            )
        ),
        ElementType.LINKS
    )

    private fun botAnswer(vararg elements: Element) =
        Response("answer", SourceType.BOT, "en-US", elements.toList())

    /**
     * A delegate written against the deprecated API, which keeps the conversation on the fragment
     * rendering path.
     */
    private class FragmentPathDelegate : ChatViewFragmentDelegate {
        @Deprecated("Deprecated in the SDK; exercised here on purpose")
        override fun getChatMessageFragment(response: Response, animated: Boolean): Fragment =
            ChatMessageFragment(
                response,
                animated,
                isBlocked = false,
                isClient = response.source == SourceType.CLIENT
            )

        override fun getSettingsFragment(): Fragment? = null
        override fun getFeedbackFragment(): Fragment? = null
    }

    /** Render [response] as a plain bot answer — not a welcome message — and measure the row. */
    private fun feedbackRowWidthFor(
        response: Response,
        animated: Boolean,
        delegate: ChatViewFragmentDelegate? = null
    ): Int {
        var width = -1

        ChatBackend.messages = ArrayList<APIMessage>()
        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = ChatViewFragment(delegate = delegate)
                    fragment.startConversationOnLoad = false
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, fragment, "chat")
                        .commitNow()

                    fragment.render(
                        response,
                        animated,
                        isWelcomeMessage = false,
                        isAwaitingFiles = false
                    )
                }

                // Animated messages reveal their elements over time
                Thread.sleep(if (animated) 3000 else 1000)

                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    val rows = visibleFeedbackRows(root)
                    assertTrue("no visible feedback row was rendered", rows.isNotEmpty())
                    // The narrowest row is the one that matters: any row laid out zero pixels wide
                    // is a hidden set of thumbs.
                    width = rows.minOf { it.width }
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
        }

        return width
    }

    private fun visibleFeedbackRows(root: ViewGroup): List<View> {
        val found = ArrayList<View>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.id == R.id.chat_message_feedback_row && child.visibility == View.VISIBLE) {
                found.add(child)
            }
            if (child is ViewGroup) found.addAll(visibleFeedbackRows(child))
        }
        return found
    }

    @Test
    fun textOnlyAnswerShowsFeedback() {
        val width = feedbackRowWidthFor(botAnswer(textElement), animated = false)
        println("feedback row width, text answer: $width")
        assertTrue("feedback row was laid out $width px wide", width > 0)
    }

    @Test
    fun answerEndingInButtonsShowsFeedback() {
        val width = feedbackRowWidthFor(botAnswer(textElement, linksElement), animated = false)
        println("feedback row width, answer with buttons: $width")
        assertTrue("feedback row was laid out $width px wide", width > 0)
    }

    @Test
    fun animatedAnswerEndingInButtonsShowsFeedback() {
        val width = feedbackRowWidthFor(botAnswer(textElement, linksElement), animated = true)
        println("feedback row width, animated answer with buttons: $width")
        assertTrue("feedback row was laid out $width px wide", width > 0)
    }

    // ------------------------------------------------- the deprecated fragment path

    @Test
    fun textOnlyAnswerShowsFeedbackOnTheFragmentPath() {
        val width = feedbackRowWidthFor(
            botAnswer(textElement),
            animated = false,
            delegate = FragmentPathDelegate()
        )
        println("feedback row width, text answer (fragments): $width")
        assertTrue("feedback row was laid out $width px wide", width > 0)
    }

    @Test
    fun answerEndingInButtonsShowsFeedbackOnTheFragmentPath() {
        val width = feedbackRowWidthFor(
            botAnswer(textElement, linksElement),
            animated = false,
            delegate = FragmentPathDelegate()
        )
        println("feedback row width, answer with buttons (fragments): $width")
        assertTrue("feedback row was laid out $width px wide", width > 0)
    }

    @Test
    fun animatedAnswerEndingInButtonsShowsFeedbackOnTheFragmentPath() {
        val width = feedbackRowWidthFor(
            botAnswer(textElement, linksElement),
            animated = true,
            delegate = FragmentPathDelegate()
        )
        println("feedback row width, animated answer with buttons (fragments): $width")
        assertTrue("feedback row was laid out $width px wide", width > 0)
    }
}
