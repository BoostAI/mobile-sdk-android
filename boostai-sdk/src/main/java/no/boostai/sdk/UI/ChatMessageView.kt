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
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.AvatarShape
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.Response.Element
import no.boostai.sdk.ChatBackend.Objects.Response.ElementType
import no.boostai.sdk.ChatBackend.Objects.Response.Link
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.ChatBackend.Objects.Response.Payload
import no.boostai.sdk.ChatBackend.Objects.Response.Response
import no.boostai.sdk.ChatBackend.Objects.Response.SourceType
import no.boostai.sdk.R
import no.boostai.sdk.UI.Helpers.TimingHelper

/**
 * One chat message — avatar plus one [ChatMessagePartView] per element.
 *
 * The view counterpart of [ChatMessageFragment], and the default rendering path. A message drawn
 * this way adds nothing to the activity's saved state, whereas the fragment tree it replaces cost
 * roughly 1.3 KB per fragment and three to thirteen fragments per message — enough to break the
 * Binder transaction limit on a long conversation.
 */
open class ChatMessageView(
    context: Context,
    val response: Response,
    val isBlocked: Boolean = false,
    val isWelcomeMessage: Boolean = false,
    val isAwaitingFiles: Boolean = false,
    val animated: Boolean = false,
    val avatarUrl: String? = null,
    val customConfig: ChatConfig? = null,
    val chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null,
    val delegate: ChatViewFragmentDelegate? = null,
    val onUploadRequested: (() -> Unit)? = null,
    /**
     * Called when an action button in this message is tapped. The conversation dims every button
     * in response, so a menu cannot be answered twice.
     */
    val onActionTapped: ((Link) -> Unit)? = null,
    /**
     * Called whenever content is added to this message. Elements of an animated message are
     * revealed one at a time, so the message keeps growing after it is first added to the
     * conversation — this is what lets the chat view keep following the bottom.
     */
    val onContentChanged: (() -> Unit)? = null
) : LinearLayout(context), ChatBackend.ConfigObserver {

    val isClient = response.source == SourceType.CLIENT

    private val partsContainer: LinearLayout
    private val avatarImageView: ImageView
    private val parts = ArrayList<ChatMessagePartView>()

    /**
     * All reveal scheduling goes through one handler so it can be cancelled when the message
     * leaves the screen. A long animated reveal otherwise kept the main-looper queue holding this
     * view — and through [onContentChanged], the whole chat UI — alive for many seconds after the
     * chat was closed mid-reveal, appending parts to a dead container all the while.
     */
    private val revealHandler = Handler(Looper.getMainLooper())

    init {
        orientation = VERTICAL
        // Client messages hug the right edge, as ChatMessageFragment does in onViewCreated
        layoutParams = LayoutParams(
            if (isClient) resources.getDimensionPixelSize(R.dimen.chat_view_part_min_width)
            else LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { if (isClient) gravity = Gravity.END }

        LayoutInflater.from(context).inflate(R.layout.chat_message, this, true)
        avatarImageView = findViewById(R.id.avatar)
        partsContainer = findViewById(R.id.chat_message_parts)

        bindAvatar()
        renderParts()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ChatBackend.addConfigObserver(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ChatBackend.removeConfigObserver(this)
        revealHandler.removeCallbacksAndMessages(null)
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) {
        bindAvatar()
        parts.forEach { it.updateStyling() }
    }

    override fun onFailure(backend: ChatBackend, error: Exception) {}

    private fun bindAvatar() {
        val hideAvatar = ChatMessageRenderers.styling(customConfig) { it?.hideAvatar } ?: false

        avatarImageView.visibility = if (isClient || hideAvatar) View.GONE else View.VISIBLE
        if (isClient || hideAvatar) return

        val avatarShape = ChatMessageRenderers.styling(customConfig) { it?.avatarShape }
            ?: ChatPanelDefaults.Styling.avatarShape
        avatarImageView.background =
            if (avatarShape == AvatarShape.SQUARED) null
            else ContextCompat.getDrawable(context, R.drawable.rounded)

        avatarImageView.clipToOutline = true
        (response.avatarUrl ?: avatarUrl)?.let { Glide.with(this).load(it).into(avatarImageView) }
    }

    private fun renderParts() {
        // A host app may supply its own view for an element. The delegate is consulted before the
        // SDK's own canRender filter — as the fragment path does — so a host can render element
        // types the SDK cannot. The hook is called exactly once per element.
        val renderable = response.elements.mapNotNull { element ->
            val hostView =
                delegate?.getChatMessagePartView(element, response.id, isClient, animated)
            when {
                hostView != null -> element to hostView
                ChatMessagePartView.canRender(element) -> element to null
                else -> null
            }
        }
        if (renderable.isEmpty() && !isAwaitingFiles) return

        val pace = ChatMessageRenderers.styling(customConfig) { it?.pace }
            ?: ChatPanelDefaults.Styling.pace
        val paceFactor = TimingHelper.calculatePace(pace)
        val staggerDelay = TimingHelper.calculateStaggerDelay(pace, 1)
        val timeUntilReveal = if (isClient) 0 else TimingHelper.calcTimeToRead(paceFactor)
        val hideMessageFeedback =
            ChatMessageRenderers.styling(customConfig) { it?.messageFeedback?.hide }
                ?: ChatPanelDefaults.Styling.MessageFeedback.hide
        val showFeedback = !isClient && !isBlocked && !isWelcomeMessage && !hideMessageFeedback

        renderable.forEachIndexed { index, (element, hostView) ->
            // A host-supplied view goes through the same reveal pipeline as the SDK's own parts,
            // so a message mixing the two keeps its elements in order and in pace.
            val part = hostView ?: makePart(element).also { parts.add(it) }

            if (!animated) {
                partsContainer.addView(part)
                // Last part only, matching the animated branch — a replayed message must not
                // grow a thumbs row (with its own independent feedback state) under every element.
                // Host views take part by implementing ChatMessageFeedbackDelegate.
                if (showFeedback && index == renderable.size - 1 && part is ChatMessageFeedbackDelegate) {
                    part.showFeedbackButtons()
                }
                onContentChanged?.invoke()
                return@forEachIndexed
            }

            // Reveal each element in turn, with a typing indicator between them
            revealHandler.postDelayed({
                removeWaitingIndicator()
                partsContainer.addView(part)
                onContentChanged?.invoke()

                revealHandler.postDelayed({
                    if (!isClient && index < renderable.size - 1) addWaitingIndicator()
                    else if (showFeedback && part is ChatMessageFeedbackDelegate) part.showFeedbackButtons()
                    onContentChanged?.invoke()
                }, staggerDelay)
            }, timeUntilReveal * index)
        }

        // The synthesized "upload a file" button stays outside the reveal loop, as it did in
        // ChatMessageFragment: it appears after the last element with no typing indicator ahead of
        // it, and it never gets feedback thumbs of its own.
        if (isAwaitingFiles) addUploadPart(renderable.size, timeUntilReveal)
    }

    private fun addUploadPart(index: Int, timeUntilReveal: Long) {
        val element = Element(
            Payload(
                links = arrayListOf(
                    Link(
                        "upload",
                        context.getString(R.string.upload_file),
                        LinkType.ACTION_LINK
                    )
                )
            ),
            ElementType.LINKS
        )
        val part = delegate?.getChatMessagePartView(element, response.id, isClient, animated)
            ?: makePart(element).also { parts.add(it) }

        if (!animated) {
            partsContainer.addView(part)
            onContentChanged?.invoke()
            return
        }

        revealHandler.postDelayed({
            removeWaitingIndicator()
            partsContainer.addView(part)
            onContentChanged?.invoke()
        }, timeUntilReveal * index)
    }

    private fun makePart(element: Element) = ChatMessagePartView(
        context,
        element,
        response.id,
        isClient,
        isBlocked,
        isWelcomeMessage,
        animated,
        customConfig,
        chatResponseViewURLHandlingDelegate,
        onUploadRequested,
        onActionTapped
    )

    private fun addWaitingIndicator() {
        if (partsContainer.findViewById<View>(R.id.dots) != null) return

        partsContainer.addView(ChatServerWaitingView(context, customConfig))
    }

    private fun removeWaitingIndicator() {
        partsContainer.findViewById<View>(R.id.dots)?.let { dots ->
            var child: View = dots
            while (child.parent != null && child.parent !== partsContainer) {
                child = child.parent as View
            }
            partsContainer.removeView(child)
        }
    }

    /**
     * Host-supplied part views that opted into button dimming by implementing
     * [ChatMessageButtonDelegate] — the same contract host part fragments had.
     */
    private val hostButtonDelegates: List<ChatMessageButtonDelegate>
        get() = (0 until partsContainer.childCount)
            .map { partsContainer.getChildAt(it) }
            .filterIsInstance<ChatMessageButtonDelegate>()

    /** Dim every action button in this message and stop them responding to taps. */
    fun disableActionButtons() {
        parts.forEach { it.disableActionButtons() }
        hostButtonDelegates.forEach { it.disableActionButtons() }
    }

    /** Restore every action button in this message. */
    fun enableActionButtons(makeInteractable: Boolean) {
        parts.forEach { it.enableActionButtons(makeInteractable) }
        hostButtonDelegates.forEach { it.enableActionButtons(makeInteractable) }
    }
}
