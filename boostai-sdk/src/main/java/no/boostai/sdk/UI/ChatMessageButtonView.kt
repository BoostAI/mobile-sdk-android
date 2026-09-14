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
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents
import no.boostai.sdk.UI.Helpers.TimingHelper

/**
 * A single action or link button.
 *
 * The view counterpart of [ChatMessageButtonFragment].
 */
open class ChatMessageButtonView(
    context: Context,
    val link: Link,
    val index: Int = 0,
    val animated: Boolean = true,
    val customConfig: ChatConfig? = null,
    val chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null,
    val onUploadRequested: (() -> Unit)? = null,
    val onActionTapped: ((Link) -> Unit)? = null
) : FrameLayout(context) {

    /** The button itself — the root of `chat_server_message_button`. */
    val button: View
    val textView: TextView
    val imageView: ImageView

    private var hasFocusState = false

    init {
        View.inflate(context, R.layout.chat_server_message_button, this)
        button = getChildAt(0)
        textView = findViewById(R.id.chat_server_message_button_text)
        imageView = findViewById(R.id.chat_server_message_button_image_view)

        textView.text = link.text
        textView.maxLines =
            if (ChatMessageRenderers.styling(customConfig) { it?.buttons?.multiline } == true) {
                Integer.MAX_VALUE
            } else 1

        if (!animated) button.alpha = 1.0f

        button.setOnClickListener { onTapped() }
        button.setOnFocusChangeListener { _, hasFocus ->
            hasFocusState = hasFocus
            applyStyling()
        }

        applyStyling()
    }

    private var revealed = !animated
    private val revealHandler = Handler(Looper.getMainLooper())

    // The fade-in is scheduled from attach, not construction: the message builds every part up
    // front and reveals them one by one, so a stagger timed from construction had long expired
    // by the time a later part was added — all its buttons then appeared at once, where the
    // fragment path (whose onViewCreated ran on attach) staggered them.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (revealed) return

        val pace = ChatMessageRenderers.styling(customConfig) { it?.pace }
            ?: ChatPanelDefaults.Styling.pace
        val staggerDelay = TimingHelper.calculateStaggerDelay(pace = pace, idx = index)
        revealHandler.postDelayed({
            revealed = true
            button.alpha = 1.0f
            button.animation =
                AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
        }, staggerDelay)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Not yet revealed: drop the pending reveal, and let a re-attach schedule a fresh one
        revealHandler.removeCallbacksAndMessages(null)
    }

    protected open fun onTapped() {
        if (link.id == ChatMessageRenderers.ACTION_LINK_UPLOAD) {
            onUploadRequested?.invoke()
            return
        }

        link.url?.let { url ->
            val uri = Uri.parse(url)
            if (chatResponseViewURLHandlingDelegate?.shouldOpenUrl(uri) != false) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            ChatBackend.urlButton(link.id)
            BoostUIEvents.notifyObservers(BoostUIEvents.Event.externalLinkClicked, url)
            return
        }

        onActionTapped?.invoke(link)
        ChatBackend.actionButton(link.id)
        BoostUIEvents.notifyObservers(BoostUIEvents.Event.actionLinkClicked, link.id)

        if (ChatMessageRenderers.showLinkClickAsChatBubble(customConfig)) {
            ChatBackend.userActionMessage(link.text)
        }
    }

    /**
     * Dim the button and stop it responding to taps.
     *
     * Not the inverse of [enable]: enabling only restores the dimming, because a button that was
     * never disabled has to stay tappable.
     */
    open fun disable() {
        button.background?.alpha = 127
        button.isClickable = false
    }

    /** Restore the button's normal appearance, and its tappability when [makeInteractable]. */
    open fun enable(makeInteractable: Boolean) {
        button.background?.alpha = 255
        if (makeInteractable) button.isClickable = true
    }

    /** Re-apply colours, icon and font. Called by the parent when the configuration changes. */
    open fun applyStyling() {
        @ColorInt val background = ChatMessageRenderers.styling(customConfig) { it?.buttons?.backgroundColor }
            ?: ContextCompat.getColor(context, R.color.buttonBackgroundColor)
        @ColorInt val foreground = ChatMessageRenderers.styling(customConfig) { it?.buttons?.textColor }
            ?: ContextCompat.getColor(context, R.color.buttonTextColor)
        @ColorInt val focusBackground =
            ChatMessageRenderers.styling(customConfig) { it?.buttons?.focusBackgroundColor }
                ?: background
        @ColorInt val focusForeground =
            ChatMessageRenderers.styling(customConfig) { it?.buttons?.focusTextColor }
                ?: foreground

        val tint = if (hasFocusState) focusForeground else foreground
        // mutate() first: the pill drawable's constant state is shared with the consent buttons
        // and every other button from the same resource — an unmutated setTint would repaint
        // them all.
        button.background?.mutate()?.setTint(if (hasFocusState) focusBackground else background)
        textView.setTextColor(tint)

        imageView.setImageResource(
            when {
                link.id == ChatMessageRenderers.ACTION_LINK_UPLOAD -> R.drawable.ic_upload_files
                link.type == LinkType.EXTERNAL_LINK && link.isAttachment == true -> R.drawable.ic_file
                link.type == LinkType.EXTERNAL_LINK -> R.drawable.ic_external_link_icon
                else -> R.drawable.ic_arrow_right
            }
        )
        imageView.imageTintList = ColorStateList.valueOf(tint)

        ChatMessageRenderers.font(
            context,
            ChatMessageRenderers.styling(customConfig) { it?.fonts?.bodyFont }
        )?.let { textView.typeface = it }
    }
}
