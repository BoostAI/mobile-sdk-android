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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.Response.FunctionType
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.R

open class ChatMessageButtonsFragment(
    var links: ArrayList<Link>? = null,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null,
    var buttonDelegate: ChatMessageButtonDelegate? = null,
    var chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null
) : Fragment(R.layout.chat_server_message_buttons),
    ChatMessageButtonDelegate {

    val linksKey = "links"
    val customConfigKey = "customConfig"
    var lastTappedButton: ChatMessageButtonFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            links = it.getParcelableArrayList(linksKey)
            customConfig = it.getParcelable(customConfigKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelableArrayList(linksKey, links)
        outState.putParcelable(customConfigKey, customConfig)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            links?.let { links ->
                val function = links.first().function

                if (function == FunctionType.APPROVE || function == FunctionType.DENY)
                    childFragmentManager.beginTransaction()
                        .add(R.id.chat_message_buttons, getChatMessageConsentFragment(links))
                        .commitAllowingStateLoss()
                else links.forEachIndexed { index, link ->
                    childFragmentManager.beginTransaction()
                        .add(R.id.chat_message_buttons, getChatMessageButtonFragment(link, index))
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    fun getChatMessageConsentFragment(links: ArrayList<Link>): Fragment =
        ChatMessageConsentFragment(links, animated, customConfig)

    fun getChatMessageButtonFragment(link: Link, index: Int): Fragment =
        ChatMessageButtonFragment(link, index, animated, customConfig, this, chatResponseViewURLHandlingDelegate)

    override fun didTapActionButton(fragment: ChatMessageButtonFragment) {
        lastTappedButton = fragment
        buttonDelegate?.didTapActionButton(fragment)
    }

    override fun disableActionButtons() {
        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.disableActionButtons()
        }
    }

    override fun enableActionButtons(makeInteractable: Boolean) {
        lastTappedButton?.let {
            it.enableActionButtons(false)
            return
        }

        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.enableActionButtons(true)
        }
    }
}