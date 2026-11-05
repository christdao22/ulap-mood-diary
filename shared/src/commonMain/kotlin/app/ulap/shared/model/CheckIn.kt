package app.ulap.shared.model

import app.ulap.shared.validation.CheckInInput
import kotlin.time.Instant

/**
 * A saved check-in, as screens see it (technical-plan.md §6 without the sync columns, which
 * only sync reads). Deleted check-ins never reach screens.
 */
data class CheckIn(
    val id: String,
    val moodCode: String,
    val energy: Int,
    val stress: Int,
    val stressAfter: Int?,
    val trigger: Trigger?,
    /** Journal text: never log it. */
    val triggerOther: String?,
    /** Journal text: never log it. */
    val note: String?,
    val supportUsed: SupportType,
    val isGoodMoment: Boolean,
    val loggedAt: Instant,
    val tzOffsetMin: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** The editable fields, to change some and save them again (an edit, or "How stressed now?"). */
    fun toInput(): CheckInInput = CheckInInput(
        moodCode = moodCode,
        energy = energy,
        stress = stress,
        stressAfter = stressAfter,
        trigger = trigger,
        triggerOther = triggerOther,
        note = note,
        supportUsed = supportUsed,
        isGoodMoment = isGoodMoment,
        loggedAt = loggedAt,
        tzOffsetMin = tzOffsetMin,
    )
}
