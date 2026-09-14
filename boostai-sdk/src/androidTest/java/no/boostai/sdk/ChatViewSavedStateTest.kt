package no.boostai.sdk

import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.ElementType
import no.boostai.sdk.ChatBackend.Objects.Response.FunctionType
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.ChatBackend.Objects.Response.Payload
import no.boostai.sdk.ChatBackend.Objects.Response.Response
import no.boostai.sdk.ChatBackend.Objects.Response.SourceType
import no.boostai.sdk.UI.ChatMessagePartView
import no.boostai.sdk.UI.ChatMessageView
import no.boostai.sdk.UI.ChatViewFragment
import no.boostai.sdk.UI.ChatViewFragmentDelegate
import no.boostai.sdk.UI.IChatMessagePartFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the rendering of a conversation, and guards the fix for the
 * `TransactionTooLargeException` reported against long conversations.
 *
 * The Binder transaction limit is 1 MB for the *whole* activity state, shared with everything else
 * the host app saves. Messages are drawn as views precisely so the chat view's share of it stays
 * flat no matter how long the conversation gets.
 */
@RunWith(AndroidJUnit4::class)
class ChatViewSavedStateTest {

    private val binderLimitBytes = 1024 * 1024
    private val conversationLength = 400

    // ------------------------------------------------------------------ fixtures

    private fun response(index: Int, vararg elements: Element) = Response(
        id = "response-$index",
        source = SourceType.BOT,
        language = "en-US",
        elements = elements.toList()
    )

    private fun textElement(index: Int) =
        Element(Payload(text = "Message number $index. ".repeat(20)), ElementType.TEXT)

    private fun buttonsElement(index: Int, count: Int = 4) = Element(
        Payload(
            links = ArrayList(
                (0 until count).map {
                    Link("link-$index-$it", "Button $it for message $index", LinkType.ACTION_LINK)
                }
            )
        ),
        ElementType.LINKS
    )

    /** The expensive shape: a text answer followed by a button menu. */
    private fun buttonHeavyResponse(index: Int) =
        response(index, textElement(index), buttonsElement(index))

    // ------------------------------------------------------------------ helpers

