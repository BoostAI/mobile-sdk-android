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
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.text.Html
import android.util.Patterns
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents
import no.boostai.sdk.UI.Helpers.handleUrlClicks
import no.boostai.sdk.UI.Helpers.trimTrailingWhitespace

/**
 * A chat bubble containing text, either plain or HTML.
 *
 * The view counterpart of [ChatMessageTextFragment].
 */
open class ChatMessageTextView(
    context: Context,
    val text: String,
    val isHtml: Boolean = false,
    val isClient: Boolean = false,
    val customConfig: ChatConfig? = null,
    val chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null
) : FrameLayout(context) {

    /** The bubble itself — the root of `chat_message_text_fragment`. */
    val bubble: View
    val textView: TextView

    init {
        View.inflate(context, R.layout.chat_message_text_fragment, this)
        bubble = getChildAt(0)
        textView = findViewById(R.id.chat_message_textview)

        if (isClient) {
            bubble.background = ContextCompat.getDrawable(context, R.drawable.semi_rounded_alt)
            // The spacing below a client message is owned by ChatMessagePartView
            // (chat_message_client_spacing); keeping the bubble's own XML margin too would
            // double the gap.
            (bubble.layoutParams as MarginLayoutParams).bottomMargin = 0
        }

        if (isHtml) renderHtml() else textView.text = text

        applyStyling()
    }

    private fun renderHtml() {
        var html = text
        // Turn bare links into anchors — human chat agents often send plain URLs
        val matcher = Patterns.WEB_URL.matcher(html)
        while (matcher.find()) {
            val url = matcher.group()
            val index = html.indexOf(url)
            if (index >= 6 &&
                html.substring(index - 6, index) != "href=\"" &&
                html.substring(index - 1, index) != "@"
            ) {
                html = html.replaceFirst(url, "<a href=\"$url\">$url</a>")
            }
        }

        textView.text = trimTrailingWhitespace(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        textView.handleUrlClicks { url, linkText -> onLinkTapped(url, linkText) }
    }

    protected open fun onLinkTapped(url: String, linkText: String) {
        if (url.startsWith("boostai://")) {
            val id = url.removePrefix("boostai://")

            ChatBackend.actionButton(id)
            BoostUIEvents.notifyObservers(BoostUIEvents.Event.actionLinkClicked, id)

            if (ChatMessageRenderers.showLinkClickAsChatBubble(customConfig)) {
                ChatBackend.userActionMessage(linkText)
            }
            return
        }

        try {
            val uri = Uri.parse(url)
            if (chatResponseViewURLHandlingDelegate?.shouldOpenUrl(uri) != false) {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.externalLinkClicked, url)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    /** Re-apply colours and fonts. Called by the parent when the configuration changes. */
    open fun applyStyling() {
        @ColorInt val background = if (isClient) {
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.userBackgroundColor }
                ?: ContextCompat.getColor(context, R.color.userBackgroundColor)
        } else {
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.vaBackgroundColor }
                ?: ContextCompat.getColor(context, R.color.vaBackgroundColor)
        }
        @ColorInt val foreground = if (isClient) {
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.userTextColor }
                ?: ContextCompat.getColor(context, R.color.userTextColor)
        } else {
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.vaTextColor }
                ?: ContextCompat.getColor(context, R.color.vaTextColor)
        }

        ChatMessageRenderers.font(
            context,
            ChatMessageRenderers.styling(customConfig) { it?.fonts?.bodyFont }
        )?.let { textView.typeface = it }

        textView.setTextColor(foreground)
        textView.setLinkTextColor(foreground)
        bubble.backgroundTintList = ColorStateList.valueOf(background)
    }
}
