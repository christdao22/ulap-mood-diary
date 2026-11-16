package app.ulap.server.common

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrashReportingTest {
    /** A failed push, the way it could arrive with everything attached. */
    private fun leakyEvent() = SentryEvent(IllegalStateException("bad change")).apply {
        exceptions = listOf(SentryException().apply { value = """JSON input: {"note":"a private note"}""" })
        request = Request().apply {
            data = """{"changes":[{"note":"a private note"}]}"""
            cookies = "refresh=abc"
        }
        user = User().apply {
            email = "christian@example.com"
            ipAddress = "203.0.113.7"
        }
        setExtra("requestId", "req_1")
        setExtra("triggerOther", "the commute")
        setTag("route", "/v1/sync/push")
        setTag("authorization", "Bearer abc")
        addBreadcrumb(Breadcrumb().apply { setData("refreshToken", "abc") })
    }

    @Test
    fun `a report keeps class names and stack frames but no message, body or user`() {
        val event = CrashReporting.scrub(leakyEvent())

        assertNull(event.request)
        assertNull(event.user)
        assertEquals(listOf(null), event.exceptions?.map { it.value })
    }

    @Test
    fun `journal fields and tokens are dropped from extras, tags and breadcrumbs`() {
        val event = CrashReporting.scrub(leakyEvent())

        assertEquals(mapOf<String, Any>("requestId" to "req_1"), event.extras)
        assertEquals(mapOf("route" to "/v1/sync/push"), event.tags)
        assertEquals(emptyMap(), event.breadcrumbs?.single()?.data)
    }
}
