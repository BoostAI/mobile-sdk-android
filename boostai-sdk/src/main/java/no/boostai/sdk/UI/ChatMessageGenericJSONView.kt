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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.Response.GenericCard
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

/**
 * A generic JSON card — heading, body, image and a link.
 *
 * The view counterpart of [ChatMessageGenericJSONFragment].
 */
open class ChatMessageGenericJSONView(
    context: Context,
    val card: GenericCard?,
    val animated: Boolean = true,
    val customConfig: ChatConfig? = null
) : FrameLayout(context) {

    val imageView: ImageView
    val headingTextView: TextView
    val textTextView: TextView
    val linkTextView: TextView

    init {
        View.inflate(context, R.layout.chat_server_message_generic_json_fragment, this)
        imageView = findViewById(R.id.generic_json_imageview)
        headingTextView = findViewById(R.id.generic_json_heading_textview)
        textTextView = findViewById(R.id.generic_json_text_textview)
        linkTextView = findViewById(R.id.generic_json_link_textview)

        if (animated) {
            getChildAt(0).animation =
                AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
        }

        headingTextView.text = card?.heading?.text
        headingTextView.setTypeface(null, Typeface.BOLD)
        textTextView.text = card?.body?.text
        card?.image?.url?.let { Glide.with(this).load(it).into(imageView) }
        card?.link?.text?.let {
            linkTextView.text = SpannableString(it).apply {
                setSpan(UnderlineSpan(), 0, length, 0)
            }
        }
        card?.link?.url?.let { url -> linkTextView.setOnClickListener { openLink(url) } }

        imageView.visibility = if (card?.image?.url.isNullOrEmpty()) View.GONE else View.VISIBLE
        headingTextView.visibility =
            if (headingTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        textTextView.visibility = if (textTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        linkTextView.visibility = if (linkTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        applyStyling()
    }

    protected open fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            BoostUIEvents.notifyObservers(BoostUIEvents.Event.externalLinkClicked, url)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    /** Re-apply fonts. Called by the parent when the configuration changes. */
    open fun applyStyling() {
        ChatMessageRenderers.font(
            context,
            ChatMessageRenderers.styling(customConfig) { it?.fonts?.bodyFont }
        )?.let {
            textTextView.typeface = it
            linkTextView.typeface = it
        }
        ChatMessageRenderers.font(
            context,
            ChatMessageRenderers.styling(customConfig) { it?.fonts?.headlineFont }
        )?.let { headingTextView.setTypeface(it, Typeface.BOLD) }
    }
}
