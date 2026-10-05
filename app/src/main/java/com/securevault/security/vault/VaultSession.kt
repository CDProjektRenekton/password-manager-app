package com.securevault.security.vault

import android.content.Context
import com.securevault.data.db.VaultDatabase
import com.securevault.security.crypto.AesGcm
import com.securevault.security.crypto.Argon2KeyDerivation
import com.securevault.security.crypto.CryptographyManager
import com.securevault.security.crypto.EncryptedPayload
import com.securevault.security.crypto.Hkdf
import com.securevault.security.crypto.KdfParams
import com.securevault.security.crypto.SecureMemory
import com.securevault.security.crypto.SqlCipherKey
import com.securevault.security.crypto.useThenWipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

sealed interface VaultState {
    data object NotInitialized : VaultState
    data object Locked : VaultState
    /** [generation] changes on every unlock so observers can tell sessions apart. */
    data class Unlocked(val generation: Long) : VaultState
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object WrongPassword : UnlockResult
    data class Throttled(val retryInMillis: Long) : UnlockResult
    data object BiometricInvalidated : UnlockResult
    data class Error(val cause: Throwable) : UnlockResult
}

/**
 * Owns the vault's key hierarchy and its lifecycle:
 *
 * ```
 *  master password ──Argon2id(salt, 64 MiB, t=3)──► KEK ─┐
 *                                                        ├─ AES-GCM unwrap ─► Vault Key (VK, 256-bit random)
 *  BiometricPrompt ──► Keystore key (auth-per-use) ──────┘                       │
 *                                                            HKDF-SHA256 ┌───────┴────────┐
 *                                                                        ▼                ▼
 *                                                             SQLCipher raw key    field AES-GCM key
 * ```
 *
 * The VK and its sub-keys exist only in memory while [state] is [VaultState.Unlocked]. [lock]
 * closes the database and zero-fills every key array.
 */
