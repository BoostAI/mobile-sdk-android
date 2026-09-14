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

package no.boostai.sdk.ChatBackend.Objects.Response

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.boostai.sdk.ChatBackend.Objects.DateAsISO8601Serializer
import java.util.*

/// Status of current chat
///
/// - virtual_agent: Chat is in virtual agent mode
/// - in_human_chat_queue: Chat is in human chat queue
/// - assigned_to_human: Chat is assigned to human
@Serializable(with = ChatStatusSerializer::class)
enum class ChatStatus {
    /// virtual_agent Chat is in virtual agent mode
    @SerialName("virtual_agent")
    VIRTUAL_AGENT,

    /// in_human_chat_queue
    @SerialName("in_human_chat_queue")
    IN_HUMAN_CHAT_QUEUE,

    /// assigned_to_human
    @SerialName("assigned_to_human")
    ASSIGNED_TO_HUMAN,

    /// A status this SDK version doesn't know. Kept distinct from VIRTUAL_AGENT so the polling
    /// decision can trust the response's own `poll` flag instead of assuming no human is involved.
    UNKNOWN
}

// A server value these enums don't know yet must not make the whole response undecodable — one
// new enum value would otherwise turn every subsequent message into onFailure and kill the
// conversation. Same pattern as ElementTypeSerializer below: unknown values map to a safe default.
@Serializer(forClass = ChatStatus::class)
object ChatStatusSerializer {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ChatStatus", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): ChatStatus = try {
        ChatStatus.valueOf(decoder.decodeString().uppercase())
    } catch (e: Exception) {
        // UNKNOWN, not VIRTUAL_AGENT: a new server-side status is likelier to appear on the
        // human-chat side, and mapping it to "no human involved" would silently stop polling
        ChatStatus.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: ChatStatus) {
        encoder.encodeString(value.name.lowercase())
    }
}

/// Possible values of response.source
@Serializable(with = SourceTypeSerializer::class)
enum class SourceType {
    @SerialName("bot")
    BOT,
    @SerialName("client")
    CLIENT
}

@Serializer(forClass = SourceType::class)
object SourceTypeSerializer {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SourceType", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): SourceType = try {
        SourceType.valueOf(decoder.decodeString().uppercase())
    } catch (e: Exception) {
        SourceType.BOT
    }
    override fun serialize(encoder: Encoder, value: SourceType) {
        encoder.encodeString(value.name.lowercase())
    }
}

@Serializable(with = LinkTypeSerializer::class)
enum class LinkType {
    @SerialName("action_link")
    ACTION_LINK,
    @SerialName("external_link")
    EXTERNAL_LINK
}

@Serializer(forClass = LinkType::class)
object LinkTypeSerializer {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LinkType", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LinkType = try {
        LinkType.valueOf(decoder.decodeString().uppercase())
    } catch (e: Exception) {
        LinkType.ACTION_LINK
    }
    override fun serialize(encoder: Encoder, value: LinkType) {
        encoder.encodeString(value.name.lowercase())
    }
}

/// Types an element result can have
///
/// When receiving data this indicates how your client should render their data.
/// - text
/// - html
/// - image
/// - video
/// - json
/// - links
@Serializable(with = ElementTypeSerializer::class)
enum class ElementType {
    @SerialName("text")
    TEXT,
    @SerialName("html")
    HTML,
    @SerialName("image")
    IMAGE,
    @SerialName("video")
    VIDEO,
    @SerialName("json")
    JSON,
    @SerialName("links")
    LINKS,
    UNKNOWN
}

// Handle unknown ElementType case
@Serializer(forClass = ElementType::class)
object ElementTypeSerializer {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("ElementType", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): ElementType {
        // If we don't have the element type provided by JSON, set it to UNKNOWN
        return try {
            ElementType.valueOf(decoder.decodeString().uppercase())
        } catch (e: Exception) {
            ElementType.UNKNOWN
        }
    }
    override fun serialize(encoder: Encoder, value: ElementType) {
        encoder.encodeString(value.name)
    }
}

@Serializable(with = FunctionTypeSerializer::class)
enum class FunctionType {
    APPROVE,
    DENY,

    /// A function this SDK version doesn't know; treated as a plain link
    UNKNOWN
}

@Serializer(forClass = FunctionType::class)
object FunctionTypeSerializer {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FunctionType", PrimitiveKind.STRING)
    // Case-insensitive: the enum has no @SerialName, so only the exact strings "APPROVE"/"DENY"
    // used to match — a lowercase wire value made the whole message undecodable
    override fun deserialize(decoder: Decoder): FunctionType = try {
        FunctionType.valueOf(decoder.decodeString().uppercase())
    } catch (e: Exception) {
        FunctionType.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: FunctionType) {
        encoder.encodeString(value.name)
    }
}

