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

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.serialization.json.decodeFromJsonElement
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ButtonType
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.FeedbackValue
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.ElementType
import no.boostai.sdk.ChatBackend.Objects.Response.GenericCard
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

open class ChatMessagePartFragment(
    var element: Element? = null,
    var responseId: String? = null,
    var isClient: Boolean = false,
    var isBlocked: Boolean = false,
    var isWelcomeMessage: Boolean = false,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null,
    var buttonDelegate: ChatMessageButtonDelegate? = null,
    var chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null
) : IChatMessagePartFragment(R.layout.chat_message_part),
    ChatBackend.ConfigObserver,
    ChatMessageButtonDelegate {

    val elementKey = "element"
    val responseIdKey = "responseId"
    val isClientKey = "isClient"
    val isBlockedKey = "isBlocked"
    val isWelcomeMessageKey = "isWelcomeMessage"
    val customConfigKey = "customConfig"

    var positiveMessageFeedbackButton: ImageButton? = null
    var negativeMessageFeedbackButton: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            element = it.getParcelable(elementKey)
            responseId = it.getString(responseIdKey)
            isClient = it.getBoolean(isClientKey)
            isBlocked = it.getBoolean(isBlockedKey)
            isWelcomeMessage = it.getBoolean(isWelcomeMessageKey)
            customConfig = it.getParcelable(customConfigKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(elementKey, element)
        outState.putString(responseIdKey, responseId)
        outState.putBoolean(isClientKey, isClient)
        outState.putBoolean(isBlockedKey, isBlocked)
        outState.putBoolean(isWelcomeMessageKey, isWelcomeMessage)
        outState.putParcelable(customConfigKey, customConfig)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        positiveMessageFeedbackButton = view.findViewById(R.id.chat_message_feedback_positive)
        negativeMessageFeedbackButton = view.findViewById(R.id.chat_message_feedback_negative)

        if (isClient) {
            val linearLayout = view as LinearLayout
            linearLayout.gravity = Gravity.END

            val bottomMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10.0f,
                resources.displayMetrics
            )
            val layoutParams = view.layoutParams as LinearLayout.LayoutParams
            layoutParams.marginStart = 0
            layoutParams.bottomMargin = bottomMargin.toInt()
            view.layoutParams = layoutParams
        }

        if (savedInstanceState == null) {
            var fragment: Fragment? = null

            when (element?.type) {
                ElementType.TEXT -> fragment =
                    element?.payload?.text?.let { getChatMessageTextFragment(it, false) }
                ElementType.HTML -> fragment =
                    element?.payload?.html?.let { getChatMessageTextFragment(it, true) }
                ElementType.LINKS -> {
                    val buttonType = customConfig?.chatPanel?.styling?.buttons?.variant
                        ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.variant
                        ?: ChatBackend.config?.chatPanel?.styling?.buttons?.variant
                        ?: ChatPanelDefaults.Styling.Buttons.variant
                    if (buttonType == ButtonType.BULLET) {
                        val stringBuilder = StringBuilder()

                        stringBuilder.append("<ul style=\"padding-left: 10px\">")
                        element?.payload?.links?.forEach { link ->
                            val url = link.url ?: "boostai://${link.id}"
                            stringBuilder.append("<li><a href=\"$url\">${link.text}</a>")
                        }
                        stringBuilder.append("</ul>")
                        fragment = getChatMessageTextFragment(stringBuilder.toString(), true)
                    } else fragment =
                        element?.payload?.links?.let { getChatMessageButtonsFragment(it) }
                }
                ElementType.IMAGE -> fragment =
                    element?.payload?.url?.let { getChatMessageImageFragment(it) }
                ElementType.VIDEO -> fragment =
                    if (element?.payload?.source != null && element?.payload?.url != null)
                        getChatMessageVideoFragment(
                            element!!.payload.source!!,
                            element!!.payload.url!!
                        )
                    else null
                ElementType.JSON -> {
                    element?.payload?.json?.let {
                        try {
                            val genericCard =
                                ChatBackend.chatbackendJson.decodeFromJsonElement<GenericCard>(it)
                            fragment = getGenericJSONFragment(genericCard)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                ElementType.UNKNOWN -> {} // TODO
                else -> {}
            }
            fragment?.let {
                childFragmentManager.beginTransaction().add(R.id.chat_message_part, it)
                    .commitAllowingStateLoss()
            }
        }

        view.findViewById<LinearLayout>(R.id.chat_message_feedback)?.visibility =
            View.GONE

        updateStyling()
        updateTranslatedMessages()
        ChatBackend.addConfigObserver(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
    }

    override fun canRender(element: Element): Boolean {
        return when (element.type) {
            ElementType.TEXT -> element.payload.text?.isNotEmpty() ?: false
            ElementType.HTML -> element.payload.html?.isNotEmpty() ?: false
            ElementType.LINKS -> element.payload.links?.count() ?: 0 > 0
            ElementType.IMAGE -> element.payload.url?.isNotEmpty() ?: false
            ElementType.VIDEO -> (element.payload.source?.isNotEmpty() ?: false) && (element.payload.url?.isNotEmpty() ?: false)
            ElementType.JSON -> element.payload.json?.let {
                    try {
                        val genericCard =
                            ChatBackend.chatbackendJson.decodeFromJsonElement<GenericCard>(it)
                        genericCard.heading != null || genericCard.body != null
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            ElementType.UNKNOWN -> false
        }
    }

    override fun showFeedbackButtons() {
        // Show feedback buttons when conditions are met
        view?.let { view ->
            var activeFeedbackValue: FeedbackValue? = null
            val thumbOnAnimation =
                AnimationUtils.loadAnimation(context, R.anim.feedback_thumb_on)
            val thumbOffAnimation =
                AnimationUtils.loadAnimation(context, R.anim.feedback_thumb_off)
            val viewTreeObserver = view.viewTreeObserver

            val iconOutlineColor = customConfig?.chatPanel?.styling?.messageFeedback?.outlineColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.messageFeedback?.outlineColor
                ?: ChatBackend.config?.chatPanel?.styling?.messageFeedback?.outlineColor
                ?: ContextCompat.getColor(requireContext(), R.color.messageFeedbackColor)
            val iconSelectedColor = customConfig?.chatPanel?.styling?.messageFeedback?.selectedColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.messageFeedback?.selectedColor
                ?: ChatBackend.config?.chatPanel?.styling?.messageFeedback?.selectedColor
                ?: iconOutlineColor

            view.findViewById<View>(R.id.chat_message_feedback_positive)?.let { button: View ->
                val sibling = view.findViewById<View>(R.id.chat_message_feedback_negative)

                button.setOnClickListener {
                    if (activeFeedbackValue == FeedbackValue.POSITIVE) {
                        activeFeedbackValue = null
                        button.setBackgroundResource(R.drawable.ic_thumbs_up)
                        button.backgroundTintList = ColorStateList.valueOf(iconOutlineColor)
                        button.startAnimation(thumbOffAnimation)
                        sibling.animation = null

                        ChatBackend.feedback(responseId!!, FeedbackValue.REMOVE_POSITIVE)
                    } else {
                        if (activeFeedbackValue == FeedbackValue.NEGATIVE) {
                            ChatBackend.feedback(responseId!!, FeedbackValue.REMOVE_NEGATIVE)
                            sibling.setBackgroundResource(R.drawable.ic_thumbs_down)
                            sibling.backgroundTintList = ColorStateList.valueOf(iconOutlineColor)
                            sibling.startAnimation(thumbOffAnimation)
                        }
                        activeFeedbackValue = FeedbackValue.POSITIVE
                        button.setBackgroundResource(R.drawable.ic_thumbs_up_filled)
                        button.backgroundTintList = ColorStateList.valueOf(iconSelectedColor)
                        button.startAnimation(thumbOnAnimation)

                        ChatBackend.feedback(responseId!!, FeedbackValue.POSITIVE)
                        BoostUIEvents.notifyObservers(BoostUIEvents.Event.positiveMessageFeedbackGiven)
                    }
                }
            }
            view.findViewById<View>(R.id.chat_message_feedback_negative)?.let { button: View ->
                val sibling = view.findViewById<View>(R.id.chat_message_feedback_positive)

                button.setOnClickListener {
                    if (activeFeedbackValue == FeedbackValue.NEGATIVE) {
                        activeFeedbackValue = null
                        button.setBackgroundResource(R.drawable.ic_thumbs_down)
                        button.startAnimation(thumbOffAnimation)
                        button.backgroundTintList = ColorStateList.valueOf(iconOutlineColor)
                        sibling.animation = null

                        ChatBackend.feedback(responseId!!, FeedbackValue.REMOVE_NEGATIVE)
                    } else {
                        if (activeFeedbackValue == FeedbackValue.POSITIVE) {
                            ChatBackend.feedback(responseId!!, FeedbackValue.REMOVE_POSITIVE)
                            sibling.setBackgroundResource(R.drawable.ic_thumbs_up)
                            sibling.backgroundTintList = ColorStateList.valueOf(iconOutlineColor)
                            sibling.startAnimation(thumbOffAnimation)
                        }
                        activeFeedbackValue = FeedbackValue.NEGATIVE
                        button.setBackgroundResource(R.drawable.ic_thumbs_down_filled)
                        button.backgroundTintList = ColorStateList.valueOf(iconSelectedColor)
                        button.startAnimation(thumbOnAnimation)

                        ChatBackend.feedback(responseId!!, FeedbackValue.NEGATIVE)
                        BoostUIEvents.notifyObservers(BoostUIEvents.Event.negativeMessageFeedbackGiven)
                    }
                }
            }
            // Set width of feedback button container to equal rendered width of the message part
            if (viewTreeObserver.isAlive)
                viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {

                        override fun onGlobalLayout() {
                            view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            view.findViewById<LinearLayout>(R.id.chat_message_feedback)
                                .layoutParams =
                                LinearLayout.LayoutParams(
                                    view.findViewById<FrameLayout>(R.id.chat_message_part)
                                        .width,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                        }

                    }
                )

            view.findViewById<LinearLayout>(R.id.chat_message_feedback)?.visibility =
                View.VISIBLE
        }
    }

    fun updateStyling() {
        val outlineColor = customConfig?.chatPanel?.styling?.messageFeedback?.outlineColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.messageFeedback?.outlineColor
            ?: ChatBackend.config?.chatPanel?.styling?.messageFeedback?.outlineColor
            ?: ContextCompat.getColor(requireContext(), R.color.messageFeedbackColor)

        val color = ColorStateList.valueOf(outlineColor)
        positiveMessageFeedbackButton?.backgroundTintList = color
        negativeMessageFeedbackButton?.backgroundTintList = color
    }

    fun updateTranslatedMessages() {
        val messageThumbsUp = customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsUp
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsUp
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.messageThumbsUp
        messageThumbsUp?.let {
            positiveMessageFeedbackButton?.contentDescription = it
        }

        val messageThumbsDown = customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsDown
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.messageThumbsDown
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.messageThumbsDown
        messageThumbsDown?.let {
            negativeMessageFeedbackButton?.contentDescription = it
        }
    }

    fun getChatMessageTextFragment(text: String, isHtml: Boolean): Fragment =
        ChatMessageTextFragment(text, isHtml, isClient, animated, customConfig)

    fun getChatMessageButtonsFragment(links: ArrayList<Link>): Fragment =
        ChatMessageButtonsFragment(links, animated, customConfig, this, chatResponseViewURLHandlingDelegate)

    fun getChatMessageImageFragment(url: String): Fragment =
        ChatMessageImageFragment(url, animated)

    fun getChatMessageVideoFragment(source: String, url: String): Fragment =
        ChatMessageVideoFragment(source, url)

    fun getGenericJSONFragment(card: GenericCard): Fragment =
        ChatMessageGenericJSONFragment(card, animated, customConfig)

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) {
        updateStyling()
        updateTranslatedMessages()
    }

    override fun onFailure(backend: ChatBackend, error: Exception) {
    }

    override fun didTapActionButton() {
        buttonDelegate?.didTapActionButton()
    }

    override fun disableActionButtons() {
        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.disableActionButtons()
        }
    }

    override fun enableActionButtons() {
        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.enableActionButtons()
        }
    }
}