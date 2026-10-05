package com.securevault.security.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Helpers for handling secrets as mutable arrays that can be zeroed, instead of immutable
 * [String]s that linger on the heap until garbage collection (and may be interned / copied).
 *
 * The JVM gives no hard guarantee that a GC'd copy was never made, so this is defence in depth:
 * it shrinks the window and the number of copies, it cannot make it zero.
 */
object SecureMemory {

    fun wipe(vararg arrays: ByteArray?) {
        for (array in arrays) array?.fill(0)
    }

    fun wipe(vararg arrays: CharArray?) {
        for (array in arrays) array?.fill('\u0000')
    }

    /** Zeroes a (heap or direct) buffer's full capacity. */
    fun wipe(buffer: ByteBuffer?) {
        if (buffer == null || buffer.isReadOnly) return
        if (buffer.hasArray()) {
            buffer.array().fill(0)
        } else {
            buffer.clear()
            while (buffer.hasRemaining()) buffer.put(0)
            buffer.clear()
        }
    }

    /** UTF-8 encodes [chars] without creating an intermediate String. Caller must wipe the result. */
    fun toUtf8Bytes(chars: CharArray): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(chars))
        val out = ByteArray(encoded.remaining())
        encoded.get(out)
        wipe(encoded)
        return out
    }

    /** UTF-8 decodes [bytes] without creating an intermediate String. Caller must wipe the result. */
    fun toUtf8Chars(bytes: ByteArray): CharArray {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = decoder.decode(ByteBuffer.wrap(bytes))
        val out = CharArray(decoded.remaining())
        decoded.get(out)
        if (decoded.hasArray()) decoded.array().fill('\u0000')
        return out
    }

    /** Copies any [CharSequence] (e.g. a Compose TextFieldState snapshot) into a wipeable array. */
    fun toCharArray(sequence: CharSequence): CharArray = CharArray(sequence.length) { sequence[it] }

    /** Constant-time comparison so equality checks don't leak a timing side channel. */
    fun constantTimeEquals(a: CharArray, b: CharArray): Boolean {
        var diff = a.size xor b.size
        val n = minOf(a.size, b.size)
        for (i in 0 until n) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}

/** Runs [block] and zeroes the array afterwards, even if [block] throws. */
inline fun <T> ByteArray.useThenWipe(block: (ByteArray) -> T): T =
    try {
        block(this)
    } finally {
        fill(0)
    }

/** Runs [block] and zeroes the array afterwards, even if [block] throws. */
inline fun <T> CharArray.useThenWipe(block: (CharArray) -> T): T =
    try {
        block(this)
    } finally {
        fill('\u0000')
    }
