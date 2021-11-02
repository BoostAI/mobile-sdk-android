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

import androidx.fragment.app.Fragment
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.Response

interface ChatViewFragmentDelegate {
    fun getChatMessageFragment(response: Response,
                               animated: Boolean): Fragment?

    fun getChatMessagePartFragment(element: Element,
                                   responseId: String?,
                                   animated: Boolean = true): Fragment?

    fun getSettingsFragment(): Fragment?
    fun getFeedbackFragment(): Fragment?
}