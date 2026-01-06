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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Patterns
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents
import no.boostai.sdk.UI.Helpers.handleUrlClicks
import no.boostai.sdk.UI.Helpers.trimTrailingWhitespace

open class ChatMessageTextFragment(
    var text: String? = null,
    var isHtml: Boolean = false,
    var isClient: Boolean = false,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null
) : Fragment(R.layout.chat_message_text_fragment),
    ChatBackend.ConfigObserver {

    val textKey = "text"
    val isHtmlKey = "isHtml"
    val isClientKey = "isClient"
    val customConfigKey = "customConfig"

    lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            text = it.getString(textKey)
            isHtml = it.getBoolean(isHtmlKey)
            isClient = it.getBoolean(isClientKey)
            customConfig = it.getParcelable(customConfigKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(textKey, text)
        outState.putBoolean(isHtmlKey, isHtml)
        outState.putBoolean(isClientKey, isClient)
        outState.putParcelable(customConfigKey, customConfig)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textView = view.findViewById(R.id.chat_message_textview)

        if (isClient) {
            view.background = ContextCompat.getDrawable(requireContext(), R.drawable.semi_rounded_alt)

            val layoutParams = view.layoutParams as FrameLayout.LayoutParams
            layoutParams.bottomMargin = 0
            view.layoutParams = layoutParams
        }

        if (isHtml) {
            // Find links in plain text (especially relevant in text from human chat)
            val matcher = text?.let { Patterns.WEB_URL.matcher(it) }
            while (matcher?.find() == true) {
                val url = matcher.group()
                val index = text?.indexOf(url) ?: -1
                // Check that the URL is not already a link
                if (index >= 6 && text!!.substring(index - 6, index) != "href=\"" && text!!.substring(index - 1, index) != "@") {
                    text = text?.replaceFirst(
                        url,
                        "<a href=\"" + url + "\">" + url + "</a>"
                    )
                }
            }

            val content: CharSequence = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)

            textView.text = trimTrailingWhitespace(content)
            textView.handleUrlClicks { url, text ->
                if (url.startsWith("boostai://")) {
                    val id = url.removePrefix("boostai://")

                    ChatBackend.actionButton(id)
                    BoostUIEvents.notifyObservers(BoostUIEvents.Event.actionLinkClicked, id)

                    val showLinkClickAsChatBubble =
                        customConfig?.chatPanel?.settings?.showLinkClickAsChatBubble
                            ?: ChatBackend.customConfig?.chatPanel?.settings?.showLinkClickAsChatBubble
                            ?: ChatBackend.config?.chatPanel?.settings?.showLinkClickAsChatBubble
                            ?: ChatPanelDefaults.Settings.showLinkClickAsChatBubble

                    if (showLinkClickAsChatBubble) {
                        ChatBackend.userActionMessage(text)
                    }
                } else {
                    try {
                        Intent(Intent.ACTION_VIEW).let {
                            it.setData(Uri.parse(url))
                            startActivity(it)
                        }
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(requireContext(), R.string.network_error_message, Toast.LENGTH_SHORT).show()
                    }

                    BoostUIEvents.notifyObservers(BoostUIEvents.Event.externalLinkClicked, url)
                }
            }
        } else textView.text = text
        /*if (animated)
            view.animation = AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)*/

        updateStyling(ChatBackend.config)
        ChatBackend.addConfigObserver(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateStyling(config: ChatConfig?) {
        if (config == null) return

        @ColorInt val backgroundColor: Int
        @ColorInt val textColor: Int

        if (isClient) {
            backgroundColor = customConfig?.chatPanel?.styling?.chatBubbles?.userBackgroundColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.userBackgroundColor
                ?: config.chatPanel?.styling?.chatBubbles?.userBackgroundColor
                ?: ContextCompat.getColor(requireContext(), R.color.userBackgroundColor)
            textColor = customConfig?.chatPanel?.styling?.chatBubbles?.userTextColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.userTextColor
                ?: config.chatPanel?.styling?.chatBubbles?.userTextColor
                ?: ContextCompat.getColor(requireContext(), R.color.userTextColor)
        } else {
            backgroundColor = customConfig?.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: config.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: ContextCompat.getColor(requireContext(), R.color.vaBackgroundColor)
            textColor = customConfig?.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: config.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: ContextCompat.getColor(requireContext(), R.color.vaTextColor)
        }

        @FontRes val bodyFontResource = customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont
        bodyFontResource?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                textView.typeface = typeface
            } catch (e: java.lang.Exception) {
                print("error")
            }
        }

        textView.setTextColor(textColor)
        textView.setLinkTextColor(textColor)
        view?.backgroundTintList = ColorStateList.valueOf(backgroundColor)
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}

}