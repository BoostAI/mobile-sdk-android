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
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
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
        // AppCompatActivity crashes under a theme without the AppCompat attributes. Fall back to
        // a bundled theme only in that case — done in code rather than the manifest, where a
        // theme attribute would be a hard merge conflict for hosts declaring this activity
        // themselves, and would silently replace the application theme for everyone else.
        val attrs = obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.windowActionBar))
        val hasAppCompatTheme = attrs.hasValue(0)
        attrs.recycle()
        if (!hasAppCompatTheme) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
        }

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        toolbar = findViewById(R.id.chat_view_toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            windowInsets
        }
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
        // Only on first creation: on a configuration change the FragmentManager restores the
        // previous instance, and adding another on top would run two conversations at once —
        // one more per rotation.
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.chat_view_activity_content, getChatViewFragment(), "chat_view")
                .commitAllowingStateLoss()
        } else {
            // The restored instance is built through its no-arg constructor: it recovers
            // isDialog/customConfig from its own saved state, but delegates are not parcelable —
            // without this rewiring, a subclass' custom rendering and URL handling silently fell
            // back to the defaults after the first rotation.
            (supportFragmentManager.findFragmentByTag("chat_view") as? ChatViewFragment)?.let {
                it.delegate = it.delegate ?: chatViewFragmentDelegate
                it.chatResponseViewURLHandlingDelegate =
                    it.chatResponseViewURLHandlingDelegate ?: chatResponseViewURLHandlingDelegate
            }
        }
        ChatBackend.addConfigObserver(this)
        updateStyling(ChatBackend.config)

        // Apply initial status bar styling based on toolbar color
        val initialPrimaryColor = customConfig?.chatPanel?.styling?.primaryColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.primaryColor
            ?: ContextCompat.getColor(this, R.color.primaryColor)
        val initialContrastColor = customConfig?.chatPanel?.styling?.contrastColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.contrastColor
            ?: ContextCompat.getColor(this, R.color.contrastColor)
        applyStatusBarStyling(initialPrimaryColor, initialContrastColor)
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

        applyStatusBarStyling(primaryColor, contrastColor)

        val title = customConfig?.chatPanel?.header?.title
            ?: ChatBackend.customConfig?.chatPanel?.header?.title
            ?: customConfig?.messages?.get(ChatBackend.languageCode)?.headerText
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.headerText
            ?: config.messages?.get(ChatBackend.languageCode)?.headerText
        // %06X instead of toHexString().substring(2): toHexString drops leading zeros, so a color
        // int below 0x10000000 produced a short string and the substring threw — reachable
        // whenever a config served a low-alpha color
        supportActionBar?.title =
            Html.fromHtml("<font color=\"#%06X\">".format(0xFFFFFF and contrastColor) + title + "</font>")
        supportActionBar?.setDisplayShowTitleEnabled(title != null)
    }

    private fun applyStatusBarStyling(@ColorInt backgroundColor: Int, @ColorInt contrastColor: Int) {
        @Suppress("DEPRECATION")
        window.statusBarColor = backgroundColor
        // Use contrastColor to determine icon appearance: light contrastColor (e.g. white text)
        // means dark background, so use light (white) status bar icons
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            ColorUtils.calculateLuminance(contrastColor) < 0.5
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) = updateStyling(config)

    override fun onFailure(backend: ChatBackend, error: Exception) {}

    fun getChatViewFragment(): Fragment = ChatViewFragment(isDialog, customConfig, chatViewFragmentDelegate, chatResponseViewURLHandlingDelegate)

}