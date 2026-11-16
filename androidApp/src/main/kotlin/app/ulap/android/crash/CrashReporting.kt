package app.ulap.android.crash

import android.content.Context
import app.ulap.android.BuildConfig
import app.ulap.shared.rules.CrashReportRules
import io.sentry.Breadcrumb
import io.sentry.KeyValueCollectionBehavior
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid

/**
 * Crash reports to Sentry (D11), only when the build has a DSN (`ulap.sentryDsn`); local builds
 * have none and report nothing. Personal data is off and [scrub] runs on every event and
 * breadcrumb, following [CrashReportRules]: no journal text, emails, tokens or request bodies
 * ever leave the phone in a report (README §14, privacy notice §6). Sentry's own auto-start is
 * turned off in the manifest, so this is the only way it starts.
 */
object CrashReporting {
    fun start(context: Context, dsn: String = BuildConfig.SENTRY_DSN) {
        if (dsn.isBlank()) return
        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.environment = BuildConfig.BUILD_TYPE
            options.release = "app.ulap.android@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
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
            // Nothing that could show what's on screen, and no taps or session tracking.
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableUserInteractionTracing = false
            options.isEnableAutoSessionTracking = false
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
