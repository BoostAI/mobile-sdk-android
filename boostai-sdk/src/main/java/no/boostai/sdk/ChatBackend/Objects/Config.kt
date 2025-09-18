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

package no.boostai.sdk.ChatBackend.Objects
import android.graphics.Color
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

// Handle hex color serialization
@Serializer(forClass = Int::class)
object HexColorSerializer {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("HexColor", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Int {
        // If we don't have the element type provided by JSON, set it to UNKNOWN
        return try {
            Color.parseColor(decoder.decodeString())
        } catch (e: Exception) {
            0
        }
    }
    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeString(java.lang.String.format("#%06X", 0xFFFFFF and value))
    }
}

@Serializable
@Parcelize
class ConfigV2 (
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var primaryColor: Int? = null,
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var contrastColor: Int? = null,
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var clientMessageBackground: Int? = null,
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var clientMessageColor: Int? = null,
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var serverMessageBackground: Int? = null,
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var serverMessageColor: Int? = null,
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var linkBelowBackground: Int? = null,
    @Serializable(with = HexColorSerializer::class)
    @ColorInt var linkBelowColor: Int? = null,
    var avatarStyle: AvatarStyle? = null,
    var linkDisplayStyle: LinkDisplayStyle? = null,
    var requestConversationFeedback: Boolean? = false,
    var rememberConversation: Boolean? = false,
    var rememberConversationExpirationDuration: String? = null,
    var removeRememberedConversationOnChatPanelClose: Boolean? = null,
    var fileUploadServiceEndpointUrl: String? = null,
    var fileExpirationSeconds: Int? = null,
    var filters: List<Filter>? = null,
    var pace: ConversationPace? = null,
    var messages: Map<String, Messages>? = null
) : Parcelable

@Serializable
@Parcelize
data class Messages (
    val back: String = "Back",

    @SerialName("close.window")
    val closeWindow: String = "Close",

    @SerialName("compose.characters.used")
    val composeCharactersUsed: String = "{0} out of {1} characters used",

    @SerialName("compose.placeholder")
    val composePlaceholder: String = "Type in here",

    @SerialName("delete.conversation")
    val deleteConversation: String = "Delete conversation",

    @SerialName("download.conversation")
    val downloadConversation: String = "Download conversation",

    @SerialName("feedback.placeholder")
    val feedbackPlaceholder: String = "Write in your feedback here",

    @SerialName("feedback.prompt")
    val feedbackPrompt: String = "Do you want to give me feedback?",

    @SerialName("feedback.thumbs.down")
    val feedbackThumbsDown: String = "Not satisfied with conversation",

    @SerialName("feedback.thumbs.up")
    val feedbackThumbsUp: String = "Satisfied with conversation",

    @SerialName("filter.select")
    val filterSelect: String = "Select user group",

    @SerialName("header.text")
    val headerText: String = "Conversational AI",

    @SerialName("logged.in")
    val loggedIn: String = "Secure chat",

    @SerialName("message.thumbs.down")
    val messageThumbsDown: String = "Not satisfied with answer",

    @SerialName("message.thumbs.up")
    val messageThumbsUp: String = "Satisfied with answer",

    @SerialName("minimize.window")
    val minimizeWindow: String = "Minimize window",

    @SerialName("open.menu")
    val openMenu: String = "Open menu",

    @SerialName("opens.in.new.tab")
    val opensInNewTab: String = "Opens in new tab",

    @SerialName("privacy.policy")
    val privacyPolicy: String = "Privacy policy",

    @SerialName("submit.feedback")
    val submitFeedback: String = "Send",

    @SerialName("submit.message")
    val submitMessage: String = "Send",

    @SerialName("text.too.long")
    val textTooLong: String = "The message cannot be longer than {0} characters",

    @SerialName("upload.file")
    val uploadFile: String = "Upload image",

    @SerialName("upload.file.error")
    val uploadFileError: String = "Upload failed",

    @SerialName("upload.file.progress")
    val uploadFileProgress: String = "Uploading ...",

    @SerialName("upload.file.success")
    val uploadFileSuccess: String = "Upload successful",

    val chatServiceUnavailable: String = "Chat service is currently unavailable.\nPlease try again later.",

    val retry: String = "Retry"
) : Parcelable

