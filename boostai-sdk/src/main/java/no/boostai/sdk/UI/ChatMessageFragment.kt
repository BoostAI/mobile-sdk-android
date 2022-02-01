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
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.AvatarShape
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.Response.*
import no.boostai.sdk.R
import no.boostai.sdk.UI.Helpers.TimingHelper
import java.util.*
import kotlin.concurrent.schedule

open class ChatMessageFragment(
    var response: Response? = null,
    val animated: Boolean = false,
    var isBlocked: Boolean = false,
    var isClient: Boolean = false,
    var isWelcomeMessage: Boolean = false,
    var isWaitingForServerResponse: Boolean = false,
    var isAwaitingFiles: Boolean = false,
    var avatarUrl: String? = null,
    var customConfig: ChatConfig? = null,
    var delegate: ChatViewFragmentDelegate? = null
) : Fragment(R.layout.chat_message), ChatBackend.ConfigObserver {

    val responseKey = "response"
    val isBlockedKey = "isBlocked"
    val isClientKey = "isClient"
    val isWelcomeMessageKey = "isWelcomeMessage"
    val isWaitingForServerResponseKey = "isWaitingForServerResponse"
    val avatarUrlKey = "avatarUrl"
    val customConfigKey = "customConfig"
    val delegateKey = "delegate"

    lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            response = it.getParcelable(responseKey)
            isBlocked = it.getBoolean(isBlockedKey)
            isClient = it.getBoolean(isClientKey)
            isWelcomeMessage = it.getBoolean(isWelcomeMessageKey)
            isWaitingForServerResponse = it.getBoolean(isWaitingForServerResponseKey)
            avatarUrl = it.getString(avatarUrlKey)
            customConfig = it.getParcelable(customConfigKey)
            //delegate = it.getParcelable(delegateKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(responseKey, response)
        outState.putBoolean(isBlockedKey, isBlocked)
        outState.putBoolean(isClientKey, isClient)
        outState.putBoolean(isWelcomeMessageKey, isWelcomeMessage)
        outState.putBoolean(isWaitingForServerResponseKey, isWaitingForServerResponse)
        outState.putString(avatarUrlKey, avatarUrl)
        outState.putParcelable(customConfigKey, customConfig)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.avatar)

        if (isClient) {
            imageView.visibility = View.GONE
            val layoutParams = view.layoutParams as LinearLayout.LayoutParams
            layoutParams.width = resources.getDimensionPixelSize(R.dimen.chat_view_part_min_width)
            layoutParams.gravity = Gravity.RIGHT
            view.layoutParams = layoutParams
        } else {
            imageView.clipToOutline = true

            response?.avatarUrl?.let { avatarUrl ->
                Glide.with(this).load(avatarUrl).into(imageView)
            } ?: avatarUrl?.let { avatarUrl ->
                Glide.with(this).load(avatarUrl).into(imageView)
            }
        }

        if (savedInstanceState == null) {
            val fragments = response?.elements?.map { element ->
                var messagePartFragment = delegate?.getChatMessagePartFragment(
                    element,
                    response?.id,
                    animated
                )

                if (messagePartFragment == null || !messagePartFragment.canRender(element)) {
                    messagePartFragment = getMessagePartFragment(element)
                }

                return@map if (messagePartFragment.canRender(element)) messagePartFragment else null
            }?.filterNotNull()

            fragments?.forEachIndexed { index, fragment ->
                val pace = ChatBackend.config?.chatPanel?.styling?.pace
                    ?: ChatPanelDefaults.Styling.pace
                val paceFactor = TimingHelper.calculatePace(pace)
                val staggerDelay = TimingHelper.calculateStaggerDelay(pace, 1)
                val timeUntilReveal = if (isClient) 0 else TimingHelper.calcTimeToRead(paceFactor)
                val hideMessageFeedback = customConfig?.chatPanel?.styling?.messageFeedback?.hide
                    ?: ChatBackend.config?.chatPanel?.styling?.messageFeedback?.hide
                    ?: ChatPanelDefaults.Styling.MessageFeedback.hide

                if (animated)
                    Timer().schedule(timeUntilReveal * index) {
                        Handler(Looper.getMainLooper()).post {
                            addMessagePart(fragment, index)

                            Timer().schedule(staggerDelay) {
                                Handler(Looper.getMainLooper()).post {
                                    // If we have more elements to show, display a waiting indicator before showing it
                                    if (!isClient && index < fragments.size - 1)
                                        addWaitingIndicator()
                                    else if (!isClient && !isBlocked && !isWelcomeMessage && !hideMessageFeedback)
                                        fragment.showFeedbackButtons()
                                }
                            }
                        }
                    }
                else {
                    addMessagePart(fragment, index)

                    if (!isClient && !isBlocked && !isWelcomeMessage && !hideMessageFeedback && fragment is ChatMessagePartFragment)
                        fragment.showFeedbackButtons()
                }
            }
            if (isAwaitingFiles) {
                val element = Element(
                    Payload(
                        links = arrayListOf(
                            Link(
                                "upload",
                                getString(R.string.upload_file),
                                LinkType.ACTION_LINK
                            )
                        )
                    ),
                    ElementType.LINKS
                )

                val index = fragments?.size ?: 0
                val messagePartFragment = delegate?.getChatMessagePartFragment(
                    element,
                    response?.id,
                    animated
                ) ?: getMessagePartFragment(element)

                messagePartFragment.let { fragment ->
                    if (animated)
                        Timer().schedule(
                            TimingHelper.timeUntilReveal() * (fragments?.size ?: 0)
                        ) {
                            Handler(Looper.getMainLooper()).post {
                                addMessagePart(fragment, index)
                            }
                        }
                    else addMessagePart(fragment, index)
                }
            }
            // If set, render a waiting indicator while waiting for server response
            if (isWaitingForServerResponse)
                childFragmentManager
                    .beginTransaction()
                    .add(R.id.chat_message_parts, getWaitingFragment(), "waitingIndicator")
                    .commitAllowingStateLoss()
        }

        updateStyling(ChatBackend.config)
        ChatBackend.addConfigObserver(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        ChatBackend.removeConfigObserver(this)
    }

    fun addMessagePart(fragment: Fragment, index: Int) {
        if (host == null) {
            return
        }

        val fragmentTransaction = childFragmentManager.beginTransaction();

        // Remove possible visible waiting indicator
        childFragmentManager.findFragmentByTag("waitingIndicator")?.let {
            fragmentTransaction.remove(it)
        }

        // Show the message
        fragmentTransaction.add(
            R.id.chat_message_parts,
            fragment
        )

        fragmentTransaction.commitAllowingStateLoss()
    }

    fun addWaitingIndicator() {
        if (host == null) {
            return
        }

        childFragmentManager
            .beginTransaction()
            .add(R.id.chat_message_parts, getWaitingFragment(), "waitingIndicator")
            .commitAllowingStateLoss()
    }

    fun getMessagePartFragment(element: Element): IChatMessagePartFragment {
        return ChatMessagePartFragment(
            element,
            responseId = response?.id,
            isClient,
            isBlocked,
            isWelcomeMessage,
            animated,
            customConfig
        )
    }

    fun getWaitingFragment(): Fragment = ChatServerWaitingFragment(customConfig)

    fun updateStyling(config: ChatConfig?) {
        if (config == null) return

        if (!isClient) {
            val avatarShape = customConfig?.chatPanel?.styling?.avatarShape
                ?: config.chatPanel?.styling?.avatarShape
                ?: ChatPanelDefaults.Styling.avatarShape
            imageView.background = if (avatarShape == AvatarShape.SQUARED) null else ContextCompat.getDrawable(requireContext(), R.drawable.rounded)
        }
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) {
        updateStyling(config)
    }

    override fun onFailure(backend: ChatBackend, error: Exception) {
        TODO("Not yet implemented")
    }
}