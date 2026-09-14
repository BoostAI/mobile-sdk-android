package no.boostai.sdk

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.boostai.sdk.ChatBackend.Objects.DateAsISO8601Serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

/**
 * The API's timestamps come in more shapes than the old literal
 * `yyyy-MM-dd'T'HH:mm:ss'.'SSSSSS` pattern accepted, and a parse failure used to escape the
 * network callback as a ParseException and kill the process.
 */
class DateAsISO8601SerializerTest {

    private fun decode(value: String) =
        Json.decodeFromString(DateAsISO8601Serializer, "\"$value\"")

    @Test
    fun parsesTimestampWithMicroseconds() {
        val withFraction = decode("2026-08-14T09:12:33.123456")
        val without = decode("2026-08-14T09:12:33")
        // Microseconds truncate to milliseconds — the old pattern read "123456" as 123456 ms,
        // skewing timestamps by up to ~17 minutes
        assertEquals(123L, withFraction.time - without.time)
    }

    @Test
    fun parsesTimestampWithoutFraction() {
        // The old pattern required a literal '.' and threw ParseException here
        decode("2026-08-14T09:12:33")
    }

    @Test
    fun parsesUtcDesignatorAndOffset() {
        val utc = decode("2026-08-14T09:12:33Z")
        val oslo = decode("2026-08-14T11:12:33+02:00")
        assertEquals("the same instant in two notations", utc.time, oslo.time)
    }

    @Test
    fun rejectsGarbageAsSerializationException() {
        // Must be SerializationException — anything else escapes the decoding catch in
        // ChatBackend.post() and crashes the process
        assertThrows(SerializationException::class.java) { decode("not a date") }
        assertThrows(SerializationException::class.java) { decode("2026-13-99T99:99:99") }
    }

    @Test
    fun parsesIndependentlyOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            // Arabic locales use Arabic-Indic digit symbols; the old formatter followed the
            // device locale and failed on the wire format's ASCII digits
            Locale.setDefault(Locale("ar", "EG"))
            decode("2026-08-14T09:12:33.123456")

            // Thailand defaults to the Buddhist calendar, putting parsed years 543 off
            Locale.setDefault(Locale("th", "TH", "TH"))
            val thai = decode("2026-08-14T09:12:33")
            Locale.setDefault(Locale.US)
            val us = decode("2026-08-14T09:12:33")
            assertEquals("year must not shift with the device calendar", us.time, thai.time)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun roundTripsThroughSerialize() {
        val date = decode("2026-08-14T09:12:33.123000")
        val encoded = Json.encodeToString(DateAsISO8601Serializer, date)
        assertEquals("\"2026-08-14T09:12:33.123000\"", encoded)
    }
}
