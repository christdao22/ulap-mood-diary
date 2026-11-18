package app.ulap.server.loadtest

import app.ulap.server.auth.AccessTokens
import app.ulap.server.config.AppConfig
import app.ulap.server.config.DATABASE_VARIABLES
import app.ulap.server.config.Secret
import app.ulap.server.config.databaseConfigFromEnv
import app.ulap.server.db.Database
import app.ulap.server.db.Db
import app.ulap.shared.api.Api
import java.time.Clock
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.slf4j.LoggerFactory

/**
 * Seeds or removes the load-test accounts (docs/runbooks/load-test.md). Run it through Gradle,
 * which passes the repo's `.env`, or the file named by `-PloadTestEnv`:
 *
 *     ./gradlew :server:loadTestUsers --args="seed --users 1000"
 *     ./gradlew :server:loadTestUsers --args="cleanup"
 *
 * It's in the test source set, so it is never part of `server-all.jar` or the server image. It
 * needs `DATABASE_*` and `JWT_SECRET`, `JWT_ISSUER` and `JWT_AUDIENCE` of the server under test.
 */
fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("app.ulap.server.loadtest")
    val env = System.getenv()
    val parsed = runCatching { LoadTestArgs.parse(args.toList()) }.getOrElse {
        log.error("{}\n{}", it.message, LoadTestArgs.USAGE)
        exitProcess(2)
    }
    val refusal = loadTestRefusal(parsed, env)
    val database = databaseConfigFromEnv(env, poolSize = 2)
    val jwt = jwtConfigFromEnv(env, parsed.ttlMinutes)
    when {
        refusal != null -> log.error(refusal)
        database == null -> log.error("$DATABASE_VARIABLES are required")
        jwt == null -> log.error("JWT_SECRET, JWT_ISSUER and JWT_AUDIENCE are required")
    }
    if (refusal != null || database == null || jwt == null) exitProcess(1)

    Database.createDataSource(database).use { dataSource ->
        val users = LoadTestUsers(Db(dataSource), AccessTokens(jwt, Clock.systemUTC()))
        runBlocking {
            when (parsed.command) {
                LoadTestArgs.Command.SEED -> {
                    val seeded = users.seed(parsed.users)
                    parsed.out.absoluteFile.parentFile.mkdirs()
                    parsed.out.writeText(Api.json.encodeToString(ListSerializer(SeededUser.serializer()), seeded))
                    log.info(
                        "Seeded {} load-test users. Tokens last {} minutes: {}",
                        seeded.size,
                        parsed.ttlMinutes,
                        parsed.out.absolutePath,
                    )
                }

                LoadTestArgs.Command.CLEANUP -> log.info("Removed {} load-test users", users.cleanup())
            }
        }
    }
}

/** The signing settings of the server under test, with the token lifetime the test needs. */
private fun jwtConfigFromEnv(env: Map<String, String>, ttlMinutes: Int): AppConfig.JwtConfig? {
    val secret = env["JWT_SECRET"]
    val issuer = env["JWT_ISSUER"]
    val audience = env["JWT_AUDIENCE"]
    if (secret.isNullOrBlank() || issuer.isNullOrBlank() || audience.isNullOrBlank()) return null
    return AppConfig.JwtConfig(
        secret = Secret(secret),
        issuer = issuer,
        audience = audience,
        accessTokenTtlMinutes = ttlMinutes,
        // Unused: the tool issues access tokens only.
        refreshTokenTtlDays = 1,
    )
}
