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
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.File
import no.boostai.sdk.ChatBackend.Objects.FileUpload
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents
import no.boostai.sdk.UI.Helpers.TimingHelper
import java.io.FileOutputStream

open class ChatMessageButtonFragment(
    var link: Link? = null,
    var idx: Int = 0,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null,
    var buttonDelegate: ChatMessageButtonDelegate? = null,
    var chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null
) : Fragment(R.layout.chat_server_message_button),
    ChatBackend.ConfigObserver,
    Animation.AnimationListener,
    ChatMessageButtonDelegate {

    private val FILE_PICKER_REQUEST = 847322
    val ACTION_LINK_UPLOAD = "upload"
    lateinit var textView: TextView
    lateinit var imageView: ImageView
    lateinit var onClickListener: View.OnClickListener

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

        val pace = customConfig?.chatPanel?.styling?.pace
            ?: ChatBackend.customConfig?.chatPanel?.styling?.pace
            ?: ChatBackend.config?.chatPanel?.styling?.pace
            ?: ChatPanelDefaults.Styling.pace
        val staggerDelay = TimingHelper.calculateStaggerDelay(pace = pace, idx = idx)
        val fragment = this

        textView.text = link?.text
        if (animated) {
            val fadeInAnimation =
                AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                if (context != null) {
                    view.alpha = 1.0f
                    view.animation = fadeInAnimation
                    fadeInAnimation.setAnimationListener(fragment)
                }
            }, staggerDelay)

            handler.postDelayed({
                if (context != null) {
                    view.alpha = 1.0f
                }
            }, staggerDelay + 300)
        }
        else view.alpha = 1.0F

        val multiline = customConfig?.chatPanel?.styling?.buttons?.multiline
            ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.multiline
            ?: ChatBackend.config?.chatPanel?.styling?.buttons?.multiline
            ?: false

        textView.maxLines = if (multiline) Integer.MAX_VALUE else 1

        val listener = View.OnClickListener {
            if (link?.id == ACTION_LINK_UPLOAD) {
                Intent(Intent.ACTION_GET_CONTENT).let { intent ->
                    intent.setType("*/*")
                    startActivityForResult(intent, FILE_PICKER_REQUEST);
                }
            } else link?.url?.let { url ->
                val parsedUrl = Uri.parse(url)
                val openUrl = {
                    Intent(Intent.ACTION_VIEW).let {
                        it.data = parsedUrl
                        startActivity(it)
                    }
                }

                if (chatResponseViewURLHandlingDelegate == null ||
                    chatResponseViewURLHandlingDelegate!!.shouldOpenUrl(parsedUrl)) {
                    openUrl()
                }

                BoostUIEvents.notifyObservers(BoostUIEvents.Event.externalLinkClicked, url)
            } ?: link?.let {
                buttonDelegate?.didTapActionButton(this)

                ChatBackend.actionButton(it.id)
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.actionLinkClicked, it.id)

                val showLinkClickAsChatBubble =
                    customConfig?.chatPanel?.settings?.showLinkClickAsChatBubble
                        ?: ChatBackend.customConfig?.chatPanel?.settings?.showLinkClickAsChatBubble
                        ?: ChatBackend.config?.chatPanel?.settings?.showLinkClickAsChatBubble
                        ?: ChatPanelDefaults.Settings.showLinkClickAsChatBubble

                if (showLinkClickAsChatBubble) {
                    ChatBackend.userActionMessage(it.text)
                }
            }
        }
        onClickListener = listener
        view.setOnClickListener(listener)

        view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            val backgroundColor = customConfig?.chatPanel?.styling?.buttons?.backgroundColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.backgroundColor
                ?: ChatBackend.config?.chatPanel?.styling?.buttons?.backgroundColor
                ?: ContextCompat.getColor(requireContext(), R.color.buttonBackgroundColor)
            val focusBackgroundColor =
                customConfig?.chatPanel?.styling?.buttons?.focusBackgroundColor
                    ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.focusBackgroundColor
                    ?: ChatBackend.config?.chatPanel?.styling?.buttons?.focusBackgroundColor
                    ?: backgroundColor
            val textColor = customConfig?.chatPanel?.styling?.buttons?.textColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.textColor
                ?: ChatBackend.config?.chatPanel?.styling?.buttons?.textColor
                ?: ContextCompat.getColor(requireContext(), android.R.color.white)
            val focusTextColor = customConfig?.chatPanel?.styling?.buttons?.focusTextColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.focusTextColor
                ?: ChatBackend.config?.chatPanel?.styling?.buttons?.focusTextColor
                ?: textColor

            view.background?.setTint(if (hasFocus) focusBackgroundColor else backgroundColor)
            imageView.imageTintList =
                ColorStateList.valueOf(if (hasFocus) focusTextColor else textColor)
            textView.setTextColor(if (hasFocus) focusTextColor else textColor)
        }

        updateStyling(ChatBackend.config)
        ChatBackend.addConfigObserver(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val contentResolver = requireContext().contentResolver

                val mimeType: String = contentResolver.getType(uri) ?: "application/octet-stream"
                var name = "file.unknown"

                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.let {
                    /*
                     * Get the column indexes of the data in the Cursor,
                     * move to the first row in the Cursor, get the data,
                     * and display it.
                     */
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    name = cursor.getString(nameIndex)
                }
                cursor?.close()

                val fileInputStream = requireContext().contentResolver.openInputStream(uri)
                val outFile =
                    java.io.File.createTempFile(name, ".unknown", requireContext().cacheDir)
                val outputStream = FileOutputStream(outFile)
                fileInputStream?.copyTo(outputStream)

                val fileUpload = FileUpload(outFile, name, mimeType)
                ChatBackend.uploadFilesToAPI(listOf(fileUpload), null, object : ChatBackend.APIFileUploadResponseListener {
                    override fun onFailure(exception: Exception) {}
                    override fun onResponse(files: List<File>) {
                        ChatBackend.sendFiles(files)
                    }
                })
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateStyling(config: ChatConfig?) {
        if (config == null) return

        @ColorInt val textColor = customConfig?.chatPanel?.styling?.buttons?.textColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.textColor
            ?: config.chatPanel?.styling?.buttons?.textColor
            ?: ContextCompat.getColor(requireContext(), R.color.buttonTextColor)
        @ColorInt val backgroundColor = customConfig?.chatPanel?.styling?.buttons?.backgroundColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.buttons?.backgroundColor
            ?: config.chatPanel?.styling?.buttons?.backgroundColor
            ?: ContextCompat.getColor(requireContext(), R.color.buttonBackgroundColor)

        val linkDrawable: Int

        if (link?.id == ACTION_LINK_UPLOAD) linkDrawable = R.drawable.ic_upload_files
        else if (link?.type == LinkType.EXTERNAL_LINK) {
            if (link?.isAttachment == true)
                linkDrawable = R.drawable.ic_file
            else
                linkDrawable = R.drawable.ic_external_link_icon
        }
        else linkDrawable = R.drawable.ic_arrow_right
        textView.setTextColor(textColor)
        imageView.setImageResource(linkDrawable)
        imageView.imageTintList = ColorStateList.valueOf(textColor)
        view?.background?.setTint(backgroundColor)

        @FontRes val bodyFont = customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont

        bodyFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                textView.typeface = typeface
            } catch (e: java.lang.Exception) {}
        }
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}

    override fun onAnimationStart(p0: Animation?) {}
    override fun onAnimationEnd(p0: Animation?) {
        view?.alpha = 1.0f
    }
    override fun onAnimationRepeat(p0: Animation?) {}

    override fun didTapActionButton(fragment: ChatMessageButtonFragment) {}

    override fun disableActionButtons() {
        view?.setOnClickListener(null)
        view?.background?.alpha = 127
    }

    override fun enableActionButtons(makeInteractable: Boolean) {
        view?.background?.alpha = 255

        if (makeInteractable) {
            view?.setOnClickListener(onClickListener)
        }
    }
}