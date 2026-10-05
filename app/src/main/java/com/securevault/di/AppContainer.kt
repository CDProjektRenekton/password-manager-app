package com.securevault.di

import android.app.Application
import android.content.pm.PackageManager
import com.securevault.data.repository.CredentialRepository
import com.securevault.security.clipboard.SecureClipboard
import com.securevault.security.crypto.Argon2KeyDerivation
import com.securevault.security.crypto.CryptographyManager
import com.securevault.security.lock.AutoLockManager
import com.securevault.security.password.PasswordGenerator
import com.securevault.security.password.PasswordStrengthEstimator
import com.securevault.security.vault.UnlockThrottle
import com.securevault.security.vault.VaultMetadataStore
import com.securevault.security.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph (process-wide singletons). Deliberately no DI framework: the security
 * core is small and explicit wiring keeps the key-handling object graph easy to audit.
 */
class AppContainer(app: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val cryptographyManager = CryptographyManager(
        strongBoxAvailable = app.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE),
    )

    val vaultSession = VaultSession(
        context = app,
        cryptographyManager = cryptographyManager,
        keyDerivation = Argon2KeyDerivation(),
        metadataStore = VaultMetadataStore(app, cryptographyManager),
        throttle = UnlockThrottle(app),
    )

    val autoLockManager = AutoLockManager(vaultSession, applicationScope)
    val credentialRepository = CredentialRepository(vaultSession)
    val secureClipboard = SecureClipboard(app, applicationScope)
    val passwordGenerator = PasswordGenerator()
    val strengthEstimator = PasswordStrengthEstimator()
}
