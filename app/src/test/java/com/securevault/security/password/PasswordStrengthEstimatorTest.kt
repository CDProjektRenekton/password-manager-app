package com.securevault.security.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.CharBuffer

class PasswordStrengthEstimatorTest {

    private val estimator = PasswordStrengthEstimator()

    @Test
    fun commonPasswordsScoreLow() {
        assertTrue(estimator.estimate("password").score <= 1)
        assertTrue(estimator.estimate("P@ssw0rd!").score <= 2)
        assertTrue(estimator.estimate("qwerty123").score <= 1)
    }

    @Test
    fun longRandomPassphraseScoresHigh() {
        assertEquals(4, estimator.estimate("vX7#qL!z9@Rt2&mW").score)
        assertEquals(4, estimator.estimate("granite-orbit-pickle-saxophone").score)
    }

    @Test
    fun emptyIsZero() {
        assertEquals(StrengthResult.EMPTY, estimator.estimate(""))
    }

    @Test
    fun doesNotMutateCallersBuffer() {
        // zxcvbn's wipe() zero-fills writable CharBuffers; the estimator must copy first.
        val chars = "granite-orbit-pickle-saxophone".toCharArray()
        estimator.estimate(CharBuffer.wrap(chars))
        assertEquals("granite-orbit-pickle-saxophone", String(chars))
    }
}