@Serializable
@Parcelize
data class Filter (
    val id: Int,
    val title: String,
    val values: List<String>
) : Parcelable

@Serializable
enum class AvatarShape {
    @SerialName("rounded")
    ROUNDED,
    @SerialName("squared")
    SQUARED
}

typealias AvatarStyle = AvatarShape

@Serializable
enum class ConversationPace {
    @SerialName("glacial")
    GLACIAL,
    @SerialName("slower")
    SLOWER,
    @SerialName("slow")
    SLOW,
    @SerialName("normal")
    NORMAL,
    @SerialName("fast")
    FAST,
    @SerialName("faster")
    FASTER,
    @SerialName("supersonic")
    SUPERSONIC
}

@Serializable
enum class LinkDisplayStyle {
    @SerialName("below")
    BELOW,
    @SerialName("inside")
    INSIDE
}

@Serializable
@Parcelize
data class ConfigV3 (
    val messages: Map<String, Messages>? = null,
    val chatPanel: ChatPanel? = null,
) : Parcelable

@Serializable
@Parcelize
data class ChatPanel (
    /// Panel header styling
    val header: Header? = null,

    /// Chat panel styling
    val styling: Styling? = null,

    /// General settings related to the chat
    val settings: Settings? = null,
) : Parcelable

@Serializable
enum class ButtonType {
    /// Default button style
    @SerialName("button")
    BUTTON,

    /// Inline <li> list with marker on the left
    @SerialName("bullet")
    BULLET
}

@Serializable
@Parcelize
data class Fonts (
    /// Font used for body text (must be an R.font reference id)
    @FontRes val bodyFont: Int? = null,

    /// Font used for headlines (must be an R.font reference id)
    @FontRes val headlineFont: Int? = null,

    /// Font used for footnote sized strings (status messages, character count text etc. – must be an R.font reference id)
    @FontRes val footnoteFont: Int? = null,

    /// Font used for menu titles (must be an R.font reference id)
    @FontRes val menuItemFont: Int? = null
) : Parcelable

@Serializable
@Parcelize
data class Header (
    val filters: Filters? = null,

    /// Sets the title of the chat window.
    /// Will override the value from the Admin Panel.
    val title: String? = null,

    /// Should we hide the minimize button?
    val hideMinimizeButton: Boolean? = null,

    /// Should we hide the menu button?
    val hideMenuButton: Boolean? = null,
) : Parcelable

/*
@Serializable
enum class MinimizeButtonOptions {
    @SerialName("always")
    ALWAYS,
    @SerialName("never")
    NEVER,
    @SerialName("mobile")
    MOBILE
}
*/

@Serializable
@Parcelize
data class Filters (
    /// An array or string of action filter values.
    /// See the chapter on action filters for more information on this feature.
    val filterValues: List<String>? = null,

    /// An array of filter objects
    val options: List<Filter>? = null,
) : Parcelable

@Serializable
@Parcelize
data class Styling (
    /// Configures the speed with which replies are shown to the user.
    /// Valid values: GLACIAL, SLOWER, SLOW, NORMAL, FAST, FASTER, SUPERSONIC
    val pace: ConversationPace? = null,

    /// Avatar shape. Valid values: ROUNDED, SQUARED
    val avatarShape: AvatarShape? = null,

    /// Hide the avatar if needed
    val hideAvatar: Boolean? = null,

    /// Color for header bar and menu background.
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val primaryColor: Int? = null,

    /// Color for header text and text input outline.
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val contrastColor: Int? = null,

    /// Background color for the chat panel
    @ColorInt val panelBackgroundColor: Int? = null,

    /// Disable style changes when transferring VA in a VAN network
    //@Serializable
    //val disableVanStylingChange: Boolean? = null,

    /// See `ChatBubbles` definition
    val chatBubbles: ChatBubbles? = null,

    /// See `Buttons` definition
    val buttons: Buttons? = null,

    /// See `Composer` definition
    val composer: Composer? = null,

    /// See `MessageFeedback` definition
    val messageFeedback: MessageFeedback? = null,

    /// See `Fonts` definition
    val fonts: Fonts? = null
) : Parcelable

