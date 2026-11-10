package app.ulap.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppVersionTest {
    @Test
    fun readsTheExampleFromTheApiSpec() {
        assertEquals(AppVersion(AppVersion.ANDROID, "1.0.0", 12), AppVersion.parse("android/1.0.0 (12)"))
    }

    @Test
    fun writesTheSameFormItReads() {
        val version = AppVersion(AppVersion.ANDROID, "1.2.0-beta", 30)

        assertEquals("android/1.2.0-beta (30)", version.toHeader())
        assertEquals(version, AppVersion.parse(version.toHeader()))
    }

    @Test
    fun anythingElseIsUnreadable() {
        listOf(
            null,
            "",
            "android",
            "android/1.0.0",
            "android/1.0.0 (x)",
            "Android/1.0.0 (12)",
            "android/1.0.0 (1234567890)",
        )
            .forEach { assertNull(AppVersion.parse(it), "\"$it\" should not parse") }
    }
}
