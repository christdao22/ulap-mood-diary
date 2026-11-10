package app.ulap.shared.api

import kotlin.time.Instant
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Timestamps on the wire: ISO 8601 in UTC with milliseconds, `2026-11-04T01:15:00.000Z`
 * (api-spec.md §2, Format). Kotlin's own `Instant.toString()` drops the fraction when it is zero,
 * so this serializer writes exactly three digits. Reading accepts any ISO 8601 instant.
 */
object ApiInstantSerializer : KSerializer<Instant> {
    private const val FRACTION_DIGITS = 3

    private val format = DateTimeComponents.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
        char('T')
        hour()
        char(':')
        minute()
        char(':')
        second()
        char('.')
        secondFraction(FRACTION_DIGITS)
        char('Z')
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.ulap.ApiInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.format(format, UtcOffset.ZERO))
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
