package no.boostai.sdk

import kotlinx.serialization.decodeFromString
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.AvatarShape
import no.boostai.sdk.ChatBackend.Objects.ConversationPace
import no.boostai.sdk.ChatBackend.Objects.LinkDisplayStyle
import no.boostai.sdk.ChatBackend.Objects.Response.ChatStatus
import no.boostai.sdk.ChatBackend.Objects.Response.FunctionType
import no.boostai.sdk.ChatBackend.Objects.Response.LinkType
import no.boostai.sdk.ChatBackend.Objects.Response.Response
import no.boostai.sdk.ChatBackend.Objects.Response.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A server value the SDK doesn't know yet must not make a whole response or config undecodable —
 * one new enum value used to turn every subsequent message into onFailure and kill the
 * conversation.
 */
class EnumFallbackTest {

    private val json = ChatBackend.chatbackendJson

    @Test
    fun unknownValuesFallBackInsteadOfThrowing() {
        // UNKNOWN, not VIRTUAL_AGENT: the polling decision trusts the response's own poll flag
        // for statuses the SDK doesn't know
        assertEquals(
            ChatStatus.UNKNOWN,
            json.decodeFromString<ChatStatus>("\"escalated_to_supervisor\"")
        )
        assertEquals(SourceType.BOT, json.decodeFromString<SourceType>("\"system\""))
        assertEquals(LinkType.ACTION_LINK, json.decodeFromString<LinkType>("\"magic_link\""))
        assertEquals(FunctionType.UNKNOWN, json.decodeFromString<FunctionType>("\"reject\""))
        assertEquals(AvatarShape.ROUNDED, json.decodeFromString<AvatarShape>("\"hexagonal\""))
        assertEquals(
            ConversationPace.NORMAL,
            json.decodeFromString<ConversationPace>("\"ludicrous\"")
        )
        assertEquals(
            LinkDisplayStyle.BELOW,
            json.decodeFromString<LinkDisplayStyle>("\"floating\"")
        )
    }

    @Test
    fun knownValuesStillMap() {
        assertEquals(
            ChatStatus.ASSIGNED_TO_HUMAN,
            json.decodeFromString<ChatStatus>("\"assigned_to_human\"")
        )
        assertEquals(SourceType.CLIENT, json.decodeFromString<SourceType>("\"client\""))
        assertEquals(LinkType.EXTERNAL_LINK, json.decodeFromString<LinkType>("\"external_link\""))
        assertEquals(AvatarShape.SQUARED, json.decodeFromString<AvatarShape>("\"squared\""))
    }

    /** FunctionType had no @SerialName, so only the exact uppercase strings matched. */
    @Test
    fun functionTypeIsCaseInsensitive() {
        assertEquals(FunctionType.APPROVE, json.decodeFromString<FunctionType>("\"approve\""))
        assertEquals(FunctionType.DENY, json.decodeFromString<FunctionType>("\"DENY\""))
    }

    /** The realistic shape: a full response whose state carries an unknown chat status. */
    @Test
    fun aResponseWithUnknownEnumValuesStillDecodes() {
        val response = json.decodeFromString<Response>(
            """
            {
              "id": "1",
              "source": "supervisor",
              "language": "en-US",
              "elements": [
                {"type": "text", "payload": {"text": "hi"}},
                {"type": "links", "payload": {"links": [
                  {"id": "a", "text": "Go", "type": "portal_link", "function": "escalate"}
                ]}}
              ]
            }
            """.trimIndent()
        )
        assertEquals(SourceType.BOT, response.source)
        assertEquals(LinkType.ACTION_LINK, response.elements[1].payload.links?.first()?.type)
        assertEquals(FunctionType.UNKNOWN, response.elements[1].payload.links?.first()?.function)
    }
}
