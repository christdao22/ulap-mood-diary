package app.ulap.shared.model

/**
 * The support the person used after checking in (api-spec.md §5.3). Stored and synced by [name],
 * so only ever add a value; never rename or remove one.
 */
enum class SupportType {
    /** They didn't pick a support action. */
    NONE,

    /** The breathing circle (Red zone). */
    BREATHE,

    /** The "something small that helps" list (Blue zone). */
    SMALL_HELPS,

    /** "Save to good moments" (Yellow and Green zones). */
    GOOD_MOMENT,
}
