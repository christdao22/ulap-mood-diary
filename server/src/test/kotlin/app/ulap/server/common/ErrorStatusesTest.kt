package app.ulap.server.common

import app.ulap.shared.api.ErrorCode
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The server's error codes and statuses match the table in api-spec.md §4, so the two can't drift. */
class ErrorStatusesTest {
    @Test
    fun matchesTheApiSpecTable() {
        assertEquals(specTable(), ErrorStatuses.all.mapValues { it.value.value })
    }

    @Test
    fun everySharedErrorCodeHasAStatus() {
        val codes = ErrorCode::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }

        assertTrue(codes.isNotEmpty())
        assertEquals(codes.toSet(), ErrorStatuses.all.keys)
    }

    /** Rows like `| 409 | `EMAIL_TAKEN` | …` under "Status codes and error codes". */
    private fun specTable(): Map<String, Int> {
        // Gradle runs the tests from the server/ folder.
        val lines = File("../docs/api-spec.md").readLines()
        val start = lines.indexOfFirst { it.startsWith("### Status codes and error codes") }
        val row = Regex("""\|\s*(\d{3})\s*\|\s*`([A-Z_]+)`\s*\|.*""")
        return lines.drop(start + 1)
            .takeWhile { !it.startsWith("#") }
            .mapNotNull { row.matchEntire(it.trim()) }
            .associate { it.groupValues[2] to it.groupValues[1].toInt() }
            .also { assertTrue(it.size >= 20, "Couldn't read the error table in api-spec.md §4") }
    }
}
