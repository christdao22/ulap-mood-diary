package app.ulap.server.scheduler

import app.ulap.server.config.DATABASE_VARIABLES
import app.ulap.server.config.databaseConfigFromEnv
import app.ulap.server.db.Database
import app.ulap.server.db.Db
import app.ulap.server.sync.TombstonePurger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Every job runs once a day at its time in the Philippines (ulap-reference.md §6.6). */
val SCHEDULE_ZONE: ZoneId = ZoneId.of("Asia/Manila")

/** One scheduled job: a name for the logs, a daily time, and a run that is safe to repeat. */
class DailyJob(val name: String, val at: LocalTime, val run: suspend () -> Unit)

private const val SCHEDULER_POOL_SIZE = 2

/** When `purge-tombstones` runs, Manila time (ulap-reference.md §6.6). */
val PURGE_TOMBSTONES_AT: LocalTime = LocalTime.of(3, 0)

/**
 * The `scheduler` service (ulap-reference.md §6.6, `infra/compose.yaml`): the same image as the
 * server, started with this main class (`app.ulap.server.scheduler.SchedulerKt`). Jobs live here
 * rather than inside the API, so they run once even if the API ever runs more than one copy.
 * It needs only the database variables.
 */
fun main() {
    val log = LoggerFactory.getLogger("app.ulap.server.scheduler")
    val config = databaseConfigFromEnv(System.getenv(), SCHEDULER_POOL_SIZE) ?: run {
        log.error("$DATABASE_VARIABLES are required")
        exitProcess(1)
    }
    val clock = Clock.systemUTC()
    Database.createDataSource(config).use { dataSource ->
        val purger = TombstonePurger(Db(dataSource), clock)
        val jobs = listOf(
            DailyJob("purge-tombstones", PURGE_TOMBSTONES_AT) {
                log.info("purge-tombstones: removed ${purger.purge()} tombstone(s)")
            },
        )
        runBlocking {
            jobs.forEach { job -> launch { runDaily(job, clock, log) } }
        }
    }
}

/** Waits for each [DailyJob.at] and runs the job. A failed run is logged and tried again the next day. */
suspend fun runDaily(job: DailyJob, clock: Clock, log: Logger) {
    while (true) {
        val now = clock.instant()
        delay(Duration.between(now, nextRun(now, job.at)).toMillis())
        try {
            job.run()
        } catch (e: CancellationException) {
            throw e
        } catch (
            // Any failure (the database is down, say) waits for the next run instead of ending the service.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // The class only: a message could quote data from a row.
            log.error("${job.name} failed: ${e::class.simpleName}")
        }
    }
}

/** The first [at] in [SCHEDULE_ZONE] that is strictly after [now]. */
fun nextRun(now: Instant, at: LocalTime, zone: ZoneId = SCHEDULE_ZONE): Instant {
    val today = now.atZone(zone).toLocalDate()
    val candidate = today.atTime(at).atZone(zone).toInstant()
    return if (candidate.isAfter(now)) candidate else today.plusDays(1).atTime(at).atZone(zone).toInstant()
}
