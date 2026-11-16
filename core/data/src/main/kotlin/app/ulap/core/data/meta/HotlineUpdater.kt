package app.ulap.core.data.meta

import app.ulap.core.common.dispatchers.ApplicationScope
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.network.ApiResult
import app.ulap.core.network.meta.MetaApi
import app.ulap.shared.rules.HotlineRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Asks `GET /meta` for the hotline once each time the app starts, and saves a changed number in
 * DataStore (api-spec.md §9.3). Screens only read the saved number, so the hotline never waits
 * for the network: offline, or on any error, the phone keeps what it has, which starts as the
 * built-in NCMH 1553 (technical-plan.md §13). Safety (🔒 in PHASES.md): a change here needs two
 * reviewers.
 */
@Singleton
class HotlineUpdater @Inject internal constructor(
    private val meta: MetaApi,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { update() }
    }

    /** One `/meta` call. Any failure is ignored: the next app start tries again. */
    internal suspend fun update() {
        val answer = try {
            meta.meta()
        } catch (e: CancellationException) {
            throw e
        } catch (
            // apiCall throws when a 2xx body isn't a MetaResponse (a captive-portal or login page,
            // a renamed field). Uncaught, that crashes the app on every start and takes the hotline
            // with it, so any failure here just keeps the saved number.
            @Suppress("TooGenericExceptionCaught") _: Exception,
        ) {
            return
        }
        if (answer !is ApiResult.Success) return
        val saved = settings.settings.first().hotlineNumber
        val next = HotlineRules.savedAfter(answer.value.hotline, saved)
        if (next != saved) settings.setHotlineNumber(next)
    }
}
