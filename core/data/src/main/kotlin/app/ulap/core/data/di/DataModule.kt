package app.ulap.core.data.di

import app.ulap.core.data.checkin.OfflineFirstCheckInRepository
import app.ulap.core.domain.checkin.CheckInRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock

/** Tells Hilt which implementation to hand out for each domain repository. */
@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindsCheckInRepository(repository: OfflineFirstCheckInRepository): CheckInRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object ClockModule {
    /** The real clock. Tests pass a fixed one instead, so times in them are exact. */
    @Provides
    fun providesClock(): Clock = Clock.System
}
