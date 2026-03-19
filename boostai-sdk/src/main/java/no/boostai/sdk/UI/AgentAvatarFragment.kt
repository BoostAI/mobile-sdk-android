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
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

open class AgentAvatarFragment(
    @DrawableRes var avatarImageResource: Int? = null,
    var customConfig: ChatConfig? = null
) : Fragment(R.layout.agent_avatar_view) {

    lateinit var imageView: ImageView
    val avatarImageResourceKey = "avatarImageResource"
    val customConfigKey = "customConfig"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            avatarImageResource = avatarImageResource ?: it.getInt(avatarImageResourceKey)
            customConfig = customConfig ?: it.getParcelable(customConfigKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(customConfigKey, customConfig)

        avatarImageResource?.let {
            outState.putInt(avatarImageResourceKey, it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.agent_avatar_imageview)

        val originalBottomMargin = (imageView.layoutParams as FrameLayout.LayoutParams).bottomMargin
        val originalEndMargin = (imageView.layoutParams as FrameLayout.LayoutParams).marginEnd
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = imageView.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = originalBottomMargin + insets.bottom
            lp.marginEnd = originalEndMargin + insets.right
            imageView.layoutParams = lp
            windowInsets
        }

        avatarImageResource?.let { imageView.setImageResource(it) }
        imageView.setOnClickListener {
            Intent(requireActivity(), ChatViewActivity::class.java).let { intent ->
                intent.putExtra(ChatViewActivity.IS_DIALOG, true)
                customConfig?.let { config ->
                    intent.putExtra(ChatViewActivity.CUSTOM_CONFIG, config)
                }

                startActivity(intent)

                BoostUIEvents.notifyObservers(BoostUIEvents.Event.chatPanelOpened)
            }
        }
    }

}