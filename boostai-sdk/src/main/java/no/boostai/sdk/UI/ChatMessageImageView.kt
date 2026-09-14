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
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import no.boostai.sdk.R

/**
 * An image sent as part of a message.
 *
 * The view counterpart of [ChatMessageImageFragment].
 */
open class ChatMessageImageView(
    context: Context,
    val url: String?,
    val animated: Boolean = true
) : FrameLayout(context) {

    val imageView: ImageView

    init {
        View.inflate(context, R.layout.chat_server_message_image, this)
        imageView = findViewById(R.id.image)
        imageView.clipToOutline = true

        if (animated) {
            getChildAt(0).animation =
                AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
        }
        if (!url.isNullOrEmpty()) Glide.with(this).load(url).into(imageView)
    }
}
