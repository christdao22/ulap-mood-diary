package app.ulap.core.sync

import app.ulap.core.database.model.SyncState
import app.ulap.shared.sync.CheckInChange
import app.ulap.shared.sync.PulledChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/** The phone's merge table, row by row (api-spec.md §8.3, Phone merge). */
class CheckInChangesTest {
    private val now = T0 + 60.minutes

    private fun pulledSave(updatedAt: kotlin.time.Instant, stress: Int = 3, seq: Long = 9) =
        PulledChange(seq, row("a", updatedAt = updatedAt, stress = stress).toChange())

    @Test
    fun `no local row, inserted as synced`() {
        val merged = mergePulled(null, pulledSave(T0, stress = 3), now)!!

        assertEquals(SyncState.SYNCED, merged.syncState)
        assertEquals(3, merged.stress)
        assertEquals(9L, merged.serverSeq)
        assertEquals(now, merged.createdAt)
    }

    @Test
    fun `no local row and a tombstone, nothing to do`() {
        assertNull(mergePulled(null, PulledChange(9, CheckInChange.Deleted("a", T0, T0)), now))
    }

    @Test
    fun `local synced, the server's version replaces it, keeping created_at`() {
        val local = row("a", syncState = SyncState.SYNCED, stress = 8)

        val merged = mergePulled(local, pulledSave(T0 + 1.minutes, stress = 3), now)!!

        assertEquals(3, merged.stress)
        assertEquals(T0, merged.createdAt)
        assertEquals(T0 + 1.minutes, merged.updatedAt)
    }

    @Test
    fun `local pending and newer, kept, to push next time`() {
        val local = row("a", updatedAt = T0 + 2.minutes)

        assertNull(mergePulled(local, pulledSave(T0 + 1.minutes), now))
    }

    @Test
    fun `local pending and the server newer or equal, the server wins`() {
        val local = row("a", updatedAt = T0 + 1.minutes, stress = 8)

        assertEquals(3, mergePulled(local, pulledSave(T0 + 2.minutes, stress = 3), now)!!.stress)
        // Equal: most likely this same version, accepted on a push whose answer got lost.
        assertEquals(SyncState.SYNCED, mergePulled(local, pulledSave(T0 + 1.minutes), now)!!.syncState)
    }

    @Test
    fun `a server tombstone deletes the check-in and its journal text, even over a newer edit`() {
        val local = row("a", updatedAt = T0 + 5.minutes, note = "Private words")

        val merged = mergePulled(local, PulledChange(9, CheckInChange.Deleted("a", T0, T0)), now)!!

        assertEquals(T0, merged.deletedAt)
        assertNull(merged.note)
        assertEquals(SyncState.SYNCED, merged.syncState)
    }

    @Test
    fun `a pending row pushes as a save, a tombstone as a delete with its times only`() {
        assertIs<CheckInChange.Saved>(row("a").toChange())
        assertEquals(
            CheckInChange.Deleted("a", T0 + 1.minutes, T0 + 1.minutes),
            row("a", updatedAt = T0 + 1.minutes, deletedAt = T0 + 1.minutes).toChange(),
        )
    }
}
