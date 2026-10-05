package com.securevault.security.vault

import android.content.Context

/**
 * Persistent exponential back-off for master-password attempts. Argon2id already makes each guess
 * expensive; this additionally caps on-device guessing (e.g. a stolen, unlocked phone).
 *
 * Attempts 1-5 are free, then 30 s, 60 s, 120 s ... capped at 1 h. Survives process death.
 */
class UnlockThrottle(context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    private val prefs = context.getSharedPreferences("unlock_throttle", Context.MODE_PRIVATE)

    /** Milliseconds the user must still wait, or 0. */
    fun remainingLockoutMillis(): Long {
        val failures = prefs.getInt(KEY_FAILURES, 0)
        if (failures < FREE_ATTEMPTS) return 0
        val lastFailure = prefs.getLong(KEY_LAST_FAILURE, 0)
        val elapsed = clock() - lastFailure
        // Clock moved backwards (user changed time): restart the full window rather than skip it.
        if (elapsed < 0) return lockoutFor(failures)
        return (lockoutFor(failures) - elapsed).coerceAtLeast(0)
    }

    fun recordFailure() {
        prefs.edit()
            .putInt(KEY_FAILURES, prefs.getInt(KEY_FAILURES, 0) + 1)
            .putLong(KEY_LAST_FAILURE, clock())
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun lockoutFor(failures: Int): Long {
        val exponent = (failures - FREE_ATTEMPTS).coerceIn(0, 7)
        return (BASE_LOCKOUT_MS shl exponent).coerceAtMost(MAX_LOCKOUT_MS)
    }

    private companion object {
        const val KEY_FAILURES = "failures"
        const val KEY_LAST_FAILURE = "last_failure"
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 60 * 60_000L
    }
}
