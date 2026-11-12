package app.ulap.core.auth.session

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import java.io.File
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** A fresh AES-256-GCM key in memory: the Android Keystore only exists on a device. */
internal fun newTestAead(): Aead {
    AeadConfig.register()
    return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM, RegistryConfiguration.get())
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}

/** The real SessionStore and serializer over [file], set up as AuthModule does. */
internal fun testSessionStore(file: File, scope: CoroutineScope, aead: Aead = newTestAead()) = SessionStore(
    DataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, SessionSerializer(lazyOf(aead)), producePath = { file.toOkioPath() }),
        corruptionHandler = ReplaceFileCorruptionHandler { SessionFile() },
        scope = scope,
    ),
)
