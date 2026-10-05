package com.securevault.ui.screens.unlock

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.security.crypto.CryptographyManager
import com.securevault.security.crypto.SecureMemory
import com.securevault.security.vault.UnlockResult
import com.securevault.security.vault.VaultSession
import com.securevault.ui.components.toCharArray
import com.securevault.ui.components.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

class UnlockViewModel(private val session: VaultSession) : ViewModel() {

    val password = TextFieldState()

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Loaded off the main thread: reading it decrypts the metadata with the Keystore. */
    var biometricEnabled by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            biometricEnabled = withContext(Dispatchers.Default) { session.isBiometricEnabled }
        }
    }

    /** Only auto-prompt for biometrics once per visit to this screen. */
    var autoPromptConsumed = false

    fun unlockWithPassword() {
        if (busy || password.text.isEmpty()) return
        val pw = password.toCharArray()
        password.wipe()
        busy = true
        error = null
        viewModelScope.launch {
            val result = try {
                session.unlockWithPassword(pw)
            } finally {
                SecureMemory.wipe(pw)
            }
            handle(result)
        }
    }

    /** Returns a Keystore cipher for BiometricPrompt, or null (with [error] set) if unavailable. */
    fun biometricCipherOrNull(): Cipher? = try {
        session.createBiometricUnlockCipher()
    } catch (e: CryptographyManager.KeyInvalidatedException) {
        biometricEnabled = false
        error = "Biometrics changed on this device. Unlock with your master password and re-enable biometric unlock."
        null
    } catch (t: Throwable) {
        error = "Biometric unlock unavailable."
        null
    }

    fun unlockWithBiometric(authenticatedCipher: Cipher) {
        busy = true
        error = null
        viewModelScope.launch { handle(session.unlockWithBiometricCipher(authenticatedCipher)) }
    }

    fun onBiometricError(message: CharSequence) {
        error = message.toString()
    }

    private fun handle(result: UnlockResult) {
        busy = false
        error = when (result) {
            UnlockResult.Success -> null // navigation reacts to VaultState.Unlocked
            UnlockResult.WrongPassword -> "Wrong master password."
            is UnlockResult.Throttled -> "Too many attempts. Try again in ${(result.retryInMillis + 999) / 1000} s."
            UnlockResult.BiometricInvalidated -> "Biometric unlock is no longer valid. Use your master password."
            is UnlockResult.Error -> "Unlock failed: ${result.cause.javaClass.simpleName}"
        }
    }

    override fun onCleared() = password.wipe()
}
