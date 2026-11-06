package app.ulap.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.ulap.core.database.dao.CheckInDao
import app.ulap.core.database.model.CheckInEntity
import app.ulap.core.database.model.SyncState
import app.ulap.shared.catalog.MoodCatalog
import app.ulap.shared.model.Ids
import app.ulap.shared.model.SupportType
import app.ulap.shared.model.Trigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug builds only. Writes fake check-ins straight into the encrypted database, spread over the
 * last [EXTRA_DAYS] days, so Logs scrolling (Phase 3 exit) and reports can be tried with real
 * volume. The file is SQLCipher, so it can't be filled from outside the app.
 *
 * ```
 * adb shell am broadcast -a app.ulap.debug.SEED_CHECK_INS -p app.ulap.android.debug --ei count 1000
 * ```
 *
 * Rows are marked SYNCED so a later sync never uploads them. Notes are fixed sample text.
 */
@AndroidEntryPoint
class SeedCheckInsReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: CheckInDao

    override fun onReceive(context: Context, intent: Intent) {
        val count = intent.getIntExtra(EXTRA_COUNT, DEFAULT_COUNT)
        val days = intent.getIntExtra(EXTRA_DAYS, DEFAULT_DAYS)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val random = Random(SEED)
                val now = Clock.System.now()
                repeat(count) { dao.insert(fakeCheckIn(random, now - (days.days * random.nextDouble()))) }
                Log.i(TAG, "Seeded $count check-ins over $days days")
            } finally {
                pending.finish()
            }
        }
    }

    private fun fakeCheckIn(random: Random, loggedAt: Instant): CheckInEntity {
        val mood = MoodCatalog.all.random(random)
        val trigger = Trigger.entries.random(random).takeIf { random.nextBoolean() }
        return CheckInEntity(
            id = Ids.newId(),
            moodCode = mood.code,
            energy = random.nextInt(1, SCALE_MAX + 1),
            stress = random.nextInt(1, SCALE_MAX + 1),
            stressAfter = random.nextInt(1, SCALE_MAX + 1).takeIf { random.nextBoolean() },
            trigger = trigger,
            triggerOther = if (trigger == Trigger.OTHER) "Sample reason" else null,
            note = "Sample note".takeIf { random.nextBoolean() },
            supportUsed = SupportType.entries.random(random),
            isGoodMoment = random.nextInt(GOOD_MOMENT_ONE_IN) == 0,
            loggedAt = loggedAt,
            tzOffsetMin = MANILA_OFFSET_MIN,
            createdAt = loggedAt,
            updatedAt = loggedAt,
            deletedAt = null,
            syncState = SyncState.SYNCED,
            syncError = null,
            syncAttempts = 0,
            serverSeq = null,
        )
    }

    private companion object {
        const val TAG = "UlapSeed"
        const val EXTRA_COUNT = "count"
        const val EXTRA_DAYS = "days"
        const val DEFAULT_COUNT = 1000
        const val DEFAULT_DAYS = 365
        const val SEED = 7
        const val MANILA_OFFSET_MIN = 480
        const val SCALE_MAX = 10
        const val GOOD_MOMENT_ONE_IN = 5
    }
}
