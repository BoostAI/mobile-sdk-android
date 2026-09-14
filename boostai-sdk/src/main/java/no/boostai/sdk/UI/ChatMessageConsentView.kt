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
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.Response.FunctionType
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents
import no.boostai.sdk.UI.Helpers.TimingHelper

/**
 * The approve/deny pair shown for a consent request.
 *
 * The view counterpart of [ChatMessageConsentFragment].
 */
open class ChatMessageConsentView(
    context: Context,
    val links: List<Link>,
    val animated: Boolean = true,
    val customConfig: ChatConfig? = null
) : FrameLayout(context) {

    val approveButton: Button
    val denyButton: Button

    init {
        View.inflate(context, R.layout.chat_server_message_consent, this)
        approveButton = findViewById(R.id.approve_button)
        denyButton = findViewById(R.id.deny_button)

        val content = getChildAt(0)
        if (animated) {
            val pace = ChatMessageRenderers.styling(customConfig) { it?.pace }
                ?: ChatPanelDefaults.Styling.pace
            val staggerDelay = TimingHelper.calculateStaggerDelay(pace = pace, idx = 0)
            Handler(Looper.getMainLooper()).postDelayed({
                content.alpha = 1.0f
                content.animation =
                    AnimationUtils.loadAnimation(context, R.anim.chat_message_animate_in)
            }, staggerDelay)
        } else {
            content.alpha = 1.0f
        }

        links.find { it.function == FunctionType.APPROVE }?.let { link ->
            approveButton.text = link.text
            approveButton.setOnClickListener { onChoice(link, approved = true) }
        }
        links.find { it.function == FunctionType.DENY }?.let { link ->
            denyButton.text = link.text
            denyButton.setOnClickListener { onChoice(link, approved = false) }
        }

        applyStyling()
    }

    /** The button the user answered with, if they have. A given consent stays locked in. */
    private var chosenButton: Button? = null

    protected open fun onChoice(link: Link, approved: Boolean) {
        ChatBackend.actionButton(link.id)
        BoostUIEvents.notifyObservers(BoostUIEvents.Event.actionLinkClicked, link.id)

        chosenButton = if (approved) approveButton else denyButton
        // A given consent is locked in — with both listeners left live, a quick second tap fired
        // a contradictory action at the backend and repainted the "locked" colours the other way
        approveButton.isClickable = false
        denyButton.isClickable = false
        applyChoiceColors()
    }

    private fun applyChoiceColors() {
        val chosen = chosenButton ?: return
        val approved = chosen === approveButton
        val other = if (approved) denyButton else approveButton
        val chosenColor =
            if (approved) R.color.consentApproveButtonTextColor else R.color.consentDenyButtonTextColor
        val otherColor = if (approved) R.color.consentDenyButtonDisabledTextColor
        else R.color.consentApproveButtonDisabledTextColor

        chosen.setTextColor(ContextCompat.getColor(context, chosenColor))
        (chosen.background as? GradientDrawable)?.alpha = 255
        other.setTextColor(ContextCompat.getColor(context, otherColor))
        (other.background as? GradientDrawable)?.alpha = 64
    }

    /** Re-apply colours. Called by the parent when the configuration changes. */
    open fun applyStyling() {
        // mutate() before painting: the pill drawable's constant state is shared with every other
        // button inflated from the same resource, and the action buttons tint theirs — without the
        // mutate, whichever button styles itself last wins for all of them, which is how consent
        // buttons could end up on an action button's background colour.
        (approveButton.background?.mutate() as? GradientDrawable)?.setColor(
            ContextCompat.getColor(context, R.color.consentApproveButtonBackgroundColor)
        )
        (denyButton.background?.mutate() as? GradientDrawable)?.setColor(
            ContextCompat.getColor(context, R.color.consentDenyButtonBackgroundColor)
        )

        // A made choice survives restyling — a config push must not light the rejected button
        // back up.
        if (chosenButton != null) {
            applyChoiceColors()
            return
        }

        approveButton.setTextColor(
            ContextCompat.getColor(context, R.color.consentApproveButtonTextColor)
        )
        denyButton.setTextColor(ContextCompat.getColor(context, R.color.consentDenyButtonTextColor))
    }
}
