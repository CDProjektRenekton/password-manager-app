package com.securevault.security.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Thin wrapper around BiometricPrompt that always authenticates a Keystore [Cipher] via
 * CryptoObject. Unlocking is therefore *cryptographically* gated by the biometric (the Keystore
 * refuses to use the key otherwise), not by a boolean "success" callback that could be hooked.
 *
 * Only Class 3 (BIOMETRIC_STRONG) sensors are accepted; weak face unlock cannot release keys.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    fun isAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: (Cipher) -> Unit,
        onError: (code: Int, message: CharSequence) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated = result.cryptoObject?.cipher
                if (authenticated != null) {
                    onSuccess(authenticated)
                } else {
                    onError(BiometricPrompt.ERROR_VENDOR, "Biometric result carried no cipher")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString)
            }
            // onAuthenticationFailed(): a single non-matching attempt; the prompt stays open.
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    companion object {
        fun isUserCancellation(code: Int) =
            code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                code == BiometricPrompt.ERROR_USER_CANCELED ||
                code == BiometricPrompt.ERROR_CANCELED
    }
}
