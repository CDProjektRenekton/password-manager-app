package com.securevault.security.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869). Used to derive independent, purpose-bound sub-keys (SQLCipher key,
 * field-encryption key) from the single random vault key, so that compromise or misuse of one
 * sub-key never exposes another.
 */
object Hkdf {
    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32

    fun deriveKey(ikm: ByteArray, info: ByteArray, length: Int = 32, salt: ByteArray? = null): ByteArray {
        require(length in 1..255 * HASH_LEN) { "Invalid HKDF output length" }
        val prk = extract(salt ?: ByteArray(HASH_LEN), ikm)
        return prk.useThenWipe { expand(it, info, length) }
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(salt, ALGORITHM))
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(prk, ALGORITHM))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            val block = mac.doFinal()
            SecureMemory.wipe(previous)
            val toCopy = minOf(block.size, length - offset)
            System.arraycopy(block, 0, out, offset, toCopy)
            offset += toCopy
            counter++
            previous = block
        }
        SecureMemory.wipe(previous)
        return out
    }
}
