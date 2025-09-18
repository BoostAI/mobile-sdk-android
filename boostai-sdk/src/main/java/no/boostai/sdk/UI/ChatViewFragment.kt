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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
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
    val settingsFragmentId = "settings"
    val feedbackFragmentId = "feedback"
    lateinit var chatContent: FrameLayout
    lateinit var secureChatWrapper: LinearLayout
    lateinit var secureChatTextView: TextView
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
    var pendingFileUploads: ArrayList<File> = ArrayList()
    var pendingFileUploadFragments: ArrayList<FileUploadFragment> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        conversationId = customConfig?.chatPanel?.settings?.conversationId
            ?: ChatBackend.customConfig?.chatPanel?.settings?.conversationId

        val bundle = savedInstanceState ?: arguments
        bundle?.let {
            conversationId = conversationId ?: it.getString(conversationId)
            isDialog = it.getBoolean(isDialogKey)
            customConfig = it.getParcelable(customConfigKey)
            // TODO: Parcelable delegate?
            // delegate =

            lastAvatarURL = it.getString(lastAvatarUrlKey)
            maxCharacterCount = it.getInt(maxCharacterCountKey)
            messages = it.getParcelableArrayList(messagesKey) ?: ArrayList()
            responses = it.getParcelableArrayList(responsesKey) ?: ArrayList()
            isBlocked = it.getBoolean(isBlockedKey)
            isSecureChat = it.getBoolean(isSecureChatKey)
        }

        setBackendProperties()

        ChatBackend.addConfigObserver(this)
        ChatBackend.addMessageObserver(this)
    }

    override fun onPause() {
        super.onPause()

        ChatBackend.stopPolling();
    }

    override fun onResume() {
        super.onResume()

        val chatStatus =
            ChatBackend.lastResponse?.conversation?.state?.chatStatus ?:
            ChatStatus.VIRTUAL_AGENT
        if (chatStatus == ChatStatus.IN_HUMAN_CHAT_QUEUE || chatStatus == ChatStatus.ASSIGNED_TO_HUMAN) {
            ChatBackend.startPolling()
        }
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
        outState.putParcelableArrayList(messagesKey, messages)
        outState.putParcelableArrayList(responsesKey, responses)
        outState.putBoolean(isBlockedKey, isBlocked)
        outState.putBoolean(isSecureChatKey, isSecureChat)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatContent = view.findViewById(R.id.chat_content)
        secureChatWrapper = view.findViewById(R.id.secure_chat_wrapper)
        secureChatTextView = view.findViewById(R.id.secure_chat_textview)
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
            if (isBlocked || pendingFileUploads.isNotEmpty()) {
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

        // Start
        start()
    }

    fun start() {
        ChatBackend.onReady(object : ChatBackend.ConfigReadyListener {
            override fun onFailure(exception: Exception) {
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

    fun startOrResumeConversation(conversationId: String? = null) {
        val messages = ChatBackend.messages

        setIsBlocked(ChatBackend.isBlocked)
        messages.forEach { handleReceivedMessage(it, false) }
        isBlocked = ChatBackend.isBlocked
        // Start new conversation if no messages exists
        if (messages.size == 0 || ChatBackend.userToken != null) {
            showWaitingForAgentResponseIndicator()

            if (conversationId != null || ChatBackend.userToken != null) {
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
        editText.hint =
            if (isBlocked) null
            else {
                val messages = customConfig?.messages
                    ?: ChatBackend.customConfig?.messages
                    ?: ChatBackend.config?.messages
                messages?.get(ChatBackend.languageCode)?.composePlaceholder ?:
                getString(R.string.chat_input_placeholder)
            }
        updateSubmitButtonState()
    }

    override fun onMessageReceived(backend: ChatBackend, message: APIMessage) {
        setIsBlocked(ChatBackend.isBlocked)
        handleReceivedMessage(message, animateMessages)
    }

    override fun onConfigReceived(backend: ChatBackend, config: ChatConfig) {
        updateStyling(config)
        setBackendProperties(config)
        activity?.invalidateOptionsMenu()
    }

    override fun onFailure(backend: ChatBackend, error: Exception) {
        hideWaitingForAgentResponseIndicator()
        showStatusMessage(error.localizedMessage ?: getString(R.string.unknown_error), error, true)
    }

    fun handleReceivedMessage(message: APIMessage, animated: Boolean = true) {
        // If we have a postedId, update the first message with a temporary ID
        message.postedId?.let { postedId ->
            val firstTemporaryId = responses.indexOfFirst { r -> r.isTempId }
            firstTemporaryId.let { id ->
                val m = responses[id]
                val messageCopy = Response(
                    id = postedId.toString(),
                    source = m.source,
                    language = m.language,
                    elements = m.elements,
                    dateCreated = m.dateCreated
                )
                responses[id] = messageCopy
            }
        }

        val messageResponses =
            message.responses?.let { ArrayList(message.responses) } ?: ArrayList()

        messages.add(message)
        message.response?.let { messageResponses.add(it) }

        // Save conversation id if applicable
        val rememberConversation = customConfig?.chatPanel?.settings?.rememberConversation
            ?: ChatBackend.customConfig?.chatPanel?.settings?.rememberConversation
            ?: ChatBackend.config?.chatPanel?.settings?.rememberConversation
            ?: ChatPanelDefaults.Settings.rememberConversation
        if (rememberConversation) {
            message.conversation?.id.let {
                storeConversationId(it)
            }
        }

        val allowHumanChatFileUpload = message.conversation?.state?.allowHumanChatFileUpload == true && ChatBackend.lastResponse?.conversation?.state?.poll == true && ChatBackend.fileUploadServiceEndpointUrl != null
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

            if (
                response.source == SourceType.CLIENT &&
                    chatStatus == ChatStatus.VIRTUAL_AGENT && animated
            )
                showWaitingForAgentResponseIndicator()
            else hideWaitingForAgentResponseIndicator()
            // Should we show the "Secure chat" badge?
            isSecureChat =
                message.conversation?.state?.authenticatedUserId != null ||
                    (
                        isSecureChat && messageResponses.size > 0 &&
                            messageResponses[0].source == SourceType.CLIENT
                    )

            secureChatWrapper.visibility = if (isSecureChat) View.VISIBLE else View.GONE
        }

        hideStatusMessage()
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

        pendingFileUploads = ArrayList()
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
            val expiry = Duration.parse(it)
            val newDate = Date(System.currentTimeMillis() + expiry.inWholeMilliseconds)

            val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
            with (sharedPref.edit()) {
                putLong(storedRememberConversationExpiryKey, newDate.time)
                apply()
            }
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
        if (childFragmentManager.findFragmentByTag(response.id) == null) {
            val fragment = delegate?.getChatMessageFragment(response, animated) ?:
                getMessageFragment(
                    response,
                    animated,
                    response.source == SourceType.CLIENT,
                    isWelcomeMessage,
                    isAwaitingFiles
                )

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
    }

    fun scrollToBottom(smoothScroll: Boolean = true) =
        scrollView.post {
            if (smoothScroll) scrollView.smoothScrollTo(0, chatMessagesLayout.bottom)
            else scrollView.scrollTo(0, chatMessagesLayout.bottom)
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
        // Remove any visible "human typing" waiting indicators
        childFragmentManager.findFragmentByTag("humanTyping")?.let {
            childFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
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

        hideStatusMessage()
        childFragmentManager
            .beginTransaction()
            .add(
                R.id.chat_messages,
                getStatusMessageFragment(m, exception, isError, retry),
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
        val initialFilter = options?.first()
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
                // Submenu was clicked
                val options = customConfig?.chatPanel?.header?.filters?.options
                    ?: ChatBackend.customConfig?.chatPanel?.header?.filters?.options
                    ?: ChatBackend.config?.chatPanel?.header?.filters?.options
                val filter = options?.find {
                    it.id == item.itemId
                }
                ChatBackend.filterValues = filter?.values
                requireActivity().invalidateOptionsMenu()
                BoostUIEvents.notifyObservers(BoostUIEvents.Event.filterValuesChanged, filter?.values)
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

                // Remove all message fragments
                val transaction = childFragmentManager.beginTransaction()

                responses.forEach {
                    val fragment = childFragmentManager.findFragmentByTag(it.id)

                    if (fragment != null && fragment.isAdded) transaction.remove(fragment)
                }
                transaction.commitAllowingStateLoss()
                // Empty the responses list
                responses = ArrayList()
                // Hide menu
                hideMenu()
                // Start a new conversation
                conversationId = null
                conversationReference = null
                startOrResumeConversation()
            }

        })
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
        ChatBackend.stopPolling()
        BoostUIEvents.notifyObservers(BoostUIEvents.Event.chatPanelClosed)
    }

    fun closeChatWithFeedback() {
        // If the feedback window is open and the user taps X again, close the dialog
        val feedbackFragment = childFragmentManager.findFragmentByTag(feedbackFragmentId)
        if (feedbackFragment != null) {
            BoostUIEvents.notifyObservers(BoostUIEvents.Event.chatPanelClosed)
            ChatBackend.stopPolling()
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
            ChatBackend.stopPolling()
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
                val contentResolver = requireContext().contentResolver

                val mimeType: String = contentResolver.getType(uri) ?: "application/octet-stream"
                var name = "file.unknown"

                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.let {
                    /*
                     * Get the column indexes of the data in the Cursor,
                     * move to the first row in the Cursor, get the data,
                     * and display it.
                     */
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    name = cursor.getString(nameIndex)
                }
                cursor?.close()

                val fileInputStream = requireContext().contentResolver.openInputStream(uri)
                val outFile =
                    java.io.File.createTempFile(name, "", requireContext().cacheDir)
                val outputStream = FileOutputStream(outFile)
                fileInputStream?.copyTo(outputStream)

                val fileExpirationSeconds = customConfig?.chatPanel?.settings?.fileExpirationSeconds
                    ?: ChatBackend.customConfig?.chatPanel?.settings?.fileExpirationSeconds
                    ?: ChatBackend.config?.chatPanel?.settings?.fileExpirationSeconds

                val pendingFile = File(name, mimeType, "", true)
                pendingFileUploads.add(pendingFile)
                renderFileUploads()

                val fileUpload = FileUpload(outFile, name, mimeType)
                ChatBackend.uploadFilesToAPI(listOf(fileUpload), fileExpirationSeconds, object : ChatBackend.APIFileUploadResponseListener {
                    override fun onFailure(exception: Exception) {
                        pendingFileUploads.remove(pendingFile)
                        pendingFileUploads.add(File(name, mimeType, "", false, true))
                        renderFileUploads()
                    }
                    override fun onResponse(files: List<File>) {
                        pendingFileUploads.remove(pendingFile)
                        pendingFileUploads.addAll(files)
                        renderFileUploads()
                    }
                })
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun didTapActionButton() {
        disableActionButtons()
    }

    override fun disableActionButtons() {
        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.disableActionButtons()
        }
    }

    override fun enableActionButtons() {
        for (fragment in childFragmentManager.fragments) {
            val f = fragment as? ChatMessageButtonDelegate
            f?.enableActionButtons()
        }
    }

    override fun didTapRetryButton() {
        hideStatusMessage()

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            start()
        }, 250)
    }
}