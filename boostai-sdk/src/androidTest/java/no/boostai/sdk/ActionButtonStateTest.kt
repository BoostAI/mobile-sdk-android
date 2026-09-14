package no.boostai.sdk

import android.view.View
import android.view.ViewGroup
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
import no.boostai.sdk.UI.ChatMessageButtonView
import no.boostai.sdk.UI.ChatViewFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Answering a button menu takes every button in the conversation out of play while the reply is on
 * its way. When it lands, an answered menu stays answered — the button that was tapped looks normal
 * again but is dead, its alternatives stay dimmed — and only untouched menus become live again.
 */
@RunWith(AndroidJUnit4::class)
class ActionButtonStateTest {

    private fun menu(id: String) = Response(
        id,
        SourceType.BOT,
        "en-US",
        listOf(
            Element(Payload(text = "Pick one."), ElementType.TEXT),
            Element(
                Payload(
                    links = arrayListOf(
                        Link("$id-a", "First choice", LinkType.ACTION_LINK),
                        Link("$id-b", "Second choice", LinkType.ACTION_LINK)
                    )
                ),
                ElementType.LINKS
            )
        )
    )

    private fun buttonViews(root: ViewGroup): List<ChatMessageButtonView> {
        val found = ArrayList<ChatMessageButtonView>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ChatMessageButtonView) found.add(child)
            if (child is ViewGroup) found.addAll(buttonViews(child))
        }
        return found
    }

    @Test
    fun tappingAnActionButtonDimsEveryButtonInTheConversation() {
        val domain = ChatBackend.domain
        ChatBackend.messages = ArrayList<APIMessage>()
        // A resolvable-looking host keeps the tap from throwing while building the request; the
        // call itself is expected to fail, asynchronously and harmlessly.
        ChatBackend.domain = "example.invalid"

        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = ChatViewFragment()
                    fragment.startConversationOnLoad = false
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, fragment, "chat")
                        .commitNow()

                    listOf(menu("m-1"), menu("m-2")).forEach {
                        fragment.render(
                            it,
                            animated = false,
                            isWelcomeMessage = false,
                            isAwaitingFiles = false
                        )
                    }
                }

                Thread.sleep(1000)

                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    val buttons = buttonViews(root)
                    assertEquals("expected four buttons", 4, buttons.size)

                    buttons.first().button.performClick()

                    buttons.forEachIndexed { index, view ->
                        assertFalse(
                            "button $index stayed tappable while the reply was in flight",
                            view.button.isClickable
                        )
                        assertEquals(
                            "button $index was not dimmed while the reply was in flight",
                            127,
                            view.button.background.alpha
                        )
                    }

                    // The reply lands, and the conversation re-enables what it can
                    val fragment = activity.supportFragmentManager.findFragmentByTag("chat")
                            as ChatViewFragment
                    fragment.enableActionButtons(false)

                    assertEquals(
                        "the answered button should look untouched",
                        255,
                        buttons[0].button.background.alpha
                    )
                    assertFalse(
                        "the answered button must not become tappable again",
                        buttons[0].button.isClickable
                    )

                    assertEquals(
                        "the choice next to it should stay dimmed",
                        127,
                        buttons[1].button.background.alpha
                    )
                    assertFalse(
                        "an answered menu must not become tappable again",
                        buttons[1].button.isClickable
                    )

                    listOf(2, 3).forEach { index ->
                        assertEquals(
                            "an untouched menu should come back to life (button $index)",
                            255,
                            buttons[index].button.background.alpha
                        )
                        assertTrue(
                            "an untouched menu should be tappable again (button $index)",
                            buttons[index].button.isClickable
                        )
                    }
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
            ChatBackend.domain = domain
        }
    }

    /** Kept honest: an untapped conversation leaves its buttons alone. */
    @Test
    fun buttonsStartOutTappable() {
        ChatBackend.messages = ArrayList<APIMessage>()
        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val fragment = ChatViewFragment()
                    fragment.startConversationOnLoad = false
                    activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, fragment, "chat")
                        .commitNow()

                    fragment.render(
                        menu("m-1"),
                        animated = false,
                        isWelcomeMessage = false,
                        isAwaitingFiles = false
                    )
                }

                Thread.sleep(1000)

                scenario.onActivity { activity ->
                    buttonViews(activity.findViewById<View>(android.R.id.content) as ViewGroup)
                        .forEach {
                            assertEquals("button was dimmed before any tap", 255, it.button.background.alpha)
                        }
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
        }
    }
}
