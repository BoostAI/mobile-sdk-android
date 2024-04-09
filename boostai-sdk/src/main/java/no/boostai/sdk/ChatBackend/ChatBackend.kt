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

package no.boostai.sdk.ChatBackend

import android.os.Handler
import android.os.Looper
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import no.boostai.sdk.ChatBackend.Objects.*
import no.boostai.sdk.ChatBackend.Objects.Response.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.min

object ChatBackend {
    val chatbackendJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /// HTTP client
    private var client: OkHttpClient = OkHttpClient()

    /// Enable or disable certificate pinning
    var isCertificatePinningEnabled = false
        set(enabled) {
            field = enabled
            if (enabled) {
                client = OkHttpClient.Builder()
                    .certificatePinner(BoostCertificatePinner.getCertificatePinner())
                    .build()
            } else {
                client = OkHttpClient()
            }
        }

    var domain: String = ""

    /// The conversation Id. If you store this for later usage, you need to set this instead of calling start()
    var conversationId: String? = null

    /// User token. This is used instead of conversation id if set
    var userToken: String? = null

    /// Reference. For internal use
    var reference: String = ""

    /// An string that is forwarded to External API's on each request
    var customPayload: String? = null

    /// The endpoint to upload files
    var fileUploadServiceEndpointUrl: String? = null

    /// Sets the Human Chat skill for the conversation
    var skill: String? = null

    /// Set your preference for html or text response. This will be added to all calls supporting it. Default is html=false
    var clean = false

    /// The current language of the bot
    var languageCode: String = "en-US"

    var isBlocked = false
    var allowDeleteConversation = false
    var chatStatus: ChatStatus = ChatStatus.VIRTUAL_AGENT
    var poll = false
    var maxInputChars = 110
    var privacyPolicyUrl = "https://www.boost.ai/privacy-policy"
    var lastResponse: APIMessage? = null

    private var lastTyped: Date? = null

    var pollInterval: Long = 2500
    var pollValue: String? = null
    var pollTimer: TimerTask? = null

    var filterValues: List<String>? = null

    private var messageObservers = ArrayList<WeakReference<MessageObserver>>()
    private var configObservers = ArrayList<WeakReference<ConfigObserver>>()
    private var eventObservers = ArrayList<WeakReference<EventObserver>>()

    var messages: ArrayList<APIMessage> = ArrayList()
    var vanId: Int? = null
    var config: ChatConfig? = null

    fun getChatUrl(): URL {
        return URL("https://" + domain + "/api/chat/v2")
    }

    fun getConfigUrl(): URL {
        return URL("https://" + domain + "/api/chat_panel/v2")
    }

    fun onReady(listener: ConfigReadyListener) {
        if (config != null) {
            listener.onReady(config!!)
        } else {
            getConfig(listener)
        }
    }


    fun getConfig(configReadyListener: ConfigReadyListener? = null) {
        val command = CommandConfig()

        if (vanId != null) {
            command.vanId = vanId
        }

        val data = Json.encodeToJsonElement(command)

        post(data, getConfigUrl(), object : APIMessageResponseListener {
            override fun onFailure(exception: Exception) {
                Handler(Looper.getMainLooper()).post {
                    configReadyListener?.onFailure(exception)
                }
            }

            override fun onResponse(apiMessage: APIMessage) {}
        }, object : APIMessageResponseHandler {
            override fun handleResponse(body: String, listener: APIMessageResponseListener?) {
                val parsedConfig = chatbackendJson.decodeFromString<ConfigV2>(body)
                val configV3 = convertConfig(parsedConfig)
                config = configV3

                Handler(Looper.getMainLooper()).post {
                    configReadyListener?.onReady(configV3)
                }

                publishConfigUpdate(configV3)
            }
        })
    }

    /// START command
    ///
    /// This starts a conversation and is mandatory before any other commands, unless you set the conversation_id manually
    ///
    /// - Parameter message: An optional CommandStart if you want to set all the parameters of the start command
    fun start(message: CommandStart? = null, listener: APIMessageResponseListener? = null) {
        val m = message ?: CommandStart()
        m.userToken = m.userToken ?: userToken
        m.skill = m.skill ?: skill
        m.customPayload = m.customPayload ?: customPayload
        m.language = m.language ?: languageCode

        send(m, listener)
    }

