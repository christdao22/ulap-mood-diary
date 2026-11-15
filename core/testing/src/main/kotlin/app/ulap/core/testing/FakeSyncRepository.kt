package app.ulap.core.testing

import app.ulap.core.domain.sync.SyncRepository
import app.ulap.core.domain.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow

/** Sync status in memory. Tests set [status]; [calls] records what the screen asked for. */
class FakeSyncRepository(initial: SyncStatus = SyncStatus()) : SyncRepository {
    override val status: MutableStateFlow<SyncStatus> = MutableStateFlow(initial)

    val calls = mutableListOf<String>()

    override suspend fun syncNow() {
        calls += "syncNow"
    }

    override suspend fun retryStuck() {
        calls += "retryStuck"
        status.value = status.value.copy(stuckCount = 0)
    }

    override suspend fun answerAddCheckIns(add: Boolean) {
        calls += "add:$add"
        status.value = status.value.copy(askToAddCheckIns = false)
    }
}
