package app.ulap.shared.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Row IDs for the phone and the server (technical-plan.md §3, D7). */
object Ids {
    /**
     * A new UUIDv7 (RFC 9562) as a lowercase string, e.g. `019a4f2e-6b1c-7d3a-9f20-5c8e1b7a4d60`.
     *
     * The first 48 bits are the current Unix time in milliseconds and the rest is random, so IDs
     * sort by creation time and database indexes stay compact. The phone makes check-in IDs
     * itself, so an ID never changes after sync.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = Uuid.generateV7().toString()
}
