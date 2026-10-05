package com.securevault.security.crypto

object SqlCipherKey {
    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Builds the ASCII bytes of SQLCipher's raw-key literal `x'<64 hex chars>'` without ever
     * materialising a String, so the result can be zeroed. Caller owns and wipes the result.
     */
    fun rawKeyLiteral(key: ByteArray): ByteArray {
        require(key.size == 32) { "SQLCipher raw key must be 256 bits" }
        val out = ByteArray(3 + key.size * 2)
        out[0] = 'x'.code.toByte()
        out[1] = '\''.code.toByte()
        key.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            out[2 + i * 2] = HEX[v ushr 4].code.toByte()
            out[3 + i * 2] = HEX[v and 0x0F].code.toByte()
        }
        out[out.size - 1] = '\''.code.toByte()
        return out
    }
}
