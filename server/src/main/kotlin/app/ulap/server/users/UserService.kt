@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.users

import app.ulap.server.auth.AuthErrors
import app.ulap.server.auth.loadUserOrGone
import app.ulap.server.common.ApiException
import app.ulap.server.db.Db
import app.ulap.shared.api.ErrorDetail
import app.ulap.shared.api.UpdateMeRequest
import app.ulap.shared.api.UserDto
import app.ulap.shared.validation.AccountRules
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The signed-in person's own profile, settings and account (api-spec.md §7). Every function takes
 * the user id from the access token, never from a request body (§11, Authorization).
 */
class UserService(private val db: Db) {
    /** `GET /me` (api-spec.md §7.1). */
    suspend fun me(userId: Uuid): UserDto = db.tx { loadUserOrGone(userId) }

    /**
     * `PATCH /me` (api-spec.md §7.2): changes only the fields that were sent and returns the
     * `User` as it is afterwards. An empty request changes nothing and still returns the `User`.
     */
    suspend fun update(userId: Uuid, request: UpdateMeRequest): UserDto {
        val displayName = request.displayName?.trim()
        requireValid(displayName, request.language)
        val changesSomething = displayName != null || request.language != null || request.mascotEnabled != null
        return db.tx {
            if (changesSomething && updateUser(userId, displayName, request.language, request.mascotEnabled) == 0) {
                throw AuthErrors.accountDeleted()
            }
            loadUserOrGone(userId)
        }
    }

    /**
     * `DELETE /me` (api-spec.md §7.3): removes the account and everything that belongs to it, in
     * one transaction. Nothing is kept and nothing can be restored afterwards.
     *
     * Deleting an account that is already gone succeeds too. If the first answer got lost on the
     * way, the app can send the request again and still finish wiping the phone.
     */
    suspend fun delete(userId: Uuid, confirm: String?) {
        if (confirm != AccountRules.DELETE_CONFIRMATION) {
            throw ApiException.badRequest("confirm must be \"${AccountRules.DELETE_CONFIRMATION}\"")
        }
        db.tx { deleteUser(userId) }
    }

    /** Reports every field that is wrong at once, so the form can mark them all (api-spec.md §4). */
    private fun requireValid(displayName: String?, language: String?) {
        val problems = buildList {
            if (displayName != null && !AccountRules.isValidDisplayName(displayName)) {
                add(ErrorDetail("displayName", if (displayName.isEmpty()) "TOO_SHORT" else "TOO_LONG"))
            }
            if (language != null && language !in AccountRules.LANGUAGES) add(ErrorDetail("language", "INVALID"))
        }
        if (problems.isNotEmpty()) throw ApiException.validationFailed(problems)
    }
}
