//  boost.ai Android SDK
//  Copyright © 2021 boost.ai
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <https://www.gnu.org/licenses/>.
//
//  Please contact us at contact@boost.ai if you have any questions.
//

package no.boostai.sdk.UI

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.decodeFromJsonElement
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ButtonType
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.FeedbackValue
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselTemplate
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.ElementType
import no.boostai.sdk.ChatBackend.Objects.Response.FunctionType
import no.boostai.sdk.ChatBackend.Objects.Response.GenericCard
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

/**
 * One element of a chat message, rendered as views.
 *
 * The view counterpart of [ChatMessagePartFragment], which now hosts these same views. It holds
 * the element's content view — a [ChatMessageTextView], [ChatMessageImageView] and so on — plus any
 * buttons and the feedback row, and costs nothing in the activity's saved state because the
 * FragmentManager has no record to persist for it.
 */
open class ChatMessagePartView(
    context: Context,
    val element: Element,
    val responseId: String?,
    val isClient: Boolean = false,
    val isBlocked: Boolean = false,
    val isWelcomeMessage: Boolean = false,
    val animated: Boolean = true,
    val customConfig: ChatConfig? = null,
    val chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null,
    val onUploadRequested: (() -> Unit)? = null,
    /** Called when an action button here is tapped, so the conversation can dim the rest. */
    val onActionTapped: ((Link) -> Unit)? = null
) : LinearLayout(context), ChatMessageFeedbackDelegate {

    private val contentContainer: FrameLayout
    private val buttonsContainer: LinearLayout
    private val feedbackRow: LinearLayout
    private val buttonViews = ArrayList<ChatMessageButtonView>()
    private var consentView: ChatMessageConsentView? = null
    private var contentView: View? = null
    private var activeFeedbackValue: FeedbackValue? = null

    /** The button that answered this menu, if one did. See [enableActionButtons]. */
    private var tappedButton: ChatMessageButtonView? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.chat_message_part, this, true)

        contentContainer = findViewById(R.id.chat_message_part)
        buttonsContainer = findViewById(R.id.chat_message_buttons)
        feedbackRow = findViewById(R.id.chat_message_feedback_row)

        // The gravity belongs on the inflated layout, not on this wrapper. The layout is a fixed
        // `chat_view_part_min_width` wide and is this view's only child, so aligning it here moves
        // nothing — it is the bubble *inside* it that has to be pushed to the right edge.
        if (isClient) {
            val content = getChildAt(0) as LinearLayout
            content.gravity = Gravity.END
            (content.layoutParams as LayoutParams).bottomMargin =
                resources.getDimensionPixelSize(R.dimen.chat_message_client_spacing)
        }

        findViewById<LinearLayout>(R.id.chat_message_feedback).visibility = View.GONE
        renderElement()
        bindGeneratedByAiLabel()
        updateTranslatedMessages()
    }

    /**
     * Point screen readers at the configured thumbs-up/down labels instead of the static XML
     * strings, as `ChatMessagePartFragment.updateTranslatedMessages` did.
     */
    private fun updateTranslatedMessages() {
        val messageThumbsUp =
            customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsUp
                ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsUp
                ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.messageThumbsUp
        messageThumbsUp?.let {
            findViewById<View>(R.id.chat_message_feedback_positive)?.contentDescription = it
        }

        val messageThumbsDown =
            customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsDown
                ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsDown
                ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.messageThumbsDown
        messageThumbsDown?.let {
            findViewById<View>(R.id.chat_message_feedback_negative)?.contentDescription = it
        }
    }

    // Deliberately NOT a ChatBackend.ConfigObserver: the owning ChatMessageView observes the
    // config once per message and pushes updateStyling() down to every part — an observer per
    // part would put thousands of registrations in the list a long conversation iterates on
    // each config push, and restyle every part twice.

    /** Whether [element] has anything worth showing. Mirrors `ChatMessagePartFragment.canRender`. */
    fun canRender(): Boolean = canRender(element)

    private fun renderElement() {
        when (element.type) {
            ElementType.TEXT -> element.payload.text?.let { addTextBubble(it, false) }
            ElementType.HTML -> element.payload.html?.let { addTextBubble(it, true) }
            ElementType.LINKS -> element.payload.links?.let { addLinks(it) }
            ElementType.IMAGE -> element.payload.url?.let {
                addContent(ChatMessageImageView(context, it, animated))
            }
            ElementType.VIDEO -> {
                val source = element.payload.source
                val url = element.payload.url
                if (source != null && url != null) {
                    addContent(ChatMessageVideoView(context, source, url))
                }
            }
            ElementType.JSON -> addJson()
            ElementType.UNKNOWN -> Unit
        }
    }

    private fun addContent(view: View) {
        contentView = view
        contentContainer.addView(view)
    }

    private fun addTextBubble(text: String, isHtml: Boolean) {
        addContent(
            ChatMessageTextView(
                context,
                text,
                isHtml,
                isClient,
                customConfig,
                chatResponseViewURLHandlingDelegate
            )
        )
    }

    private fun addLinks(links: ArrayList<Link>) {
        if (links.isEmpty()) return

        // The bullet variant wins over consent rendering, as it did in ChatMessagePartFragment:
        // a panel configured with `buttons.variant = bullet` gets every links element as a bullet
        // list, approve/deny included — the `boostai://` fallback keeps the taps working.
        val buttonType = ChatMessageRenderers.styling(customConfig) { it?.buttons?.variant }
            ?: ChatPanelDefaults.Styling.Buttons.variant
        if (buttonType == ButtonType.BULLET) {
            val html = StringBuilder("<ul style=\"padding-left: 10px\">")
            links.forEach { link ->
                val url = link.url ?: "boostai://${link.id}"
                html.append("<li><a href=\"$url\">${link.text}</a>")
            }
            html.append("</ul>")
            addTextBubble(html.toString(), true)
            return
        }

        val function = links.first().function
        if (function == FunctionType.APPROVE || function == FunctionType.DENY) {
            consentView = ChatMessageConsentView(context, links, animated, customConfig)
            buttonsContainer.addView(consentView)
            return
        }

        links.forEachIndexed { index, link ->
            lateinit var button: ChatMessageButtonView
            button = ChatMessageButtonView(
                context,
                link,
                index,
                animated,
                customConfig,
                chatResponseViewURLHandlingDelegate,
                onUploadRequested,
                onActionTapped = { tappedLink ->
                    tappedButton = button
                    onActionTapped?.invoke(tappedLink)
                }
            )
            buttonViews.add(button)
            buttonsContainer.addView(button)
        }
    }

    private fun addJson() {
        val json = element.payload.json ?: return

        val carousel = try {
            ChatBackend.chatbackendJson.decodeFromJsonElement<CarouselTemplate>(json)
        } catch (e: Exception) {
            null
        }

        if (carousel != null && carousel.template == "carousel" && carousel.elements.isNotEmpty()) {
            addContent(ChatMessageCarouselView(context, carousel, animated, customConfig))
            return
        }

        val card = try {
            ChatBackend.chatbackendJson.decodeFromJsonElement<GenericCard>(json)
        } catch (e: Exception) {
            return
        }

        addContent(ChatMessageGenericJSONView(context, card, animated, customConfig))
    }

    private fun bindGeneratedByAiLabel() {
        val generatedByAiView = findViewById<LinearLayout>(R.id.chat_message_generated_by_ai)
        val aiLabelEnabled = customConfig?.chatPanel?.settings?.aiGeneratedContentLabelEnabled
            ?: ChatBackend.customConfig?.chatPanel?.settings?.aiGeneratedContentLabelEnabled
            ?: ChatBackend.config?.chatPanel?.settings?.aiGeneratedContentLabelEnabled
            ?: false

        if (element.payload.style == "generated" && !isClient && aiLabelEnabled) {
            generatedByAiView.visibility = View.VISIBLE
            feedbackRow.visibility = View.VISIBLE

            val disclaimer =
                customConfig?.messages?.get(ChatBackend.languageCode)?.generatedByAiDisclaimer
                    ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.generatedByAiDisclaimer
                    ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.generatedByAiDisclaimer
            // Explicit fallback: after a language switch to a config without the key, keeping
            // the previous text would leave the disclaimer in the old language
            findViewById<TextView>(R.id.chat_message_generated_by_ai_text).text =
                disclaimer ?: context.getString(R.string.generated_by_ai)
        } else {
            generatedByAiView.visibility = View.GONE
            // The label may have forced the row visible on an earlier config push; give that
            // back unless the thumbs are showing in it
            if (findViewById<LinearLayout>(R.id.chat_message_feedback).visibility != View.VISIBLE) {
                feedbackRow.visibility = View.GONE
            }
        }
    }

    /** Reveal the thumbs up/down row for this message part. */
    override fun showFeedbackButtons() {
        val responseId = responseId ?: return
        val thumbOnAnimation = AnimationUtils.loadAnimation(context, R.anim.feedback_thumb_on)
        val thumbOffAnimation = AnimationUtils.loadAnimation(context, R.anim.feedback_thumb_off)

        val outlineColor = ChatMessageRenderers.styling(customConfig) { it?.messageFeedback?.outlineColor }
            ?: ContextCompat.getColor(context, R.color.messageFeedbackColor)
        val selectedColor =
            ChatMessageRenderers.styling(customConfig) { it?.messageFeedback?.selectedColor }
                ?: outlineColor

        val positive = findViewById<View>(R.id.chat_message_feedback_positive)
        val negative = findViewById<View>(R.id.chat_message_feedback_negative)

        positive.setOnClickListener {
            if (activeFeedbackValue == FeedbackValue.POSITIVE) {
                activeFeedbackValue = null
                positive.setBackgroundResource(R.drawable.ic_thumbs_up)
                positive.backgroundTintList = ColorStateList.valueOf(outlineColor)
                positive.startAnimation(thumbOffAnimation)
                negative.animation = null

                ChatBackend.feedback(responseId, FeedbackValue.REMOVE_POSITIVE)
            } else {
                if (activeFeedbackValue == FeedbackValue.NEGATIVE) {
                    ChatBackend.feedback(responseId, FeedbackValue.REMOVE_NEGATIVE)
                    negative.setBackgroundResource(R.drawable.ic_thumbs_down)
                    negative.backgroundTintList = ColorStateList.valueOf(outlineColor)
                    negative.startAnimation(thumbOffAnimation)
                }
                activeFeedbackValue = FeedbackValue.POSITIVE
                positive.setBackgroundResource(R.drawable.ic_thumbs_up_filled)
                positive.backgroundTintList = ColorStateList.valueOf(selectedColor)
                positive.startAnimation(thumbOnAnimation)

                ChatBackend.feedback(responseId, FeedbackValue.POSITIVE)
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.positiveMessageFeedbackGiven)
            }
        }

        negative.setOnClickListener {
            if (activeFeedbackValue == FeedbackValue.NEGATIVE) {
                activeFeedbackValue = null
                negative.setBackgroundResource(R.drawable.ic_thumbs_down)
                negative.backgroundTintList = ColorStateList.valueOf(outlineColor)
                negative.startAnimation(thumbOffAnimation)
                positive.animation = null

                ChatBackend.feedback(responseId, FeedbackValue.REMOVE_NEGATIVE)
            } else {
                if (activeFeedbackValue == FeedbackValue.POSITIVE) {
                    ChatBackend.feedback(responseId, FeedbackValue.REMOVE_POSITIVE)
                    positive.setBackgroundResource(R.drawable.ic_thumbs_up)
                    positive.backgroundTintList = ColorStateList.valueOf(outlineColor)
                    positive.startAnimation(thumbOffAnimation)
                }
                activeFeedbackValue = FeedbackValue.NEGATIVE
                negative.setBackgroundResource(R.drawable.ic_thumbs_down_filled)
                negative.backgroundTintList = ColorStateList.valueOf(selectedColor)
                negative.startAnimation(thumbOnAnimation)

                ChatBackend.feedback(responseId, FeedbackValue.NEGATIVE)
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.negativeMessageFeedbackGiven)
            }
        }

        // Match the feedback row width to the rendered width of the message part
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                        // Mutated rather than replaced: a fresh LayoutParams would drop the
                        // margins the layout uses to tuck this row under its own bubble.
                        feedbackRow.layoutParams = feedbackRow.layoutParams.apply {
                            width = contentWidth()
                        }
                    }
                }
            )
        }

        feedbackRow.visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.chat_message_feedback).visibility = View.VISIBLE
        updateStyling()
    }

    /**
     * The rendered width the feedback row should match.
     *
     * An element's content and its action buttons live in sibling containers, and only one of them
     * is ever filled — a message that ends in a button menu leaves [contentContainer] empty. Taking
     * the widest of the two keeps the row aligned with whatever was actually drawn; measuring the
     * content alone laid the row out zero pixels wide and hid the thumbs entirely. `WRAP_CONTENT`
     * is the fallback for the case where nothing has been measured yet, so the row can never
     * disappear.
     */
    private fun contentWidth(): Int =
        maxOf(contentContainer.width, buttonsContainer.width)
            .takeIf { it > 0 } ?: LayoutParams.WRAP_CONTENT

    /** Dim this part's action buttons and stop them responding to taps. */
    fun disableActionButtons() = buttonViews.forEach { it.disable() }

    /**
     * Restore this part's action buttons once a reply has landed.
     *
     * A menu that was answered stays answered: the button that was tapped goes back to its normal
     * appearance but remains dead, and the choices next to it stay dimmed. Only menus that were
     * never touched become live again — which is why [makeInteractable] is not passed on, matching
     * `ChatMessageButtonsFragment`.
     */
    fun enableActionButtons(makeInteractable: Boolean) {
        tappedButton?.let {
            it.enable(false)
            return
        }
        buttonViews.forEach { it.enable(true) }
    }

    fun updateStyling() {
        updateTranslatedMessages()
        // Re-evaluated on config pushes: the label's enablement and its translated disclaimer
        // come from the config, which may not have arrived when the part was first rendered —
        // and a language switch must not leave the disclaimer in the old language
        bindGeneratedByAiLabel()

        val outlineColor =
            ChatMessageRenderers.styling(customConfig) { it?.messageFeedback?.outlineColor }
                ?: ContextCompat.getColor(context, R.color.messageFeedbackColor)
        val tint = ColorStateList.valueOf(outlineColor)

        if (activeFeedbackValue == null) {
            findViewById<View>(R.id.chat_message_feedback_positive)?.backgroundTintList = tint
            findViewById<View>(R.id.chat_message_feedback_negative)?.backgroundTintList = tint
        }

        // Styling is pushed down rather than observed per view: a long conversation would otherwise
        // register thousands of config observers.
        (contentView as? ChatMessageTextView)?.applyStyling()
        (contentView as? ChatMessageCarouselView)?.applyStyling()
        (contentView as? ChatMessageGenericJSONView)?.applyStyling()
        consentView?.applyStyling()
        buttonViews.forEach { it.applyStyling() }
    }

    companion object {

        /** Whether an element has any renderable content. */
        fun canRender(element: Element): Boolean = when (element.type) {
            ElementType.TEXT -> !element.payload.text.isNullOrEmpty()
            ElementType.HTML -> !element.payload.html.isNullOrEmpty()
            ElementType.LINKS -> (element.payload.links?.size ?: 0) > 0
            ElementType.IMAGE -> !element.payload.url.isNullOrEmpty()
            ElementType.VIDEO ->
                !element.payload.source.isNullOrEmpty() && !element.payload.url.isNullOrEmpty()
            ElementType.JSON -> element.payload.json?.let { json ->
                try {
                    val carousel =
                        ChatBackend.chatbackendJson.decodeFromJsonElement<CarouselTemplate>(json)
                    if (carousel.template == "carousel" && carousel.elements.isNotEmpty()) {
                        return true
                    }
                } catch (_: Exception) {
                }
                try {
                    val card = ChatBackend.chatbackendJson.decodeFromJsonElement<GenericCard>(json)
                    card.heading != null || card.body != null
                } catch (e: Exception) {
                    false
                }
            } ?: false
            ElementType.UNKNOWN -> false
        }
    }
}
