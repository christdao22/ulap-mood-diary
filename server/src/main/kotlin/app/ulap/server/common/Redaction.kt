package app.ulap.server.common

/**
 * A copy of this exception, and its causes, with every message removed but the class names and
 * stack frames kept. Exception messages can quote the request: a JSON parse error prints the
 * body around the bad character, and that may be a journal note (api-spec.md §11, README §7.1).
 * Log the copy, never the original.
 */
fun Throwable.redacted(): Throwable = redact(this, depth = 0)

private const val MAX_CAUSES = 16

private fun redact(original: Throwable, depth: Int): Throwable {
    val cause = original.cause?.takeIf { it !== original && depth < MAX_CAUSES }
    return RedactedException(
        type = original::class.qualifiedName ?: original.javaClass.name,
        cause = cause?.let { redact(it, depth + 1) },
    ).apply { stackTrace = original.stackTrace }
}

/** Shows as `RedactedException: kotlinx.serialization.SerializationException (message hidden)`. */
class RedactedException(type: String, cause: Throwable?) : RuntimeException("$type (message hidden)", cause)