@Serializable
@Parcelize
data class ChatBubbles (
    /// Background color for client messages
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val userBackgroundColor: Int? = null,

    /// Color of text in client messages
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val userTextColor: Int? = null,

    /// Background color for virtual agent messages
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val vaBackgroundColor: Int? = null,

    /// Color of text from the virtual agent
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val vaTextColor: Int? = null,

    /// Color of dots showing VA or human operator is typing
    //@Serializable(with = HexColorSerializer::class)
    @ColorInt val typingDotColor: Int? = null,

    /// Background color of dots showing VA or human operator is typing
    //@Serializable(with = HexColorSerializer::class)
    @ColorInt val typingBackgroundColor: Int? = null,
) : Parcelable

@Serializable
@Parcelize
data class Buttons (
    /// Background color for links and buttons
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val backgroundColor: Int? = null,

    /// Background color when focused
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val focusBackgroundColor: Int? = null,

    /// Text color when focused
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val focusTextColor: Int? = null,

    /// Allow multiline text in buttons? Default false.
    val multiline: Boolean? = null,

    /// Text color for links and buttons
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val textColor: Int? = null,

    /** Display type of button
     *
     * Valid values:
     * BUTTON (default button style),
     * BULLET (inline <li> list with marker on the left)
     */
    val variant: ButtonType? = null,
) : Parcelable

@Serializable
@Parcelize
data class MessageFeedback (
    /// Hide message feedback? Default false.
    val hide: Boolean? = null,

    /// Outline color of thumbs up/down
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val outlineColor: Int? = null,

    /// Color of thumbs up/down when selected
    @Serializable(with = HexColorSerializer::class)
    @ColorInt val selectedColor: Int? = null,
) : Parcelable

@Serializable
@Parcelize
data class Composer (
    /// Hide composer
    val hide: Boolean? = null,

    /// Color of the text that says how many characters have been typed (i.e. "5 / 130")
    @ColorInt val composeLengthColor: Int? = null,

    /// Disabled color of the number of characters typed
    //@ColorInt val composeLengthDisabledColor: Int? = null,

    /// Color of the file upload button
    @ColorInt val fileUploadButtonColor: Int? = null,

    /// Background color of the frame around the composer. The “frame” is everything below the
    /// topBorder  – the container around the text area (gray by default).
    @ColorInt val frameBackgroundColor: Int? = null,

    /// Color of the send button
    @ColorInt val sendButtonColor: Int? = null,

    /// Color of send button when disabled
    @ColorInt val sendButtonDisabledColor: Int? = null,

    /// Input text area background color
    @ColorInt val textareaBackgroundColor: Int? = null,

    /// Input text area border color
    @ColorInt val textareaBorderColor: Int? = null,

    /// Text area border color
    @ColorInt val textareaFocusBorderColor: Int? = null,

    /// Input text area outline color
    @ColorInt val textareaFocusOutlineColor: Int? = null,

    /// Input text area text color
    @ColorInt val textareaTextColor: Int? = null,

    /// Input text area placeholder color
    @ColorInt val textareaPlaceholderTextColor: Int? = null,

    /// Top border color
    @ColorInt val topBorderColor: Int? = null,

    /// Top border color when focused
    @ColorInt val topBorderFocusColor: Int? = null,
) : Parcelable

