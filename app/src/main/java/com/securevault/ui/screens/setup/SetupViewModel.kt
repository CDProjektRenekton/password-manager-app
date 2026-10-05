package com.securevault.ui.screens.setup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.security.crypto.SecureMemory
import com.securevault.security.password.PasswordStrengthEstimator
import com.securevault.security.vault.VaultSession
import com.securevault.ui.components.toCharArray
import com.securevault.ui.components.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.CharBuffer

class SetupViewModel(
    private val session: VaultSession,
    val estimator: PasswordStrengthEstimator,
) : ViewModel() {

    val password = TextFieldState()
    val confirmation = TextFieldState()

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun createVault() {
        if (busy) return
        busy = true
        error = null
        val pw = password.toCharArray()
        val confirm = confirmation.toCharArray()
        viewModelScope.launch {
            try {
                // Re-score synchronously: the live meter is debounced and may lag the last edit.
                val score = withContext(Dispatchers.Default) { estimator.estimate(CharBuffer.wrap(pw)).score }
                error = when {
                    pw.size < MIN_LENGTH -> "Use at least $MIN_LENGTH characters."
                    score < MIN_SCORE -> "Too guessable. Try a longer passphrase of unrelated words."
                    !SecureMemory.constantTimeEquals(pw, confirm) -> "Passwords don't match."
                    else -> null
                }
                if (error == null) {
                    session.createVault(pw) // state -> Unlocked; navigation reacts to it.
                    wipeFields()
                }
            } catch (t: Throwable) {
                error = "Could not create vault: ${t.javaClass.simpleName}"
            } finally {
                SecureMemory.wipe(pw, confirm)
                busy = false
            }
        }
    }

    private fun wipeFields() {
        password.wipe()
        confirmation.wipe()
    }

    override fun onCleared() = wipeFields()

    private companion object {
        const val MIN_LENGTH = 12
        const val MIN_SCORE = 3
    }
}
