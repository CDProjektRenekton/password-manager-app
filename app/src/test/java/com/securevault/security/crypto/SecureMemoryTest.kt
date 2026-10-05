package com.securevault.security.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureMemoryTest {

    @Test
    fun utf8RoundTripIncludingNonAscii() {
        val chars = "pässwörd-🔐-密码".toCharArray()
        val bytes = SecureMemory.toUtf8Bytes(chars)
        assertArrayEquals("pässwörd-🔐-密码".toByteArray(Charsets.UTF_8), bytes)
        assertArrayEquals(chars, SecureMemory.toUtf8Chars(bytes))
    }

    @Test
    fun useThenWipeZeroesEvenOnException() {
        val secret = charArrayOf('a', 'b', 'c')
        runCatching { secret.useThenWipe { error("boom") } }
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun constantTimeEquals() {
        assertTrue(SecureMemory.constantTimeEquals("abc".toCharArray(), "abc".toCharArray()))
        assertFalse(SecureMemory.constantTimeEquals("abc".toCharArray(), "abd".toCharArray()))
        assertFalse(SecureMemory.constantTimeEquals("abc".toCharArray(), "abcd".toCharArray()))
    }

    @Test
    fun sqlCipherRawKeyLiteral() {
        val key = ByteArray(32) { it.toByte() }
        val literal = String(SqlCipherKey.rawKeyLiteral(key), Charsets.US_ASCII)
        assertEquals("x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'", literal)
    }
}
