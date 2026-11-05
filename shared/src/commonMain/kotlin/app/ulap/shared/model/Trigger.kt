package app.ulap.shared.model

/**
 * What triggered the feeling (single choice, optional). Stored and synced by [name], e.g. `WORK`
 * (api-spec.md §5.3), so only ever add a value; never rename or remove one.
 *
 * [OTHER] comes with free text (`triggerOther`, at most 100 characters).
 */
enum class Trigger { WORK, FAMILY, MONEY, HEALTH, RELATIONSHIPS, SCHOOL, OTHER }
