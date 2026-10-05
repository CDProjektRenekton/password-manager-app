package com.securevault.security.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.SecureRandom

/**
 * Argon2id parameters. They are persisted alongside the vault so they can be raised in a later
 * release (re-wrap on next unlock) without breaking existing vaults.
 *
 * Defaults exceed the OWASP 2024 minimum (m=19 MiB, t=2, p=1) while keeping unlock under ~1 s on
 * mid-range 2022+ hardware. Benchmark on your lowest supported device before changing them.
 */
data class KdfParams(
    val salt: ByteArray,
    val memoryKiB: Int = DEFAULT_MEMORY_KIB,
    val iterations: Int = DEFAULT_ITERATIONS,
    val parallelism: Int = DEFAULT_PARALLELISM,
) {
    init {
        require(salt.size >= SALT_LENGTH) { "Salt too short" }
        require(memoryKiB >= 19 * 1024) { "Argon2 memory cost below OWASP minimum" }
        require(iterations >= 2) { "Argon2 iteration count below OWASP minimum" }
        require(parallelism >= 1)
    }

    override fun equals(other: Any?): Boolean =
        other is KdfParams && salt.contentEquals(other.salt) && memoryKiB == other.memoryKiB &&
            iterations == other.iterations && parallelism == other.parallelism

    override fun hashCode(): Int =
        ((salt.contentHashCode() * 31 + memoryKiB) * 31 + iterations) * 31 + parallelism

    companion object {
        const val SALT_LENGTH = 16
        const val DEFAULT_MEMORY_KIB = 64 * 1024 // 64 MiB
        const val DEFAULT_ITERATIONS = 3
        const val DEFAULT_PARALLELISM = 2

        fun generate(random: SecureRandom = SecureRandom()): KdfParams =
            KdfParams(salt = ByteArray(SALT_LENGTH).also(random::nextBytes))
    }
}

/**
 * Derives the 256-bit Key-Encryption-Key (KEK) from the master password with Argon2id.
 * The master password itself is never stored, hashed-for-verification, or written anywhere:
 * a wrong password simply fails the AES-GCM tag check when unwrapping the vault key.
 */
class Argon2KeyDerivation(private val argon2: Argon2Kt = Argon2Kt()) {

    /** Must be called off the main thread: deliberately slow and memory-hard. Caller wipes result. */
    fun deriveKey(password: CharArray, params: KdfParams): ByteArray {
        val passwordBytes = SecureMemory.toUtf8Bytes(password)
        return passwordBytes.useThenWipe { pw ->
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pw, // Argon2Kt copies into a direct buffer and wipes it itself.
                salt = params.salt,
                tCostInIterations = params.iterations,
                mCostInKibibyte = params.memoryKiB,
                parallelism = params.parallelism,
                hashLengthInBytes = KEY_LENGTH,
            )
            try {
                result.rawHashAsByteArray()
            } finally {
                // The native result buffers also contain the derived key; zero them.
                SecureMemory.wipe(result.rawHash)
                SecureMemory.wipe(result.encodedOutput)
            }
        }
    }

    companion object {
        const val KEY_LENGTH = 32
    }
}
