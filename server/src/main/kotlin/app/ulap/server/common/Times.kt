package app.ulap.server.common

import java.time.OffsetDateTime
import java.time.ZoneOffset

/*
 * The server keeps time in java.time (Exposed, java-jwt and the JDK clock speak it) and the API
 * DTOs in kotlin.time.Instant (shared with the app). These convert between the two.
 */

fun java.time.Instant.toApiInstant(): kotlin.time.Instant = kotlin.time.Instant.fromEpochSeconds(epochSecond, nano)

/** For TIMESTAMPTZ columns: PostgreSQL stores the instant, so the offset is only a formality. */
fun java.time.Instant.atUtc(): OffsetDateTime = atOffset(ZoneOffset.UTC)
