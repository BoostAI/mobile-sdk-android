package no.boostai.sdk

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.boostai.sdk.ChatBackend.ChatBackend
import no.boostai.sdk.ChatBackend.Objects.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The FILES payload used to be assembled by string interpolation, so a filename containing a
 * quote or backslash — perfectly legal from the system file picker — produced malformed JSON and
 * crashed on the main thread.
 */
class SendFilesPayloadTest {

    @Test
    fun filenamesWithQuotesAndBackslashesSurvive() {
        val hostileName = "report \"final\" \\ v2.pdf"
        val json = ChatBackend.filesAsJson(
            listOf(File(hostileName, "application/pdf", "https://example.com/f/1"))
        ).jsonArray

        val file = json.single().jsonObject
        assertEquals(hostileName, file.getValue("filename").jsonPrimitive.content)
        // The API endpoint expects all-lowercase "mimetype", unlike the upload service's response
        assertEquals("application/pdf", file.getValue("mimetype").jsonPrimitive.content)
        assertEquals("https://example.com/f/1", file.getValue("url").jsonPrimitive.content)
    }
}
