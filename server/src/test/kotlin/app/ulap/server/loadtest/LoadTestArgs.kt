package app.ulap.server.loadtest

import java.io.File
import java.net.URI

/** What `./gradlew :server:loadTestUsers --args="…"` asked for. */
data class LoadTestArgs(
    val command: Command,
    val users: Int = DEFAULT_USERS,
    val out: File = File(DEFAULT_OUT),
    val ttlMinutes: Int = DEFAULT_TTL_MIN,
    val confirmIssuer: String? = null,
) {
    enum class Command { SEED, CLEANUP }

    companion object {
        const val DEFAULT_USERS: Int = 1_000
        const val DEFAULT_OUT: String = "build/loadtest/users.json"
        const val DEFAULT_TTL_MIN: Int = 120
        val USERS_RANGE: IntRange = 1..10_000
        val TTL_RANGE: IntRange = 1..720

        const val USAGE: String =
            "Usage: seed [--users 1000] [--out build/loadtest/users.json] [--ttl-min 120] " +
                "[--confirm-issuer <JWT_ISSUER>]  |  cleanup [--confirm-issuer <JWT_ISSUER>]"

        /** Throws [IllegalArgumentException] with a message for the person at the terminal. */
        fun parse(args: List<String>): LoadTestArgs {
            val command = when (args.firstOrNull()) {
                "seed" -> Command.SEED
                "cleanup" -> Command.CLEANUP
                else -> throw IllegalArgumentException("The first argument must be seed or cleanup")
            }
            val options = args.drop(1).chunked(2).associate { pair ->
                require(pair.size == 2 && pair[0].startsWith("--")) { "Options come as --name value pairs" }
                pair[0] to pair[1]
            }
            val unknown = options.keys - setOf("--users", "--out", "--ttl-min", "--confirm-issuer")
            require(unknown.isEmpty()) { "Unknown option ${unknown.first()}" }
            return LoadTestArgs(
                command = command,
                users = options["--users"]?.let { intIn("--users", it, USERS_RANGE) } ?: DEFAULT_USERS,
                out = File(options["--out"] ?: DEFAULT_OUT),
                ttlMinutes = options["--ttl-min"]?.let { intIn("--ttl-min", it, TTL_RANGE) } ?: DEFAULT_TTL_MIN,
                confirmIssuer = options["--confirm-issuer"],
            )
        }

        private fun intIn(name: String, raw: String, range: IntRange): Int {
            val value = raw.toIntOrNull()
            require(value != null && value in range) {
                "$name must be a whole number from ${range.first} to ${range.last}"
            }
            return value
        }
    }
}

/**
 * The host of production's `JWT_ISSUER` (docs/runbooks/deploy.md, a placeholder domain: change it
 * with the domain). SECURITY.md promises no load testing there, so the tool refuses it outright.
 */
const val PRODUCTION_API_HOST: String = "api.ulap.app"

/**
 * Why this run must not go ahead, or null when it may. Outside development the person has to
 * repeat the server's `JWT_ISSUER` with `--confirm-issuer`, so a shell left pointing at the wrong
 * `.env` can't seed or wipe accounts by accident. Production is refused whatever is passed.
 */
fun loadTestRefusal(args: LoadTestArgs, env: Map<String, String>): String? {
    val issuer = env["JWT_ISSUER"].orEmpty()
    val host = runCatching { URI(issuer).host }.getOrNull()
    return when {
        host.equals(PRODUCTION_API_HOST, ignoreCase = true) ->
            "JWT_ISSUER is production ($PRODUCTION_API_HOST). Load tests run on staging or locally only."

        env["APP_ENV"] != "development" && args.confirmIssuer != issuer ->
            "APP_ENV is not development. Pass --confirm-issuer with this server's JWT_ISSUER to show which server you mean."

        else -> null
    }
}
