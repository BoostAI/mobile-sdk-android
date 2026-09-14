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
import android.webkit.WebView
import androidx.fragment.app.Fragment

/**
 * Deprecated. Hosts a [ChatMessageVideoView], which does the work.
 *
 * Messages are rendered as views; see [ChatViewFragmentDelegate.getChatMessagePartView].
 */
open class ChatMessageVideoFragment(var source: String? = null, var url: String? = null) :
    Fragment() {

    val sourceKey = "source"
    val urlKey = "url"

    lateinit var videoView: ChatMessageVideoView
    var contentWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            source = it.getString(sourceKey) ?: source
            url = it.getString(urlKey) ?: url
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        videoView = ChatMessageVideoView(requireContext(), source, url)
        contentWebView = videoView.webView
        return videoView
    }

    // Kept as forwarders because they are public API; the implementations live in
    // [ChatMessageRenderers] so both rendering paths share them.

    fun youTubeVideo(url: String): String? =
        ChatMessageRenderers.videoEmbedUrl("youtube", url)
            ?.let { ChatMessageRenderers.videoEmbedHTML(it) }

    fun vimeoVideo(url: String): String? =
        ChatMessageRenderers.videoEmbedUrl("vimeo", url)
            ?.let { ChatMessageRenderers.videoEmbedHTML(it) }

    fun wistiaVideo(url: String): String? =
        ChatMessageRenderers.videoEmbedUrl("wistia", url)
            ?.let { ChatMessageRenderers.videoEmbedHTML(it) }

    fun extractYouTubeID(url: String): String? = ChatMessageRenderers.extractYouTubeID(url)

    fun extractVimeoID(url: String): String? = ChatMessageRenderers.extractVimeoID(url)

    fun extractWistiaURL(url: String): String? = ChatMessageRenderers.extractWistiaURL(url)

    fun videoEmbedHTML(url: String): String = ChatMessageRenderers.videoEmbedHTML(url)
}
