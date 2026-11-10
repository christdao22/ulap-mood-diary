package app.ulap.shared.api

/**
 * The `X-App-Version` header, e.g. `android/1.0.0 (12)`: platform, version name and the build
 * number in brackets (api-spec.md §2). The server compares only [code] with its minimum, so the
 * version name can be anything without a space.
 */
data class AppVersion(val platform: String, val name: String, val code: Int) {
    fun toHeader(): String = "$platform/$name ($code)"

    companion object {
        const val ANDROID: String = "android"
        const val IOS: String = "ios"

        private val pattern = Regex("""([a-z]+)/(\S+) \((\d{1,9})\)""")

        /** Null when [header] is missing or isn't in the `platform/name (code)` form. */
        fun parse(header: String?): AppVersion? {
            val match = header?.trim()?.let(pattern::matchEntire) ?: return null
            val (platform, name, code) = match.destructured
            return AppVersion(platform, name, code.toInt())
        }
    }
}
