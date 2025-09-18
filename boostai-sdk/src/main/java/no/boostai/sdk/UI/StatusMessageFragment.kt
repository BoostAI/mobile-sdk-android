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

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.R

open class StatusMessageFragment(var message: String? = null,
                                 var isError: Boolean = false,
                                 var retry: Boolean = false,
                                 var customConfig: ChatConfig? = null,
                                 var retryDelegate: StatusMessageRetryDelegate? = null)
    : Fragment(R.layout.status_message) {

    val messageKey = "message"
    val isErrorKey = "isError"
    val retryKey = "retry"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            message = it.getString(messageKey)
            isError = it.getBoolean(isErrorKey)
            retry = it.getBoolean(retryKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(messageKey, message)
        outState.putBoolean(isErrorKey, isError)
        outState.putBoolean(retryKey, retry)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textView = view.findViewById<TextView>(R.id.text_view)

        val textColor = if (isError) android.R.color.holo_red_dark else R.color.textColor
        textView.setTextColor(ContextCompat.getColor(requireContext(), textColor))
        textView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        textView.text = message

        val retryButton = view.findViewById<Button>(R.id.retry_button)
        val retryTitle = customConfig?.messages?.get(ChatBackend.languageCode)?.retry
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.retry
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.retry
            ?: getString(R.string.retry)

        @ColorInt val primaryColor = customConfig?.chatPanel?.styling?.primaryColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.primaryColor
            ?: ContextCompat.getColor(requireContext(), R.color.primaryColor)

        @ColorInt val contrastColor = customConfig?.chatPanel?.styling?.contrastColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.contrastColor
            ?: ContextCompat.getColor(requireContext(), R.color.contrastColor)

        retryButton.text = retryTitle
        retryButton.setBackgroundColor(primaryColor)
        retryButton.setTextColor(contrastColor)
        retryButton.visibility = if (retry) View.VISIBLE else View.GONE

        retryButton.setOnClickListener({
            retryDelegate?.didTapRetryButton()
        })

        @FontRes val bodyFont = customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont

        bodyFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                textView.typeface = typeface
                retryButton.typeface = typeface
            } catch (_: java.lang.Exception) {}
        }
    }
}