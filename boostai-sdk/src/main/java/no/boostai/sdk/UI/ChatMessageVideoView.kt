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

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import no.boostai.sdk.R

/**
 * An embedded YouTube, Vimeo or Wistia player.
 *
 * The view counterpart of [ChatMessageVideoFragment].
 */
open class ChatMessageVideoView(
    context: Context,
    val source: String?,
    val url: String?
) : FrameLayout(context) {

    val webView: WebView

    init {
        View.inflate(context, R.layout.chat_server_message_video, this)
        webView = findViewById(R.id.chat_server_message_webview)
        loadVideo()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadVideo() {
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        getChildAt(0).animation =
            AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)

        val embedUrl = url?.let { ChatMessageRenderers.videoEmbedUrl(source, it) } ?: return
        webView.loadData(
            ChatMessageRenderers.videoEmbedHTML(embedUrl),
            "text/html; charset=UTF-8",
            null
        )
        webView.visibility = View.VISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        webView.onResume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Pause the player when the message leaves the screen (chat closed, conversation
        // deleted) — nothing else stops it, and a playing embed kept its audio going until the
        // WebView happened to be garbage collected
        webView.onPause()
    }
}
