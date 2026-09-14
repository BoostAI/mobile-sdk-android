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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.File
import no.boostai.sdk.ChatBackend.Objects.FileUpload
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import java.io.FileOutputStream

/**
 * Deprecated. Hosts a [ChatMessageButtonView], which does the work.
 *
 * Messages are rendered as views; see [ChatViewFragmentDelegate.getChatMessagePartView].
 */
open class ChatMessageButtonFragment(
    var link: Link? = null,
    var idx: Int = 0,
    val animated: Boolean = true,
    var customConfig: ChatConfig? = null,
    var buttonDelegate: ChatMessageButtonDelegate? = null,
    var chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null
) : Fragment(),
    ChatBackend.ConfigObserver,
    Animation.AnimationListener,
    ChatMessageButtonDelegate {

    companion object {
        private const val FILE_PICKER_REQUEST = 847321
    }

    val ACTION_LINK_UPLOAD = ChatMessageRenderers.ACTION_LINK_UPLOAD

    val linkKey = "link"
    val idxKey = "idx"
    val customConfigKey = "customConfig"

    lateinit var buttonView: ChatMessageButtonView
    val textView: TextView get() = buttonView.textView
    val imageView: ImageView get() = buttonView.imageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            link = it.getParcelable(linkKey) ?: link
            idx = it.getInt(idxKey, idx)
            customConfig = it.getParcelable(customConfigKey) ?: customConfig
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        buttonView = ChatMessageButtonView(
            requireContext(),
            link ?: Link("", "", no.boostai.sdk.ChatBackend.Objects.Response.LinkType.ACTION_LINK),
            idx,
            animated,
            customConfig,
            chatResponseViewURLHandlingDelegate,
            onUploadRequested = {
                Intent(Intent.ACTION_GET_CONTENT).let { intent ->
                    intent.setType("*/*")
                    startActivityForResult(intent, FILE_PICKER_REQUEST)
                }
            },
            onActionTapped = { buttonDelegate?.didTapActionButton(this) }
        )
        return buttonView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ChatBackend.addConfigObserver(this)
        updateStyling(ChatBackend.config)
    }

    override fun onDestroy() {
        super.onDestroy()

        ChatBackend.removeConfigObserver(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // Resolved here: the fragment may be detached by the time the worker runs
                val contentResolver = requireContext().contentResolver
                val cacheDir = requireContext().cacheDir
                val appContext = requireContext().applicationContext

                // Copied off the main thread — blocking I/O against a third-party content
                // provider, and a large file used to freeze the UI for its duration
                Thread {
                    val fileUpload = try {
                        val mimeType: String =
                            contentResolver.getType(uri) ?: "application/octet-stream"
                        var name = "file.unknown"

                        contentResolver.query(uri, null, null, null, null)?.use {
                            // Content providers are third-party code: the display-name column,
                            // or a first row, may simply not be there
                            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0 && it.moveToFirst()) {
                                name = it.getString(nameIndex) ?: name
                            }
                        }

                        // createTempFile rejects prefixes shorter than three characters, and a
                        // display name is no place for path separators
                        val prefix = name.replace('/', '_').padEnd(3, '_')
                        val outFile = java.io.File.createTempFile(prefix, ".unknown", cacheDir)
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(outFile).use { output -> input.copyTo(output) }
                        }

                        FileUpload(outFile, name, mimeType)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // The bot showed the upload button and is waiting for a file — failing
                        // with no trace at all left the conversation hanging
                        Handler(Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(
                                appContext, no.boostai.sdk.R.string.unknown_error, android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@Thread
                    }

                    Handler(Looper.getMainLooper()).post {
                        ChatBackend.uploadFilesToAPI(
                            listOf(fileUpload),
                            null,
                            object : ChatBackend.APIFileUploadResponseListener {
                                override fun onFailure(exception: Exception) {}
                                override fun onResponse(files: List<File>) {
                                    ChatBackend.sendFiles(files)
                                }
                            }
                        )
                    }
                }.start()
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun updateStyling(config: ChatConfig?) {
        if (::buttonView.isInitialized) buttonView.applyStyling()
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}

    override fun onAnimationStart(p0: Animation?) {}
    override fun onAnimationEnd(p0: Animation?) {
        view?.alpha = 1.0f
    }
    override fun onAnimationRepeat(p0: Animation?) {}

    override fun didTapActionButton(fragment: ChatMessageButtonFragment) {
        buttonDelegate?.didTapActionButton(fragment)
    }

    override fun disableActionButtons() {
        if (::buttonView.isInitialized) buttonView.disable()
    }

    override fun enableActionButtons(makeInteractable: Boolean) {
        if (::buttonView.isInitialized) buttonView.enable(makeInteractable)
    }
}
