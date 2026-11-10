package app.ulap.server.common

import app.ulap.server.error
import app.ulap.server.withTestServer
import app.ulap.shared.api.ErrorCode
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

/**
 * Nothing private reaches the log: not a request body, a query string, a header token, or an
 * exception message that quotes the body (api-spec.md §11, README §7.1). 🔒 Logging is a privacy
 * control: changes here need two reviewers.
 */
class LoggingTest {
    private val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun attach() {
        appender.start()
        root.addAppender(appender)
    }

    @AfterTest
    fun detach() {
        root.detachAppender(appender)
    }

    @Test
    fun aCrashLogsTheTypeButNotTheMessageOrBody() = withTestServer(
        extraRoutes = {
            post("/test/boom") {
                val body = call.receiveText()
                error("could not save $body")
            }
        },
    ) {
        val response = client.post("/v1/test/boom") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $SECRET")
            setBody("""{"note":"$SECRET"}""")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ErrorCode.INTERNAL, response.error().code)
        val crash = appender.list.single { it.formattedMessage.startsWith("Unhandled exception") }
        assertTrue(crash.throwableProxy.message.contains("IllegalStateException"))
        assertNothingLeaked()
    }

    @Test
    fun queryStringsAreNotLogged() = withTestServer {
        client.get("/v1/nope?token=$SECRET")

        assertNothingLeaked()
    }

    @Test
    fun redactionKeepsTypesAndFramesForEveryCause() {
        val original = IllegalStateException("outer $SECRET", IllegalArgumentException("inner $SECRET"))

        val redacted = original.redacted()

        assertFalse(redacted.message.orEmpty().contains(SECRET))
        assertTrue(redacted.message.orEmpty().contains("IllegalStateException"))
        assertEquals(original.stackTrace.toList(), redacted.stackTrace.toList())
        val cause = requireNotNull(redacted.cause)
        assertFalse(cause.message.orEmpty().contains(SECRET))
        assertTrue(cause.message.orEmpty().contains("IllegalArgumentException"))
    }

    private fun assertNothingLeaked() {
        val text = appender.list.joinToString("\n") { it.formattedMessage + "\n" + it.throwableProxy.describe() }
        assertFalse(text.contains(SECRET), "A log line contained private text:\n$text")
    }

    private fun IThrowableProxy?.describe(): String =
        if (this == null) "" else "$className: $message\n" + cause.describe()

    private companion object {
        const val SECRET = "my-private-note-7f3a"
    }
}
