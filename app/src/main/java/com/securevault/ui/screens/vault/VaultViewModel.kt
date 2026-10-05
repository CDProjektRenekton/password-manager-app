package com.securevault.ui.screens.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.repository.CredentialRepository
import com.securevault.data.repository.CredentialSummary
import com.securevault.security.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

class VaultViewModel(
    repository: CredentialRepository,
    private val session: VaultSession,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    /** Decrypted summaries filtered in memory (ciphertext columns can't be searched in SQL). */
    val items: StateFlow<List<CredentialSummary>> =
        combine(repository.observeSummaries(), query) { all, q ->
            val needle = q.trim()
            if (needle.isEmpty()) {
                all
            } else {
                all.filter {
                    it.title.contains(needle, ignoreCase = true) ||
                        it.url.contains(needle, ignoreCase = true) ||
                        it.username.contains(needle, ignoreCase = true)
                }
            }.sortedBy { it.title.lowercase() }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var biometricEnabled by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            biometricEnabled = withContext(Dispatchers.Default) { session.isBiometricEnabled }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun lockNow() = session.lock()

    fun biometricEnrollmentCipherOrNull(): Cipher? = runCatching { session.createBiometricEnrollmentCipher() }
        .onFailure { message = "Couldn't enable biometrics: ${it.javaClass.simpleName}" }
        .getOrNull()

    fun completeBiometricEnrollment(cipher: Cipher) {
        viewModelScope.launch {
            runCatching { session.completeBiometricEnrollment(cipher) }
                .onSuccess {
                    biometricEnabled = true
                    message = "Biometric unlock enabled."
                }
                .onFailure { message = "Couldn't enable biometrics: ${it.javaClass.simpleName}" }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { session.disableBiometric() }
            biometricEnabled = false
            message = "Biometric unlock disabled."
        }
    }
}
