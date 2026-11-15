package app.ulap.server.common

import app.ulap.shared.api.Api
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.SyncLimits
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

/*
 * The 1 MB request body limit (api-spec.md §11, Input limits). A body over it gets
 * `413 PAYLOAD_TOO_LARGE` before it's read into memory.
 */

private fun payloadTooLarge() =
    ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "Request body is over ${SyncLimits.MAX_BODY_BYTES} bytes")

/**
 * Turns away any request whose `Content-Length` says it's too big. A body sent without a length
 * (chunked) passes here; routes that take large bodies read it with [receiveLimited], which counts.
 */
val RequestBodyLimit = createApplicationPlugin("RequestBodyLimit") {
    onCall { call ->
        if ((call.request.contentLength() ?: 0) > SyncLimits.MAX_BODY_BYTES) throw payloadTooLarge()
    }
}

/**
 * Reads at most [maxBytes] of the body (after any decompression, so a small gzip can't expand
 * into a huge one) and decodes it. Too big → `413`; not valid JSON for [T] → `400 BAD_REQUEST`.
 */
suspend inline fun <reified T> ApplicationCall.receiveLimited(maxBytes: Long = SyncLimits.MAX_BODY_BYTES): T {
    val bytes = readLimitedBody(maxBytes)
    return try {
        Api.json.decodeFromString<T>(bytes.decodeToString())
    } catch (
        // SerializationException and a bad timestamp both are IllegalArgumentExceptions. The
        // message can quote the body, so it's dropped.
        @Suppress("SwallowedException") e: IllegalArgumentException,
    ) {
        throw ApiException.badRequest("Malformed request")
    }
}

suspend fun ApplicationCall.readLimitedBody(maxBytes: Long): ByteArray {
    if ((request.contentLength() ?: 0) > maxBytes) throw payloadTooLarge()
    val bytes = receiveChannel().readBuffer(maxBytes + 1).readByteArray()
    if (bytes.size > maxBytes) throw payloadTooLarge()
    return bytes
}
