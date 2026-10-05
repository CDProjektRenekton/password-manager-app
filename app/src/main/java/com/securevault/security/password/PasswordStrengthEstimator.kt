package com.securevault.security.password

import com.nulabinc.zxcvbn.Zxcvbn
import java.nio.CharBuffer

data class StrengthResult(
    /** zxcvbn score 0 (too guessable) .. 4 (very unguessable). */
    val score: Int,
    /** Estimated time for an offline attacker doing 10^4 slow-hash guesses/s. */
    val crackTimeDisplay: String,
    val warning: String?,
    val suggestions: List<String>,
) {
    val label: String
        get() = when (score) {
            0 -> "Very weak"
            1 -> "Weak"
            2 -> "Fair"
            3 -> "Strong"
            else -> "Very strong"
        }

    companion object {
        val EMPTY = StrengthResult(0, "instant", null, emptyList())
    }
}

/**
 * zxcvbn-based estimator (Dropbox's algorithm, nulab Java port). Unlike naive "has a digit and a
 * symbol" rules it models dictionary words, l33t substitutions, keyboard walks, dates, repeats
 * and sequences, so "P@ssw0rd!" correctly scores as weak.
 *
 * Runs fully offline (dictionaries are bundled). Construction loads ~1 MB of dictionaries, so the
 * instance is created lazily and estimate() must run off the main thread.
 */
class PasswordStrengthEstimator {

    private val zxcvbn by lazy { Zxcvbn() }

    /**
     * @param userInputs context words (e.g. the item's title/username) that should count as
     *   guessable when they appear in the password.
     */
    @Synchronized
    fun estimate(password: CharSequence, userInputs: List<String> = emptyList()): StrengthResult {
        if (password.isEmpty()) return StrengthResult.EMPTY
        // Work on a private copy: zxcvbn's wipe() zero-fills writable CharBuffers it is given,
        // and must never mutate the caller's buffer (e.g. a live text field).
        val copy = CharArray(password.length) { password[it] }
        val buffer = CharBuffer.wrap(copy)
        val strength = zxcvbn.measure(buffer, userInputs)
        try {
            return StrengthResult(
                score = strength.score,
                crackTimeDisplay = strength.crackTimesDisplay.offlineSlowHashing1e4perSecond,
                warning = strength.feedback.warning?.takeIf { it.isNotBlank() },
                suggestions = strength.feedback.suggestions.orEmpty(),
            )
        } finally {
            strength.wipe()
            copy.fill('\u0000')
        }
    }
}
