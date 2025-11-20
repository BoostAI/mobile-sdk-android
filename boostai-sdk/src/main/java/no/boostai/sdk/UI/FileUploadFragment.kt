package no.boostai.sdk.UI

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.File
import no.boostai.sdk.R

open class FileUploadFragment(var file: File) : Fragment(R.layout.file_upload) {
    private lateinit var fileImageView: ImageView
    private lateinit var textView: TextView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var errorImageView: ImageView
    private lateinit var checkmarkImageView: ImageView
    private lateinit var removeButton: ImageButton

    var delegate: FileUploadFragmentDelegate? = null

    interface FileUploadFragmentDelegate {
        fun removeFileUpload(fragment: FileUploadFragment,file: File)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileImageView = view.findViewById(R.id.file_upload_file_image_view)
        textView = view.findViewById(R.id.file_upload_text_view)
        progressIndicator = view.findViewById(R.id.file_upload_progress_indicator)
        errorImageView = view.findViewById(R.id.file_upload_error)
        checkmarkImageView = view.findViewById(R.id.file_upload_checkmark)
        removeButton = view.findViewById(R.id.file_upload_remove_button)

        val scale = resources.displayMetrics.density
        val verticalPadding = (10 * scale + 0.5f).toInt()
        val horizontalPadding = (15 * scale + 0.5f).toInt()

        if (file.isUploading) {
            checkmarkImageView.visibility = View.GONE
            errorImageView.visibility = View.GONE
            removeButton.visibility = View.GONE

            view.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        } else {
            progressIndicator.visibility = View.GONE

            if (file.hasUploadError) {
                checkmarkImageView.visibility = View.GONE
            } else {
                errorImageView.visibility = View.GONE
            }

            view.setPadding(horizontalPadding, 0, 0, 0)
        }

        textView.text = file.filename
        removeButton.setOnClickListener {
            delegate?.removeFileUpload(this, file)
        }

        @FontRes val bodyFont = ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont

        bodyFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                textView.typeface = typeface
            } catch (_: java.lang.Exception) {}
        }
    }
}