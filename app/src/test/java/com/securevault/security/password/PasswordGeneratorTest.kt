package com.securevault.security.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private val generator = PasswordGenerator()

    @Test
    fun respectsLengthBounds() {
        for (length in PasswordGenerator.MIN_LENGTH..PasswordGenerator.MAX_LENGTH) {
            assertEquals(length, generator.generate(GeneratorOptions(length = length)).size)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooShort() {
        generator.generate(GeneratorOptions(length = 7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNoCharacterSets() {
        generator.generate(GeneratorOptions(uppercase = false, lowercase = false, digits = false, symbols = false))
    }

    @Test
    fun containsEverySelectedClass() {
        repeat(500) {
            val pw = generator.generate(GeneratorOptions(length = 8))
            assertTrue(pw.any(Char::isUpperCase))
            assertTrue(pw.any(Char::isLowerCase))
            assertTrue(pw.any(Char::isDigit))
            assertTrue(pw.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun onlyDigitsWhenOnlyDigitsSelected() {
        val pw = generator.generate(GeneratorOptions(length = 32, uppercase = false, lowercase = false, symbols = false))
        assertTrue(pw.all(Char::isDigit))
    }

    @Test
    fun excludesAmbiguousCharacters() {
        repeat(200) {
            val pw = generator.generate(GeneratorOptions(length = 64, excludeAmbiguous = true))
            assertTrue(pw.none { it in "Il1O0o|" })
        }
    }

    @Test
    fun roughlyUniformDistribution() {
        // 10 digits x 64 chars x 2000 runs: each digit expected ~12800 times.
        val counts = IntArray(10)
        repeat(2000) {
            generator.generate(GeneratorOptions(length = 64, uppercase = false, lowercase = false, symbols = false))
                .forEach { counts[it - '0']++ }
        }
        val expected = 2000 * 64 / 10.0
        counts.forEach { assertTrue("count $it deviates", kotlin.math.abs(it - expected) < expected * 0.05) }
    }

    @Test
    fun entropyEstimate() {
        // 26+26+10+28 = 90 symbols -> log2(90) ~ 6.49 bits/char
        val bits = generator.entropyBits(GeneratorOptions(length = 20))
        assertTrue(bits in 129.0..131.0)
    }
}
