package com.securevault.security.crypto

import java.security.GeneralSecurityException

/**
 * AES-256-GCM output. Serialized layout (all ciphertexts in the app share it):
 *
 * ```
 * | version (1 byte) | iv length (1 byte) | iv (12 bytes) | ciphertext || 128-bit tag |
 * ```
 *
 * The version byte lets the format/algorithm evolve without ambiguity.
 */
class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray) {

    fun toByteArray(): ByteArray {
        val out = ByteArray(HEADER_SIZE + iv.size + ciphertext.size)
        out[0] = FORMAT_VERSION
        out[1] = iv.size.toByte()
        System.arraycopy(iv, 0, out, HEADER_SIZE, iv.size)
        System.arraycopy(ciphertext, 0, out, HEADER_SIZE + iv.size, ciphertext.size)
        return out
    }

    companion object {
        const val FORMAT_VERSION: Byte = 1
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
        private const val HEADER_SIZE = 2

        fun fromByteArray(bytes: ByteArray): EncryptedPayload {
            if (bytes.size < HEADER_SIZE + GCM_IV_LENGTH + GCM_TAG_LENGTH_BITS / 8) {
                throw GeneralSecurityException("Ciphertext too short")
            }
            if (bytes[0] != FORMAT_VERSION) throw GeneralSecurityException("Unsupported ciphertext version ${bytes[0]}")
            val ivLength = bytes[1].toInt()
            if (ivLength != GCM_IV_LENGTH) throw GeneralSecurityException("Unexpected IV length")
            val iv = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + ivLength)
            val ciphertext = bytes.copyOfRange(HEADER_SIZE + ivLength, bytes.size)
            return EncryptedPayload(iv, ciphertext)
        }
    }
}