@Serializable
@Parcelize
data class Settings (
    /// Action to trigger instead of the welcome message when the chat window opens and the user is authenticated
    val authStartTriggerActionId: Int? = null,

    /// Initial context topic for the chat.
    val contextTopicIntentId: Int? = null,

    /** Conversation ID. If provided, the chat window will attempt to resume the conversation with
     *  the given ID.
     *  If a conversation with the provided ID does not exist, a new conversation with a new ID will
     *  be generated. Note that in this case, the provided ID will not be used. To know the current
     *  conversation ID, listen to the `conversationIdChanged` event.
     */
    val conversationId: String? = null,

    /// Custom payload to send for each request
    @IgnoredOnParcel
    val customPayload: JsonElement? = null,

    /// The endpoint to upload files
    val fileUploadServiceEndpointUrl: String? = null,

    // Expiry time for file uploads
    val fileExpirationSeconds: Int? = null,

    /// Enable or disable thumbs up or down in the welcome message. Default false.
    val messageFeedbackOnFirstAction: Boolean? = null,

    /// Whether the app should remember conversation when closed (to resume on next launch).
    /// Default false
    val rememberConversation: Boolean? = null,

    /// How long should the app should remember conversations
    val rememberConversationExpirationDuration: String? = null,

    /// Whether the app should remove remembered conversation when panel is closed.
    /// Default false
    val removeRememberedConversationOnChatPanelClose: Boolean? = null,

    /// Whether the user should be asked for feedback when they close the panel. Default true.
    val requestFeedback: Boolean? = null,

    /// Whether to show clicked links as new messages (appears as sent from client)
    val showLinkClickAsChatBubble: Boolean? = null,

    /// Sets the Human Chat skill for the conversation
    val skill: String? = null,

    /// Preferred BCP47 language for welcome message. Examples: 'en-US', 'fr-FR' and 'sv-SE'.
    /// Default language as configured in Admin Panel.
    val startLanguage: String? = null,

    /// If an invalid conversation Id is provided, start a new conversation. Default true
    val startNewConversationOnResumeFailure: Boolean? = null,

    /// Action to trigger instead of the welcome message when the chat window opens
    val startTriggerActionId: Int? = null,

    /// Should we trigger action on resume (requires a startTriggerActionId to be set). Default false.
    val triggerActionOnResume: Boolean? = null,

    /// Sets the user token generator for authenticated conversations
    val userToken: String? = null,

    /// Should we skip displaying the welcome message?
    val skipWelcomeMessage: Boolean? = null
) : Parcelable

class ChatPanelDefaults {
    class Styling {
        companion object {
            val avatarShape = AvatarShape.ROUNDED
            val pace = ConversationPace.NORMAL
            //val disableVanStylingChange = false
        }

        class Buttons {
            companion object {
                val variant = ButtonType.BUTTON
            }
        }

        class Composer {
            companion object {
                const val hide = false
            }
        }

        class MessageFeedback {
            companion object {
                const val hide = false
            }
        }
    }

    class Settings {
        companion object {
            const val messageFeedbackOnFirstAction = false
            const val rememberConversation = false
            const val removeRememberedConversationOnChatPanelClose = false
            const val requestFeedback = true
            const val showLinkClickAsChatBubble = false
            const val startNewConversationOnResumeFailure = true
            const val triggerActionOnResume = false
        }
    }
}

fun convertConfig(configV2: ConfigV2): ConfigV3 {
    fun convertButtonVariant(configV2Style: LinkDisplayStyle?): ButtonType {
        return when (configV2Style) {
            LinkDisplayStyle.INSIDE -> ButtonType.BULLET
            LinkDisplayStyle.BELOW -> ButtonType.BUTTON
            else -> ButtonType.BUTTON
        }
    }

    return ConfigV3(
        messages = configV2.messages,
        chatPanel = ChatPanel(
            header = Header(
                filters = Filters(
                    options = configV2.filters
                ),
            ),
            styling = Styling(
                avatarShape = configV2.avatarStyle,
                primaryColor = configV2.primaryColor,
                contrastColor = configV2.contrastColor,
                pace = configV2.pace,
                chatBubbles = ChatBubbles(
                    userBackgroundColor = configV2.clientMessageBackground,
                    userTextColor = configV2.clientMessageColor,
                    vaBackgroundColor = configV2.serverMessageBackground,
                    vaTextColor = configV2.serverMessageColor,
                ),
                buttons = Buttons(
                    variant = convertButtonVariant(configV2.linkDisplayStyle),
                    backgroundColor = configV2.linkBelowBackground,
                    textColor = configV2.linkBelowColor,
                )
            ),
            settings = Settings(
                fileUploadServiceEndpointUrl = configV2.fileUploadServiceEndpointUrl,
                fileExpirationSeconds = configV2.fileExpirationSeconds,
                requestFeedback = configV2.requestConversationFeedback,
                rememberConversation = configV2.rememberConversation,
                rememberConversationExpirationDuration = configV2.rememberConversationExpirationDuration,
                removeRememberedConversationOnChatPanelClose = configV2.removeRememberedConversationOnChatPanelClose,
            )
        )
    )
}

typealias ChatConfig = ConfigV3