    /// STOP command
    ///
    /// This clears the conversation. You should call this to tell the API that the client is finished with the conversation
    ///
    /// - Parameter message: An optional CommandStop if you want to set all the parameters of the stop command
    fun stop(message: CommandStop? = null, listener: APIMessageResponseListener? = null) {
        send(message ?: CommandStop(conversationId, userToken), object : APIMessageResponseListener {
            override fun onFailure(exception: Exception) {
                Handler(Looper.getMainLooper()).post {
                    listener?.onFailure(exception)
                }
            }

            override fun onResponse(apiMessage: APIMessage) {
                Handler(Looper.getMainLooper()).post {
                    resetConversationState()

                    listener?.onResponse(apiMessage)
                }
            }
        })
    }

    /// RESUME command
    /// - Parameter message: An optional CommandResume if you want to set all the parameters of the resume command
    public fun resume(message: CommandResume? = null, listener: APIMessageResponseListener? = null) {
        val m = message ?: CommandResume()
        m.conversationId = m.conversationId
        m.userToken = m.userToken ?: userToken
        m.skill = m.skill ?: skill

        send(m, listener)
    }

    /// DELETE command
    /// - Parameter message: An optional CommandDelete if you want to set all the parameters of the delete command
    public fun delete(message: CommandDelete? = null, listener: APIMessageResponseListener? = null) {
        send(message ?: CommandDelete(conversationId, userToken), object : APIMessageResponseListener {
            override fun onFailure(exception: Exception) {
                Handler(Looper.getMainLooper()).post {
                    listener?.onFailure(exception)
                }
            }

            override fun onResponse(apiMessage: APIMessage) {
                Handler(Looper.getMainLooper()).post {
                    resetConversationState()

                    listener?.onResponse(apiMessage)
                }
            }
        })
    }

    /// POLL command
    ///
    /// This is mostly an internal command. The SDK will handle the poll for you.
    ///
    /// - Parameter message: An optional CommandPoll if you want to set all the parameters of the poll command
    fun poll(message: CommandPoll? = null, listener: APIMessageResponseListener? = null) {
        send(message ?: CommandPoll(conversationId, userToken, pollValue ?: ""), listener)
    }

    /// POLLSTOP command
    ///
    /// Call this if you want to stop a human poll sequence
    ///
    /// - Parameter message: An optional CommandPollStop if you want to set all the parameters of the pollstop command
    fun pollStop(message: CommandPollStop? = null, listener: APIMessageResponseListener? = null) {
        send(message ?: CommandPollStop(conversationId, userToken), listener)
    }

    fun smartReply(message: CommandSmartReply, listener: APIMessageResponseListener? = null) {
        send(message, listener)
    }

    fun humanChatPost(message: CommandHumanChatPost, listener: APIMessageResponseListener? = null) {
        send(message, listener)
    }

    /// This command is mostly internal. Try to use clientTyping(text) instead.
    fun typing(listener: APIMessageResponseListener? = null) {
        if (chatStatus == ChatStatus.VIRTUAL_AGENT) {
            return
        }

        if (lastTyped != null) {
            val now = Date()
            if (Math.abs(now.time - lastTyped!!.time) < 5) {
                return
            }
        }
        lastTyped = Date()

        val message = CommandTyping(conversationId, userToken)
        send(message, listener)
    }

    /// FEEDBACK command
    ///
    /// This is mostly an internal command. Try to use conversationFeedback(rating, text) instead
    ///
    /// - Parameter message: An optional CommandFeedback if you want to set all the parameters of the feedback command
    fun conversationFeedback(message: CommandFeedback, listener: APIMessageResponseListener? = null) {
        send(message, listener)
    }

    /// DOWNLOAD command
    ///
    /// Use this to download the conversation as text. You will get the result in the APIMessage.download message
    ///
    /// - Parameter userToken: An optional userToken. If this is set the command will use this instead of the conversation_id
    fun download(userToken: String? = null) {
        download(userToken, null)
    }

