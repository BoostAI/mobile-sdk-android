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
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.R

/**
 * The three animated dots shown while the agent is composing a reply.
 *
 * The view counterpart of [ChatServerWaitingFragment]. Unlike the message leaves, this view
 * observes the config itself: it is transient and there is at most one per message being revealed,
 * so it never adds up — and its parent has no restyle pass that reaches it, since it is not a
 * message part.
 */
open class ChatServerWaitingView(
    context: Context,
    val customConfig: ChatConfig? = null
) : FrameLayout(context), ChatBackend.ConfigObserver {

    val dotsWrapper: ViewGroup
    val dots: List<View>

    init {
        View.inflate(context, R.layout.chat_server_waiting, this)
        dotsWrapper = findViewById(R.id.dots)
        dots = (0 until dotsWrapper.childCount).map { dotsWrapper.getChildAt(it) }

        val content = getChildAt(0)
        content.animation = AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
        dots.forEachIndexed { index, dot ->
            val animation = AnimationUtils.loadAnimation(context, R.anim.chat_waiting_animate)
            animation.repeatMode = Animation.REVERSE
            animation.startOffset = 100L * index
            dot.startAnimation(animation)
        }

        applyStyling()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ChatBackend.addConfigObserver(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ChatBackend.removeConfigObserver(this)
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = applyStyling()

    override fun onFailure(backend: ChatBackend, error: Exception) {}

    /** Re-apply colours. Called when the configuration changes. */
    open fun applyStyling() {
        @ColorInt val backgroundColor =
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.typingBackgroundColor }
                ?: ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.vaBackgroundColor }
                ?: ContextCompat.getColor(context, R.color.vaBackgroundColor)
        @ColorInt val dotColor =
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.typingDotColor }
                ?: ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.vaTextColor }
                ?: ContextCompat.getColor(context, R.color.vaTextColor)

        tint(getChildAt(0), backgroundColor)
        dots.forEach { tint(it, dotColor) }
    }

    private fun tint(view: View, @ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.background?.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
        } else {
            @Suppress("DEPRECATION")
            view.background?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
    }
}
