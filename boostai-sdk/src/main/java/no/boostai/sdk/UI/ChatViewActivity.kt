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

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Html
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.R

open class ChatViewActivity: AppCompatActivity(R.layout.chat_view_activity), ChatBackend.ConfigObserver {

    companion object {
        const val CUSTOM_CONFIG = "customConfig"
        const val IS_DIALOG = "isDialog"
    }

    lateinit var toolbar: Toolbar
    var customConfig: ChatConfig? = null
    var isDialog = false
    var chatViewFragmentDelegate: ChatViewFragmentDelegate? = null
    var chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        toolbar = findViewById(R.id.chat_view_toolbar)
        customConfig = intent.getParcelableExtra(CUSTOM_CONFIG)
        isDialog = intent.getBooleanExtra(IS_DIALOG, false)

        if (supportActionBar == null) {
            setSupportActionBar(toolbar)
        } else {
            toolbar.visibility = View.GONE
        }

        supportActionBar?.setDisplayShowTitleEnabled(false)
        savedInstanceState?.let {
            isDialog = it.getBoolean(IS_DIALOG)
            customConfig = it.getParcelable(CUSTOM_CONFIG)
        }
        supportFragmentManager
            .beginTransaction()
            .add(R.id.chat_view_activity_content, getChatViewFragment(), "chat_view")
            .commitAllowingStateLoss()
        ChatBackend.addConfigObserver(this)
        updateStyling(ChatBackend.config)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(CUSTOM_CONFIG, customConfig)
        outState.putBoolean(IS_DIALOG, isDialog)
    }

    override fun onDestroy() {
        super.onDestroy()

        ChatBackend.removeConfigObserver(this)
    }

    fun updateStyling(config: ChatConfig?) {
        if (config == null) return

        @ColorInt val primaryColor = customConfig?.chatPanel?.styling?.primaryColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.primaryColor
            ?: config.chatPanel?.styling?.primaryColor
            ?: ContextCompat.getColor(this, R.color.primaryColor)

        @ColorInt val contrastColor = customConfig?.chatPanel?.styling?.contrastColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.contrastColor
            ?: config.chatPanel?.styling?.contrastColor
            ?: ContextCompat.getColor(this, R.color.contrastColor)

        supportActionBar?.setBackgroundDrawable(ColorDrawable(primaryColor))

        val title = customConfig?.chatPanel?.header?.title
            ?: ChatBackend.customConfig?.chatPanel?.header?.title
            ?: customConfig?.messages?.get(ChatBackend.languageCode)?.headerText
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.headerText
            ?: config.messages?.get(ChatBackend.languageCode)?.headerText
        supportActionBar?.title =
            Html.fromHtml("<font color=\"#" + Integer.toHexString(contrastColor).substring(2) + "\">" + title + "</font>")
        supportActionBar?.setDisplayShowTitleEnabled(title != null)
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {
        TODO("Not yet implemented")
    }

    fun getChatViewFragment(): Fragment = ChatViewFragment(isDialog, customConfig, chatViewFragmentDelegate, chatResponseViewURLHandlingDelegate)

}