    private fun savedStateSize(manager: FragmentManager, fragment: ChatViewFragment): Int {
        val state = manager.saveFragmentInstanceState(fragment)
        val parcel = Parcel.obtain()
        return try {
            parcel.writeParcelable(state, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    private fun messagesContainer(fragment: ChatViewFragment): LinearLayout =
        fragment.requireView().findViewById(R.id.chat_messages)

    /**
     * Replay a conversation into a chat view, exactly as `startOrResumeConversation` does after a
     * restore, and run [body] against it.
     */
    private fun withConversation(
        responses: List<Response>,
        delegate: ChatViewFragmentDelegate? = null,
        body: (ChatViewFragment, FragmentManager) -> Unit
    ) {
        ChatBackend.messages = ArrayList(responses.map { APIMessage(response = it) })

        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = ChatViewFragment(delegate = delegate)
                    // Keep the test offline — no START/RESUME round trip.
                    fragment.startConversationOnLoad = false

                    val manager = activity.supportFragmentManager
                    manager.beginTransaction()
                        .add(android.R.id.content, fragment, "chat")
                        .commitNow()

                    fragment.startOrResumeConversation()
                    manager.executePendingTransactions()
                    fragment.childFragmentManager.executePendingTransactions()

                    body(fragment, manager)
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
        }
    }

    // ------------------------------------------------------------------ saved state

    @Test
    fun theWholeConversationRendersWithoutGrowingTheSavedState() {
        val conversation = (0 until conversationLength).map { buttonHeavyResponse(it) }

        withConversation(conversation) { fragment, manager ->
            assertEquals(
                "whole conversation should be replayed into the model",
                conversationLength,
                fragment.responses.size
            )
            assertEquals(
                "every message should be on screen — there is no paging",
                conversationLength,
                messagesContainer(fragment).childCount
            )

            val size = savedStateSize(manager, fragment)
            println("saved state for $conversationLength rendered messages: $size bytes")
            assertTrue(
                "Saved state was $size bytes; rendering messages as views should keep it negligible",
                size < 64 * 1024
            )
            assertTrue(
                "Saved state must stay far below the $binderLimitBytes byte Binder limit",
                size < binderLimitBytes / 8
            )
        }
    }

    @Test
    fun savedStateBarelyGrowsWithConversationLength() {
        var short = 0
        var long = 0

        withConversation((0 until 10).map { buttonHeavyResponse(it) }) { fragment, manager ->
            short = savedStateSize(manager, fragment)
        }
        withConversation((0 until 400).map { buttonHeavyResponse(it) }) { fragment, manager ->
            long = savedStateSize(manager, fragment)
        }

        println("saved state: 10 messages = $short bytes, 400 messages = $long bytes")
        assertTrue(
            "40x the messages should not meaningfully grow the saved state ($short -> $long)",
            long < short + 8 * 1024
        )
    }

    // ------------------------------------------------------------------ element types

    @Test
    fun everyElementTypeRenders() {
        val carouselJson = Json.parseToJsonElement(
            """
            {"template":"carousel","elements":[
              {"id":"1","title":"First card","body":"Body one","buttonText":"Go","buttonActionId":"a1"},
              {"id":"2","title":"Second card","body":"Body two","buttonText":"Go","buttonActionId":"a2"}
            ]}
            """.trimIndent()
        )
        val cardJson = Json.parseToJsonElement(
            """
            {"heading":{"text":"Card heading"},"body":{"text":"Card body"},
             "link":{"text":"Open","url":"https://boost.ai"}}
            """.trimIndent()
        )

        val cases = listOf(
            "text" to Element(Payload(text = "Plain text"), ElementType.TEXT),
            "html" to Element(
                Payload(html = "Rich <a href=\"https://boost.ai\">text</a>"),
                ElementType.HTML
            ),
            "links" to buttonsElement(0, 3),
            "consent" to Element(
                Payload(
                    links = arrayListOf(
                        Link("yes", "I agree", LinkType.ACTION_LINK, FunctionType.APPROVE),
                        Link("no", "No thanks", LinkType.ACTION_LINK, FunctionType.DENY)
                    )
                ),
                ElementType.LINKS
            ),
            "image" to Element(
                Payload(url = "https://example.com/image.png"),
                ElementType.IMAGE
            ),
            "video" to Element(
                Payload(source = "youtube", url = "https://www.youtube.com/watch?v=gcugRVtkBtU"),
                ElementType.VIDEO
            ),
            "carousel" to Element(Payload(json = carouselJson), ElementType.JSON),
            "genericCard" to Element(Payload(json = cardJson), ElementType.JSON)
        )

        val conversation = cases.mapIndexed { index, (_, element) -> response(index, element) }

        withConversation(conversation) { fragment, _ ->
            val container = messagesContainer(fragment)
            assertEquals("one view per message", cases.size, container.childCount)

            cases.forEachIndexed { index, (name, element) ->
                assertTrue("$name should be renderable", ChatMessagePartView.canRender(element))

                val messageView = container.getChildAt(index)
                assertTrue("$name should render a ChatMessageView", messageView is ChatMessageView)

                val part = firstPartView(messageView as ViewGroup)
                assertNotNull("$name produced no message part", part)
                assertTrue(
                    "$name rendered an empty part",
                    countLeafViews(part as ViewGroup) > 0
                )
            }
        }
    }

    private fun firstPartView(root: ViewGroup): View? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ChatMessagePartView) return child
            if (child is ViewGroup) firstPartView(child)?.let { return it }
        }
        return null
    }

    private fun countLeafViews(root: ViewGroup): Int {
        var count = 0
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.visibility == View.GONE) continue
            count += if (child is ViewGroup) countLeafViews(child) else 1
        }
        return count
    }

    // ------------------------------------------------------------------ delegate compatibility

    /** A delegate that overrides nothing — the common case — must get the view path. */
    private class ViewDelegate : ChatViewFragmentDelegate {
        override fun getSettingsFragment(): Fragment? = null
        override fun getFeedbackFragment(): Fragment? = null
    }

    /** A delegate written against the deprecated API must keep working, on the fragment path. */
    private class LegacyFragmentDelegate : ChatViewFragmentDelegate {
        var asked = 0

        @Deprecated("Deprecated in the SDK; exercised here on purpose")
        override fun getChatMessageFragment(response: Response, animated: Boolean): Fragment? {
            asked++
            return ChatMessageStub()
        }

        override fun getSettingsFragment(): Fragment? = null
        override fun getFeedbackFragment(): Fragment? = null
    }

    class ChatMessageStub : Fragment()

    @Test
    fun aDelegateWithoutOverridesGetsTheViewPath() {
        withConversation((0 until 5).map { buttonHeavyResponse(it) }, ViewDelegate()) { fragment, _ ->
            val container = messagesContainer(fragment)
            assertEquals(5, container.childCount)
            assertTrue(
                "an unmodified delegate should not force the fragment path",
                container.getChildAt(0) is ChatMessageView
            )
        }
    }

    @Test
    fun aDelegateSupplyingMessageFragmentsKeepsTheFragmentPath() {
        val delegate = LegacyFragmentDelegate()

        withConversation((0 until 5).map { buttonHeavyResponse(it) }, delegate) { fragment, _ ->
            assertTrue("the deprecated hook should still be consulted", delegate.asked > 0)
            assertEquals(
                "messages should be hosted as fragments, not views",
                5,
                fragment.childFragmentManager.fragments.count { it is ChatMessageStub }
            )
            assertEquals(
                "no message views should have been added",
                0,
                messagesContainer(fragment).children().count { it is ChatMessageView }
            )
        }
    }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    // ------------------------------------------------------------------ restore

    /**
     * After a configuration change the conversation is replayed from `ChatBackend.messages`; the
     * replay must replace what was on screen, not stack on top of it.
     */
    @Test
    fun discardedMessagesAreRebuiltFromTheBackend() {
        withConversation((0 until 12).map { buttonHeavyResponse(it) }) { fragment, _ ->
            val rendered = messagesContainer(fragment).childCount
            assertEquals(12, rendered)

            fragment.discardRestoredMessageFragments()
            messagesContainer(fragment).removeAllViews()

            fragment.responses.clear()
            fragment.messages.clear()
            fragment.startOrResumeConversation()
            fragment.childFragmentManager.executePendingTransactions()

            assertEquals(
                "conversation was not rebuilt",
                12,
                fragment.responses.size
            )
            assertEquals(
                "rebuild produced a different view tree",
                rendered,
                messagesContainer(fragment).childCount
            )
        }
    }
}
