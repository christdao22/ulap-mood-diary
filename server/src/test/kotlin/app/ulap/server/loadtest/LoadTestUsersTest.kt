package app.ulap.server.loadtest

import app.ulap.server.auth.AccessTokens
import app.ulap.server.auth.withAuthServer
import app.ulap.server.db.Db
import app.ulap.server.db.TestPostgres
import app.ulap.server.sync.checkIn
import app.ulap.server.sync.pulledAll
import app.ulap.server.sync.pushed
import app.ulap.server.sync.status
import app.ulap.server.sync.syncUser
import app.ulap.server.testEnv
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The load-test seeder (docs/runbooks/load-test.md): its accounts sync, and cleanup removes only them. */
class LoadTestUsersTest {
    private val db = Db(TestPostgres.dataSource)

    @Test
    fun seededUsersCanSyncAndCleanupRemovesOnlyThem() = withAuthServer {
        val users = LoadTestUsers(db, AccessTokens(TestPostgres.config.jwt, clock))
        val realUser = syncUser()

        val seeded = users.seed(3)
        assertEquals(3, seeded.map { it.userId }.toSet().size)
        val change = checkIn()
        pushed(seeded[0].accessToken, change)
        assertEquals(listOf(change.id), pulledAll(seeded[0].accessToken).map { it.id })
        seeded.forEach { assertEquals(HttpStatusCode.OK, status(it.accessToken).status) }

        assertTrue(users.cleanup() >= seeded.size)
        seeded.forEach { assertEquals(HttpStatusCode.Forbidden, status(it.accessToken).status) }
        assertEquals(HttpStatusCode.OK, status(realUser).status)
    }

    @Test
    fun seededUserHidesItsTokenWhenPrinted() {
        val user = SeededUser("u", "d", "secret-token")
        assertTrue("secret-token" !in user.toString())
    }

    @Test
    fun parseReadsOptionsAndDefaults() {
        assertEquals(LoadTestArgs(LoadTestArgs.Command.CLEANUP), LoadTestArgs.parse(listOf("cleanup")))
        assertEquals(
            LoadTestArgs(
                LoadTestArgs.Command.SEED,
                users = 50,
                out = File("x.json"),
                ttlMinutes = 30,
                confirmIssuer = "i",
            ),
            LoadTestArgs.parse(
                listOf("seed", "--users", "50", "--out", "x.json", "--ttl-min", "30", "--confirm-issuer", "i"),
            ),
        )
    }

    @Test
    fun parseRejectsBadArguments() {
        listOf(
            emptyList(),
            listOf("wipe"),
            listOf("seed", "--users"),
            listOf("seed", "--users", "0"),
            listOf("seed", "--users", "lots"),
            listOf("seed", "--ttl-min", "721"),
            listOf("seed", "--colour", "blue"),
        ).forEach { args -> assertFailsWith<IllegalArgumentException>(args.toString()) { LoadTestArgs.parse(args) } }
    }

    @Test
    fun developmentNeedsNoConfirmation() {
        assertNull(loadTestRefusal(LoadTestArgs(LoadTestArgs.Command.SEED), testEnv()))
    }

    @Test
    fun outsideDevelopmentTheIssuerMustBeRepeated() {
        val staging = testEnv() + mapOf("APP_ENV" to "production", "JWT_ISSUER" to "https://staging-api.ulap.app")

        assertNotNull(loadTestRefusal(LoadTestArgs(LoadTestArgs.Command.SEED), staging))
        assertNotNull(loadTestRefusal(LoadTestArgs(LoadTestArgs.Command.CLEANUP, confirmIssuer = "https://x"), staging))
        assertNull(
            loadTestRefusal(
                LoadTestArgs(LoadTestArgs.Command.SEED, confirmIssuer = "https://staging-api.ulap.app"),
                staging,
            ),
        )
    }

    @Test
    fun productionIsRefusedEvenWhenConfirmed() {
        val production = testEnv() + mapOf("APP_ENV" to "production", "JWT_ISSUER" to "https://api.ulap.app")
        val confirmed = LoadTestArgs(LoadTestArgs.Command.SEED, confirmIssuer = "https://api.ulap.app")

        assertNotNull(loadTestRefusal(confirmed, production))
    }
}
