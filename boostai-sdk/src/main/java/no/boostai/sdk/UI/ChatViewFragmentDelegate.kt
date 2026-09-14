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

import android.view.View
import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.Response

interface ChatViewFragmentDelegate {

    /**
     * Supply a custom view for a message, or `null` to use the SDK's own.
     *
     * This replaces [getChatMessageFragment]. Messages are drawn as views rather than fragments
     * because the FragmentManager persists a record for every fragment — a few kilobytes per
     * message — which pushed long conversations past the Binder transaction limit and crashed the
     * host app with `TransactionTooLargeException`. Views cost nothing to save.
     */
    fun getChatMessageView(response: Response, animated: Boolean): View? = null

    /**
     * Supply a custom view for a single message element, or `null` to use the SDK's own.
     *
     * Replaces [getChatMessagePartFragment].
     */
    fun getChatMessagePartView(
        element: Element,
        responseId: String?,
        isClient: Boolean = false,
        animated: Boolean = true
    ): View? = null

    @Deprecated(
        "Messages are rendered as views. Implement getChatMessageView instead — returning a " +
            "fragment here still works, but keeps this conversation on the fragment rendering " +
            "path, whose saved state grows with the number of messages.",
        ReplaceWith("getChatMessageView(response, animated)")
    )
    fun getChatMessageFragment(response: Response, animated: Boolean): Fragment? = null

    @Deprecated(
        "Messages are rendered as views. Implement getChatMessagePartView instead — returning a " +
            "fragment here still works, but keeps this conversation on the fragment rendering " +
            "path, whose saved state grows with the number of messages.",
        ReplaceWith("getChatMessagePartView(element, responseId, isClient, animated)")
    )
    fun getChatMessagePartFragment(
        element: Element,
        responseId: String?,
        isClient: Boolean = false,
        animated: Boolean = true
    ): IChatMessagePartFragment? = null

    fun getSettingsFragment(): Fragment?
    fun getFeedbackFragment(): Fragment?
}

abstract class IChatMessagePartFragment(contentLayoutId: Int) : Fragment(contentLayoutId) {
    abstract fun canRender(element: Element): Boolean
    open fun showFeedbackButtons() {}
}
