package no.boostai.sdk

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.AvatarShape
import no.boostai.sdk.ChatBackend.Objects.ButtonType
import no.boostai.sdk.ChatBackend.Objects.Buttons
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanel
import no.boostai.sdk.ChatBackend.Objects.Styling
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselElement
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselTemplate
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.ElementType
import no.boostai.sdk.ChatBackend.Objects.Response.FunctionType
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.ChatBackend.Objects.Response.Payload
import no.boostai.sdk.ChatBackend.Objects.Response.Response
import no.boostai.sdk.ChatBackend.Objects.Response.SourceType
import no.boostai.sdk.UI.ChatMessageCarouselView
import no.boostai.sdk.UI.ChatMessageConsentView
import no.boostai.sdk.UI.ChatMessagePartView
import no.boostai.sdk.UI.ChatMessageTextView
import no.boostai.sdk.UI.ChatMessageView
import no.boostai.sdk.UI.StatusMessageFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regressions found by comparing the view implementations against the fragments they replaced.
 * Each test pins a behaviour of the old fragment path that the rewrite had lost.
 */
@RunWith(AndroidJUnit4::class)
class ViewRegressionTest {

    private fun <T : View> findAll(root: ViewGroup, type: Class<T>): List<T> {
        val found = ArrayList<T>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (type.isInstance(child)) found.add(type.cast(child)!!)
            if (child is ViewGroup) found.addAll(findAll(child, type))
        }
        return found
    }

    /** The old text fragment zeroed the bubble's XML margin for client messages; the part view
     *  owns that spacing now, and keeping both doubled the gap below every user message. */
    @Test
    fun clientBubbleHasNoBottomMarginOfItsOwn() {
        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val client = ChatMessageTextView(activity, "hello", isClient = true)
                assertEquals(
                    "the client bubble's own margin must be zero — the part view adds the spacing",
                    0,
                    (client.bubble.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                )

                val server = ChatMessageTextView(activity, "hello", isClient = false)
                val expected = (10 * activity.resources.displayMetrics.density).toInt()
                assertEquals(
                    "server bubbles keep their XML margin",
                    expected,
                    (server.bubble.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                )
            }
        }
    }

    @Test
    fun squaredAvatarShapeIsRespected() {
        val response = Response(
            "r-1", SourceType.BOT, "en-US",
            listOf(Element(Payload(text = "Hi."), ElementType.TEXT))
        )
        val squaredConfig =
            ChatConfig(chatPanel = ChatPanel(styling = Styling(avatarShape = AvatarShape.SQUARED)))

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val squared = ChatMessageView(activity, response, customConfig = squaredConfig)
                assertNull(
                    "a squared avatar must not get the rounded background",
                    squared.findViewById<ImageView>(R.id.avatar).background
                )

                val default = ChatMessageView(activity, response)
                assertNotNull(
                    "the default avatar keeps the rounded background",
                    default.findViewById<ImageView>(R.id.avatar).background
                )
            }
        }
    }

    /** The synthesized "upload a file" button sits outside the reveal pipeline: it must not get
     *  feedback thumbs of its own — those belong to the last real element. */
    @Test
    fun uploadPartGetsNoFeedbackThumbs() {
        val response = Response(
            "r-upload", SourceType.BOT, "en-US",
            listOf(Element(Payload(text = "Please send the document."), ElementType.TEXT))
        )

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val message = ChatMessageView(
                    activity,
                    response,
                    isAwaitingFiles = true,
                    animated = false
                )
                activity.findViewById<ViewGroup>(android.R.id.content).addView(message)

                val parts = findAll(message, ChatMessagePartView::class.java)
                assertEquals("expected the text part and the upload part", 2, parts.size)

                val feedbackOf = { part: ChatMessagePartView ->
                    part.findViewById<LinearLayout>(R.id.chat_message_feedback).visibility
                }
                assertEquals(
                    "the real element carries the thumbs",
                    View.VISIBLE,
                    feedbackOf(parts[0])
                )
                assertEquals(
                    "the upload button must not get thumbs of its own",
                    View.GONE,
                    feedbackOf(parts[1])
                )
            }
        }
    }

    /** A config push after the user answered a consent request must not light the rejected
     *  button back up. */
    @Test
    fun consentChoiceSurvivesRestyle() {
        val domain = ChatBackend.domain
        ChatBackend.messages = ArrayList<APIMessage>()
        ChatBackend.domain = "example.invalid"

        try {
            ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val part = ChatMessagePartView(
                        activity,
                        Element(
                            Payload(
                                links = arrayListOf(
                                    Link("c-yes", "Yes", LinkType.ACTION_LINK, FunctionType.APPROVE),
                                    Link("c-no", "No", LinkType.ACTION_LINK, FunctionType.DENY)
                                )
                            ),
                            ElementType.LINKS
                        ),
                        "r-consent",
                        animated = false
                    )
                    activity.findViewById<ViewGroup>(android.R.id.content).addView(part)

                    val consent = findAll(part, ChatMessageConsentView::class.java).single()
                    consent.approveButton.performClick()

                    // The reply lands and pushes fresh styling
                    part.updateStyling()

                    assertEquals(
                        "the approved button stays at full strength",
                        255,
                        (consent.approveButton.background as GradientDrawable).alpha
                    )
                    assertEquals(
                        "the rejected button must stay dimmed after a restyle",
                        64,
                        (consent.denyButton.background as GradientDrawable).alpha
                    )
                    assertEquals(
                        "the rejected button's text must keep the disabled colour",
                        androidx.core.content.ContextCompat.getColor(
                            activity, R.color.consentDenyButtonDisabledTextColor
                        ),
                        consent.denyButton.currentTextColor
                    )
                    assertFalse(
                        "a made choice is locked in — the other button must not stay tappable",
                        consent.denyButton.isClickable
                    )
                    assertFalse(
                        "nor may the chosen one fire again",
                        consent.approveButton.isClickable
                    )
                }
            }
        } finally {
            ChatBackend.messages = ArrayList()
            ChatBackend.domain = domain
        }
    }

    /** `buttons.variant = bullet` renders every links element as a bullet list — approve/deny
     *  included, exactly as the fragment path always has. */
    @Test
    fun bulletVariantWinsOverConsentRendering() {
        val bulletConfig = ChatConfig(
            chatPanel = ChatPanel(styling = Styling(buttons = Buttons(variant = ButtonType.BULLET)))
        )

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val part = ChatMessagePartView(
                    activity,
                    Element(
                        Payload(
                            links = arrayListOf(
                                Link("c-yes", "Yes", LinkType.ACTION_LINK, FunctionType.APPROVE),
                                Link("c-no", "No", LinkType.ACTION_LINK, FunctionType.DENY)
                            )
                        ),
                        ElementType.LINKS
                    ),
                    "r-bullet",
                    animated = false,
                    customConfig = bulletConfig
                )

                assertTrue(
                    "bullet-configured links render as a text bubble",
                    findAll(part, ChatMessageTextView::class.java).isNotEmpty()
                )
                assertTrue(
                    "no consent button pair should be rendered",
                    findAll(part, ChatMessageConsentView::class.java).isEmpty()
                )
            }
        }
    }

    /** While swiping, carousel cards must slide under the message margin to the screen edge —
     *  the scroll view itself carries the start padding, not the wrapper it sits in. */
    @Test
    fun carouselScrollsEdgeToEdge() {
        val template = CarouselTemplate(
            template = "carousel",
            elements = listOf(
                CarouselElement(title = "One", body = "First card"),
                CarouselElement(title = "Two", body = "Second card")
            )
        )

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            lateinit var carousel: ChatMessageCarouselView
            scenario.onActivity { activity ->
                val inset = LinearLayout(activity)
                inset.setPadding(100, 0, 0, 0)
                carousel = ChatMessageCarouselView(activity, template, animated = false)
                inset.addView(
                    carousel,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                activity.findViewById<ViewGroup>(android.R.id.content).addView(inset)
            }

            // extendToScreenEdges runs in a post after layout
            Thread.sleep(500)

            scenario.onActivity {
                assertEquals(
                    "the wrapper must not be padded — it would inset the scroll view",
                    0,
                    carousel.paddingLeft
                )
                assertEquals(
                    "the scroll view carries the start padding",
                    100,
                    carousel.scrollView.paddingLeft
                )
                assertFalse(
                    "the scroll view must draw into its padding while scrolled",
                    carousel.scrollView.clipToPadding
                )
            }
        }
    }

    /** The carousel, the action buttons and the consent buttons all inflate the same pill
     *  drawable. Styling a carousel used to tint the shared constant state, and since a tint
     *  filter masks setColor completely, every consent button created afterwards painted in the
     *  action-button colour for the rest of the process. */
    /** Cards whose titles wrap to a different number of lines must still line up their buttons —
     *  every card stretches to the tallest one and pins its button to the bottom. */
    @Test
    fun carouselButtonsLineUpAcrossCards() {
        val template = CarouselTemplate(
            template = "carousel",
            elements = listOf(
                CarouselElement(
                    title = "A title long enough to wrap onto a second line",
                    body = "First card",
                    buttonText = "Go"
                ),
                CarouselElement(title = "Short", body = "Second card", buttonText = "Go")
            )
        )

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            lateinit var carousel: ChatMessageCarouselView
            scenario.onActivity { activity ->
                carousel = ChatMessageCarouselView(activity, template, animated = false)
                activity.findViewById<ViewGroup>(android.R.id.content).addView(
                    carousel,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            Thread.sleep(500)

            scenario.onActivity {
                val cards = (0 until carousel.container.childCount)
                    .map { carousel.container.getChildAt(it) }
                val titles = cards.map {
                    it.findViewById<android.widget.TextView>(R.id.carousel_item_title)
                }
                assertTrue(
                    "the first title must wrap for this test to mean anything",
                    titles[0].lineCount > titles[1].lineCount
                )

                val buttonTops = cards.map {
                    val location = IntArray(2)
                    it.findViewById<View>(R.id.carousel_item_button).getLocationOnScreen(location)
                    location[1]
                }
                assertEquals("buttons must line up", buttonTops[0], buttonTops[1])
                assertEquals("cards must be equally tall", cards[0].height, cards[1].height)
            }
        }
    }

    @Test
    fun carouselStylingDoesNotRepaintConsentButtons() {
        val template = CarouselTemplate(
            template = "carousel",
            elements = listOf(CarouselElement(title = "One", buttonText = "Go"))
        )

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ChatMessageCarouselView(activity, template, animated = false).applyStyling()

                val consent = ChatMessageConsentView(
                    activity,
                    listOf(
                        Link("c-yes", "Yes", LinkType.ACTION_LINK, FunctionType.APPROVE),
                        Link("c-no", "No", LinkType.ACTION_LINK, FunctionType.DENY)
                    ),
                    animated = false
                )

                // The tint is not readable through the Drawable API, so draw the background and
                // sample the centre pixel
                val drawable = consent.approveButton.background
                val bitmap = android.graphics.Bitmap.createBitmap(
                    24, 24, android.graphics.Bitmap.Config.ARGB_8888
                )
                drawable.setBounds(0, 0, 24, 24)
                drawable.draw(android.graphics.Canvas(bitmap))

                assertEquals(
                    "the approve button must keep its own colour after a carousel was styled",
                    androidx.core.content.ContextCompat.getColor(
                        activity, R.color.consentApproveButtonBackgroundColor
                    ),
                    bitmap.getPixel(12, 12)
                )
            }
        }
    }

    /** Deleting the conversation must take the rendered messages off screen — they are views now,
     *  so clearing the child fragments alone leaves the whole transcript visible. */
    @Test
    fun clearingTheConversationRemovesRenderedMessages() {
        ChatBackend.messages = ArrayList<APIMessage>()

        val response = { id: String ->
            Response(
                id, SourceType.BOT, "en-US",
                listOf(Element(Payload(text = "Message $id."), ElementType.TEXT))
            )
        }

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = NoAutoStartChatViewFragment()
                activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment, "chat")
                    .commitNow()

                listOf("d-1", "d-2").forEach {
                    fragment.render(
                        response(it),
                        animated = false,
                        isWelcomeMessage = false,
                        isAwaitingFiles = false
                    )
                }
                assertEquals(2, fragment.chatMessagesLayout.childCount)

                fragment.clearRenderedConversation()

                assertEquals(
                    "the transcript must be gone from the screen",
                    0,
                    fragment.chatMessagesLayout.childCount
                )

                // A new conversation reusing an old response id must render, not be deduped away
                fragment.render(
                    response("d-1"),
                    animated = false,
                    isWelcomeMessage = true,
                    isAwaitingFiles = false
                )
                assertEquals(
                    "a fresh welcome message with a recycled id must appear",
                    1,
                    fragment.chatMessagesLayout.childCount
                )
            }
        }
    }

    /** An error banner (and its Retry button) has to survive a configuration change; it cannot be
     *  rebuilt from `ChatBackend.messages`, so it is carried over explicitly. */
    @Test
    fun statusMessageSurvivesRecreate() {
        ChatBackend.messages = ArrayList<APIMessage>()

        ActivityScenario.launch(SavedStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = NoAutoStartChatViewFragment()
                activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment, "chat")
                    .commitNow()

                fragment.showStatusMessage("Something broke", null, true)
                fragment.childFragmentManager.executePendingTransactions()
                assertNotNull(
                    "sanity: the status message is on screen before the recreate",
                    fragment.childFragmentManager.findFragmentByTag(fragment.errorId)
                )
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("chat")
                        as NoAutoStartChatViewFragment
                fragment.childFragmentManager.executePendingTransactions()

                val status = fragment.childFragmentManager.findFragmentByTag(fragment.errorId)
                assertNotNull("the error banner must survive the recreate", status)
                assertEquals(
                    "with its message intact",
                    "Something broke",
                    (status as StatusMessageFragment).message
                )
                assertTrue("and still marked as an error", status.isError)
            }
        }
    }
}
