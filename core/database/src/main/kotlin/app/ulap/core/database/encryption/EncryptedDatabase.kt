package app.ulap.core.database.encryption

import android.content.Context
import androidx.room.Room
import app.ulap.core.database.UlapDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the app's database, encrypted with SQLCipher (technical-plan.md §6, D3).
 *
 * Building is fast. The passphrase is unlocked, and the file opened, on the first query, off the
 * main thread (see [DeferredOpenHelperFactory]).
 *
 * If the passphrase is new, any old database file is deleted first, because it can't be opened
 * without the old passphrase. That loses a guest's check-ins and any that hadn't synced, which is
 * the price of real encryption (technical-plan.md §6, "If the Keystore key is lost").
 */
internal fun buildEncryptedDatabase(
    context: Context,
    name: String = UlapDatabase.NAME,
    passphraseStore: () -> DatabasePassphrase = { DatabasePassphrase.forApp(context) },
): UlapDatabase = Room.databaseBuilder(context, UlapDatabase::class.java, name)
    .openHelperFactory(
        DeferredOpenHelperFactory { configuration ->
            val passphrase = passphraseStore().getOrCreate()
            if (passphrase.isNew) {
                // On a first install there's nothing to delete. Phase 7 sync also has to reset
                // last_pull_seq to 0 here so a signed-in user's check-ins come back.
                context.deleteDatabase(name)
            }
            System.loadLibrary("sqlcipher")
            SupportOpenHelperFactory(passphrase.bytes).create(configuration)
        },
    )
    .build()