/**
Response from an interactive conversation
 */
@Serializable
@Parcelize
data class Response (
    /// The id of the response
    val id: String?,
    /// The source of the response. Either "bot" or "client"
    val source: SourceType,
    /// bcp-47 code
    val language: String,
    /// A list of response elements
    val elements: List<Element>,
    /// Avatar URL if the mssage is from Human Chat
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    /// Date of the response
    @SerialName("date_created")
    @Serializable(with = DateAsISO8601Serializer::class)
    val dateCreated: Date? = null,
    /// Message feedback
    val feedback: String? = null,
    /// Server URL used by the chat client
    @SerialName("source_url")
    val sourceUrl: String? = null,
    /// The text of a link that was clicked by an end-user
    @SerialName("link_text")
    val linkText: String? = null,
    val error: String? = null,
    /// Change of van id
    @SerialName("van_id")
    val vanId: Int? = null,
    val isTempId: Boolean = false
) : Parcelable

@Serializable
@Parcelize
data class APIMessage (
    /// Conversation object
    val conversation: ConversationResult? = null,
    /// Response from an interactive conversation
    val response: Response? = null,
    /// List of historic `Response` objects
    val responses: ArrayList<Response>? = null,
    /// Response from a SMARTREPLY call
    @SerialName("smart_reply")
    val smartReplies: SmartReply? = null,
    @SerialName("posted_id")
    val postedId: Int? = null,
    /// Extra variable to be used with the download command. You will get the result as a String in this variable
    var download: String? = null
) : Parcelable

@Serializable
@Parcelize
data class Element (
    /// Element data
    val payload: Payload,
    /// The data type of the response
    val type: ElementType = ElementType.UNKNOWN
) : Parcelable

/**
Generic JSON card
 */
@Serializable
@Parcelize
data class GenericCard (
    val body: TextContent? = null,
    val heading: TextContent? = null,
    val image: Image? = null,
    val link: Link? = null,
    val template: String? = null
) : Parcelable {

    @Serializable
    @Parcelize
    data class TextContent (
        val text: String
    ) : Parcelable

    @Serializable
    @Parcelize
    data class Image (
        val url: String?,
        val alt: String? = null,
        val position: String? = null,
    ) : Parcelable

    @Serializable
    @Parcelize
    data class Link (
        val text: String?,
        val url: String
    ) : Parcelable
}

/**
Serializer for buttonActionId which can be a string, int, or object {"id": 3}
 */
object ButtonActionIdSerializer : kotlinx.serialization.KSerializer<String?> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("ButtonActionId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder ?: return null
        return when (val element = jsonDecoder.decodeJsonElement()) {
            // JsonNull IS a JsonPrimitive, and its content is the string "null" — matched first
            // so an explicit `"buttonActionId": null` doesn't turn into a phantom action id
            is kotlinx.serialization.json.JsonNull -> null
            is kotlinx.serialization.json.JsonPrimitive -> element.content
            is kotlinx.serialization.json.JsonObject -> element["id"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { p -> p !is kotlinx.serialization.json.JsonNull }?.content
            }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) encoder.encodeString(value) else encoder.encodeNull()
    }
}

/**
Carousel element within a carousel template
 */
@Serializable
@Parcelize
data class CarouselElement(
    val id: String? = null,
    val title: String? = null,
    val body: String? = null,
    val imageUrl: String? = null,
    val imageAltText: String? = null,
    val buttonText: String? = null,
    val buttonHref: String? = null,
    @Serializable(with = ButtonActionIdSerializer::class)
    val buttonActionId: String? = null
) : Parcelable

/**
Carousel JSON template
 */
@Serializable
@Parcelize
data class CarouselTemplate(
    val template: String? = null,
    val elements: List<CarouselElement> = emptyList(),
    val emitEvent: @WriteWith<JsonElementParceler> JsonElement? = null
) : Parcelable

@Serializable
@Parcelize
data class Payload (
    val html: String? = null,
    val text: String? = null,
    val url: String? = null,  // Video: youtube, vimeo, wistia
    val source: String? = null,
    val fullScreen: Boolean? = null,
    val json: @WriteWith<JsonElementParceler> JsonElement? = null,
    val links: ArrayList<Link>? = null,
    val style: String? = null
) : Parcelable

/**
Conversation object
 */
