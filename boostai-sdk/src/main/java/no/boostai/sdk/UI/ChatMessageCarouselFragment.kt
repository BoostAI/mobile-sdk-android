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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.request.RequestOptions
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselElement
import no.boostai.sdk.ChatBackend.Objects.Response.CarouselTemplate
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents

open class ChatMessageCarouselFragment(
    var carousel: CarouselTemplate? = null,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null
) : Fragment(R.layout.chat_carousel_fragment) {

    val carouselKey = "carousel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            carousel = it.getParcelable(carouselKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(carouselKey, carousel)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (animated)
            view.animation = AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)

        val container = view.findViewById<LinearLayout>(R.id.carousel_container)
        val inflater = LayoutInflater.from(context)

        carousel?.elements?.forEach { element ->
            val itemView = inflater.inflate(R.layout.chat_carousel_item, container, false)
            bindCarouselItem(itemView, element)
            container.addView(itemView)
        }

        // Make the HorizontalScrollView extend edge-to-edge. The carousel is deeply nested
        // inside fixed-width containers. We disable clipping on all ancestors up to the
        // ScrollView so the carousel can overflow visually, then use negative margins and
        // padding to position it edge-to-edge while keeping the first card aligned with
        // normal message content.
        view.post {
            val scrollView = view as HorizontalScrollView
            val screenWidth = resources.displayMetrics.widthPixels

            // Calculate the absolute left position of the HorizontalScrollView on screen
            val location = IntArray(2)
            scrollView.getLocationOnScreen(location)
            val absoluteLeft = location[0]
            val absoluteRight = screenWidth - (absoluteLeft + scrollView.width)

            // Disable clipping on all ancestor ViewGroups so the carousel can overflow
            var current: View = scrollView
            while (current.parent is ViewGroup) {
                val parent = current.parent as ViewGroup
                parent.clipChildren = false
                parent.clipToPadding = false
                current = parent
                if (parent.id == R.id.chat_messages_scrollview) break
            }

            // Apply negative margins to extend the scroll view to screen edges
            val layoutParams = scrollView.layoutParams
            if (layoutParams is ViewGroup.MarginLayoutParams) {
                layoutParams.marginStart = -absoluteLeft
                layoutParams.marginEnd = -absoluteRight
                layoutParams.width = screenWidth
                scrollView.layoutParams = layoutParams
            }

            // Pad the start so the first card aligns with normal message content position
            scrollView.setPadding(absoluteLeft, 0, 0, 0)
            scrollView.clipToPadding = false
        }

        updateStyling()
    }

    private fun bindCarouselItem(itemView: View, element: CarouselElement) {
        val imageView = itemView.findViewById<ImageView>(R.id.carousel_item_image)
        val titleView = itemView.findViewById<TextView>(R.id.carousel_item_title)
        val bodyView = itemView.findViewById<TextView>(R.id.carousel_item_body)
        val buttonView = itemView.findViewById<TextView>(R.id.carousel_item_button)

        // Image
        if (!element.imageUrl.isNullOrEmpty()) {
            imageView.visibility = View.VISIBLE
            val radiusPx = (10 * resources.displayMetrics.density)
            Glide.with(this)
                .load(element.imageUrl)
                .apply(RequestOptions.bitmapTransform(
                    MultiTransformation(
                        CenterCrop(),
                        GranularRoundedCorners(radiusPx, radiusPx, 0f, 0f)
                    )
                ))
                .into(imageView)
            element.imageAltText?.let { imageView.contentDescription = it }
        } else {
            imageView.visibility = View.GONE
        }

        // Title
        if (!element.title.isNullOrEmpty()) {
            titleView.visibility = View.VISIBLE
            titleView.text = element.title
        } else {
            titleView.visibility = View.GONE
        }

        // Body
        if (!element.body.isNullOrEmpty()) {
            bodyView.visibility = View.VISIBLE
            bodyView.text = element.body
        } else {
            bodyView.visibility = View.GONE
        }

        // Button
        if (!element.buttonText.isNullOrEmpty()) {
            buttonView.visibility = View.VISIBLE
            buttonView.text = element.buttonText

            buttonView.setOnClickListener {
                if (element.buttonActionId != null) {
                    ChatBackend.triggerAction(element.buttonActionId)
                    BoostUIEvents.notifyObservers(
                        BoostUIEvents.Event.actionLinkClicked,
                        element.buttonActionId
                    )
                } else if (!element.buttonHref.isNullOrEmpty()) {
                    Intent(Intent.ACTION_VIEW).let { intent ->
                        intent.data = Uri.parse(element.buttonHref)
                        startActivity(intent)
                    }
                    BoostUIEvents.notifyObservers(
                        BoostUIEvents.Event.externalLinkClicked,
                        element.buttonHref
                    )
                }
            }
        } else {
            buttonView.visibility = View.GONE
        }
    }

    fun updateStyling() {
        val container = view?.findViewById<LinearLayout>(R.id.carousel_container) ?: return

        @ColorInt val cardBackgroundColor =
            customConfig?.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: ChatBackend.config?.chatPanel?.styling?.chatBubbles?.vaBackgroundColor
                ?: ContextCompat.getColor(requireContext(), R.color.vaBackgroundColor)
        @ColorInt val textColor =
            customConfig?.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: ChatBackend.config?.chatPanel?.styling?.chatBubbles?.vaTextColor
                ?: ContextCompat.getColor(requireContext(), R.color.vaTextColor)
        @ColorInt val buttonBackgroundColor =
            customConfig?.chatPanel?.styling?.buttons?.backgroundColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.backgroundColor
                ?: ChatBackend.config?.chatPanel?.styling?.buttons?.backgroundColor
                ?: ContextCompat.getColor(requireContext(), R.color.buttonBackgroundColor)
        @ColorInt val buttonTextColor = customConfig?.chatPanel?.styling?.buttons?.textColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.textColor
            ?: ChatBackend.config?.chatPanel?.styling?.buttons?.textColor
            ?: ContextCompat.getColor(requireContext(), R.color.buttonTextColor)
        @FontRes val bodyFont = customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont
        @FontRes val headlineFont = customConfig?.chatPanel?.styling?.fonts?.headlineFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.headlineFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.headlineFont

        for (i in 0 until container.childCount) {
            val itemView = container.getChildAt(i)
            val titleView = itemView.findViewById<TextView>(R.id.carousel_item_title)
            val bodyView = itemView.findViewById<TextView>(R.id.carousel_item_body)
            val buttonView = itemView.findViewById<TextView>(R.id.carousel_item_button)

            itemView.background?.setTint(cardBackgroundColor)
            titleView.setTextColor(textColor)
            bodyView.setTextColor(textColor)
            buttonView.background?.setTint(buttonBackgroundColor)
            buttonView.setTextColor(buttonTextColor)

            headlineFont?.let {
                try {
                    val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                    titleView.typeface = typeface
                } catch (e: Exception) {}
            }

            bodyFont?.let {
                try {
                    val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                    bodyView.typeface = typeface
                    buttonView.typeface = typeface
                } catch (e: Exception) {}
            }
        }
    }
}
