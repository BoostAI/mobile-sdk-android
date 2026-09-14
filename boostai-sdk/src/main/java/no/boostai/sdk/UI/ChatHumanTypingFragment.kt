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

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.R

open class ChatHumanTypingFragment (var customConfig: ChatConfig? = null) :
    Fragment(R.layout.chat_human_typing),
    ChatBackend.ConfigObserver {

    val customConfigKey = "customConfig"

    lateinit var dotsWrapper: ViewGroup
    var dots = arrayOfNulls<View>(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            customConfig = it.getParcelable(customConfigKey) ?: customConfig
        }
    }

    // Intentionally no `onSaveInstanceState` override: this fragment is rebuilt by
    // ChatViewFragment when the conversation is replayed, so persisting its payload here
    // only added to the activity's state parcel (see ChatViewFragment.onCreate).

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dotsWrapper = view.findViewById(R.id.dots);

        view.animation = AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
        for (index in 0 until dotsWrapper.childCount) {
            val dotAnimation = AnimationUtils.loadAnimation(context, R.anim.chat_waiting_animate)

            dots[index] = dotsWrapper.getChildAt(index)
            dotAnimation.repeatMode = Animation.REVERSE;
            dotAnimation.startOffset = 100.times(index).toLong();
            dots[index]?.startAnimation(dotAnimation)
        }
        ChatBackend.config?.let { updateStyling(it) }
        ChatBackend.addConfigObserver(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateStyling(config: ChatConfig) {
        @ColorInt val backgroundColor =
            customConfig?.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: config.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: ContextCompat.getColor(requireContext(), R.color.vaBackgroundColor)
        @ColorInt val textColor =
            customConfig?.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: config.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: ContextCompat.getColor(requireContext(), R.color.vaTextColor)

        backgroundColor.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                view?.background?.colorFilter =
                    BlendModeColorFilter(it, BlendMode.SRC_ATOP)
            else view?.background?.setColorFilter(it, PorterDuff.Mode.SRC_ATOP)
        }
        textColor.let { color: Int ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                dots.forEach {
                    it?.background?.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
                }
            else dots.forEach { it?.background?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP) }
        }
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}

}