package com.securevault.security.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with in-memory session keys (keys that only exist while the vault is unlocked and
 * are themselves protected at rest by the Keystore + Argon2id, see VaultSession).
 *
 * Every call uses a fresh random 96-bit IV; [aad] binds the ciphertext to its context (row id +
 * column name) so ciphertexts cannot be swapped between rows/fields without detection.
 */
object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val secureRandom = SecureRandom()

    fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = ByteArray(EncryptedPayload.GCM_IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(EncryptedPayload.GCM_TAG_LENGTH_BITS, iv))
        aad?.let(cipher::updateAAD)
        return EncryptedPayload(iv, cipher.doFinal(plaintext)).toByteArray()
    }

    /** @throws javax.crypto.AEADBadTagException if the data, key or AAD don't match. */
    fun decrypt(key: SecretKey, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        val payload = EncryptedPayload.fromByteArray(blob)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(EncryptedPayload.GCM_TAG_LENGTH_BITS, payload.iv))
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(payload.ciphertext)
    }
}
