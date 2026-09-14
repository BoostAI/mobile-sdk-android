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

/**
 * Deprecated. Hosts a [ChatMessageImageView], which does the work.
 *
 * Messages are rendered as views; see [ChatViewFragmentDelegate.getChatMessagePartView].
 */
open class ChatMessageImageFragment(var url: String? = null, val animated: Boolean = true) :
    Fragment() {

    val urlKey = "url"

    lateinit var imageView: ChatMessageImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            url = it.getString(urlKey) ?: url
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        imageView = ChatMessageImageView(requireContext(), url, animated)
        imageView.setOnClickListener {
            // TODO: Expand image
        }
        return imageView
    }
}