@Serializable
@Parcelize
data class ConversationResult (
    /// Identifies the conversation
    val id: String?,
    val reference: String?,
    /// Conversation state object
    val state: ConversationState
) : Parcelable

/**
Conversation state object
 */
@Serializable
@Parcelize
data class ConversationState (
    /// One of `ChatStatus`
    @SerialName("chat_status")
    val chatStatus: ChatStatus,
    /// When true, the conversation is blocked
    @SerialName("is_blocked")
    val isBlocked: Boolean? = null,
    /// Identifier for the user-user, if authenticated
    @SerialName("authenticated_user_id")
    val authenticatedUserId: String? = null,
    /// Conversation id used before the user was authenticated
    @SerialName("unauth_conversation_id")
    val unauthConversationId: String? = null,
    /// Privacy policy URL
    @SerialName("privacy_policy_url")
    val privacyPolicyUrl: String? = null,
    /// If true, the DELETE command is operational
    @SerialName("allow_delete_conversation")
    val allowDeleteConversation: Boolean? = null,
    /// Should we allow file upload in human chat?
    @SerialName("allow_human_chat_file_upload")
    val allowHumanChatFileUpload: Boolean? = null,
    /// Wether the conversation is in Human Chat state. The client should POLL the server for more data. The SDK will handle this automatically.
    val poll: Boolean? = null,
    /// true if human is typing in Human Chat
    @SerialName("human_is_typing")
    val humanIsTyping: Boolean? = null,
    /// Maximum characters allowed in a text POST. Overflow will result in an error on message()
    @SerialName("max_input_chars")
    val maxInputChars: Int? = null,
    /// A string containing the skill set on the predicted intent
    val skill: String? = null,
    ///Present when an upload file entity extraction has been triggered
    @SerialName("awaiting_files")
    val awaitingFiles: ConversationStateFiles? = null
) : Parcelable

@Serializable
@Parcelize
data class Link (
    val id: String,
    val text: String,
    val type: LinkType,
    val function: FunctionType? = null,
    val question: String? = null,
    val url: String? = null,
    @SerialName("is_attachment")
    val isAttachment: Boolean? = null,
    @SerialName("van_base_url")
    val vanBaseUrl: String? = null,
    @SerialName("van_name")
    val vanName: String? = null,
    @SerialName("van_organization")
    val vanOrganization: String? = null
) : Parcelable

@Serializable
@Parcelize
data class SmartReply (
    @SerialName("important_words") val importantWords: SmartReplySmartWords,
    val va: List<SmartReplyVa>
) : Parcelable

@Serializable
@Parcelize
data class SmartReplyVa (
    val links: List<Link>,
    val messages: List<String>,
    val score: Int,
    val subTitle: String
) : Parcelable

@Serializable
@Parcelize
data class SmartReplySmartWords (
    val original: List<String>,
    val processed: List<String>
) : Parcelable

@Serializable
@Parcelize
data class ConversationStateFiles (
    @SerialName("accepted_types") val acceptedTypes: List<String>?,
    @SerialName("max_number_of_files") val maxNumberOfFiles: Int?
) : Parcelable


@Serializable
data class APIResponseError(val error: String)

@Serializable
open class SDKException : Exception {
    // The overriding property shadows Throwable's message, so each constructor has to assign it
    // too — with only the super() call, SDKException("reason").message was always "" and the
    // reason was lost.
    @SerialName("error")
    override var message: String = ""

    constructor() : super()
    constructor(message: String) : super(message) {
        this.message = message
    }
    constructor(message: String, cause: Throwable?) : super(message, cause) {
        this.message = message
    }
    constructor(cause: Throwable) : super(cause) {
        this.message = cause.toString()
    }
}

/**
 * A response the SDK could not make sense of — an HTTP error without a usable `error` field, or
 * a body that failed to decode. [body] carries the raw response for diagnostics; [message] is a
 * short description, never the body itself, since hosts routinely show `localizedMessage` to the
 * user.
 */
open class SDKSerializationException(
    val body: String = "",
    cause: Throwable? = null
) : SDKException("Unexpected response from the server", cause)

object JsonElementParceler : Parceler<JsonElement?> {
    // Null-safe in both directions: `toString()` on a null receiver used to write the string
    // "null", which read back as JsonNull — a parcel round-trip flipped `x != null` from false
    // to true.
    override fun create(parcel: Parcel) =
        parcel.readString()?.let { Json.parseToJsonElement(it) }

    override fun JsonElement?.write(parcel: Parcel, flags: Int) =
        parcel.writeString(this?.toString())
}