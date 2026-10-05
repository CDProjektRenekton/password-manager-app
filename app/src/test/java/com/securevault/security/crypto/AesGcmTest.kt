package com.securevault.security.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class AesGcmTest {

    private val key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
    private val aad = "row-1|password".toByteArray()

    @Test
    fun roundTrip() {
        val plaintext = "correct horse battery staple".toByteArray()
        val blob = AesGcm.encrypt(key, plaintext, aad)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, blob, aad))
    }

    @Test
    fun freshIvEveryTime() {
        val p = "same".toByteArray()
        assertFalse(AesGcm.encrypt(key, p, aad).contentEquals(AesGcm.encrypt(key, p, aad)))
    }

    @Test(expected = AEADBadTagException::class)
    fun tamperedCiphertextIsRejected() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray(), aad)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        AesGcm.decrypt(key, blob, aad)
    }

    @Test(expected = AEADBadTagException::class)
    fun ciphertextMovedToAnotherFieldIsRejected() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray(), aad)
        AesGcm.decrypt(key, blob, "row-1|notes".toByteArray())
    }

    @Test(expected = AEADBadTagException::class)
    fun wrongKeyIsRejected() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray(), aad)
        AesGcm.decrypt(SecretKeySpec(ByteArray(32) { 8 }, "AES"), blob, aad)
    }
}
