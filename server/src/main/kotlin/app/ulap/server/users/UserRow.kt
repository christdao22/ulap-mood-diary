@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.users

import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One row of `users` (technical-plan.md §7). */
class UserRow(
    val id: Uuid,
    val displayName: String?,
    val language: String,
    val mascotEnabled: Boolean,
    val createdAt: OffsetDateTime,
)
