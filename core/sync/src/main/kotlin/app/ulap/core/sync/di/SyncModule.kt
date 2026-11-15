package app.ulap.core.sync.di

import app.ulap.core.domain.sync.SyncRepository
import app.ulap.core.domain.sync.SyncSignInPreparer
import app.ulap.core.sync.AccountHandover
import app.ulap.core.sync.AppForeground
import app.ulap.core.sync.Connectivity
import app.ulap.core.sync.ConnectivityManagerConnectivity
import app.ulap.core.sync.ProcessAppForeground
import app.ulap.core.sync.RoomSyncRepository
import app.ulap.core.sync.SessionManagerSyncSession
import app.ulap.core.sync.SyncScheduler
import app.ulap.core.sync.SyncSession
import app.ulap.core.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface SyncModule {
    /** The Account screen reads sync through the domain interface only (README §5). */
    @Binds
    fun bindsSyncRepository(repository: RoomSyncRepository): SyncRepository

    /** AccountRepository (core:auth) hands each sign-in over here. */
    @Binds
    fun bindsSyncSignInPreparer(handover: AccountHandover): SyncSignInPreparer

    @Binds
    fun bindsSyncSession(session: SessionManagerSyncSession): SyncSession

    @Binds
    fun bindsSyncScheduler(scheduler: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    fun bindsAppForeground(foreground: ProcessAppForeground): AppForeground

    @Binds
    fun bindsConnectivity(connectivity: ConnectivityManagerConnectivity): Connectivity
}
