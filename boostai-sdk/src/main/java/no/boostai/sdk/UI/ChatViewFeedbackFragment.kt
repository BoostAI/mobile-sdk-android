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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.FeedbackValue
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

open class ChatViewFeedbackFragment(
    var settingsDelegate: ChatViewSettingsDelegate? = null,
    var isDialog: Boolean = false,
    var customConfig: ChatConfig? = null
) : Fragment(R.layout.chat_view_feedback),
    ChatBackend.ConfigObserver {

    lateinit var feedbackInputWrapper: LinearLayout
    lateinit var feedbackTextView: TextView
    lateinit var feedbackEditText: EditText
    lateinit var feedbackButtonsWrapper: LinearLayout
    lateinit var feedbackResponseTextView: TextView
    lateinit var backButton: TextView
    lateinit var submitButton: ImageButton
    lateinit var thumbsUpButton: ImageButton
    lateinit var thumbsDownButton: ImageButton
    var feedbackState: FeedbackState = FeedbackState.INITIAL
    var feedbackValue: FeedbackValue? = null

    enum class FeedbackState {
        INITIAL,
        PROMPT_FOR_TEXT,
        COMPLETE
    }

    val settingsDelegateKey = "settingsDelegate"
    val isDialogKey = "isDialog"
    val customConfigKey = "customConfig"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            isDialog = it.getBoolean(isDialogKey)
            customConfig = it.getParcelable(customConfigKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(isDialogKey, isDialog)
        outState.putParcelable(customConfigKey, customConfig)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        feedbackInputWrapper = view.findViewById(R.id.feedback_input)
        feedbackTextView = view.findViewById(R.id.give_feedback)
        feedbackEditText = view.findViewById(R.id.feedback_edittext)
        feedbackButtonsWrapper = view.findViewById(R.id.feedback_buttons_wrapper)
        feedbackResponseTextView = view.findViewById(R.id.feedback_response)
        backButton = view.findViewById(R.id.back)
        submitButton = view.findViewById(R.id.submit_button)
        thumbsUpButton = view.findViewById(R.id.thumbs_up)
        thumbsDownButton = view.findViewById(R.id.thumbs_down)

        val fadeAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in_fast)

        view.animation = fadeAnimation
        feedbackEditText.hint = getString(R.string.feedback_text_hint)
        feedbackInputWrapper.visibility = View.GONE
        thumbsUpButton.setOnClickListener { userGavePositiveFeedback(thumbsUpButton) }
        thumbsDownButton.setOnClickListener { userGaveNegativeFeedback(thumbsDownButton) }
        backButton.setOnClickListener {
            if (isDialog) settingsDelegate?.closeChat()
            else {
                settingsDelegate?.hideFeedback()
                settingsDelegate?.hideMenu()
            }
        }
        submitButton.setOnClickListener { submitFeedback() }
        view.setOnClickListener {
            // Prevent clicks on the view to fall through to the background view
        }
        updateState()
        updateStyling(ChatBackend.config)
        ChatBackend.addConfigObserver(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateState() {
        when (feedbackState) {
            FeedbackState.INITIAL -> {
                feedbackTextView.visibility = View.VISIBLE
                feedbackButtonsWrapper.visibility = View.VISIBLE
                feedbackInputWrapper.visibility = View.GONE
                feedbackResponseTextView.visibility = View.GONE
                backButton.visibility = View.VISIBLE
            }
            FeedbackState.PROMPT_FOR_TEXT -> {
                feedbackTextView.visibility = View.VISIBLE
                feedbackButtonsWrapper.visibility = View.VISIBLE
                feedbackInputWrapper.visibility = View.VISIBLE
                feedbackResponseTextView.visibility = View.GONE
                backButton.visibility = View.GONE
            }
            FeedbackState.COMPLETE -> {
                feedbackTextView.visibility = View.GONE
                feedbackButtonsWrapper.visibility = View.GONE
                feedbackInputWrapper.visibility = View.GONE
                feedbackResponseTextView.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                hideKeyboard()
            }
        }
    }

    fun userGavePositiveFeedback(sender: ImageButton) =
        setFeedbackValue(FeedbackValue.POSITIVE, sender)

    fun userGaveNegativeFeedback(sender: ImageButton) =
        setFeedbackValue(FeedbackValue.NEGATIVE, sender)

    fun setFeedbackValue(feedbackValue: FeedbackValue, sender: ImageButton) {
        when (feedbackValue) {
            FeedbackValue.POSITIVE -> {
                thumbsUpButton.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumbs_up_filled)
                )
                thumbsDownButton.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumbs_down)
                )
                thumbsUpButton.alpha = 1.0f
                thumbsDownButton.alpha = 0.5f
            }
            FeedbackValue.NEGATIVE -> {
                thumbsUpButton.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumbs_up)
                )
                thumbsDownButton.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_thumbs_down_filled)
                )
                thumbsUpButton.alpha = 0.5f
                thumbsDownButton.alpha = 1.0f
            }
            else -> {}
        }

        val rating = if (feedbackValue == FeedbackValue.POSITIVE) 1 else -1
        sendFeedback(rating, feedbackEditText.text.toString())

        this.feedbackValue = feedbackValue
        feedbackState = FeedbackState.PROMPT_FOR_TEXT
        updateState()
    }

    fun submitFeedback() {
        if (feedbackValue == null) return

        val rating = if (feedbackValue == FeedbackValue.POSITIVE) 1 else -1
        sendFeedback(rating, feedbackEditText.text.toString())

        if (isDialog && settingsDelegate != null) {
            settingsDelegate?.closeChat()
            return
        }

        feedbackState = FeedbackState.COMPLETE
        updateState()
    }

    private var sentRating: Int? = null
    private var sentText: String = ""

    /**
     * Send the rating/text to the backend, but only what has actually changed: the thumb tap
     * already sends the rating, and submit used to unconditionally send (and fire events for)
     * the same rating again — every tap-then-submit produced two API calls and duplicate
     * feedback-given events.
     */
    private fun sendFeedback(rating: Int, text: String) {
        if (rating == sentRating && text == sentText) return

        val previousRating = sentRating
        val previousText = sentText
        ChatBackend.conversationFeedback(rating, text, object : ChatBackend.APIMessageResponseListener {
            override fun onFailure(exception: Exception) {
                // Nothing reached the server, so the same tap or submit must be allowed to try
                // again — as it always was before the duplicate guard
                if (sentRating == rating && sentText == text) {
                    sentRating = previousRating
                    sentText = previousText
                }
            }

            override fun onResponse(apiMessage: APIMessage) {}
        })

        if (rating != sentRating) {
            val event = if (rating > 0) BoostUIEvents.Event.positiveConversationFeedbackGiven
            else BoostUIEvents.Event.negativeConversationFeedbackGiven
            BoostUIEvents.notifyObservers(event)
        }
        if (text.isNotEmpty() && text != sentText) {
            BoostUIEvents.notifyObservers(BoostUIEvents.Event.conversationFeedbackTextGiven)
        }

        sentRating = rating
        sentText = text
    }

    fun hide() {
        val fadeAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_out_fast)

        view?.startAnimation(fadeAnimation)
    }

    fun hideKeyboard() {
        // Hide keyboard if visible
        requireActivity().currentFocus?.let { view ->
            val imm = requireActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    fun updateStyling(config: ChatConfig?) {
        if (config == null) return

        @ColorInt val primaryColor = customConfig?.chatPanel?.styling?.primaryColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.primaryColor
            ?: config.chatPanel?.styling?.primaryColor
            ?: ContextCompat.getColor(requireContext(), R.color.primaryColor)
        @ColorInt val contrastColor = customConfig?.chatPanel?.styling?.contrastColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.contrastColor
            ?: config.chatPanel?.styling?.contrastColor
            ?: ContextCompat.getColor(requireContext(), R.color.contrastColor)

        val closeWindowMessage = customConfig?.messages?.get(ChatBackend.languageCode)?.closeWindow
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.closeWindow
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.closeWindow
        val backMessage = customConfig?.messages?.get(ChatBackend.languageCode)?.back
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.back
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.back

        val bodyFont = customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont
        bodyFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                feedbackTextView.typeface = typeface
                feedbackEditText.typeface = typeface
                feedbackResponseTextView.typeface = typeface
                backButton.typeface = typeface
            } catch (e: java.lang.Exception) {
                print("error")
            }
        }

        view?.background = ColorDrawable(primaryColor)
        feedbackTextView.setTextColor(contrastColor)
        feedbackResponseTextView.setTextColor(contrastColor)
        thumbsUpButton.imageTintList = ColorStateList.valueOf(contrastColor)
        thumbsDownButton.imageTintList = ColorStateList.valueOf(contrastColor)
        backButton.setTextColor(primaryColor)
        (backButton.background as? GradientDrawable)?.setColor(contrastColor)
        submitButton.imageTintList = ColorStateList.valueOf(contrastColor)
        if (isDialog) backButton.text = closeWindowMessage ?: getString(R.string.close)
        else backButton.text = backMessage ?: getString(R.string.back)

        updateTranslatedMessages(config)
    }

    fun updateTranslatedMessages(config: ChatConfig?) {
        val customStrings = customConfig?.messages?.get(ChatBackend.languageCode)
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)
        val strings = config?.messages?.get(ChatBackend.languageCode)
        val fallbackStrings = ChatBackend.config?.messages?.get("en-US")

        feedbackTextView.text = getMessage(
            customStrings?.feedbackPrompt,
            strings?.feedbackPrompt,
            fallbackStrings?.feedbackPrompt
        )

        thumbsUpButton.contentDescription = getMessage(
            customStrings?.feedbackThumbsUp,
            strings?.feedbackThumbsUp,
            fallbackStrings?.feedbackThumbsUp
        )

        thumbsDownButton.contentDescription = getMessage(
            customStrings?.feedbackThumbsDown,
            strings?.feedbackThumbsDown,
            fallbackStrings?.feedbackThumbsDown
        )
    }

    private fun getMessage(string1: String?, string2: String?, string3: String?): String? {
        return string1?.let { if (it.isNotEmpty()) it else null }
            ?: string2?.let { if (it.isNotEmpty()) it else null }
            ?: string3?.let { if (it.isNotEmpty()) it else null }
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}

}