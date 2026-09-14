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
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselTemplate

/**
 * Deprecated. Hosts a [ChatMessageCarouselView], which does the work.
 *
 * Messages are rendered as views; see [ChatViewFragmentDelegate.getChatMessagePartView].
 */
open class ChatMessageCarouselFragment(
    var carousel: CarouselTemplate? = null,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null
) : Fragment(), ChatBackend.ConfigObserver {

    val carouselKey = "carousel"

    lateinit var carouselView: ChatMessageCarouselView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            carousel = it.getParcelable(carouselKey) ?: carousel
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        carouselView =
            ChatMessageCarouselView(requireContext(), carousel, animated, customConfig)
        return carouselView
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
        if (::carouselView.isInitialized) carouselView.applyStyling()
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling()

    override fun onFailure(backend: ChatBackend, error: Exception) {}
}
