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

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

open class ChatViewSettingsFragment(
    var menuDelegate: ChatViewSettingsDelegate? = null,
    var isDialog: Boolean = false,
    var customConfig: ChatConfig? = null
) : Fragment(R.layout.chat_view_settings),
    ChatBackend.ConfigObserver {

    lateinit var feedbackButton: TextView
    lateinit var downloadButton: TextView
    lateinit var deleteButton: TextView
    lateinit var privacyPolicyButton: LinearLayout
    lateinit var privacyPolicyTextView: TextView
    lateinit var privacyPolicyImageView: ImageView
    lateinit var backButton: TextView
    lateinit var poweredByTextView: TextView
    lateinit var poweredByImageView: ImageView

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

        feedbackButton = view.findViewById(R.id.give_feedback)
        downloadButton = view.findViewById(R.id.download_conversation)
        deleteButton = view.findViewById(R.id.delete_conversation)
        privacyPolicyButton = view.findViewById(R.id.privacy_policy)
        privacyPolicyTextView = view.findViewById(R.id.privacy_policy_textview)
        privacyPolicyImageView = view.findViewById(R.id.privacy_policy_imageview)
        backButton = view.findViewById(R.id.back)
        poweredByTextView = view.findViewById(R.id.powered_by_textview)
        poweredByImageView = view.findViewById(R.id.powered_by_imageview)

        val fadeAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in_fast)
        view.animation = fadeAnimation

        val requestConversationFeedback = customConfig?.chatPanel?.settings?.requestFeedback
            ?: ChatBackend.customConfig?.chatPanel?.settings?.requestFeedback
            ?: ChatBackend.config?.chatPanel?.settings?.requestFeedback
            ?: ChatPanelDefaults.Settings.requestFeedback
        feedbackButton.visibility =
            if (requestConversationFeedback && !isDialog)
                View.VISIBLE else View.GONE
        deleteButton.visibility =
            if (ChatBackend.allowDeleteConversation) View.VISIBLE else View.GONE
        privacyPolicyButton.visibility =
            if (ChatBackend.privacyPolicyUrl.isNotEmpty()) View.VISIBLE else View.GONE
        feedbackButton.setOnClickListener { menuDelegate?.showFeedback() }
        downloadButton.setOnClickListener { downloadConversation() }
        deleteButton.setOnClickListener { deleteConversation() }
        privacyPolicyButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(ChatBackend.privacyPolicyUrl)
            startActivity(intent)

            BoostUIEvents.notifyObservers(BoostUIEvents.Event.privacyPolicyOpened)
        }
        poweredByImageView.setOnClickListener {
            Intent(Intent.ACTION_VIEW).let {
                it.setData(Uri.parse("https://www.boost.ai/"))
                startActivity(it)
            }
        }
        backButton.setOnClickListener { menuDelegate?.hideMenu() }
        view.setOnClickListener {
            // Prevent clicks on the view to fall through to the background view
        }
        updateStyling(ChatBackend.config)
        ChatBackend.addConfigObserver(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
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

        view?.background = ColorDrawable(primaryColor)
        feedbackButton.setTextColor(contrastColor)
        downloadButton.setTextColor(contrastColor)
        deleteButton.setTextColor(contrastColor)
        privacyPolicyTextView.setTextColor(contrastColor)
        privacyPolicyImageView.imageTintList = ColorStateList.valueOf(contrastColor)
        backButton.setTextColor(primaryColor)
        (backButton.background as? GradientDrawable)?.setColor(contrastColor)
        poweredByTextView.setTextColor(contrastColor)
        poweredByImageView.imageTintList = ColorStateList.valueOf(contrastColor)

        updateTranslatedMessages(config)

        @FontRes val bodyFont = customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont
        @FontRes val menuItemFont = customConfig?.chatPanel?.styling?.fonts?.menuItemFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.menuItemFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.menuItemFont

        bodyFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                backButton.typeface = typeface
            } catch (e: java.lang.Exception) {}
        }

        menuItemFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                feedbackButton.typeface = typeface
                downloadButton.typeface = typeface
                deleteButton.typeface = typeface
                privacyPolicyTextView.typeface = typeface

            } catch (e: java.lang.Exception) {}
        }

        @FontRes val footnoteFont = customConfig?.chatPanel?.styling?.fonts?.footnoteFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.footnoteFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.footnoteFont

        footnoteFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                poweredByTextView.typeface = typeface
            } catch (e: java.lang.Exception) {}
        }
    }

    fun updateTranslatedMessages(config: ChatConfig?) {
        val customStrings = customConfig?.messages?.get(ChatBackend.languageCode)
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)
        val strings = config?.messages?.get(ChatBackend.languageCode)
        val fallbackStrings = ChatBackend.config?.messages?.get("en-US")

        downloadButton.text = getMessage(
            customStrings?.downloadConversation,
            strings?.downloadConversation,
            fallbackStrings?.downloadConversation
        )

        deleteButton.text = getMessage(
            customStrings?.deleteConversation,
            strings?.deleteConversation,
            fallbackStrings?.deleteConversation
        )

        privacyPolicyTextView.text = getMessage(
            customStrings?.privacyPolicy,
            strings?.privacyPolicy,
            fallbackStrings?.privacyPolicy
        )

        backButton.text = getMessage(
            customStrings?.back,
            strings?.back,
            fallbackStrings?.back
        )

        feedbackButton.text = getMessage(
            customStrings?.feedbackPrompt,
            strings?.feedbackPrompt,
            fallbackStrings?.feedbackPrompt
        )
    }

    private fun getMessage(string1: String?, string2: String?, string3: String?): String? {
        return string1?.let { if (it.isNotEmpty()) it else null }
            ?: string2?.let { if (it.isNotEmpty()) it else null }
            ?: string3?.let { if (it.isNotEmpty()) it else null }
    }

    fun downloadConversation() {
        ChatBackend.download(null, object : ChatBackend.APIMessageResponseListener {

            override fun onFailure(exception: Exception) {}

            override fun onResponse(apiMessage: APIMessage) {
                if (apiMessage.download != null) {
                    val intent = Intent(Intent.ACTION_SEND)

                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.conversation))
                    intent.putExtra(Intent.EXTRA_TEXT, apiMessage.download)
                    startActivity(Intent.createChooser(intent, getString(R.string.download_conversation)))

                    BoostUIEvents.notifyObservers(BoostUIEvents.Event.conversationDownloaded, ChatBackend.conversationId)
                }
            }

        })
    }

    fun deleteConversation() = menuDelegate?.deleteConversation()

    fun hide() {
        val fadeAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_out_fast)

        view?.startAnimation(fadeAnimation)
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}
}