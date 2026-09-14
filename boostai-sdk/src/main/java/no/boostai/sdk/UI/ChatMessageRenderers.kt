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
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.Styling

/**
 * Cross-cutting helpers for the message views.
 *
 * Rendering itself lives in the view classes — [ChatMessageTextView], [ChatMessageButtonView] and
 * the rest. What is left here is what genuinely belongs to no single view: the configuration
 * lookup chain, font resolution, and the pure URL handling behind embedded video.
 */
object ChatMessageRenderers {

    /** The id used for the "upload a file" action link. */
    const val ACTION_LINK_UPLOAD = "upload"

    /**
     * Resolve a styling value, preferring the view's own config, then the one set on the backend,
     * then whatever the server sent. Returns `null` when none of them define it, so callers can
     * fall back to a resource default.
     */
    fun <T> styling(customConfig: ChatConfig?, pick: (Styling?) -> T?): T? =
        pick(customConfig?.chatPanel?.styling)
            ?: pick(ChatBackend.customConfig?.chatPanel?.styling)
            ?: pick(ChatBackend.config?.chatPanel?.styling)

    /** Load a configured font, or `null` if unset or unloadable. */
    fun font(context: Context, @FontRes res: Int?): Typeface? = res?.let {
        try {
            ResourcesCompat.getFont(context.applicationContext, it)
        } catch (e: Exception) {
            null
        }
    }

    /** Whether a tapped link should also appear as a chat bubble from the user. */
    fun showLinkClickAsChatBubble(customConfig: ChatConfig?): Boolean =
        customConfig?.chatPanel?.settings?.showLinkClickAsChatBubble
            ?: ChatBackend.customConfig?.chatPanel?.settings?.showLinkClickAsChatBubble
            ?: ChatBackend.config?.chatPanel?.settings?.showLinkClickAsChatBubble
            ?: ChatPanelDefaults.Settings.showLinkClickAsChatBubble

    // ---------------------------------------------------------------- embedded video

    /** The embed URL for a supported video [source], or `null` if it cannot be derived. */
    fun videoEmbedUrl(source: String?, url: String): String? = when (source) {
        "youtube" -> extractYouTubeID(url)?.let { "https://www.youtube.com/embed/$it" }
        "vimeo" -> extractVimeoID(url)?.let {
            "https://player.vimeo.com/video/$it?title=0&byline=0&styling=portrait"
        }
        "wistia" -> extractWistiaURL(url)?.let {
            "https://fast.wistia.net/embed/iframe/$it?seo=false&videoFoam=true"
        }
        else -> null
    }

    fun extractYouTubeID(url: String): String? {
        val pattern = Regex("(.*?)(^|/|v=)([a-z0-9_-]{11})(.*)?", RegexOption.IGNORE_CASE)
        pattern.findAll(url).forEach {
            if (it.groups.size >= 4) return it.groups[3]?.value
        }
        return null
    }

    fun extractVimeoID(url: String): String? {
        val pattern = Regex(
            "(http|https)?://(www\\.|player\\.)?vimeo\\.com/(?:channels/(?:\\w+/)?|groups/([^/]*)/videos/|video/|)(\\d+)(?:|/\\?)",
            RegexOption.IGNORE_CASE
        )
        pattern.findAll(url).forEach {
            if (it.groups.size >= 5) return it.groups[4]?.value
        }
        return null
    }

    fun extractWistiaURL(url: String): String? {
        val pattern =
            Regex("(http|https)?:?//(www)?wistia.com/([a-z0-9]+)/?", RegexOption.IGNORE_CASE)
        pattern.findAll(url).forEach {
            if (it.groups.size >= 4) return it.groups[3]?.value
        }
        return null
    }

    fun videoEmbedHTML(url: String): String = """
        <!doctype html>
        <html>
            <head>
                <style>
                    html,
                    body {
                        overflow: hidden;
                    }

                    body {
                        margin: 0;
                        padding: 0;
                        width: 100vw;
                        height: 100vh;
                    }

                    iframe {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                    }
                </style>
            </head>
            <body>
                <iframe src="$url" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" scrolling="no" playsinline webkitAllowFullScreen mozallowfullscreen allowfullscreen></iframe>
            </body>
        </html>
    """.trimIndent()
}
