package com.securevault

import android.content.Context
import com.securevault.data.db.VaultDatabase
import com.securevault.security.crypto.CryptographyManager
import com.securevault.security.vault.UnlockThrottle
import com.securevault.security.vault.VaultMetadataStore

/** Removes every trace of a vault so each test starts from a fresh install state. */
fun resetVaultStorage(context: Context) {
    val crypto = CryptographyManager(strongBoxAvailable = false)
    VaultMetadataStore(context, crypto).delete()
    context.deleteDatabase(VaultDatabase.DATABASE_NAME)
    UnlockThrottle(context).reset()
    crypto.deleteKey(CryptographyManager.ALIAS_DEVICE_KEY)
    crypto.deleteKey(CryptographyManager.ALIAS_BIOMETRIC_KEY)
}