class VaultSession(
    private val context: Context,
    private val cryptographyManager: CryptographyManager,
    private val keyDerivation: Argon2KeyDerivation,
    private val metadataStore: VaultMetadataStore,
    private val throttle: UnlockThrottle,
) {
    private val secureRandom = SecureRandom()
    private val guard = Any()

    private val _state = MutableStateFlow<VaultState>(
        if (metadataStore.exists()) VaultState.Locked else VaultState.NotInitialized,
    )
    val state: StateFlow<VaultState> = _state.asStateFlow()

    // Guarded by [guard].
    private var keys: SessionKeys? = null
    private var database: VaultDatabase? = null
    private var generation = 0L

    val isUnlocked: Boolean get() = _state.value is VaultState.Unlocked

    // ---------------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------------

    /** Creates a brand-new vault. [masterPassword] is NOT wiped here; the caller owns it. */
    suspend fun createVault(masterPassword: CharArray) = withContext(Dispatchers.Default) {
        check(!metadataStore.exists()) { "Vault already exists" }
        // Start from a clean slate in case a previous install left orphaned state behind.
        context.deleteDatabase(VaultDatabase.DATABASE_NAME)
        cryptographyManager.deleteKey(CryptographyManager.ALIAS_BIOMETRIC_KEY)
        throttle.reset()

        val params = KdfParams.generate(secureRandom)
        val vaultKey = ByteArray(VAULT_KEY_LENGTH).also(secureRandom::nextBytes)
        vaultKey.useThenWipe { vk ->
            val wrapped = keyDerivation.deriveKey(masterPassword, params).useThenWipe { kek ->
                AesGcm.encrypt(SecretKeySpec(kek, "AES"), vk, AAD_PASSWORD_WRAP)
            }
            metadataStore.write(VaultMetadata(params, wrapped, biometricWrappedVaultKey = null))
            openSession(vk)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Unlock
    // ---------------------------------------------------------------------------------------------

    suspend fun unlockWithPassword(masterPassword: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val wait = throttle.remainingLockoutMillis()
        if (wait > 0) return@withContext UnlockResult.Throttled(wait)
        try {
            val metadata = metadataStore.read()
            val vaultKey = try {
                keyDerivation.deriveKey(masterPassword, metadata.kdfParams).useThenWipe { kek ->
                    AesGcm.decrypt(SecretKeySpec(kek, "AES"), metadata.passwordWrappedVaultKey, AAD_PASSWORD_WRAP)
                }
            } catch (e: AEADBadTagException) {
                throttle.recordFailure()
                return@withContext UnlockResult.WrongPassword
            }
            throttle.reset()
            vaultKey.useThenWipe { openSession(it) }
            UnlockResult.Success
        } catch (t: Throwable) {
            UnlockResult.Error(t)
        }
    }

    val isBiometricEnabled: Boolean
        get() = runCatching { metadataStore.read().biometricWrappedVaultKey != null }.getOrDefault(false)

    /**
     * Returns a Keystore cipher to hand to BiometricPrompt as a CryptoObject. The cipher can only
     * be used after the user authenticates; then pass it to [unlockWithBiometricCipher].
     * Returns null if biometric unlock is not enabled.
     */
    fun createBiometricUnlockCipher(): Cipher? {
        val wrapped = metadataStore.read().biometricWrappedVaultKey ?: return null
        return try {
            cryptographyManager.getInitializedCipherForDecryption(
                CryptographyManager.ALIAS_BIOMETRIC_KEY,
                EncryptedPayload.fromByteArray(wrapped).iv,
            )
        } catch (e: CryptographyManager.KeyInvalidatedException) {
            // New fingerprint enrolled / screen lock removed: the wrapped VK is permanently
            // unrecoverable via biometrics. Fall back to the master password.
            clearBiometricWrap()
            throw e
        }
    }

    suspend fun unlockWithBiometricCipher(authenticatedCipher: Cipher): UnlockResult =
        withContext(Dispatchers.Default) {
            try {
                val wrapped = metadataStore.read().biometricWrappedVaultKey
                    ?: return@withContext UnlockResult.BiometricInvalidated
                val vaultKey = cryptographyManager.decryptData(
                    EncryptedPayload.fromByteArray(wrapped),
                    authenticatedCipher,
                    AAD_BIOMETRIC_WRAP,
                )
                throttle.reset()
                vaultKey.useThenWipe { openSession(it) }
                UnlockResult.Success
            } catch (t: Throwable) {
                UnlockResult.Error(t)
            }
        }

    // ---------------------------------------------------------------------------------------------
    // Biometric enrollment (only while unlocked)
    // ---------------------------------------------------------------------------------------------

    fun createBiometricEnrollmentCipher(): Cipher {
        check(isUnlocked) { "Vault must be unlocked to enable biometrics" }
        // Always mint a fresh key so an old, possibly compromised wrap can't be reused.
        cryptographyManager.deleteKey(CryptographyManager.ALIAS_BIOMETRIC_KEY)
        return cryptographyManager.getInitializedCipherForEncryption(
            CryptographyManager.ALIAS_BIOMETRIC_KEY,
            CryptographyManager.KeyPolicy.BIOMETRIC_BOUND,
        )
    }

    suspend fun completeBiometricEnrollment(authenticatedCipher: Cipher) = withContext(Dispatchers.Default) {
        val wrapped = synchronized(guard) {
            val vk = checkNotNull(keys) { "Vault locked" }.vaultKey
            cryptographyManager.encryptData(vk, authenticatedCipher, AAD_BIOMETRIC_WRAP).toByteArray()
        }
        metadataStore.write(metadataStore.read().copy(biometricWrappedVaultKey = wrapped))
    }

    fun disableBiometric() = clearBiometricWrap()

    private fun clearBiometricWrap() {
        runCatching { metadataStore.write(metadataStore.read().copy(biometricWrappedVaultKey = null)) }
        cryptographyManager.deleteKey(CryptographyManager.ALIAS_BIOMETRIC_KEY)
    }

    // ---------------------------------------------------------------------------------------------
    // Session
    // ---------------------------------------------------------------------------------------------

    /** Must run off the main thread (opening SQLCipher derives/validates keys). */
    private fun openSession(vaultKey: ByteArray) {
        val dbKey = Hkdf.deriveKey(vaultKey, INFO_SQLCIPHER)
        val passphrase = dbKey.useThenWipe(SqlCipherKey::rawKeyLiteral)
        val fieldKeyBytes = Hkdf.deriveKey(vaultKey, INFO_FIELDS)

        val db = VaultDatabase.open(context, passphrase)
        try {
            // Force the open now: throws immediately if the key doesn't match the file.
            db.openHelper.writableDatabase
        } catch (t: Throwable) {
            db.close()
            SecureMemory.wipe(passphrase, fieldKeyBytes)
            throw t
        }

        synchronized(guard) {
            closeSessionLocked()
            keys = SessionKeys(
                vaultKey = vaultKey.copyOf(),
                databasePassphrase = passphrase,
                fieldKeyBytes = fieldKeyBytes,
                fieldKey = SecretKeySpec(fieldKeyBytes, "AES"),
            )
            database = db
            generation++
            _state.value = VaultState.Unlocked(generation)
        }
    }

    /** Immediately locks the vault: closes the DB and zeroes every in-memory key. Idempotent. */
    fun lock() {
        synchronized(guard) {
            if (keys == null && database == null) return
            closeSessionLocked()
            _state.value = if (metadataStore.exists()) VaultState.Locked else VaultState.NotInitialized
        }
    }

    private fun closeSessionLocked() {
        runCatching { database?.close() }
        database = null
        keys?.wipe()
        keys = null
    }

    /** Runs [block] with the open database and field key, or throws [VaultLockedException]. */
    fun <T> withUnlocked(block: (VaultDatabase, SecretKey) -> T): T {
        val (db, key) = synchronized(guard) {
            val k = keys ?: throw VaultLockedException()
            val d = database ?: throw VaultLockedException()
            d to k.fieldKey
        }
        return block(db, key)
    }

    fun databaseOrNull(): VaultDatabase? = synchronized(guard) { database }

    private class SessionKeys(
        val vaultKey: ByteArray,
        val databasePassphrase: ByteArray,
        val fieldKeyBytes: ByteArray,
        /**
         * JCA copies key bytes into SecretKeySpec and offers no way to zero that copy; it becomes
         * unreachable on lock and is collected. This is an inherent JCA limitation.
         */
        val fieldKey: SecretKey,
    ) {
        fun wipe() = SecureMemory.wipe(vaultKey, databasePassphrase, fieldKeyBytes)
    }

    companion object {
        private const val VAULT_KEY_LENGTH = 32
        private val INFO_SQLCIPHER = "securevault/sqlcipher/v1".toByteArray(Charsets.UTF_8)
        private val INFO_FIELDS = "securevault/fields/v1".toByteArray(Charsets.UTF_8)
        private val AAD_PASSWORD_WRAP = "securevault/vk/password/v1".toByteArray(Charsets.UTF_8)
        private val AAD_BIOMETRIC_WRAP = "securevault/vk/biometric/v1".toByteArray(Charsets.UTF_8)
    }
}

class VaultLockedException : IllegalStateException("Vault is locked")
