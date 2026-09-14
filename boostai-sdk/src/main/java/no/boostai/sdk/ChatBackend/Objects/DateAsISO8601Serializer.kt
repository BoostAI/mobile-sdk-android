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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Serializer for the API's `yyyy-MM-dd'T'HH:mm:ss[.ffffff][Z|±HH:MM]` timestamps.
 *
 * A `SimpleDateFormat` is created per call — the shared instance this replaces was not thread
 * safe, and responses are decoded on concurrent OkHttp threads — and pinned to `Locale.US`, so
 * digit symbols and calendar follow the wire format rather than the device locale. The fraction
 * and zone designator are optional (the old literal pattern threw on their absence and presence
 * respectively), the fraction is truncated to milliseconds instead of being misread as a
 * millisecond *count*, and an unparseable value raises [SerializationException] so decoding fails
 * gracefully instead of escaping the network callback and killing the process.
 */
object DateAsISO8601Serializer : KSerializer<Date> {
    private const val BASE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    // Offset hours limited to 00-14: TimeZone.getTimeZone("GMT+9959") silently returns plain
    // GMT, so an out-of-range offset would otherwise be read as UTC instead of being rejected
    private val PATTERN =
        Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-](?:0\d|1[0-4]):?\d{2})?$""")

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Date) {
        // Milliseconds padded to the API's six fractional digits
        encoder.encodeString(SimpleDateFormat("$BASE_FORMAT.SSS'000'", Locale.US).format(value))
    }

    override fun deserialize(decoder: Decoder): Date {
        val string = decoder.decodeString()
        val match = PATTERN.matchEntire(string.trim())
            ?: throw SerializationException("Unparseable date: \"$string\"")
        val (base, fraction, zone) = match.destructured

        val formatter = SimpleDateFormat(BASE_FORMAT, Locale.US)
        // Not lenient: "2026-13-99" should be an error, not roll over into the next year
        formatter.isLenient = false
        when {
            zone == "Z" -> formatter.timeZone = TimeZone.getTimeZone("UTC")
            zone.isNotEmpty() -> formatter.timeZone = TimeZone.getTimeZone("GMT$zone")
            // No zone designator: interpreted as device-local time, as this serializer always has
        }

        val date = try {
            formatter.parse(base)
        } catch (e: ParseException) {
            throw SerializationException("Unparseable date: \"$string\"", e)
        } ?: throw SerializationException("Unparseable date: \"$string\"")

        val millis = fraction.padEnd(3, '0').take(3).toLong()
        return Date(date.time + millis)
    }
}
