package com.securevault.security.password

import java.security.SecureRandom
import kotlin.math.log2

data class GeneratorOptions(
    val length: Int = 20,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
) {
    val isValid: Boolean
        get() = length in PasswordGenerator.MIN_LENGTH..PasswordGenerator.MAX_LENGTH &&
            (uppercase || lowercase || digits || symbols)
}

/**
 * Cryptographically secure password generator.
 *
 *  - Uses [SecureRandom] only (never kotlin.random / java.util.Random).
 *  - Index selection uses SecureRandom.nextInt(bound), which is rejection-sampled: no modulo bias.
 *  - Guarantees at least one character from every selected class, then Fisher-Yates shuffles so
 *    those guaranteed characters don't sit at predictable positions.
 *  - Returns a CharArray; the caller wipes it after use.
 */
class PasswordGenerator(private val random: SecureRandom = SecureRandom()) {

    fun generate(options: GeneratorOptions): CharArray {
        require(options.length in MIN_LENGTH..MAX_LENGTH) { "Length must be $MIN_LENGTH-$MAX_LENGTH" }
        val sets = characterSets(options)
        require(sets.isNotEmpty()) { "Select at least one character set" }

        val pool = sets.flatMap { it.asIterable() }.toCharArray()
        val out = CharArray(options.length)
        sets.forEachIndexed { i, set -> out[i] = set[random.nextInt(set.size)] }
        for (i in sets.size until out.size) out[i] = pool[random.nextInt(pool.size)]
        shuffle(out)
        return out
    }

    /** Upper-bound entropy estimate in bits: length x log2(pool size). */
    fun entropyBits(options: GeneratorOptions): Double {
        val poolSize = characterSets(options).sumOf { it.size }
        return if (poolSize == 0) 0.0 else options.length * log2(poolSize.toDouble())
    }

    private fun shuffle(chars: CharArray) {
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
    }

    private fun characterSets(options: GeneratorOptions): List<CharArray> = buildList {
        fun add(enabled: Boolean, chars: String) {
            if (!enabled) return
            val filtered = if (options.excludeAmbiguous) chars.filterNot { it in AMBIGUOUS } else chars
            if (filtered.isNotEmpty()) add(filtered.toCharArray())
        }
        add(options.uppercase, UPPER)
        add(options.lowercase, LOWER)
        add(options.digits, DIGITS)
        add(options.symbols, SYMBOLS)
    }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
        private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        private const val DIGITS = "0123456789"
        // No quotes/backslash/space: they break too many sites' input validation.
        private const val SYMBOLS = "!#$%&()*+,-./:;<=>?@[]^_{|}~"
        private const val AMBIGUOUS = "Il1O0o|"
    }
}