    /// LOGINEVENT command
    ///
    fun loginEvent(message: CommandLoginEvent, listener: APIMessageResponseListener? = null) {
        send(message, listener)
    }

    /// If a response contains a list of buttons (links), you can trigger the action connected to the button with the
    /// link_id value in the response
    ///
    /// { "command": "POST", "type": "action_link", "conversation_id": String, "id": String}
    /// - parameter id: link_id from the buttons in the payload element list
    fun actionButton(id: String, listener: APIMessageResponseListener? = null) {
        val message = createPostMessage(Type.ACTION_LINK)
        message.id = id
        send(message, listener)
    }

    /// Use the message (type: text) when sending chat messages to the server
    ///
    /// { "command": "POST", "type": "type", "conversation_id": String, "value": String}
    /// - parameter value: A string to send
    fun message(value: String, listener: APIMessageResponseListener? = null) {
        val message = createPostMessage(Type.TEXT)
        message.value = Json.encodeToJsonElement(value)

        try {
            val uuid = UUID.randomUUID().toString()
            val apiMessage = APIMessage(
                response = no.boostai.sdk.ChatBackend.Objects.Response.Response(
                    id = uuid,
                    dateCreated = Date(),
                    language = languageCode,
                    source = SourceType.CLIENT,
                    elements = listOf(
                        Element(
                            payload = Payload(
                                text = value,
                            ),
                            type = ElementType.TEXT
                        )
                    )
                )
            )
            messages.add(apiMessage)
            publishResponse(apiMessage, null)
        } catch (e: SerializationException) {
            publishResponse(null, e)
            return
        }

        send(message, object : APIMessageResponseListener {
            override fun onFailure(exception: Exception) {
                Handler(Looper.getMainLooper()).post {
                    listener?.onFailure(exception)
                }
            }

            override fun onResponse(apiMessage: APIMessage) {
                Handler(Looper.getMainLooper()).post {
                    listener?.onResponse(apiMessage)
                }
            }
        })
    }

    /// User feedback allows your users to give thumbs up/down responses in you chat panel. Use the feedback function when sending *message* feedback
    ///
    /// { "command": "POST", "type": "feedback", "conversation_id": String, "id": String, "value": FeedbackValue}
    /// - parameter id: The id on the payload element you are giving feedback on
    /// - parameter value: Value of the feedback
    fun feedback(id: String, value: FeedbackValue, listener: APIMessageResponseListener? = null) {
        val message = createPostMessage(Type.FEEDBACK)
        message.id = id
        message.value = Json.encodeToJsonElement(value)
        send(message, listener)
    }

    /// External links do not trigger a response from the server, but they should be sent for logging purposes. If a response
    /// contains a list of buttons (links), you can log with this function
    ///
    /// {"command": "POST", "type": "external_link", "conversation_id": String, "id": String}
    /// - parameter id: id on external link in payload
    fun urlButton(id: String, listener: APIMessageResponseListener? = null) {
        val message = createPostMessage(Type.EXTERNAL_LINK)
        message.id = id
        send(message, listener)
    }

    /// When the conversation state is awaiting_files, you can post this message to complete the entity extraction action
    /// TODO: Implement value
    ///
    /// {"command": "POST", "type": "files", "conversation_id": String, "value": [{ "filename": String, "mimetype": String, "url": String}]}
    /// - parameter files: Array of files
    fun sendFiles(files: List<File>, listener: APIMessageResponseListener? = null) {
        val message = createPostMessage(Type.FILES)

        // Hack to fix the fact that the upload service returns "mimeType" while the API endpoint
        // expects "mimetype" (all lowercase)
        val jsonFiles = files.map {
            "{\"filename\":\"${it.filename}\", \"mimetype\": \"${it.mimeType}\", \"url\": \"${it.url}\"}"
        }
        val jsonString = "[${jsonFiles.joinToString(", ")}]"

        message.value = Json.parseToJsonElement(jsonString)
        send(message, listener)
    }

