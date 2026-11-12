package app.ulap.core.auth.di

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import app.ulap.core.auth.account.SessionAccountRepository
import app.ulap.core.auth.session.SessionFile
import app.ulap.core.auth.session.SessionManager
import app.ulap.core.auth.session.SessionSerializer
import app.ulap.core.auth.session.SessionStore
import app.ulap.core.domain.account.AccountRepository
import app.ulap.core.network.AccessTokenSource
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.inject.Singleton
import okio.FileSystem
import okio.Path.Companion.toOkioPath

@Module
@InstallIn(SingletonComponent::class)
internal object AuthModule {
    /**
     * One DataStore for the session file in the whole app. noBackupFilesDir is never included in
     * Auto Backup or a device transfer, so a refresh token never leaves this phone.
     */
    @Provides
    @Singleton
    fun providesSessionStore(@ApplicationContext context: Context): SessionStore = SessionStore(
        DataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = SessionSerializer(lazy { sessionAead(context) }),
                producePath = { File(context.noBackupFilesDir, SESSION_FILE).toOkioPath() },
            ),
            // An unreadable file (a lost Keystore key) means signed out, not a crash.
            corruptionHandler = ReplaceFileCorruptionHandler { SessionFile() },
        ),
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal interface AuthBindsModule {
    /** core:network's bearer plugin gets its tokens from the session. */
    @Binds
    fun bindsAccessTokenSource(manager: SessionManager): AccessTokenSource

    /** Features sign in and out through the domain interface only (README §5). */
    @Binds
    fun bindsAccountRepository(repository: SessionAccountRepository): AccountRepository
}

private const val SESSION_FILE = "session.enc"
private const val KEYSET_PREFS = "ulap_session_keyset"
private const val KEYSET_NAME = "session_keyset"

// Its own Keystore key, apart from the database's (DatabasePassphrase), so losing one can't
// take the other with it.
private const val MASTER_KEY_ALIAS = "ulap_session_master_key"
private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"

/**
 * An AES-256-GCM key, itself locked by a key that never leaves the Android Keystore. Slow, so
 * DataStore calls it on its own background thread when it first reads the file.
 */
private fun sessionAead(context: Context): Aead {
    AeadConfig.register()
    val manager = try {
        keysetManager(context)
    } catch (_: GeneralSecurityException) {
        startOver(context)
    } catch (_: IOException) {
        startOver(context)
    } catch (_: ProviderException) {
        // The Keystore throws this RuntimeException for some broken-key states.
        startOver(context)
    }
    return manager.keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}

/**
 * The saved keyset can't be unlocked any more: make a new one. The old session file then can't
 * be decrypted and reads as signed out, the same as DatabasePassphrase handles a lost key.
 */
private fun startOver(context: Context): AndroidKeysetManager {
    context.deleteSharedPreferences(KEYSET_PREFS)
    try {
        // Tink refuses to replace a master key that exists but is unusable.
        AndroidKeystore.deleteKey(MASTER_KEY_ALIAS)
    } catch (_: GeneralSecurityException) {
        // No Keystore at all: Tink falls back to an unwrapped keyset below.
    }
    return keysetManager(context)
}

private fun keysetManager(context: Context): AndroidKeysetManager = AndroidKeysetManager.Builder()
    .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
    .withMasterKeyUri(MASTER_KEY_URI)
    .build()
