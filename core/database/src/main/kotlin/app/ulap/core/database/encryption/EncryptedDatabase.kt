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
            val passphrase = passphraseStore().getOrCreate(
                beforeNewPassphrase = {
                    // On a first install there's nothing to delete. The sync position lives in
                    // the same database, so the new one pulls from 0 and a signed-in person's
                    // check-ins come back on the next sync.
                    context.deleteDatabase(name)
                },
            )
            System.loadLibrary("sqlcipher")
            SupportOpenHelperFactory(passphrase.bytes).create(configuration)
        },
    )
    .build()
