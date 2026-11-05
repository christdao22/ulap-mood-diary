package app.ulap.core.database.model

/** Whether a row's latest change has reached the server. Stored by name. */
enum class SyncState {
    /** Changed here and not yet accepted by the server. */
    PENDING,

    /** The server has this exact version. */
    SYNCED,
}