    /// Use this request type to trigger action flow elements directly
    ///
    /// {"command": "POST", "type": "trigger_action", "conversation_id": String, "id": String}
    /// - parameter id: action id
    fun triggerAction(id: String, listener: APIMessageResponseListener? = null) {
        val message = createPostMessage(Type.TRIGGER_ACTION)
        message.id = id
        send(message, listener)
    }

    fun smartReply(value: String, listener: APIMessageResponseListener? = null) {
        send(CommandSmartReply(conversationId, userToken, value), listener)
    }

    fun humanChatPost(value: String, listener: APIMessageResponseListener? = null) {
        send(CommandHumanChatPost(conversationId, userToken, value), listener)
    }

    /// Inform the API that the client is typing
    /// - parameter value: The text the client has written so far in the textbox
    /// - Returns: ClientTyping including length and maxLength
    fun clientTyping(value: String): ClientTyping {
        if (isBlocked) {
            return ClientTyping(length = 0, maxLength = 0)
        }

        typing()
        return ClientTyping(length = value.length, maxLength = maxInputChars)
    }

    /// Feedback of the conversation
    ///
    ///  When a conversation ends, you might want to give the user the opportunity to give feedback on the conversation
    ///
    /// - parameter rating: 0 or 1. If above 1 it will be 1
    /// - parameter text: Optional text feedback
    fun conversationFeedback(rating: Int, text: String? = null, listener: APIMessageResponseListener? = null) {
        val feedback = CommandFeedbackValue(min(rating, 1), text)
        val message = CommandFeedback(conversationId, userToken, feedback)

        send(message, listener)
    }

    public fun loginEvent(userToken: String, listener: APIMessageResponseListener? = null) {
        send(CommandLoginEvent(conversationId, userToken), listener)
    }

    fun send(message: ICommand, listener: APIMessageResponseListener? = null, responseHandler: APIMessageResponseHandler? = null) {
        when (message) {
            is CommandPost -> {
                if (message.clean == null && this.clean) {
                    message.clean = true
                }
                message.filterValues = filterValues
            }
            is CommandResume -> {
                if (this.clean) {
                    message.clean = true
                }
            }
            is CommandStart -> {
                message.filterValues = filterValues
            }
        }

        // Remove class discriminator key (it makes the server backend throw a 400 error)
        val data = Json { classDiscriminator = "_type" }.encodeToJsonElement(message)
        val valuesWithoutType = data.jsonObject.filterNot { it.key == "_type" }
        val body = Json.encodeToJsonElement(valuesWithoutType)

        post(body, null, object : APIMessageResponseListener {
            override fun onFailure(exception: Exception) {
                publishResponse(null, exception)
                Handler(Looper.getMainLooper()).post {
                    listener?.onFailure(exception)
                }
            }

            override fun onResponse(apiMessage: APIMessage) {
                if (apiMessage.postedId != null && apiMessage.postedId > 0) {
                    apiMessage.postedId.toString().also { pollValue = it }
                }

                messages.add(apiMessage)
                handleApiMessage(apiMessage)
                publishResponse(apiMessage, null)

                Handler(Looper.getMainLooper()).post {
                    listener?.onResponse(apiMessage)
                }
            }
        }, responseHandler)
    }

