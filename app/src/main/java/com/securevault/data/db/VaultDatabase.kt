package com.securevault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [CredentialEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun credentialDao(): CredentialDao

    companion object {
        const val DATABASE_NAME = "vault.db"

        /** Must be called once per process before opening any SQLCipher database. */
        fun loadNativeLibrary() {
            System.loadLibrary("sqlcipher")
        }

        /**
         * Opens the SQLCipher-encrypted Room database.
         *
         * @param rawKeyPassphrase SQLCipher raw-key literal (`x'<64 hex chars>'`) as ASCII bytes.
         *   A raw key skips SQLCipher's internal PBKDF2 because the key is already a 256-bit
         *   HKDF output of the random vault key. The caller keeps ownership of the array and
         *   zeroes it after [close] (SQLCipher re-reads it when it opens new pool connections).
         */
        fun open(context: Context, rawKeyPassphrase: ByteArray): VaultDatabase {
            val factory = SupportOpenHelperFactory(
                rawKeyPassphrase,
                MemorySecurityHook,
                /* enableWriteAheadLogging = */ false,
            )
            return Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(factory)
                // Single connection, no WAL file: fewer plaintext page copies in memory and no
                // -wal sidecar file. Throughput is irrelevant at password-manager scale.
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        }
    }

    /**
     * Ask SQLCipher to zero its internal buffers when freeing them (off by default since 4.5 for
     * performance). Best-effort: the PRAGMA is ignored by builds that don't support it.
     */
    private object MemorySecurityHook : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection) = Unit

        override fun postKey(connection: SQLiteConnection) {
            runCatching { connection.executeRaw("PRAGMA cipher_memory_security = ON;", null, null) }
        }
    }
}
