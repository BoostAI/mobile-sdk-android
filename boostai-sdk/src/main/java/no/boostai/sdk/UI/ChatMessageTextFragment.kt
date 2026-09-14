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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig

/**
 * Deprecated. Hosts a [ChatMessageTextView], which does the work.
 *
 * Messages are rendered as views; see [ChatViewFragmentDelegate.getChatMessagePartView].
 */
open class ChatMessageTextFragment(
    var text: String? = null,
    var isHtml: Boolean = false,
    var isClient: Boolean = false,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null,
    var chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null
) : Fragment(), ChatBackend.ConfigObserver {

    val textKey = "text"
    val isHtmlKey = "isHtml"
    val isClientKey = "isClient"
    val customConfigKey = "customConfig"

    /** The hosted view. Available from [onCreateView]. */
    lateinit var messageView: ChatMessageTextView
    val textView: android.widget.TextView
        get() = messageView.textView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            text = it.getString(textKey) ?: text
            isHtml = it.getBoolean(isHtmlKey, isHtml)
            isClient = it.getBoolean(isClientKey, isClient)
            customConfig = it.getParcelable(customConfigKey) ?: customConfig
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        messageView = ChatMessageTextView(
            requireContext(),
            text ?: "",
            isHtml,
            isClient,
            customConfig,
            chatResponseViewURLHandlingDelegate
        )
        return messageView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ChatBackend.addConfigObserver(this)
        updateStyling(ChatBackend.config)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateStyling(config: ChatConfig?) {
        if (config == null) return

        if (::messageView.isInitialized) messageView.applyStyling()
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}
}
