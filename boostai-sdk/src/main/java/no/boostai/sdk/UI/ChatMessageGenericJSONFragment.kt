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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.Response.GenericCard

/**
 * Deprecated. Hosts a [ChatMessageGenericJSONView], which does the work.
 *
 * Messages are rendered as views; see [ChatViewFragmentDelegate.getChatMessagePartView].
 */
open class ChatMessageGenericJSONFragment(
    var card: GenericCard? = null,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null
) : Fragment(), ChatBackend.ConfigObserver {

    val cardKey = "card"

    lateinit var cardView: ChatMessageGenericJSONView
    val imageView: ImageView get() = cardView.imageView
    val headingTextView: TextView get() = cardView.headingTextView
    val textTextView: TextView get() = cardView.textTextView
    val linkTextView: TextView get() = cardView.linkTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            card = it.getParcelable(cardKey) ?: card
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        cardView = ChatMessageGenericJSONView(requireContext(), card, animated, customConfig)
        return cardView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ChatBackend.addConfigObserver(this)
        updateStyling()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateStyling() {
        if (::cardView.isInitialized) cardView.applyStyling()
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling()

    override fun onFailure(backend: ChatBackend, error: Exception) {}
}
