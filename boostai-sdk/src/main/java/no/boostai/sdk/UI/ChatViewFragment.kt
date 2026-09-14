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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import kotlinx.serialization.json.JsonElement
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.ChatConfig
import no.boostai.sdk.ChatBackend.Objects.ChatPanelDefaults
import no.boostai.sdk.ChatBackend.Objects.CommandResume
import no.boostai.sdk.ChatBackend.Objects.CommandStart
import no.boostai.sdk.ChatBackend.Objects.File
import no.boostai.sdk.ChatBackend.Objects.FileUpload
import no.boostai.sdk.ChatBackend.Objects.Response.APIMessage
import no.boostai.sdk.ChatBackend.Objects.Response.ChatStatus
import no.boostai.sdk.ChatBackend.Objects.Response.Response
import no.boostai.sdk.ChatBackend.Objects.Response.SDKSerializationException
import no.boostai.sdk.ChatBackend.Objects.Response.SourceType
import no.boostai.sdk.R
import no.boostai.sdk.UI.Events.BoostUIEvents
import no.boostai.sdk.UI.Helpers.TimingHelper
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.UUID
import kotlin.time.Duration

open class ChatViewFragment(
    var isDialog: Boolean = false,
    var customConfig: ChatConfig? = null,
    var delegate: ChatViewFragmentDelegate? = null,
    var chatResponseViewURLHandlingDelegate: ChatResponseViewURLHandlingDelegate? = null
) :
    Fragment(R.layout.chat_view),
    ChatBackend.MessageObserver,
    ChatBackend.ConfigObserver,
    ChatViewSettingsDelegate,
    FileUploadFragment.FileUploadFragmentDelegate,
    ChatMessageButtonDelegate,
    BoostUIEvents.Observer,
    StatusMessageRetryDelegate {

    private val FILE_PICKER_REQUEST = 847321

    /**
     * File picks originating from an "upload a file" action button. Kept apart from
     * [FILE_PICKER_REQUEST]: a file picked through the composer becomes a pending attachment the
     * user still has to send, while one picked through an action button is uploaded and sent
     * immediately, with no further step.
     */
    private val ACTION_UPLOAD_REQUEST = 847322

    val conversationIdKey = "conversationId"
    val isDialogKey = "isDialog"
    val customConfigKey = "customConfig"
    val delegateKey = "delegate"
    val lastAvatarUrlKey = "lastAvatarURL"
    val maxCharacterCountKey = "maxCharacterCount"
    val messagesKey = "messages"
    val responsesKey = "responses"
    val isBlockedKey = "isBlocked"
    val isSecureChatKey = "isSecureChat"
    val storedConversationIdKey = "conversationId"
    val storedRememberConversationExpiryKey = "rememberConversationExpiryKey"
    
    val errorId = "error"
    val statusMessageKey = "statusMessage"
    val statusMessageIsErrorKey = "statusMessageIsError"
    val statusMessageRetryKey = "statusMessageRetry"
    val hasResumedWithUserTokenKey = "hasResumedWithUserToken"

    /** The status message currently on screen, if any, so it can survive a configuration change. */
    private var statusMessage: String? = null
    private var statusMessageIsError = false
    private var statusMessageRetry = false
    val settingsFragmentId = "settings"
    val feedbackFragmentId = "feedback"
    lateinit var chatContent: FrameLayout
    lateinit var secureChatWrapper: LinearLayout
    lateinit var secureChatTextView: TextView
    lateinit var secureChatImageView: ImageView
    lateinit var editText: EditText
    lateinit var submitButton: ImageButton
    lateinit var characterCountTextView: TextView
    lateinit var characterCountWrapper: FrameLayout
    lateinit var scrollView: ScrollView
    lateinit var chatInputWrapper: FrameLayout
    lateinit var chatInputWrapperTopBorder: FrameLayout
    lateinit var chatInputInner: LinearLayout
    lateinit var chatMessagesLayout: LinearLayout
    lateinit var chatInputOutline: FrameLayout
    lateinit var chatInputBorder: FrameLayout
    lateinit var fileUploadButton: ImageButton
    lateinit var fileUploadsWrapper: LinearLayout

    var lastAvatarURL: String? = null
    var maxCharacterCount = 110
    var messages: ArrayList<APIMessage> = ArrayList()
    var responses: ArrayList<Response> = ArrayList()
    var waitingForAgentResponseFragmentTags: ArrayList<String> = ArrayList()
    var animateMessages = true
    var isBlocked = false
    var isSecureChat = false
    var conversationReference: String? = null
    var conversationId: String? = null

    /**
     * Whether the conversation should be started automatically when the view is created.
     *
     * Defaults to `true`, which preserves the historical behaviour of starting (or resuming) the
     * conversation from [onViewCreated].
     *
     * Set this to `false` when the host app needs to assign [ChatBackend.userToken] and/or
     * [ChatBackend.customPayload] *before* the first START/RESUME is sent — for example when the
     * token/payload is fetched asynchronously (SSO). These values are snapshotted at the moment the
     * START/RESUME command is built, so if the auto-start chain runs before they are set the
     * conversation is created with an empty `custom_payload`. With this flag set to `false` the host
     * is responsible for calling [start] (or `start(userToken, customPayload)`) once the values are
     * in place.
     */
    var startConversationOnLoad: Boolean = true

    var pendingFileUploads: ArrayList<File> = ArrayList()

    /**
     * The composer upload failure most recently shown as a red chip, so the observer
     * [onFailure] that hears about the same exception next can skip the banner. Compared by
     * identity; not saved, as both deliveries happen back to back on the main thread.
     */
    private var uploadFailureShownAsChip: Exception? = null
    var pendingFileUploadFragments: ArrayList<FileUploadFragment> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        conversationId = customConfig?.chatPanel?.settings?.conversationId
            ?: ChatBackend.customConfig?.chatPanel?.settings?.conversationId

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            conversationId = conversationId ?: it.getString(conversationIdKey)
            isDialog = it.getBoolean(isDialogKey)
            customConfig = it.getParcelable(customConfigKey)
            // TODO: Parcelable delegate?
            // delegate =

            lastAvatarURL = it.getString(lastAvatarUrlKey)
            maxCharacterCount = it.getInt(maxCharacterCountKey)
            // `messages` and `responses` are deliberately *not* restored from the bundle. They are
            // rebuilt from `ChatBackend.messages` in `startOrResumeConversation`, which already
            // replays the whole conversation on every launch. Parcelling them here duplicated the
            // entire conversation into the saved state and was the main contributor to
            // TransactionTooLargeException on long conversations.
            isBlocked = it.getBoolean(isBlockedKey)
            isSecureChat = it.getBoolean(isSecureChatKey)
            statusMessage = it.getString(statusMessageKey)
            statusMessageIsError = it.getBoolean(statusMessageIsErrorKey)
            statusMessageRetry = it.getBoolean(statusMessageRetryKey)
        }
        // Saved state only, never arguments: a fresh open must resume, a rebuilt one must not
        hasResumedWithUserToken = savedInstanceState?.getBoolean(hasResumedWithUserTokenKey) ?: false

        setBackendProperties()

        ChatBackend.addConfigObserver(this)
        ChatBackend.addMessageObserver(this)
    }

    override fun onPause() {
        super.onPause()

        // Suspended, not merely stopped: a poll response landing after this must not restart
        // the timer while the chat is out of sight. onResume lifts the suspension.
        ChatBackend.suspendPolling()
    }

    override fun onResume() {
        super.onResume()

        // The chat is visible again, so the pause-time suspension no longer applies — cleared
        // unconditionally, or a suspension picked up in the virtual-agent phase would block
        // polling when the user is later transferred to a human agent
        ChatBackend.allowPolling()

        if (ChatBackend.isInHumanChat) ChatBackend.startPolling()
    }

    override fun onDestroy() {
        super.onDestroy()

        ChatBackend.removeConfigObserver(this)
        ChatBackend.removeMessageObserver(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(conversationIdKey, conversationId)
        outState.putBoolean(isDialogKey, isDialog)
        outState.putParcelable(customConfigKey, customConfig)
        outState.putString(lastAvatarUrlKey, lastAvatarURL)
        outState.putInt(maxCharacterCountKey, maxCharacterCount)
        // NB: `messages`/`responses` are not written here on purpose — see `onCreate`.
        outState.putBoolean(isBlockedKey, isBlocked)
        outState.putBoolean(isSecureChatKey, isSecureChat)
        // Status messages come from `onFailure`, not from `ChatBackend.messages`, so the replay in
        // `startOrResumeConversation` cannot rebuild them — an error banner (and its Retry button)
        // has to be carried over explicitly or a rotation silently swallows it.
        outState.putString(statusMessageKey, statusMessage)
        outState.putBoolean(statusMessageIsErrorKey, statusMessageIsError)
        outState.putBoolean(statusMessageRetryKey, statusMessageRetry)
        outState.putBoolean(hasResumedWithUserTokenKey, hasResumedWithUserToken)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatContent = view.findViewById(R.id.chat_content)
        secureChatWrapper = view.findViewById(R.id.secure_chat_wrapper)
        secureChatTextView = view.findViewById(R.id.secure_chat_textview)
        secureChatImageView = view.findViewById(R.id.secure_chat_imageview)
        editText = view.findViewById(R.id.chat_input_editText)
        submitButton = view.findViewById(R.id.chat_input_submit_button)
        characterCountTextView = view.findViewById(R.id.chat_input_character_count_textview)
        characterCountWrapper = view.findViewById(R.id.chat_input_character_count_wrapper)
        scrollView = view.findViewById(R.id.chat_messages_scrollview)
        chatMessagesLayout = view.findViewById(R.id.chat_messages)
        chatInputWrapper = view.findViewById(R.id.chat_input_wrapper)
        chatInputWrapperTopBorder = view.findViewById(R.id.chat_input_wrapper_top_border)
        chatInputOutline = view.findViewById(R.id.chat_input_outline)
        chatInputBorder = view.findViewById(R.id.chat_input_border)
        chatInputInner = view.findViewById(R.id.chat_input_inner)
        fileUploadButton = view.findViewById(R.id.upload_files_button)
        fileUploadsWrapper = view.findViewById(R.id.file_uploads_wrapper)

        chatInputInner.background.setTint(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )

        val inputWrapperOriginalPaddingBottom = chatInputWrapper.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(chatInputWrapper) { v, windowInsets ->
            // ime() included: with enforced edge-to-edge (targetSdk 35+) a host window without
            // legacy adjustResize behaviour doesn't shrink for the keyboard, which would
            // otherwise cover the composer. When the window does resize, the dispatched ime
            // inset is zero, so nothing is double-padded.
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(bottom = inputWrapperOriginalPaddingBottom + insets.bottom)
            windowInsets
        }

        scrollView.isSmoothScrollingEnabled = true
        editText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                sequence: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { updateInputStates(it) }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, after: Int) {}

        })
        editText.setOnKeyListener(object : View.OnKeyListener {

            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (event?.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    val text = editText.text.toString().trim()
                    submitText(text)
                    return true
                }

                return false
            }

        })
        editText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            // Update chat input color
            val primaryColor = customConfig?.chatPanel?.styling?.primaryColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.primaryColor
                ?: ChatBackend.config?.chatPanel?.styling?.primaryColor
                ?: ContextCompat.getColor(requireContext(), R.color.primaryColor)
            val textareaBorderColor = customConfig?.chatPanel?.styling?.composer?.textareaBorderColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.textareaBorderColor
                ?: ChatBackend.config?.chatPanel?.styling?.composer?.textareaBorderColor
                ?: ContextCompat.getColor(requireContext(), R.color.gray)
            val textareaFocusBorderColor = customConfig?.chatPanel?.styling?.composer?.textareaFocusBorderColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.textareaFocusBorderColor
                ?: ChatBackend.config?.chatPanel?.styling?.composer?.textareaFocusBorderColor
                ?: primaryColor
            val textareaFocusOutlineColor =
                customConfig?.chatPanel?.styling?.composer?.textareaFocusOutlineColor
                    ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.textareaFocusOutlineColor
                    ?: ChatBackend.config?.chatPanel?.styling?.composer?.textareaFocusOutlineColor
                    ?: (primaryColor and 0x00FFFFFF or 0x77000000)
            val defaultTopBorderColor = customConfig?.chatPanel?.styling?.composer?.topBorderColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.topBorderColor
                ?: ChatBackend.config?.chatPanel?.styling?.composer?.topBorderColor
                ?: ContextCompat.getColor(requireContext(), R.color.gray)
            val topBorderFocusColor = customConfig?.chatPanel?.styling?.composer?.topBorderFocusColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.topBorderFocusColor
                ?: ChatBackend.config?.chatPanel?.styling?.composer?.topBorderFocusColor
                ?: defaultTopBorderColor

            val outlineColor: Int
            val borderColor: Int
            val topBorderColor: Int

            if (hasFocus) {
                outlineColor = textareaFocusOutlineColor
                borderColor = textareaFocusBorderColor
                topBorderColor = topBorderFocusColor
            } else {
                outlineColor = textareaFocusOutlineColor and 0x00FFFFFF // Transparent
                borderColor = textareaBorderColor
                topBorderColor = defaultTopBorderColor
            }

            chatInputOutline.background.setTint(outlineColor)
            chatInputBorder.background.setTint(borderColor)
            chatInputWrapperTopBorder.setBackgroundColor(topBorderColor)

        }
        submitButton.setOnClickListener {
            val text = editText.text.toString().trim()
            submitText(text)
        }
        fileUploadButton.setOnClickListener {
            // isCopyingPickedFile closes the async window: the pending chip only appears once
            // the background copy completes, so the list-emptiness guard alone let a second tap
            // during a slow copy queue a second attachment in a UI built for one
            if (isBlocked || pendingFileUploads.isNotEmpty() || isCopyingPickedFile) {
                return@setOnClickListener
            }

            Intent(Intent.ACTION_GET_CONTENT).let { intent ->
                intent.setType("*/*")
                startActivityForResult(intent, FILE_PICKER_REQUEST);
            }
        }

        // Set up menu
        setHasOptionsMenu(true)

        // Update styling
        updateStyling(ChatBackend.config)

        // Drop any message fragments the FragmentManager restored for us. They come back without
        // their payload (see `onCreate`), and the conversation is replayed from
        // `ChatBackend.messages` below anyway. Only the message container is cleared, so an open
        // settings/feedback sheet (added to `R.id.chat_content`) survives the restore.
        if (savedInstanceState != null) {
            discardRestoredMessageFragments()

            // Put back the status message the restored fragments just took with them. If the
            // conversation resumes successfully it is hidden again, exactly as it would have been
            // had the rotation not happened.
            statusMessage?.let { presentStatusMessage(it, statusMessageIsError, statusMessageRetry) }

            // A restored settings/feedback sheet comes back with a null delegate (delegates are
            // not parcelable), which left Back, Close and Delete dead — and since the sheet's
            // root swallows clicks, the user was stuck on it. Rewire them.
            (childFragmentManager.findFragmentByTag(settingsFragmentId)
                    as? ChatViewSettingsFragment)?.let {
                it.menuDelegate = this
                it.customConfig = it.customConfig ?: customConfig
            }
            (childFragmentManager.findFragmentByTag(feedbackFragmentId)
                    as? ChatViewFeedbackFragment)?.let {
                it.settingsDelegate = this
                it.customConfig = it.customConfig ?: customConfig
            }
        }

        // Start
        if (startConversationOnLoad) {
            start()
        } else if (ChatBackend.messages.isNotEmpty()) {
            // A host that starts the conversation itself (SSO flows) still needs the transcript
            // back after a view recreation: messages are views and die with the view, and the
            // replay otherwise only runs from start(). Render-only — no START/RESUME is sent.
            replayConversation()
        }
    }

    /**
     * Synchronously remove every fragment restored into the message container, so the conversation
     * can be rendered from scratch without colliding with the restored ones. Restored upload chips
     * go too: `pendingFileUploads` is not saved, so a restored chip would be an orphan with no
     * backing state.
     */
    fun discardRestoredMessageFragments() {
        val restored = childFragmentManager.fragments.filter {
            it.id == R.id.chat_messages || it.id == R.id.file_uploads_wrapper
        }
        if (restored.isEmpty()) return

        val transaction = childFragmentManager.beginTransaction()
        restored.forEach { transaction.remove(it) }
        transaction.commitNowAllowingStateLoss()

        waitingForAgentResponseFragmentTags.clear()
    }

    /**
     * Start (or resume) the conversation.
     *
     * @param userToken When non-null, assigned to [ChatBackend.userToken] *before* the START/RESUME
     * command is built. Use this to guarantee the token is present in the snapshot taken when the
     * command is serialized.
     * @param customPayload When non-null, assigned to [ChatBackend.customPayload] *before* the
     * START/RESUME command is built. Use this to guarantee the payload is present in the snapshot —
     * this is the supported way to avoid an empty `custom_payload` when the value is resolved
     * asynchronously.
     */
    fun start(userToken: String? = null, customPayload: JsonElement? = null) {
        userToken?.let { ChatBackend.userToken = it }
        customPayload?.let { ChatBackend.customPayload = it }

        ChatBackend.onReady(object : ChatBackend.ConfigReadyListener {
            override fun onFailure(exception: Exception) {
                if (!isAdded) return

                var message = exception.localizedMessage ?: "An unknown error occured"
                when (exception) {
                    is UnknownHostException,
                    is SocketTimeoutException,
                    is ConnectException -> {
                        message = getString(R.string.network_error_message)
                    }
                }

                showStatusMessage(message, exception, true)
            }

            override fun onReady(config: ChatConfig) {
                if (!isAdded) return

                setBackendProperties(config)
                setupEventListeners()

                // Should we resume a stored/remembered conversation?
                val rememberConversation = customConfig?.chatPanel?.settings?.rememberConversation
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.rememberConversation
                    ?: ChatBackend.config?.chatPanel?.settings?.rememberConversation
                    ?: ChatPanelDefaults.Settings.rememberConversation
                val rememberConversationExpirationDuration = customConfig?.chatPanel?.settings?.rememberConversationExpirationDuration
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.rememberConversationExpirationDuration
                    ?: ChatBackend.config?.chatPanel?.settings?.rememberConversationExpirationDuration
                val isRememberConversationExpiryInFuture = isRememberConversationExpiryInFuture()

                if (conversationId == null && rememberConversation &&
                    (rememberConversationExpirationDuration == null || isRememberConversationExpiryInFuture)) {
                    conversationId = getStoredConversationId()
                }

                startOrResumeConversation(conversationId)
            }
        })
    }

    /**
     * Re-render the whole conversation from [ChatBackend.messages] into the current view.
     * Render-only — nothing is sent.
     */
    fun replayConversation() {
        setIsBlocked(ChatBackend.isBlocked)
        ChatBackend.messages.forEach { handleReceivedMessage(it, animated = false, isReplay = true) }
        isBlocked = ChatBackend.isBlocked
    }

    /**
     * Whether this open of the chat has already resumed with the user token.
     *
     * Per open, not per fragment instance: it is carried through the saved instance state, so
     * the fragment the FragmentManager rebuilds on a rotation inherits it, while a fresh open
     * starts at false. Kept out of [ChatBackend] on purpose — a singleton flag is shared by
     * every live chat fragment (an embedded one and the modal activity, say), so one open would
     * skip its resume and another's close would re-arm the duplicate.
     */
    private var hasResumedWithUserToken = false

    fun startOrResumeConversation(conversationId: String? = null) {
        val messages = ChatBackend.messages

        replayConversation()
        // Start when there is no local conversation to replay; with a user token, also resume
        // the first time this open of the chat sits over an existing one — that refreshes the
        // conversation with the current token and runs triggerActionOnResume, as every open
        // always has. Once per open, though: resuming from every rebuilt fragment sent another
        // RESUME on each rotation and, with startTriggerActionId + triggerActionOnResume,
        // re-fired the trigger action, appending duplicate bot responses every time.
        val resumeWithUserToken = ChatBackend.userToken != null && !hasResumedWithUserToken
        if (messages.size == 0 || resumeWithUserToken) {
            showWaitingForAgentResponseIndicator()

            if (conversationId != null || ChatBackend.userToken != null) {
                hasResumedWithUserToken = ChatBackend.userToken != null
                // Make sure we don't animate in the message when resuming a conversation
                animateMessages = false

                val startTriggerActionId = customConfig?.chatPanel?.settings?.startTriggerActionId
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.startTriggerActionId
                    ?: ChatBackend.config?.chatPanel?.settings?.startTriggerActionId
                val triggerActionOnResume = customConfig?.chatPanel?.settings?.triggerActionOnResume
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.triggerActionOnResume
                    ?: ChatBackend.config?.chatPanel?.settings?.triggerActionOnResume
                    ?: ChatPanelDefaults.Settings.triggerActionOnResume

                // Skip welcome message if userToken and startTriggerActionId is defined and triggerActionOnResume is true
                val settingsSkipWelcomeMessage = customConfig?.chatPanel?.settings?.skipWelcomeMessage
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.skipWelcomeMessage
                    ?: ChatBackend.config?.chatPanel?.settings?.skipWelcomeMessage
                    ?: false
                val skipWelcomeMessage = (ChatBackend.userToken != null && startTriggerActionId != null && triggerActionOnResume) || settingsSkipWelcomeMessage
                val startLanguage = customConfig?.chatPanel?.settings?.startLanguage
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.startLanguage
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.startLanguage

                val resumeCommand = CommandResume(conversationId, language = startLanguage)
                resumeCommand.skipWelcomeMessage = skipWelcomeMessage

                ChatBackend.resume(resumeCommand, object : ChatBackend.APIMessageResponseListener {
                    override fun onFailure(exception: Exception) {
                        // Nothing was resumed, so a rebuilt fragment gets to try again
                        hasResumedWithUserToken = false

                        if (!isAdded) return

                        animateMessages = true

                        val startNewMessage =
                            customConfig?.chatPanel?.settings?.startNewConversationOnResumeFailure
                                ?: ChatBackend.customConfig?.chatPanel?.settings?.startNewConversationOnResumeFailure
                                ?: ChatPanelDefaults.Settings.startNewConversationOnResumeFailure
                        if (startNewMessage) {
                            startConversation()
                        }
                    }

                    override fun onResponse(apiMessage: APIMessage) {
                        if (!isAdded) return

                        // Enable animation of new messages (conversation has been resumed at this point)
                        animateMessages = true

                        if (startTriggerActionId != null && triggerActionOnResume) {
                            ChatBackend.triggerAction(startTriggerActionId.toString())
                        }
                    }
                })
            } else {
                startConversation()
            }
        } else
            // Scroll to bottom after x ms
            Handler(Looper.getMainLooper()).postDelayed({
                if (context != null) {
                    scrollToBottom()
                }
            }, 200)
    }

    fun startConversation() {
        ChatBackend.start(
            CommandStart(
                language = customConfig?.chatPanel?.settings?.startLanguage ?: ChatBackend.customConfig?.chatPanel?.settings?.startLanguage,
                contextIntentId = customConfig?.chatPanel?.settings?.contextTopicIntentId ?: ChatBackend.customConfig?.chatPanel?.settings?.contextTopicIntentId,
                triggerAction = customConfig?.chatPanel?.settings?.startTriggerActionId ?: ChatBackend.customConfig?.chatPanel?.settings?.startTriggerActionId,
                authTriggerAction = customConfig?.chatPanel?.settings?.authStartTriggerActionId ?: ChatBackend.customConfig?.chatPanel?.settings?.authStartTriggerActionId,
                skipWelcomeMessage = customConfig?.chatPanel?.settings?.skipWelcomeMessage ?: ChatBackend.customConfig?.chatPanel?.settings?.skipWelcomeMessage
            )
        )
    }

    fun setIsBlocked(isBlocked: Boolean) {
        this.isBlocked = isBlocked
        editText.inputType =
            if (isBlocked) InputType.TYPE_NULL else InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
        if (!isBlocked) {
            // While blocked, clientTyping reports max length 0 and updateInputStates installs a
            // LengthFilter(0). That filter rejects every character, so afterTextChanged can never
            // run to replace it — without this reset the composer stayed dead after unblocking.
            maxCharacterCount = ChatBackend.maxInputChars
            editText.filters = arrayOf(InputFilter.LengthFilter(maxCharacterCount))
        }
        editText.hint =
            if (isBlocked) null
            else {
                val messages = customConfig?.messages
                    ?: ChatBackend.customConfig?.messages
                    ?: ChatBackend.config?.messages
                messages?.get(ChatBackend.languageCode)?.composePlaceholder
            }
        updateSubmitButtonState()
    }

    override fun onMessageReceived(backend: ChatBackend, message: APIMessage) {
        // The view can be gone while the fragment stays alive (on the back stack, for one).
        // Rendering into the dead view tree would also add the response to `responses`, and the
        // replay into the next view would then dedupe it away — losing the message for good; the
        // skipped message is still in ChatBackend.messages and comes back with the replay. The
        // conversation bookkeeping still runs: the remembered id must reach the preferences even
        // if the process dies before a new view exists.
        if (view == null) {
            recordConversationMetadata(message)
            return
        }

        setIsBlocked(ChatBackend.isBlocked)
        handleReceivedMessage(message, animateMessages)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // The rendered output dies with the view, so the bookkeeping must die with it: the next
        // onViewCreated replays the conversation from ChatBackend.messages, and anything left in
        // `responses` would be deduped away — a back-stack pop used to come back to an empty
        // transcript because of exactly that.
        responses = ArrayList()
        messages = ArrayList()
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) {
        if (!isAdded) return

        updateStyling(config)
        setBackendProperties(config)
        activity?.invalidateOptionsMenu()
    }

    override fun onFailure(backend: ChatBackend, error: Exception) {
        if (!isAdded) return

        // A composer upload failure has already been shown as a red chip by its own listener;
        // the banner on top of that is one failure reported twice
        if (error === uploadFailureShownAsChip) {
            uploadFailureShownAsChip = null
            return
        }

        hideWaitingForAgentResponseIndicator()
        // An SDKSerializationException means the server's answer made no sense; its English
        // description is for logs, the banner gets the localised generic error
        val message =
            if (error is SDKSerializationException) getString(R.string.unknown_error)
            else error.localizedMessage ?: getString(R.string.unknown_error)
        showStatusMessage(message, error, true)
    }

    fun handleReceivedMessage(message: APIMessage, animated: Boolean = true, isReplay: Boolean = false) {
        // If we have a postedId, update the first message with a temporary ID
        message.postedId?.let { postedId ->
            // `indexOfFirst` returns -1 when there is no pending temporary response — which is the
            // normal case right after a state restore, since `responses` is rebuilt from scratch.
            val firstTemporaryId = responses.indexOfFirst { r -> r.isTempId }
            if (firstTemporaryId >= 0) {
                val m = responses[firstTemporaryId]
                val messageCopy = Response(
                    id = postedId.toString(),
                    source = m.source,
                    language = m.language,
                    elements = m.elements,
                    dateCreated = m.dateCreated
                )
                responses[firstTemporaryId] = messageCopy
            }
        }

        val messageResponses =
            message.responses?.let { ArrayList(message.responses) } ?: ArrayList()

        // The retry path replays over an already-populated list; duplicates would skew the
        // welcome-message index math below
        if (!messages.contains(message)) messages.add(message)
        message.response?.let { messageResponses.add(it) }

        recordConversationMetadata(message)

        // The backend's merged flags, not this message's own fields: the server sends both
        // intermittently, and locally-echoed client messages carry no conversation at all — the
        // per-message read made the attach button vanish the moment the user sent anything.
        // isInHumanChat rather than the bare poll flag: that flag is sticky, so once the human
        // handed back to the virtual agent with poll omitted, the button would have stayed.
        val allowHumanChatFileUpload = ChatBackend.allowHumanChatFileUpload &&
                ChatBackend.isInHumanChat && ChatBackend.fileUploadServiceEndpointUrl != null
        fileUploadButton.visibility = if (allowHumanChatFileUpload) View.VISIBLE else View.GONE

        messageResponses.forEachIndexed { index, response ->
            // Skip if the response is already present
            if (responses.find { it.id == response.id } != null) {
                hideWaitingForAgentResponseIndicator()
                return@forEachIndexed
            }

            // Add it to our response list if it's not already there
            responses.add(response)
            // Store the last avatar URL for later re-use
            lastAvatarURL = response.avatarUrl ?: lastAvatarURL

            val firstNonBlockedMessageIndex = messages.indexOfFirst { (conversation) ->
                !(conversation?.state?.isBlocked ?: false)
            }
            val messageFeedbackOnFirstAction =
                customConfig?.chatPanel?.settings?.messageFeedbackOnFirstAction
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.messageFeedbackOnFirstAction
                    ?: ChatPanelDefaults.Settings.messageFeedbackOnFirstAction
            val isWelcomeMessage = messages.indexOf(message) <= firstNonBlockedMessageIndex

            if (response.elements.isNotEmpty()) {
                // Render the response
                render(
                    response,
                    animated,
                    !messageFeedbackOnFirstAction && isWelcomeMessage,
                    message.conversation?.state?.awaitingFiles != null &&
                            index == messageResponses.size - 1
                )
            }
            // Are we waiting for an agent response? Show a "waiting view"
            val chatStatus =
                ChatBackend.lastResponse?.conversation?.state?.chatStatus ?:
                    ChatStatus.VIRTUAL_AGENT

            // An UNKNOWN status that isn't polling behaves like the virtual agent here — unknown
            // statuses used to decode as VIRTUAL_AGENT and showed the dots; leaving the user with
            // no feedback at all after sending would be the opposite of degrading gracefully. A
            // polling UNKNOWN is human-chat-like, where the dots are wrong. The same
            // isInHumanChat that drives polling and the attach button decides "polling" here, so
            // the three can't disagree (the merged ChatBackend.poll can be stale residue from an
            // earlier human chat that ended with the flag omitted).
            val waitsForAgent = chatStatus == ChatStatus.VIRTUAL_AGENT ||
                    (chatStatus == ChatStatus.UNKNOWN && !ChatBackend.isInHumanChat)
            if (response.source == SourceType.CLIENT && waitsForAgent && animated)
                showWaitingForAgentResponseIndicator()
            else hideWaitingForAgentResponseIndicator()
            // Should we show the "Secure chat" badge?
            isSecureChat =
                message.conversation?.state?.authenticatedUserId != null ||
                    (
                        isSecureChat && messageResponses.size > 0 &&
                            messageResponses[0].source == SourceType.CLIENT
                    )

            val secureChatBackgroundColor = customConfig?.chatPanel?.styling?.secureChatBannerBackgroundColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.secureChatBannerBackgroundColor
                ?: ChatBackend.config?.chatPanel?.styling?.secureChatBannerBackgroundColor
            val secureChatBannerTextColor = customConfig?.chatPanel?.styling?.secureChatBannerTextColor
                ?: ChatBackend.customConfig?.chatPanel?.styling?.secureChatBannerTextColor
                ?: ChatBackend.config?.chatPanel?.styling?.secureChatBannerTextColor

            secureChatBackgroundColor?.let {
                secureChatWrapper.setBackgroundColor(it)
            }

            secureChatBannerTextColor?.let {
                secureChatTextView.setTextColor(it)
                secureChatImageView.setColorFilter(it, android.graphics.PorterDuff.Mode.SRC_IN)
            }

            secureChatWrapper.visibility = if (isSecureChat) View.VISIBLE else View.GONE
        }

        // A replay re-renders what is already known; only a genuinely new server message is
        // evidence that the connection works, and only that should clear an error banner — the
        // replay after a rotation used to remove the very banner the restore had just put back,
        // taking the Retry button with it.
        if (!isReplay) hideStatusMessage()
        updateTranslatedMessages()
        enableActionButtons()

        // Show human typing indicator if applicable
        if (message.conversation?.state?.humanIsTyping == true) {
            if (messageResponses.size > 0)
                hideHumanTypingIndicator()
            showHumanTypingIndicator()
        } else {
            hideHumanTypingIndicator()
        }
    }

    /**
     * Persist and publish the conversation metadata a message carries. Kept apart from the
     * rendering so it also runs for messages that arrive while the view is gone — the remembered
     * conversation id must reach the preferences even if the process dies before a new view is
     * created.
     */
    private fun recordConversationMetadata(message: APIMessage) {
        val rememberConversation = customConfig?.chatPanel?.settings?.rememberConversation
            ?: ChatBackend.customConfig?.chatPanel?.settings?.rememberConversation
            ?: ChatBackend.config?.chatPanel?.settings?.rememberConversation
            ?: ChatPanelDefaults.Settings.rememberConversation
        if (rememberConversation) {
            // `?.let`, not `.let`: locally-echoed user messages carry no conversation, and
            // unconditionally storing null here wiped the remembered conversation on every sent
            // message — losing it for good if the app died before the server's reply re-stored it.
            message.conversation?.id?.let {
                storeConversationId(it)
            }
        }

        message.conversation?.id?.let { newConversationId ->
            if (conversationId != newConversationId) {
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.conversationIdChanged, newConversationId)
                conversationId = newConversationId
            }
        }

        message.conversation?.reference?.let { newReference ->
            if (conversationReference != newReference) {
                conversationReference = newReference
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.conversationReferenceChanged, newReference)
            }
        }
    }

    fun storeConversationId(conversationId: String?) {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(storedConversationIdKey, conversationId)
            apply()
        }
    }

    fun getStoredConversationId(): String? {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return null
        return sharedPref.getString(storedConversationIdKey, null)
    }

    fun submitText(text: String) {
        val uploadedFiles = pendingFileUploads.filter { !it.isUploading && !it.hasUploadError }
        val hasCompletedFileUploads = pendingFileUploads.size > 0 && pendingFileUploads.size == uploadedFiles.size
        val hasUploadingFiles = pendingFileUploads.any { it.isUploading }

        if (hasUploadingFiles || (text.isEmpty() && !hasCompletedFileUploads)) return

        if (hasCompletedFileUploads) {
            ChatBackend.sendFiles(uploadedFiles, text)
        } else {
            ChatBackend.message(text)
        }

        // Failed chips stay on screen: an attachment the user saw error must not vanish
        // silently just because they sent a text message — they can still remove it themselves
        pendingFileUploads = ArrayList(pendingFileUploads.filter { it.hasUploadError })
        renderFileUploads()

        BoostUIEvents.notifyObservers(BoostUIEvents.Event.messageSent)

        // Update view state
        editText.setText("")
        updateInputStates("")
    }

    fun updateStyling(config: ChatConfig? = null) {
        updateTranslatedMessages()
        updateSubmitButtonState()

        val hide = customConfig?.chatPanel?.styling?.composer?.hide
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.hide
            ?: config?.chatPanel?.styling?.composer?.hide
            ?: ChatPanelDefaults.Styling.Composer.hide
        chatInputWrapper.visibility = if (hide) View.GONE else View.VISIBLE

        val frameBackgroundColor = customConfig?.chatPanel?.styling?.composer?.frameBackgroundColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.frameBackgroundColor
            ?: config?.chatPanel?.styling?.composer?.frameBackgroundColor
        frameBackgroundColor?.let {
            chatInputWrapper.setBackgroundColor(it)
        }

        val composeLengthColor = customConfig?.chatPanel?.styling?.composer?.composeLengthColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.composeLengthColor
            ?: config?.chatPanel?.styling?.composer?.composeLengthColor
        composeLengthColor?.let {
            characterCountTextView.setTextColor(it)
        }

        val textareaBackgroundColor = customConfig?.chatPanel?.styling?.composer?.textareaBackgroundColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.textareaBackgroundColor
            ?: config?.chatPanel?.styling?.composer?.textareaBackgroundColor
            ?: ContextCompat.getColor(requireContext(), android.R.color.white)
        chatInputInner.background.setTint(textareaBackgroundColor)

        val textareaBorderColor = customConfig?.chatPanel?.styling?.composer?.textareaBorderColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.textareaBorderColor
            ?: config?.chatPanel?.styling?.composer?.textareaBorderColor
            ?: ContextCompat.getColor(requireContext(), R.color.gray)
        chatInputBorder.background.setTint(textareaBorderColor)

        val textareaTextColor = customConfig?.chatPanel?.styling?.composer?.textareaTextColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.textareaTextColor
            ?: config?.chatPanel?.styling?.composer?.textareaTextColor
        textareaTextColor?.let {
            editText.setTextColor(it)
        }

        val textareaPlaceholderTextColor = customConfig?.chatPanel?.styling?.composer?.textareaPlaceholderTextColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.textareaPlaceholderTextColor
            ?: config?.chatPanel?.styling?.composer?.textareaPlaceholderTextColor
        textareaPlaceholderTextColor?.let {
            editText.setHintTextColor(it)
        }

        val topBorderColor = customConfig?.chatPanel?.styling?.composer?.topBorderColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.topBorderColor
            ?: config?.chatPanel?.styling?.composer?.topBorderColor
            ?: ContextCompat.getColor(requireContext(), R.color.gray)
        chatInputWrapperTopBorder.setBackgroundColor(topBorderColor)

        val panelBackgroundColor = customConfig?.chatPanel?.styling?.panelBackgroundColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.panelBackgroundColor
            ?: config?.chatPanel?.styling?.panelBackgroundColor
        panelBackgroundColor?.let {
            chatMessagesLayout.setBackgroundColor(panelBackgroundColor)
            scrollView.setBackgroundColor(panelBackgroundColor)
        }

        val fileUploadButtonColor = customConfig?.chatPanel?.styling?.composer?.fileUploadButtonColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.fileUploadButtonColor
            ?: config?.chatPanel?.styling?.composer?.fileUploadButtonColor
        fileUploadButtonColor?.let {
            val states = arrayOf(
                intArrayOf(android.R.attr.state_enabled), // enabled
                intArrayOf(-android.R.attr.state_enabled), // disabled
            )
            val colors = intArrayOf(
                fileUploadButtonColor,
                R.color.gray,
            )
            fileUploadButton.imageTintList = ColorStateList(states, colors)
        }

        @FontRes val bodyFont = customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.bodyFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.bodyFont
        @FontRes val footnoteFont = customConfig?.chatPanel?.styling?.fonts?.footnoteFont
            ?: ChatBackend.customConfig?.chatPanel?.styling?.fonts?.footnoteFont
            ?: ChatBackend.config?.chatPanel?.styling?.fonts?.footnoteFont

        bodyFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                editText.typeface = typeface
            } catch (e: java.lang.Exception) {}
        }

        footnoteFont?.let {
            try {
                val typeface = ResourcesCompat.getFont(requireContext().applicationContext, it)
                characterCountTextView.typeface = typeface
            } catch (e: java.lang.Exception) {}
        }
    }

    fun setBackendProperties(config: ChatConfig? = null) {
        val fileUploadServiceEndpointUrl =
            customConfig?.chatPanel?.settings?.fileUploadServiceEndpointUrl
                ?: ChatBackend.customConfig?.chatPanel?.settings?.fileUploadServiceEndpointUrl
                ?: config?.chatPanel?.settings?.fileUploadServiceEndpointUrl
        val userToken = customConfig?.chatPanel?.settings?.userToken
            ?: ChatBackend.customConfig?.chatPanel?.settings?.userToken
            ?: config?.chatPanel?.settings?.userToken

        fileUploadServiceEndpointUrl?.let { ChatBackend.fileUploadServiceEndpointUrl = it }
        userToken?.let { ChatBackend.userToken = it }

        val filterValues = customConfig?.chatPanel?.header?.filters?.filterValues
            ?: ChatBackend.customConfig?.chatPanel?.header?.filters?.filterValues
            ?: config?.chatPanel?.header?.filters?.filterValues
        if (ChatBackend.filterValues == null && filterValues != null) {
            ChatBackend.filterValues = filterValues
        }

        if (ChatBackend.customPayload == null) {
            ChatBackend.customPayload = customConfig?.chatPanel?.settings?.customPayload
                ?: ChatBackend.customConfig?.chatPanel?.settings?.customPayload
                ?: config?.chatPanel?.settings?.customPayload
        }
    }

    fun setupEventListeners() {
        val rememberConversation = customConfig?.chatPanel?.settings?.rememberConversation
            ?: ChatBackend.customConfig?.chatPanel?.settings?.rememberConversation
            ?: ChatBackend.config?.chatPanel?.settings?.rememberConversation
            ?: ChatPanelDefaults.Settings.rememberConversation
        val rememberConversationExpirationDuration = customConfig?.chatPanel?.settings?.rememberConversationExpirationDuration
            ?: ChatBackend.customConfig?.chatPanel?.settings?.rememberConversationExpirationDuration
            ?: ChatBackend.config?.chatPanel?.settings?.rememberConversationExpirationDuration

        if (rememberConversation && rememberConversationExpirationDuration != null) {
            BoostUIEvents.addObserver(this)
        }
    }

    override fun onUIEventReceived(event: BoostUIEvents.Event, detail: Any?) {
        when (event) {
            BoostUIEvents.Event.chatPanelOpened,
            BoostUIEvents.Event.messageSent,
            BoostUIEvents.Event.externalLinkClicked,
            BoostUIEvents.Event.actionLinkClicked -> {
                val rememberConversationExpirationDuration = customConfig?.chatPanel?.settings?.rememberConversationExpirationDuration
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.rememberConversationExpirationDuration
                    ?: ChatBackend.config?.chatPanel?.settings?.rememberConversationExpirationDuration
                rememberConversationExpirationDuration?.let {
                    storeRememberConversationExpiry(it)
                }
            }
            BoostUIEvents.Event.chatPanelClosed -> {
                val removeRememberedConversationOnChatPanelClose = customConfig?.chatPanel?.settings?.removeRememberedConversationOnChatPanelClose
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.removeRememberedConversationOnChatPanelClose
                    ?: ChatBackend.config?.chatPanel?.settings?.removeRememberedConversationOnChatPanelClose
                    ?: ChatPanelDefaults.Settings.removeRememberedConversationOnChatPanelClose
                if (removeRememberedConversationOnChatPanelClose) {
                    removeRememberConversationExpiry()
                }
            }
            else -> {}
        }
    }

    fun storeRememberConversationExpiry(rememberConversationExpirationDuration: String?) {
        rememberConversationExpirationDuration?.let {
            try {
                val expiry = Duration.parse(it)
                val newDate = Date(System.currentTimeMillis() + expiry.inWholeMilliseconds)

                val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
                with(sharedPref.edit()) {
                    putLong(storedRememberConversationExpiryKey, newDate.time)
                    apply()
                }
            } catch (_: IllegalArgumentException) {}
        }
    }

    fun removeRememberConversationExpiry() {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            remove(storedRememberConversationExpiryKey)
            apply()
        }
    }

    fun getStoredRememberConversationExpiry(): Date? {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return null
        val time = sharedPref.getLong(storedRememberConversationExpiryKey, 0)
        if (time > 0) {
            return Date(time)
        }

        return null
    }

    fun isRememberConversationExpiryInFuture(): Boolean {
        val storedExpiryDate = getStoredRememberConversationExpiry() ?: return false

        return Date().before(storedExpiryDate)
    }

    fun updateTranslatedMessages() {
        val composePlaceHolder = customConfig?.messages?.get(ChatBackend.languageCode)?.composePlaceholder
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.composePlaceholder
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.composePlaceholder

        val submitMessage = customConfig?.messages?.get(ChatBackend.languageCode)?.submitMessage
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.submitMessage
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.submitMessage

        val loggedIn = customConfig?.messages?.get(ChatBackend.languageCode)?.loggedIn
            ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.loggedIn
            ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.loggedIn

        composePlaceHolder?.let {
            editText.hint = it
        }

        submitMessage?.let {
            submitButton.contentDescription = it
        }

        loggedIn?.let {
            secureChatTextView.text = it
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateInputStates(text: String) {
        val textLength = text.length
        val value = ChatBackend.clientTyping(text)

        maxCharacterCount = value.maxLength
        characterCountTextView.text = "$textLength / $maxCharacterCount"
        characterCountWrapper.visibility = if (editText.lineCount >= 3) View.VISIBLE else View.GONE

        editText.filters = arrayOf(InputFilter.LengthFilter(maxCharacterCount))

        updateSubmitButtonState(text)
    }

    fun updateSubmitButtonState(text: String? = null) {
        val currentText = text ?: editText.text.toString()
        val primaryColor = customConfig?.chatPanel?.styling?.primaryColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.primaryColor
            ?: ChatBackend.config?.chatPanel?.styling?.primaryColor
            ?: ContextCompat.getColor(requireContext(), R.color.primaryColor)
        val sendButtonColor = customConfig?.chatPanel?.styling?.composer?.sendButtonColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.sendButtonColor
            ?: ChatBackend.config?.chatPanel?.styling?.composer?.sendButtonColor ?: primaryColor
        val sendButtonDisabledColor = customConfig?.chatPanel?.styling?.composer?.sendButtonDisabledColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.composer?.sendButtonDisabledColor
            ?: ChatBackend.config?.chatPanel?.styling?.composer?.sendButtonDisabledColor
            ?: ContextCompat.getColor(requireContext(), R.color.gray)

        val uploadedFiles = pendingFileUploads.filter { !it.isUploading && !it.hasUploadError }
        val hasCompletedFileUploads = pendingFileUploads.size > 0 && pendingFileUploads.size == uploadedFiles.size
        val hasUploadingFiles = pendingFileUploads.any { it.isUploading }

        val isEnabled = (currentText.trim().isNotEmpty() || hasCompletedFileUploads) && !isBlocked && !hasUploadingFiles

        submitButton.isEnabled = isEnabled
        submitButton.backgroundTintList = ColorStateList.valueOf(
            if (isEnabled) sendButtonColor else sendButtonDisabledColor
        )
    }

    fun render(
        response: Response,
        animated: Boolean = true,
        isWelcomeMessage: Boolean,
        isAwaitingFiles: Boolean
    ) {
        // Already on screen: view-path messages carry their response id as a view tag,
        // fragment-path messages as the child fragment's tag. Both checks come before the
        // delegate is consulted, or a replay would ask the host to build every message part
        // again only to discard the result.
        if (chatMessagesLayout.findViewWithTag<View>(response.id) != null) return
        if (childFragmentManager.findFragmentByTag(response.id) != null) return

        // Messages are drawn as views. A host app that supplies its own message fragments through
        // [delegate] still gets the fragment path — see `messageFragmentFor`.
        val fragment = messageFragmentFor(response, animated, isWelcomeMessage, isAwaitingFiles)
        if (fragment == null) {
            renderAsView(response, animated, isWelcomeMessage, isAwaitingFiles)
            return
        }

        // Add the message
        childFragmentManager.beginTransaction()
            .add(
                R.id.chat_messages,
                fragment,
                response.id
            )
            .commitAllowingStateLoss()

        val handler = Handler(Looper.getMainLooper())
        if (animated) {
            val pace = customConfig?.chatPanel?.styling?.pace
                ?: ChatBackend.customConfig?.chatPanel?.styling?.pace
                ?: ChatBackend.config?.chatPanel?.styling?.pace
                ?: ChatPanelDefaults.Styling.pace
            val paceFactor = TimingHelper.calculatePace(pace)
            val staggerDelay = TimingHelper.calculateStaggerDelay(pace, 1)
            val timeUntilReveal = TimingHelper.calcTimeToRead(paceFactor)

            // Animate the scroll view to the bottom after each element display
            response.elements.forEachIndexed { index, _ ->
                handler.postDelayed({
                    if (context != null) {
                        // Scroll to bottom after x ms
                        scrollToBottom()
                    }
                }, timeUntilReveal * index + staggerDelay + 100)
            }
            if (isAwaitingFiles)
                handler.postDelayed({
                    if (context != null) {
                        scrollToBottom()
                    }
                }, timeUntilReveal * response.elements.size + staggerDelay + 100)
        } else {
            scrollToBottom()
            handler.postDelayed({
                if (context != null) {
                    scrollToBottom(false)
                }
            }, 150)
        }
    }

    fun scrollToBottom(smoothScroll: Boolean = true) {
        // Deliberately tied to the next layout pass rather than posted: this is normally called
        // right after content is added, when the container has not been measured yet and its
        // `bottom` is still the height from before.
        OneShotPreDrawListener.add(chatMessagesLayout) {
            if (smoothScroll) scrollView.smoothScrollTo(0, chatMessagesLayout.bottom)
            else scrollView.scrollTo(0, chatMessagesLayout.bottom)
        }
        // A pre-draw listener only fires when a frame is actually drawn. Calls that arrive with
        // the screen idle — a scroll scheduled 200 ms after an indicator's entry animation has
        // finished, say — would otherwise wait for some unrelated redraw.
        chatMessagesLayout.invalidate()
    }

    fun renderFileUploads() {
        // Remove existing file upload fragments
        val transaction = childFragmentManager.beginTransaction()
        for (fragment in pendingFileUploadFragments) {
            if (fragment.isAdded) {
                transaction.remove(fragment)
            }
        }
        transaction.commitAllowingStateLoss()
        pendingFileUploadFragments = ArrayList()

        for (file in pendingFileUploads) {
            val fragment = FileUploadFragment(file)
            pendingFileUploadFragments.add(fragment)
            childFragmentManager.beginTransaction()
                .add(
                    R.id.file_uploads_wrapper,
                    fragment,
                    file.url
                )
                .commitAllowingStateLoss()

            fragment.delegate = this
        }

        updateSubmitButtonState()
        fileUploadButton.isEnabled = !isBlocked && pendingFileUploads.isEmpty()
    }

    override fun removeFileUpload(fragment: FileUploadFragment, file: File) {
        pendingFileUploads.remove(file)
        renderFileUploads()
    }

    fun showHumanTypingIndicator() {
        if (childFragmentManager.findFragmentByTag("humanTyping") != null)
            return

        childFragmentManager
            .beginTransaction()
            .add(
                R.id.chat_messages,
                getHumanTypingFragment(),
                "humanTyping"
            )
            .commitAllowingStateLoss()

        Handler(Looper.getMainLooper()).postDelayed({
            if (context != null) {
                scrollToBottom(true)
            }
        }, 200)
    }

    fun hideHumanTypingIndicator() {
        // Remove any visible "human typing" waiting indicators. Synchronously: the caller may
        // re-show the indicator in the same pass (agent sent a message while still typing), and
        // with a queued removal the show would see the old fragment by tag, skip the add, and
        // the removal would then take the indicator down even though the agent is typing.
        childFragmentManager.findFragmentByTag("humanTyping")?.let {
            childFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
        }
    }

    fun showWaitingForAgentResponseIndicator() {
        val uuid = UUID.randomUUID().toString()
        childFragmentManager
            .beginTransaction()
            .add(
                R.id.chat_messages,
                getWaitingForServerResponseFragment(),
                uuid
            )
            .commitAllowingStateLoss()
        waitingForAgentResponseFragmentTags.add(uuid)

        Handler(Looper.getMainLooper()).postDelayed({
            if (context != null) {
                scrollToBottom(true)
            }
        }, 200)
    }

    fun hideWaitingForAgentResponseIndicator() {
        // Remove any visible "waiting for agent response" waiting indicators
        val transaction = childFragmentManager.beginTransaction()
        waitingForAgentResponseFragmentTags.forEach { tag ->
            childFragmentManager.findFragmentByTag(tag)?.let {
                if (it.isAdded) {
                    transaction.remove(it)
                }
            }
        }
        waitingForAgentResponseFragmentTags.clear()
        transaction.commitAllowingStateLoss()
    }

    fun toggleSettingsFragment() {
        val settingsFragment =
            childFragmentManager.findFragmentByTag(settingsFragmentId)
        val feedbackFragment =
            childFragmentManager.findFragmentByTag(feedbackFragmentId)

        if (settingsFragment != null) {
            hideMenu()
        } else showMenu()

        if (feedbackFragment != null) {
            hideFeedback()
        }
    }

    fun showStatusMessage(message: String, exception: Exception? = null, isError: Boolean = false) {
        var m = message
        var retry = false
        when (exception) {
            is UnknownHostException,
            is SocketTimeoutException,
            is ConnectException -> {
                m = customConfig?.messages?.get(ChatBackend.languageCode)?.chatServiceUnavailable
                    ?: ChatBackend.customConfig?.messages?.get(ChatBackend.languageCode)?.chatServiceUnavailable
                    ?: ChatBackend.config?.messages?.get(ChatBackend.languageCode)?.chatServiceUnavailable
                    ?: getString(R.string.network_error_message)
                retry = true
            }
        }

        presentStatusMessage(m, isError, retry, exception)
    }

    private fun presentStatusMessage(
        message: String,
        isError: Boolean,
        retry: Boolean,
        exception: Exception? = null
    ) {
        hideStatusMessage()

        statusMessage = message
        statusMessageIsError = isError
        statusMessageRetry = retry

        childFragmentManager
            .beginTransaction()
            .add(
                R.id.chat_messages,
                getStatusMessageFragment(message, exception, isError, retry),
                errorId
            )
            .commitAllowingStateLoss()

        Handler(Looper.getMainLooper()).postDelayed({
            if (context != null) {
                scrollToBottom(true)
            }
        }, 200)
    }

    fun hideStatusMessage() {
        statusMessage = null

        // Remove any visible status messages
        childFragmentManager.findFragmentByTag(errorId)?.let {
            childFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()

        inflater.inflate(R.menu.chat_toolbar_menu, menu)

        val selectedFilterValues = ChatBackend.filterValues
            ?: customConfig?.chatPanel?.header?.filters?.filterValues
            ?: ChatBackend.customConfig?.chatPanel?.header?.filters?.filterValues
            ?: ChatBackend.config?.chatPanel?.header?.filters?.filterValues
            ?: emptyList()
        val options = customConfig?.chatPanel?.header?.filters?.options
            ?: ChatBackend.customConfig?.chatPanel?.header?.filters?.options
            ?: ChatBackend.config?.chatPanel?.header?.filters?.options
        // firstOrNull: the options list is server-controlled and can be empty
        val initialFilter = options?.firstOrNull()
        val hasSelectedFilterValues = selectedFilterValues.isNotEmpty()

        var currentFilter: no.boostai.sdk.ChatBackend.Objects.Filter? = null
        if (options != null && selectedFilterValues != null) {
            currentFilter = options.find { it.values == selectedFilterValues }
        }

        val availableFilterValues = currentFilter?.values ?: emptyList()

        @ColorInt val tintColor = customConfig?.chatPanel?.styling?.contrastColor
            ?: ChatBackend.customConfig?.chatPanel?.styling?.contrastColor
            ?: ChatBackend.config?.chatPanel?.styling?.contrastColor
            ?: ContextCompat.getColor(requireContext(), R.color.contrastColor)

        // Set correct color on settings item
        val settingsItem = menu.findItem(R.id.action_settings)
        val settingsDrawable = DrawableCompat.wrap(settingsItem.icon!!)
        DrawableCompat.setTint(settingsDrawable, tintColor)
        settingsItem.icon = settingsDrawable

        // Set correct color on close item
        val closeItem = menu.findItem(R.id.action_close)
        val closeDrawable = DrawableCompat.wrap(closeItem.icon!!)
        DrawableCompat.setTint(closeDrawable, tintColor)
        closeItem.icon = closeDrawable

        // Set correct color on minimize item
        val minimizeItem = menu.findItem(R.id.action_minimize)
        val minimizeDrawable = DrawableCompat.wrap(minimizeItem.icon!!)
        DrawableCompat.setTint(minimizeDrawable, tintColor)
        minimizeItem.icon = minimizeDrawable

        // Set correct color on filter item
        val filterItem = menu.findItem(R.id.action_filter)
        val filterMenu = filterItem.subMenu
        filterMenu?.clear()

        val filter = currentFilter ?: initialFilter
        if ((!hasSelectedFilterValues || (availableFilterValues.isNotEmpty() && availableFilterValues.equals(selectedFilterValues))) && filter != null) {
            filterItem.setVisible(true)
            filterItem.title = filter.title ?: getString(R.string.filter)
            val s = SpannableString(filterItem.title)
            s.setSpan(ForegroundColorSpan(tintColor), 0, s.length, 0)
            filterItem.title = s

            // Add filters as a submenu
            options?.forEach {
                filterMenu?.add(0, it.id, Menu.NONE, it.title)
            }
        } else {
            filterItem.setVisible(false)
        }

        // Only show the filter selector if we have any filters
        filterItem.isVisible = (filterMenu?.size() ?: 0) > 0

        val hideMinimizeButton = customConfig?.chatPanel?.header?.hideMinimizeButton
            ?: ChatBackend.config?.chatPanel?.header?.hideMinimizeButton
            ?: ChatBackend.customConfig?.chatPanel?.header?.hideMinimizeButton
            ?: false

        val hideMenuButton = customConfig?.chatPanel?.header?.hideMenuButton
            ?: ChatBackend.config?.chatPanel?.header?.hideMenuButton
            ?: ChatBackend.customConfig?.chatPanel?.header?.hideMenuButton
            ?: false

        minimizeItem.isVisible = isDialog && !hideMinimizeButton
        settingsItem.isVisible = !hideMenuButton
        closeItem.isVisible = isDialog

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> toggleSettingsFragment()
            R.id.action_filter -> {}
            R.id.action_close -> closeChatWithFeedback()
            R.id.action_minimize -> {
                activity?.finish()
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.chatPanelMinimized)
            }
            else -> {
                // Possibly a filter submenu item — but only claim item ids that actually match a
                // filter. Anything else (the host activity's own toolbar items, the "up" button)
                // used to fall through here, wipe the active filter with null and get swallowed.
                val options = customConfig?.chatPanel?.header?.filters?.options
                    ?: ChatBackend.customConfig?.chatPanel?.header?.filters?.options
                    ?: ChatBackend.config?.chatPanel?.header?.filters?.options
                val filter = options?.find {
                    it.id == item.itemId
                } ?: return super.onOptionsItemSelected(item)

                ChatBackend.filterValues = filter.values
                requireActivity().invalidateOptionsMenu()
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.filterValuesChanged, filter.values)
            }
        }

        return true
    }

    override fun deleteConversation() {
        // Conversation ID will be reset on deletion, so we save this for event publishing purposes
        val existingConversationId = ChatBackend.conversationId

        ChatBackend.delete(null, object : ChatBackend.APIMessageResponseListener {

            override fun onFailure(exception: java.lang.Exception) {}

            override fun onResponse(apiMessage: APIMessage) {
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.conversationDeleted, existingConversationId)

                if (!isAdded) return

                clearRenderedConversation()
                // Hide menu
                hideMenu()
                // Start a new conversation — a fresh one, so a user token gets its resume again
                conversationId = null
                conversationReference = null
                hasResumedWithUserToken = false
                startOrResumeConversation()
            }

        })
    }

    /**
     * Take every rendered message off screen — fragments and views — and reset the response
     * bookkeeping so the next conversation starts from a clean slate.
     *
     * Clearing the fragments alone is not enough: messages are drawn as views, and without the
     * `removeAllViews` the deleted transcript would stay on screen with the new conversation
     * rendering below it — and a new welcome message reusing an old response id would be swallowed
     * by the duplicate check in `render`.
     */
    fun clearRenderedConversation() {
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.forEach {
            if (it != null && it.isAdded) transaction.remove(it)
        }
        transaction.commitAllowingStateLoss()
        chatMessagesLayout.removeAllViews()
        responses = ArrayList()
        messages = ArrayList()
    }

    override fun showMenu() {
        hideKeyboard()
        childFragmentManager
            .beginTransaction()
            .add(
                R.id.chat_content,
                delegate?.getSettingsFragment() ?: getChatViewSettingsFragment(),
                settingsFragmentId
            )
            .commitAllowingStateLoss()

        BoostUIEvents.notifyObservers(BoostUIEvents.Event.menuOpened)
    }

    override fun hideMenu() {
        val settingsFragment =
            childFragmentManager.findFragmentByTag(settingsFragmentId) as? ChatViewSettingsFragment

        if (settingsFragment != null) {
            settingsFragment.hide()
            Handler(Looper.getMainLooper()).postDelayed({
                if (context != null) {
                    childFragmentManager
                        .beginTransaction()
                        .remove(settingsFragment)
                        .commitAllowingStateLoss()
                }
            }, 150)
        }

        BoostUIEvents.notifyObservers(BoostUIEvents.Event.menuClosed)
    }

    override fun showFeedback() {
        hideKeyboard()
        childFragmentManager
            .beginTransaction()
            .add(
                R.id.chat_content,
                delegate?.getFeedbackFragment() ?: getChatViewFeedbackFragment(),
                feedbackFragmentId
            )
            .commitAllowingStateLoss()
    }

    override fun hideFeedback() {
        val feedbackFragment =
            childFragmentManager.findFragmentByTag(feedbackFragmentId) as? ChatViewFeedbackFragment

        if (feedbackFragment != null) {
            feedbackFragment.hide()
            Handler(Looper.getMainLooper()).postDelayed({
                if (context != null) {
                    childFragmentManager
                        .beginTransaction()
                        .remove(feedbackFragment)
                        .commitAllowingStateLoss()
                }
            }, 150)
        }
    }

    override fun closeChat() {
        activity?.finish()
        ChatBackend.suspendPolling()
        BoostUIEvents.notifyObservers(BoostUIEvents.Event.chatPanelClosed)
    }

    fun closeChatWithFeedback() {
        // If the feedback window is open and the user taps X again, close the dialog
        val feedbackFragment = childFragmentManager.findFragmentByTag(feedbackFragmentId)
        if (feedbackFragment != null) {
            BoostUIEvents.notifyObservers(BoostUIEvents.Event.chatPanelClosed)
            ChatBackend.suspendPolling()
            activity?.finish();
            return;
        }

        // Should we request conversation feedback? Show feedback dialogue
        val hasClientMessages = ChatBackend.messages.filter { apiMessage ->
            val source = apiMessage.response?.source
                ?: apiMessage.responses?.firstOrNull()?.source
                ?: SourceType.BOT

            source == SourceType.CLIENT
        }.isNotEmpty()

        val requestConversationFeedback =
            customConfig?.chatPanel?.settings?.requestFeedback
                ?: ChatBackend.customConfig?.chatPanel?.settings?.requestFeedback
                ?: ChatBackend.config?.chatPanel?.settings?.requestFeedback
                ?: ChatPanelDefaults.Settings.requestFeedback
        if (requestConversationFeedback && hasClientMessages)
            showFeedback()
        else {
            // If all else fails, close the window
            activity?.finish()
            ChatBackend.suspendPolling()
            BoostUIEvents.notifyObservers(BoostUIEvents.Event.chatPanelClosed)
        }
    }

    fun hideKeyboard() =
        // Hide keyboard if visible
        requireActivity().currentFocus?.let { view ->
            val imm =
                requireActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }

    /**
     * Render a message as views — the default path.
     *
     * Nothing is paged or trimmed: the whole conversation can stay on screen, because views add
     * nothing to the activity's saved state.
     */
    fun renderAsView(
        response: Response,
        animated: Boolean,
        isWelcomeMessage: Boolean,
        isAwaitingFiles: Boolean
    ) {
        // A conversation can mix paths when a host supplies fragments for only some messages.
        // Fragment additions are queued while addView lands immediately, so flush the queue first
        // or this view would jump ahead of an earlier message still waiting in it.
        childFragmentManager.executePendingTransactions()

        val messageView = delegate?.getChatMessageView(response, animated) ?: getMessageView(
            response,
            animated,
            isWelcomeMessage,
            isAwaitingFiles
        )
        messageView.tag = response.id
        chatMessagesLayout.addView(messageView)
        scrollToBottom()
    }

    /** Open the system file picker; the picked file becomes a pending attachment in the composer. */
    fun openFilePicker() {
        Intent(Intent.ACTION_GET_CONTENT).let { intent ->
            intent.setType("*/*")
            startActivityForResult(intent, FILE_PICKER_REQUEST)
        }
    }

    /**
     * Open the system file picker for an "upload a file" action button. Unlike [openFilePicker],
     * the picked file is uploaded and sent as a chat message straight away — the flow a bot that
     * asks for a file is waiting on, and what `ChatMessageButtonFragment` has always done.
     */
    fun openFilePickerForActionUpload() {
        Intent(Intent.ACTION_GET_CONTENT).let { intent ->
            intent.setType("*/*")
            startActivityForResult(intent, ACTION_UPLOAD_REQUEST)
        }
    }

    fun getMessageView(
        response: Response,
        animated: Boolean,
        isWelcomeMessage: Boolean,
        isAwaitingFiles: Boolean
    ): View = ChatMessageView(
        requireContext(),
        response,
        isBlocked,
        isWelcomeMessage,
        isAwaitingFiles,
        animated,
        lastAvatarURL,
        customConfig,
        chatResponseViewURLHandlingDelegate,
        delegate,
        ::openFilePickerForActionUpload,
        onActionTapped = { disableActionButtons() },
        onContentChanged = { scrollToBottom() }
    )

    /**
     * The message fragment to use for [response], or `null` when it should be drawn as views.
     *
     * A host app that overrides [ChatViewFragmentDelegate.getChatMessageFragment] or
     * [ChatViewFragmentDelegate.getChatMessagePartFragment] keeps the fragment path, so existing
     * integrations are unaffected by the switch to views. Both hooks default to returning `null`,
     * which is what selects the view path for everyone else.
     */
    fun messageFragmentFor(
        response: Response,
        animated: Boolean,
        isWelcomeMessage: Boolean,
        isAwaitingFiles: Boolean
    ): Fragment? {
        val delegate = delegate ?: return null

        @Suppress("DEPRECATION")
        delegate.getChatMessageFragment(response, animated)?.let { return it }

        // Asked once per element, and the answers handed to the message fragment: probing here
        // and letting ChatMessageFragment ask again made the host build every part twice
        @Suppress("DEPRECATION")
        val partFragments = response.elements.map { element ->
            delegate.getChatMessagePartFragment(
                element,
                response.id,
                response.source == SourceType.CLIENT,
                animated
            )
        }
        if (partFragments.all { it == null }) return null

        return getMessageFragment(
            response,
            animated,
            response.source == SourceType.CLIENT,
            isWelcomeMessage,
            isAwaitingFiles
        ).also { (it as? ChatMessageFragment)?.preparedPartFragments = partFragments }
    }

    fun getMessageFragment(
        response: Response,
        animated: Boolean,
        isClient: Boolean,
        isWelcomeMessage: Boolean,
        isAwaitingFiles: Boolean
    ): Fragment = ChatMessageFragment(
        response,
        animated,
        isBlocked,
        isClient,
        isWelcomeMessage,
        isWaitingForServerResponse = false,
        isAwaitingFiles,
        avatarUrl = lastAvatarURL,
        customConfig = customConfig,
        delegate,
        buttonDelegate = this,
        chatResponseViewURLHandlingDelegate = chatResponseViewURLHandlingDelegate
    )

    fun getHumanTypingFragment(): Fragment = ChatHumanTypingFragment()

    fun getWaitingForServerResponseFragment(): Fragment = ChatMessageFragment(
        response = null,
        animated = false,
        isBlocked = false,
        isClient = false,
        isWelcomeMessage = false,
        isWaitingForServerResponse = true,
        avatarUrl = lastAvatarURL,
        customConfig = customConfig,
        delegate = delegate
    )

    fun getStatusMessageFragment(message: String, exception: Exception? = null, isError: Boolean = false, retry: Boolean = false): Fragment =
        StatusMessageFragment(message, isError, retry, customConfig, this)

    fun getChatViewFeedbackFragment(): Fragment =
        ChatViewFeedbackFragment(this, isDialog, customConfig)

    fun getChatViewSettingsFragment(): Fragment =
        ChatViewSettingsFragment(this, isDialog, customConfig)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                copyPickedFileAsync(uri) { fileUpload ->
                    // The composer chips live in this fragment's view; a copy finishing after a
                    // rotation has nothing to attach to (pending uploads don't survive rotation
                    // anyway)
                    if (!isAdded) return@copyPickedFileAsync

                    val name = fileUpload.filename
                    val mimeType = fileUpload.mimeType

                    val fileExpirationSeconds = customConfig?.chatPanel?.settings?.fileExpirationSeconds
                        ?: ChatBackend.customConfig?.chatPanel?.settings?.fileExpirationSeconds
                        ?: ChatBackend.config?.chatPanel?.settings?.fileExpirationSeconds

                    val pendingFile = File(name, mimeType, "", true)
                    pendingFileUploads.add(pendingFile)
                    renderFileUploads()

                    ChatBackend.uploadFilesToAPI(listOf(fileUpload), fileExpirationSeconds, object : ChatBackend.APIFileUploadResponseListener {
                        override fun onFailure(exception: Exception) {
                            if (!isAdded) return

                            // The chip is this failure's UI. ChatBackend hands the same exception
                            // to the message observers right after this listener (both posted to
                            // the main thread, in that order), where onFailure must not also
                            // raise the banner — the action-button path has no chip and keeps it.
                            uploadFailureShownAsChip = exception
                            pendingFileUploads.remove(pendingFile)
                            pendingFileUploads.add(File(name, mimeType, "", false, true))
                            renderFileUploads()
                        }
                        override fun onResponse(files: List<File>) {
                            if (!isAdded) return

                            pendingFileUploads.remove(pendingFile)
                            pendingFileUploads.addAll(files)
                            renderFileUploads()
                        }
                    })
                }
                return
            }
        }

        if (requestCode == ACTION_UPLOAD_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                copyPickedFileAsync(uri) { fileUpload ->
                    uploadAndSendPickedFile(fileUpload)
                }
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun uploadAndSendPickedFile(fileUpload: FileUpload) {
        val fileExpirationSeconds = customConfig?.chatPanel?.settings?.fileExpirationSeconds
            ?: ChatBackend.customConfig?.chatPanel?.settings?.fileExpirationSeconds
            ?: ChatBackend.config?.chatPanel?.settings?.fileExpirationSeconds

        // Sent as a message the moment the upload lands — the bot flow that showed the
        // upload button is waiting for it, not for the user to also press send.
        ChatBackend.uploadFilesToAPI(listOf(fileUpload), fileExpirationSeconds, object : ChatBackend.APIFileUploadResponseListener {
            override fun onFailure(exception: Exception) {
                // The bot is waiting for this file, so the failure must be visible — and it is:
                // every upload failure also reaches the message observers, and this fragment's
                // observer onFailure raises the banner. Raising it here too showed it twice.
            }
            override fun onResponse(files: List<File>) {
                ChatBackend.sendFiles(files)
            }
        })
    }

    /**
     * Copy the picked document into the app cache off the main thread — the copy is blocking
     * I/O against a third-party content provider, and a large file used to freeze the UI for
     * its duration — then hand the result back on the main thread. A failed copy (file removed
     * between pick and result, provider errors) shows an error status message.
     */
    /** True while a picked file is being copied on the worker thread; gates the attach button. */
    private var isCopyingPickedFile = false

    private fun copyPickedFileAsync(uri: android.net.Uri, onCopied: (FileUpload) -> Unit) {
        // Resolved here: the fragment may be detached by the time the worker runs
        val contentResolver = requireContext().contentResolver
        val cacheDir = requireContext().cacheDir

        isCopyingPickedFile = true
        Thread {
            val fileUpload = try {
                copyPickedFile(contentResolver, cacheDir, uri)
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    isCopyingPickedFile = false
                    if (isAdded) {
                        showStatusMessage(
                            e.localizedMessage ?: getString(R.string.unknown_error), e, true
                        )
                    }
                }
                return@Thread
            }

            // Delivered even if the fragment has been detached meanwhile (rotation during a slow
            // copy): the action-upload continuation is backend-level and must reach the bot —
            // continuations that need the fragment guard on isAdded themselves
            Handler(Looper.getMainLooper()).post {
                isCopyingPickedFile = false
                onCopied(fileUpload)
            }
        }.start()
    }

    /** Copy the picked document into the app cache, resolving its display name and MIME type. */
    private fun copyPickedFile(
        contentResolver: android.content.ContentResolver,
        cacheDir: java.io.File,
        uri: android.net.Uri
    ): FileUpload {
        val mimeType: String = contentResolver.getType(uri) ?: "application/octet-stream"
        var name = "file.unknown"

        contentResolver.query(uri, null, null, null, null)?.use {
            // Content providers are third-party code: the display-name column, or a first row,
            // may simply not be there
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                name = it.getString(nameIndex) ?: name
            }
        }

        // createTempFile rejects prefixes shorter than three characters, and a display name is
        // no place for path separators
        val prefix = name.replace('/', '_').padEnd(3, '_')
        val outFile = java.io.File.createTempFile(prefix, "", cacheDir)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }

        return FileUpload(outFile, name, mimeType)
    }

    override fun didTapActionButton(fragment: ChatMessageButtonFragment) {
        disableActionButtons()
    }

    override fun disableActionButtons() {
        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.disableActionButtons()
        }
        forEachMessageView({ it.disableActionButtons() }, { it.disableActionButtons() })
    }

    override fun enableActionButtons(makeInteractable: Boolean) {
        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.enableActionButtons(makeInteractable)
        }
        forEachMessageView(
            { it.enableActionButtons(makeInteractable) },
            { it.enableActionButtons(makeInteractable) }
        )
    }

    /**
     * Visit every message rendered as views. A host-supplied message view (from
     * [ChatViewFragmentDelegate.getChatMessageView]) that isn't a [ChatMessageView] can still
     * take part in button dimming by implementing [ChatMessageButtonDelegate].
     */
    private inline fun forEachMessageView(
        action: (ChatMessageView) -> Unit,
        hostAction: (ChatMessageButtonDelegate) -> Unit
    ) {
        for (index in 0 until chatMessagesLayout.childCount) {
            when (val child = chatMessagesLayout.getChildAt(index)) {
                is ChatMessageView -> action(child)
                is ChatMessageButtonDelegate -> hostAction(child)
                else -> {}
            }
        }
    }

    override fun didTapRetryButton() {
        hideStatusMessage()

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (context == null) return@postDelayed

            if (ChatBackend.messages.isEmpty()) {
                // Nothing local yet: retry the initial START/RESUME
                start()
            } else {
                // Mid-conversation, start() would replay locally and stop — the resume gate sees
                // the existing messages and never touches the network, so the banner would just
                // vanish without verifying anything. Send a RESUME instead: success re-syncs and
                // clears the banner, failure re-raises it with Retry intact.
                ChatBackend.resume(CommandResume(ChatBackend.conversationId))
            }
        }, 250)
    }
}