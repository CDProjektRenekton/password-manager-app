package com.securevault.security.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption/decryption with keys generated *inside* the AndroidKeyStore
 * (TEE, or the StrongBox secure element when present). Key material is non-exportable: the app
 * only ever holds an opaque handle, and every encrypt/decrypt runs in secure hardware.
 *
 * Two kinds of Keystore keys are used:
 *  - [KeyPolicy.DEVICE_BOUND]: no user auth, usable only while the device is unlocked. Wraps the
 *    vault metadata so a copied app-data directory is useless off this device (an attacker cannot
 *    even start an offline brute-force of the master password without the hardware).
 *  - [KeyPolicy.BIOMETRIC_BOUND]: every single use must be authorised by a Class 3 (strong)
 *    biometric through BiometricPrompt + CryptoObject, and the key is permanently invalidated
 *    when a new fingerprint/face is enrolled.
 */
class CryptographyManager(
    private val strongBoxAvailable: Boolean,
) {
    enum class KeyPolicy { DEVICE_BOUND, BIOMETRIC_BOUND }

    /** The Keystore key behind a biometric cipher was invalidated (new enrollment / lock screen removed). */
    class KeyInvalidatedException(cause: Throwable) : GeneralSecurityException(cause)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // ---------------------------------------------------------------------------------------------
    // Key management
    // ---------------------------------------------------------------------------------------------

    fun hasKey(alias: String): Boolean = keyStore.containsAlias(alias)

    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    @Synchronized
    fun getOrCreateSecretKey(alias: String, policy: KeyPolicy): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return if (strongBoxAvailable) {
            try {
                generateKey(alias, policy, useStrongBox = true)
            } catch (e: StrongBoxUnavailableException) {
                generateKey(alias, policy, useStrongBox = false)
            }
        } else {
            generateKey(alias, policy, useStrongBox = false)
        }
    }

    private fun generateKey(alias: String, policy: KeyPolicy, useStrongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Keystore generates the IV; the app can never force IV reuse.
            .setRandomizedEncryptionRequired(true)
            // Key is unusable while the device is locked, even by this app.
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(useStrongBox)

        if (policy == KeyPolicy.BIOMETRIC_BOUND) {
            builder
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Timeout 0 = auth-per-use: each operation needs its own BiometricPrompt approval.
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    // ---------------------------------------------------------------------------------------------
    // Cipher initialisation (also what BiometricPrompt.CryptoObject wraps)
    // ---------------------------------------------------------------------------------------------

    fun getInitializedCipherForEncryption(alias: String, policy: KeyPolicy): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = getOrCreateSecretKey(alias, policy)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey(alias)
            throw KeyInvalidatedException(e)
        }
        return cipher
    }

    fun getInitializedCipherForDecryption(alias: String, iv: ByteArray): Cipher {
        val key = keyStore.getKey(alias, null) as? SecretKey
            ?: throw GeneralSecurityException("Keystore key '$alias' not found")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(EncryptedPayload.GCM_TAG_LENGTH_BITS, iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey(alias)
            throw KeyInvalidatedException(e)
        }
        return cipher
    }

    // ---------------------------------------------------------------------------------------------
    // Encrypt / decrypt with an initialised (possibly biometric-authorised) cipher
    // ---------------------------------------------------------------------------------------------

    fun encryptData(plaintext: ByteArray, cipher: Cipher, aad: ByteArray? = null): EncryptedPayload {
        aad?.let(cipher::updateAAD)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(cipher.iv, ciphertext)
    }

    /** Caller owns (and must wipe) the returned plaintext. */
    fun decryptData(payload: EncryptedPayload, cipher: Cipher, aad: ByteArray? = null): ByteArray {
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(payload.ciphertext)
    }

    // ---------------------------------------------------------------------------------------------
    // Convenience for DEVICE_BOUND keys (no user-auth needed, so no CryptoObject dance)
    // ---------------------------------------------------------------------------------------------

    fun encryptWithDeviceKey(alias: String, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = getInitializedCipherForEncryption(alias, KeyPolicy.DEVICE_BOUND)
        return encryptData(plaintext, cipher, aad).toByteArray()
    }

    fun decryptWithDeviceKey(alias: String, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        val payload = EncryptedPayload.fromByteArray(blob)
        val cipher = getInitializedCipherForDecryption(alias, payload.iv)
        return decryptData(payload, cipher, aad)
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256

        const val ALIAS_DEVICE_KEY = "securevault.device_binding.v1"
        const val ALIAS_BIOMETRIC_KEY = "securevault.biometric_unlock.v1"
    }
}
