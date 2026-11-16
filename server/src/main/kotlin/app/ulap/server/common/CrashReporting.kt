package app.ulap.server.common

import app.ulap.server.config.AppEnv
import app.ulap.shared.rules.CrashReportRules
import io.sentry.Breadcrumb
import io.sentry.KeyValueCollectionBehavior
import io.sentry.Sentry
import io.sentry.SentryEvent

/**
 * Crash reports to Sentry (D11), only when `SENTRY_DSN` is set; without it nothing is sent. The
 * server reports only unexpected `500 INTERNAL` errors (Errors.kt) and uncaught exceptions,
 * never request bodies. Personal data is off and [scrub] runs on every event and breadcrumb,
 * following [CrashReportRules]: class names and stack frames only, no journal text, emails or
 * tokens (README §14, privacy notice §6). Sentry keeps them at most 90 days, set in the project.
 */
object CrashReporting {
    fun start(dsn: String?, env: AppEnv) {
        if (dsn.isNullOrBlank()) return
        Sentry.init { options ->
            options.dsn = dsn
            options.environment = env.name.lowercase()
            // Personal data off (D11): no user, cookies, query strings, headers or bodies. This is
            // what sendDefaultPii = false meant; the SDK now configures it here.
            options.dataCollection.apply {
                userInfo = false
                cookies = KeyValueCollectionBehavior.off()
                urlQueryParams = KeyValueCollectionBehavior.off()
                httpBodies = emptySet()
                databaseQueryData = false
                httpHeaders.request = KeyValueCollectionBehavior.off()
                httpHeaders.response = KeyValueCollectionBehavior.off()
            }
            // The server's host name isn't needed to fix a crash.
            options.isAttachServerName = false
            options.setBeforeSend { event, _ -> scrub(event) }
            options.setBeforeBreadcrumb { breadcrumb, _ -> scrub(breadcrumb) }
        }
    }

    /** The report without anything [CrashReportRules] keeps out. */
    fun scrub(event: SentryEvent): SentryEvent = event.apply {
        request = null
        user = null
        message = null
        exceptions?.forEach { it.value = null }
        extras = extras?.let(::scrubbed)
        tags = tags?.filterKeys { !CrashReportRules.isSensitiveKey(it) }
        contexts.keys().toList().filter(CrashReportRules::isSensitiveKey).forEach(contexts::remove)
        breadcrumbs?.forEach { scrub(it) }
    }

    fun scrub(breadcrumb: Breadcrumb): Breadcrumb = breadcrumb.apply {
        val kept = scrubbed(data)
        data.clear()
        kept.forEach(::setData)
    }

    /** Sentry's maps hold no nulls, so the scrubbed copy leaves them out too. */
    private fun scrubbed(values: Map<String, Any>): Map<String, Any> = buildMap {
        CrashReportRules.scrub(values).forEach { (key, value) -> if (value != null) put(key, value) }
    }
}
