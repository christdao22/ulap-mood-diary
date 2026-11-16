package app.ulap.shared.rules

/**
 * What a crash report may carry (D11, README §14, privacy notice §6). Sentry runs on the phone and
 * the server with `sendDefaultPii = false`, and each platform's `beforeSend` applies these rules:
 * - No request bodies, cookies or query strings, and no user (so no email or IP address).
 * - No exception messages: class names and stack frames only. A message can quote the input it
 *   failed on, and a JSON parse error prints the body, which may hold a journal note. The
 *   server's logs follow the same rule (`Redaction.kt`).
 * - No value under a [isSensitiveKey] name anywhere in extras, contexts, tags or breadcrumbs.
 *
 * Sentry keeps reports at most 90 days, set in the Sentry project (privacy notice §6).
 */
object CrashReportRules {
    /**
     * Field names whose values never leave the device or server in a report: the journal fields,
     * credentials and contact details. Compared case-insensitively, ignoring `_` and `-`, so
     * `trigger_other`, `triggerOther` and `Trigger-Other` all match.
     */
    private val SENSITIVE_KEYS = setOf(
        "note",
        "triggerother",
        "password",
        "newpassword",
        "email",
        "token",
        "accesstoken",
        "refreshtoken",
        "idtoken",
        "authorization",
        "cookie",
    )

    fun isSensitiveKey(key: String): Boolean = key.lowercase().filter { it != '_' && it != '-' } in SENSITIVE_KEYS

    /** [values] without any sensitive key, checking maps and lists inside it too. */
    fun scrub(values: Map<String, Any?>): Map<String, Any?> =
        values.filterKeys { !isSensitiveKey(it) }.mapValues { (_, value) -> scrubValue(value) }

    private fun scrubValue(value: Any?): Any? = when (value) {
        is Map<*, *> ->
            value.entries
                .filter { (key, _) -> key !is String || !isSensitiveKey(key) }
                .associate { (key, inner) -> key to scrubValue(inner) }

        is List<*> -> value.map(::scrubValue)

        else -> value
    }
}
