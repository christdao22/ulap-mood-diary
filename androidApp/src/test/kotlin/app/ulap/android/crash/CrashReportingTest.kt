package app.ulap.android.crash

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.SerializationException

class CrashReportingTest {
    /** A crash while reading a check-in, the way it could arrive with everything attached. */
    private fun leakyEvent() =
        SentryEvent(SerializationException("""Unexpected JSON at {"note":"a private note"}""")).apply {
            exceptions = listOf(SentryException().apply { value = """JSON input: {"note":"a private note"}""" })
            request = Request().apply { data = """{"note":"a private note"}""" }
            user = User().apply { email = "christian@example.com" }
            setExtra("requestId", "req_1")
            setExtra("note", "a private note")
            setTag("screen", "details")
            setTag("email", "christian@example.com")
            addBreadcrumb(
                Breadcrumb().apply {
                    category = "sync"
                    setData("moodCode", "worried")
                    setData("triggerOther", "the commute")
                },
            )
        }

    @Test
    fun `a report keeps class names and stack frames but no message, body or user`() {
        val event = CrashReporting.scrub(leakyEvent())

        assertNull(event.request)
        assertNull(event.user)
        assertNull(event.message)
        assertEquals(listOf(null), event.exceptions?.map { it.value })
    }

    @Test
    fun `journal fields and emails are dropped from extras, tags and breadcrumbs`() {
        val event = CrashReporting.scrub(leakyEvent())

        assertEquals(mapOf<String, Any>("requestId" to "req_1"), event.extras)
        assertEquals(mapOf("screen" to "details"), event.tags)
        assertEquals(mapOf<String, Any>("moodCode" to "worried"), event.breadcrumbs?.single()?.data)
    }

    @Test
    fun `without a DSN nothing starts`() {
        // No context is touched: start returns before Sentry is set up.
        CrashReporting.start(context = throwingContext(), dsn = "")
    }

    private fun throwingContext(): android.content.Context = object : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): android.content.Context = error("Sentry must not start")
    }
}