    fun post(data: JsonElement, url: URL? = null, listener: APIMessageResponseListener? = null, responseHandler: APIMessageResponseHandler? = null) {
        val request = Request.Builder()
            .url(url ?: getChatUrl())
            .method("POST", data.toString().toRequestBody())
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace();
                Handler(Looper.getMainLooper()).post {
                    listener?.onFailure(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body!!.string()

                    if (response.isSuccessful) {
                        if (responseHandler != null) {
                             responseHandler.handleResponse(body, listener)
                        } else {
                            val apiMessage = chatbackendJson.decodeFromString<APIMessage>(body)

                            Handler(Looper.getMainLooper()).post {
                                listener?.onResponse(apiMessage)
                            }

                            handleJsonEvent(apiMessage)
                        }
                    } else {
                        try {
                            val exception = chatbackendJson.decodeFromString<SDKException>(body)
                            Handler(Looper.getMainLooper()).post {
                                listener?.onFailure(exception)
                            }
                        } catch (e: SerializationException) {
                            Handler(Looper.getMainLooper()).post {
                                listener?.onFailure(SDKSerializationException(body))
                            }
                        }
                    }
                } catch (e: SerializationException) {
                    e.printStackTrace()
                    Handler(Looper.getMainLooper()).post {
                        listener?.onFailure(e)
                    }
                }
            }
        })
    }

    fun download(userToken: String? = null, listener: APIMessageResponseListener? = null) {
        val message = CommandDownload(conversationId, userToken ?: this.userToken)
        val body = chatbackendJson.encodeToJsonElement(message)

        val responseListener = listener

        post(body, null, null, object : APIMessageResponseHandler {
            override fun handleResponse(body: String, listener: APIMessageResponseListener?) {
                val apiMessage = APIMessage()
                apiMessage.download = body

                Handler(Looper.getMainLooper()).post {
                    responseListener?.onResponse(apiMessage)
                }
            }
        })
    }

    // Local message only. Used to display action link clicks as user message bubbles
    fun userActionMessage(message: String) {
        val apiMessage = APIMessage(
            response = no.boostai.sdk.ChatBackend.Objects.Response.Response(
                id = UUID.randomUUID().toString(),
                SourceType.CLIENT,
                language = languageCode,
                elements = listOf(
                    Element(
                        payload = Payload(text = message),
                        type = ElementType.TEXT
                    )
                )
            )
        )

        messages.add(apiMessage)
        publishResponse(apiMessage, null)
    }

    fun handleApiMessage(apiMessage: APIMessage) {
        val conversation = apiMessage.conversation ?: throw SDKException("No conversation in response")

        this.conversationId = conversation.id
        this.reference = conversation.reference ?: this.reference
        val state = conversation.state
        this.allowDeleteConversation = state.allowDeleteConversation ?: this.allowDeleteConversation
        this.chatStatus = state.chatStatus
        this.isBlocked = state.isBlocked ?: false
        this.maxInputChars = state.maxInputChars ?: this.maxInputChars
        this.lastResponse = apiMessage
        this.pollValue = apiMessage.response?.id ?: (if (apiMessage.responses?.size ?: 0 > 0) apiMessage.responses?.last()?.id else null) ?: pollValue
        this.privacyPolicyUrl = conversation.state.privacyPolicyUrl ?: this.privacyPolicyUrl

        // Handling human poll
        if (state.poll != null) {
            if (state.poll != this.poll) {
                this.poll = state.poll
            }
        }

        this.poll = state.poll ?: this.poll

        if (state.poll != null && state.poll && Arrays.asList(ChatStatus.IN_HUMAN_CHAT_QUEUE, ChatStatus.ASSIGNED_TO_HUMAN).indexOf(conversation.state.chatStatus) != -1) {
            startPolling()
        } else {
            stopPolling()
            pollValue = null
        }

        // Handling change of van and language
        val response = apiMessage.response
        if (response != null) {
            this.languageCode = response.language
            if (this.vanId != response.vanId) {
                val newVanId = response.vanId
                if (newVanId != null) {
                    this.vanId = newVanId
                } else {
                    this.vanId = null
                }

                getConfig()
            }
        } else if ((apiMessage.responses?.count() ?: 0) > 0) {
            this.languageCode = apiMessage.responses!!.last().language
        }
    }

    fun uploadFilesToAPI(files: List<java.io.File>) {
        val endpointURL = fileUploadServiceEndpointUrl ?: return

        val formBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        files.forEach {
            formBuilder.addFormDataPart("files", it.name, it.asRequestBody("image/jpg".toMediaTypeOrNull()))
        }

        val b = formBuilder.build()

        val request = Request.Builder()
            .url(endpointURL)
            .post(b)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace();
                publishResponse(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body!!.string()

                    if (response.isSuccessful) {
                        val fileJson = chatbackendJson.decodeFromString<Files>(body)
                        sendFiles(fileJson.files)
                    }
                } catch (e: SerializationException) {
                    e.printStackTrace()
                    publishResponse(null, e)
                }
            }
        })
    }

    fun handleJsonEvent(apiMessage: APIMessage) {
        val messageResponses = apiMessage.responses?.let {
            ArrayList(it)
        } ?: ArrayList()

        apiMessage.response?.let {
            messageResponses.add(it)
        }

        messageResponses.forEach { r ->
            r.elements.forEach { element ->
                if (element.type == ElementType.JSON && element.payload.json is JsonObject) {
                    try {
                        val event =
                            chatbackendJson.decodeFromJsonElement<EmitEvent>(element.payload.json)
                        val emitEvent = event.emitEvent
                        Handler(Looper.getMainLooper()).post {
                            publishEvent(emitEvent.type, emitEvent.detail)
                        }
                    } catch (e: SerializationException) {}
                }
            }
        }
    }

    fun startPolling() {
        pollTimer?.cancel()

        pollTimer = Timer().scheduleAtFixedRate(pollInterval, pollInterval) {
            poll()
        }
    }

    fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }

    fun resetConversationState() {
        messages = ArrayList()
        conversationId = null
        reference = ""
        userToken = null
        lastResponse = null
    }

    fun publishResponse(message: APIMessage?, error: Exception?) {
        Handler(Looper.getMainLooper()).post {
            messageObservers.forEach { observer ->
                if (message != null) {
                    observer.get()?.onMessageReceived(this, message)
                } else if (error != null) {
                    observer.get()?.onFailure(this, error)
                }
            }
        }
    }

    fun publishConfigUpdate(config: ChatConfig?) {
        if (config == null) return

        Handler(Looper.getMainLooper()).post {
            configObservers.forEach { observer ->
                observer.get()?.onConfigReceived(this, config)
            }
        }
    }

    fun publishEvent(type: String, detail: JsonElement?) {
        Handler(Looper.getMainLooper()).post {
            eventObservers.forEach { observer ->
                observer.get()?.onBackendEventReceived(this, type, detail)
            }
        }
    }

    fun createPostMessage(type: Type): CommandPost {
        return CommandPost(
            conversationId,
            userToken,
            type,
            skill = skill,
            customPayload = customPayload)
    }

    fun addMessageObserver(observer: MessageObserver) {
        messageObservers.add(WeakReference(observer))
    }

    fun removeMessageObserver(observer: MessageObserver) {
        var remove: WeakReference<MessageObserver>? = null
        messageObservers.forEach {
            if (it.get() == observer) {
                remove = it
            }
        }

        if (remove != null) {
            messageObservers.remove(remove)
        }
    }

    fun addConfigObserver(observer: ConfigObserver) {
        configObservers.add(WeakReference(observer))
    }

    fun removeConfigObserver(observer: ConfigObserver) {
        var remove: WeakReference<ConfigObserver>? = null
        configObservers.forEach {
            if (it.get() == observer) {
                remove = it
            }
        }

        if (remove != null) {
            configObservers.remove(remove)
        }
    }

    fun addEventObserver(observer: EventObserver) {
        eventObservers.add(WeakReference(observer))
    }

    fun removeEventObserver(observer: EventObserver) {
        var remove: WeakReference<EventObserver>? = null

        eventObservers.forEach {
            if (it.get() == observer) {
                remove = it
            }
        }

        if (remove != null) {
            eventObservers.remove(remove)
        }
    }

    interface ConfigReadyListener {
        fun onFailure(exception: Exception)
        fun onReady(config: ChatConfig)
    }

    interface APIMessageResponseListener {
        fun onFailure(exception: Exception)
        fun onResponse(apiMessage: APIMessage)
    }

    interface APIMessageResponseHandler {
        fun handleResponse(body: String, listener: APIMessageResponseListener? = null)
    }

    interface SDKObserver {
        fun onFailure(backend: ChatBackend, error: Exception)
    }

    interface EventObserver {
        fun onBackendEventReceived(backend: ChatBackend, type: String, detail: JsonElement?)
    }

    interface MessageObserver: SDKObserver {
        fun onMessageReceived(backend: ChatBackend, message: APIMessage)
    }

    interface ConfigObserver: SDKObserver {
        fun onConfigReceived(backend: ChatBackend, config: ChatConfig)
    }
}