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

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatConfigDefaults
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.R
import no.boostai.sdk.UI.Helpers.TimingHelper
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.concurrent.schedule

open class ChatMessageButtonFragment(
    var link: Link? = null,
    var idx: Int = 0,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null
) : Fragment(R.layout.chat_server_message_button),
    ChatBackend.ConfigObserver,
    Animation.AnimationListener {

    private val FILE_PICKER_REQUEST = 847322
    val ACTION_LINK_UPLOAD = "upload"
    lateinit var textView: TextView
    lateinit var imageView: ImageView

    val linkKey = "link"
    val idxKey = "idx"
    val customConfigKey = "customConfig"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            link = it.getParcelable(linkKey)
            idx = it.getInt(idxKey)
            customConfig = it.getParcelable(customConfigKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(linkKey, link)
        outState.putInt(idxKey, idx)
        outState.putParcelable(customConfigKey, customConfig)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textView = view.findViewById(R.id.chat_server_message_button_text)
        imageView = view.findViewById(R.id.chat_server_message_button_image_view)

        val pace = ChatBackend.config?.pace ?: ChatConfigDefaults.pace
        val staggerDelay = TimingHelper.calculateStaggerDelay(pace = pace, idx = idx)
        val fragment = this

        textView.text = link?.text
        if (animated) {
            Timer().schedule(staggerDelay) {
                view.alpha = 1.0f
                val fadeInAnimation =
                    AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
                view.animation = fadeInAnimation
                fadeInAnimation.setAnimationListener(fragment)
            }
            Timer().schedule(staggerDelay + 300) {
                view.alpha = 1.0f
            }
        }
        else view.alpha = 1.0F
        view.setOnClickListener {
            if (link?.id == ACTION_LINK_UPLOAD) {
                Intent(Intent.ACTION_GET_CONTENT).let { intent ->
                    intent.setType("*/*")
                    startActivityForResult(intent, FILE_PICKER_REQUEST);
                }
            } else link?.url?.let { url ->
                Intent(Intent.ACTION_VIEW).let {
                    it.data = Uri.parse(url)
                    startActivity(it)
                }
            } ?: link?.let {
                ChatBackend.actionButton(it.id)
            }
        }
        updateStyling(ChatBackend.config)
        ChatBackend.addConfigObserver(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_PICKER_REQUEST && resultCode == Activity.RESULT_OK)
            data?.data?.let {
                val fileInputStream = requireActivity().contentResolver.openInputStream(it)
                val outFile =
                    File.createTempFile("test", ".svg", requireActivity().cacheDir)
                val outputStream = FileOutputStream(outFile)
                val files = Arrays.asList(outFile)

                fileInputStream?.copyTo(outputStream)
                ChatBackend.uploadFilesToAPI(files)
            }
        else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateStyling(config: ChatConfig?) {
        if (config == null) return

        @ColorRes val textColor = customConfig?.linkBelowColor ?: config.linkBelowColor
            ?: ContextCompat.getColor(requireContext(), R.color.linkBelowColor)
        @ColorRes val backgroundColor = customConfig?.linkBelowBackground
            ?: config.linkBelowBackground
            ?: ContextCompat.getColor(requireContext(), R.color.linkBelowBackground)

        val linkDrawable: Int

        if (link?.id == ACTION_LINK_UPLOAD) linkDrawable = R.drawable.ic_upload_files
        else if (link?.type == LinkType.external_link)
            linkDrawable = R.drawable.ic_external_link_icon
        else linkDrawable = R.drawable.ic_arrow_right
        textView.setTextColor(textColor)
        imageView.setImageResource(linkDrawable)
        imageView.imageTintList = ColorStateList.valueOf(textColor)
        (view?.background as? GradientDrawable)?.setColor(backgroundColor)
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}

    override fun onAnimationStart(p0: Animation?) {}
    override fun onAnimationEnd(p0: Animation?) {
        view?.alpha = 1.0f
    }
    override fun onAnimationRepeat(p0: Animation?) {}

}