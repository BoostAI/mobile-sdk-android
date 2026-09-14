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
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.request.RequestOptions
import androidx.core.content.ContextCompat
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselElement
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselTemplate
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

/**
 * A horizontally scrolling row of cards.
 *
 * The view counterpart of [ChatMessageCarouselFragment].
 */
open class ChatMessageCarouselView(
    context: Context,
    val carousel: CarouselTemplate?,
    val animated: Boolean = true,
    val customConfig: ChatConfig? = null
) : FrameLayout(context) {

    val scrollView: HorizontalScrollView
    val container: LinearLayout

    init {
        View.inflate(context, R.layout.chat_carousel_fragment, this)
        scrollView = getChildAt(0) as HorizontalScrollView
        container = findViewById(R.id.carousel_container)

        if (animated) {
            scrollView.animation =
                AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
        }

        val inflater = LayoutInflater.from(context)
        carousel?.elements?.forEach { element ->
            val itemView = inflater.inflate(R.layout.chat_carousel_item, container, false)
            bindItem(itemView, element)
            container.addView(itemView)
        }

        extendToScreenEdges()
        applyStyling()
    }

    /**
     * The carousel sits inside fixed-width containers. Disable clipping on every ancestor up to the
     * scroll view so it can overflow, then use negative margins and padding to reach the screen
     * edges while keeping the first card aligned with normal message content.
     */
    private fun extendToScreenEdges() = post {
        val screenWidth = context.resources.displayMetrics.widthPixels

        val location = IntArray(2)
        scrollView.getLocationOnScreen(location)
        val absoluteLeft = location[0]
        val absoluteRight = screenWidth - (absoluteLeft + scrollView.width)

        var current: View = this
        while (current.parent is ViewGroup) {
            val parent = current.parent as ViewGroup
            parent.clipChildren = false
            parent.clipToPadding = false
            current = parent
            if (parent.id == R.id.chat_messages_scrollview) break
        }

        (layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = -absoluteLeft
            it.marginEnd = -absoluteRight
            it.width = screenWidth
            layoutParams = it
        }

        // The padding and clipToPadding belong on the scroll view, not on this wrapper: a
        // HorizontalScrollView clips scrolled content to its own bounds, so if the wrapper carried
        // the padding the scroll view would be laid out inset by it and cards would be cut off at
        // the message margin while swiping, instead of sliding under it to the screen edge.
        scrollView.setPadding(absoluteLeft, 0, 0, 0)
        scrollView.clipToPadding = false
    }

    private fun bindItem(itemView: View, element: CarouselElement) {
        val imageView = itemView.findViewById<ImageView>(R.id.carousel_item_image)
        val titleView = itemView.findViewById<TextView>(R.id.carousel_item_title)
        val bodyView = itemView.findViewById<TextView>(R.id.carousel_item_body)
        val buttonView = itemView.findViewById<TextView>(R.id.carousel_item_button)

        if (!element.imageUrl.isNullOrEmpty()) {
            imageView.visibility = View.VISIBLE
            val radiusPx = 10 * context.resources.displayMetrics.density
            Glide.with(itemView)
                .load(element.imageUrl)
                .apply(
                    RequestOptions.bitmapTransform(
                        MultiTransformation(
                            CenterCrop(),
                            GranularRoundedCorners(radiusPx, radiusPx, 0f, 0f)
                        )
                    )
                )
                .into(imageView)
            element.imageAltText?.let { imageView.contentDescription = it }
        } else {
            imageView.visibility = View.GONE
        }

        titleView.visibility = if (element.title.isNullOrEmpty()) View.GONE else View.VISIBLE
        titleView.text = element.title
        bodyView.visibility = if (element.body.isNullOrEmpty()) View.GONE else View.VISIBLE
        bodyView.text = element.body

        if (element.buttonText.isNullOrEmpty()) {
            buttonView.visibility = View.GONE
        } else {
            buttonView.visibility = View.VISIBLE
            buttonView.text = element.buttonText
            buttonView.setOnClickListener { onItemTapped(element) }
        }
    }

    protected open fun onItemTapped(element: CarouselElement) {
        if (element.buttonActionId != null) {
            ChatBackend.triggerAction(element.buttonActionId)
            BoostUIEvents.notifyObservers(
                BoostUIEvents.Event.actionLinkClicked,
                element.buttonActionId
            )
            return
        }

        if (element.buttonHref.isNullOrEmpty()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(element.buttonHref)))
            BoostUIEvents.notifyObservers(
                BoostUIEvents.Event.externalLinkClicked,
                element.buttonHref
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    /** Re-apply colours and fonts to every card. */
    open fun applyStyling() {
        @ColorInt val cardBackgroundColor =
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.vaBackgroundColor }
                ?: ContextCompat.getColor(context, R.color.vaBackgroundColor)
        @ColorInt val textColor =
            ChatMessageRenderers.styling(customConfig) { it?.chatBubbles?.vaTextColor }
                ?: ContextCompat.getColor(context, R.color.vaTextColor)
        @ColorInt val buttonBackgroundColor =
            ChatMessageRenderers.styling(customConfig) { it?.buttons?.backgroundColor }
                ?: ContextCompat.getColor(context, R.color.buttonBackgroundColor)
        @ColorInt val buttonTextColor =
            ChatMessageRenderers.styling(customConfig) { it?.buttons?.textColor }
                ?: ContextCompat.getColor(context, R.color.buttonTextColor)
        val bodyTypeface = ChatMessageRenderers.font(
            context,
            ChatMessageRenderers.styling(customConfig) { it?.fonts?.bodyFont }
        )
        val headlineTypeface = ChatMessageRenderers.font(
            context,
            ChatMessageRenderers.styling(customConfig) { it?.fonts?.headlineFont }
        )

        for (i in 0 until container.childCount) {
            val itemView = container.getChildAt(i)
            val titleView = itemView.findViewById<TextView>(R.id.carousel_item_title)
            val bodyView = itemView.findViewById<TextView>(R.id.carousel_item_body)
            val buttonView = itemView.findViewById<TextView>(R.id.carousel_item_button)

            // mutate() before tinting: the pill/card drawables share constant state with the
            // consent buttons and every other view inflated from the same resources. An
            // unmutated setTint here landed in the shared state, and since a tint filter masks
            // setColor completely, one rendered carousel painted every later consent button in
            // the action-button colour for the rest of the process.
            itemView.background?.mutate()?.setTint(cardBackgroundColor)
            titleView.setTextColor(textColor)
            bodyView.setTextColor(textColor)
            buttonView.background?.mutate()?.setTint(buttonBackgroundColor)
            buttonView.setTextColor(buttonTextColor)

            headlineTypeface?.let { titleView.typeface = it }
            bodyTypeface?.let {
                bodyView.typeface = it
                buttonView.typeface = it
            }
        }
    }
}
