package com.securevault.security.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HkdfTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun rfc5869_testCase1() {
        val okm = Hkdf.deriveKey(
            ikm = hex("0b".repeat(22)),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
            salt = hex("000102030405060708090a0b0c"),
        )
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm,
        )
    }

    @Test
    fun rfc5869_testCase3_noSaltNoInfo() {
        val okm = Hkdf.deriveKey(ikm = hex("0b".repeat(22)), info = ByteArray(0), length = 42)
        assertArrayEquals(
            hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
            okm,
        )
    }

    @Test
    fun differentInfoGivesIndependentKeys() {
        val ikm = ByteArray(32) { it.toByte() }
        val a = Hkdf.deriveKey(ikm, "securevault/sqlcipher/v1".toByteArray())
        val b = Hkdf.deriveKey(ikm, "securevault/fields/v1".toByteArray())
        assertFalse(a.contentEquals(b))
    }
}
