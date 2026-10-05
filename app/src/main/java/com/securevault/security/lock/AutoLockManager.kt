package com.securevault.security.lock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.securevault.security.vault.VaultSession
import com.securevault.security.vault.VaultState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Locks the vault:
 *  - immediately when the *whole app* leaves the foreground (registered on ProcessLifecycleOwner,
 *    whose ON_STOP is debounced ~700 ms so rotation/config changes don't trigger it);
 *  - after [inactivityTimeoutMillis] without user interaction while in the foreground.
 *
 * Uses a single watchdog coroutine and a monotonic timestamp rather than restarting a timer on
 * every touch event.
 */
class AutoLockManager(
    private val session: VaultSession,
    private val scope: CoroutineScope,
    private val inactivityTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : DefaultLifecycleObserver {

    @Volatile private var lastInteraction = SystemClock.elapsedRealtime()
    @Volatile private var inForeground = false
    private var watchdog: Job? = null

    init {
        scope.launch {
            session.state.collect { state ->
                if (state is VaultState.Unlocked) {
                    // An unlock that completed after the app was backgrounded must not stay open.
                    if (!inForeground) session.lock() else startWatchdog()
                } else {
                    stopWatchdog()
                }
            }
        }
    }

    /** Call for every touch/key event and every text edit (IME input bypasses Activity callbacks). */
    fun onUserInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        inForeground = true
        onUserInteraction()
    }

    override fun onStop(owner: LifecycleOwner) {
        inForeground = false
        session.lock()
    }

    @Synchronized
    private fun startWatchdog() {
        watchdog?.cancel()
        onUserInteraction()
        watchdog = scope.launch {
            while (isActive) {
                val idle = SystemClock.elapsedRealtime() - lastInteraction
                if (idle >= inactivityTimeoutMillis) {
                    session.lock()
                    break
                }
                delay(inactivityTimeoutMillis - idle)
            }
        }
    }

    @Synchronized
    private fun stopWatchdog() {
        watchdog?.cancel()
        watchdog = null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}
