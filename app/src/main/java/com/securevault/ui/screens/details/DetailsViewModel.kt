package com.securevault.ui.screens.details

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.repository.CredentialRepository
import com.securevault.data.repository.CredentialSummary
import com.securevault.security.clipboard.SecureClipboard
import com.securevault.security.crypto.useThenWipe
import com.securevault.ui.components.setSecret
import com.securevault.ui.components.wipe
import kotlinx.coroutines.launch

/**
 * Password and notes are decrypted only on explicit user action (reveal/copy) and are wiped again
 * when hidden, when the screen is left, or when the vault locks (the nav graph is popped, which
 * clears this ViewModel).
 */
class DetailsViewModel(
    private val id: String,
    private val repository: CredentialRepository,
    private val clipboard: SecureClipboard,
) : ViewModel() {

    var summary by mutableStateOf<CredentialSummary?>(null)
        private set
    var notFound by mutableStateOf(false)
        private set
    var passwordVisible by mutableStateOf(false)
        private set
    var notesVisible by mutableStateOf(false)
        private set

    /** Rendered only while visible; emptied (and undo history dropped) on hide. */
    val revealedPassword = TextFieldState()
    val revealedNotes = TextFieldState()

    init {
        viewModelScope.launch {
            summary = runCatching { repository.getSummary(id) }.getOrNull()
            notFound = summary == null
        }
    }

    fun togglePassword() {
        if (passwordVisible) {
            revealedPassword.wipe()
            passwordVisible = false
            return
        }
        viewModelScope.launch {
            val secrets = runCatching { repository.revealSecrets(id) }.getOrNull() ?: return@launch
            try {
                revealedPassword.setSecret(secrets.password)
                passwordVisible = true
            } finally {
                secrets.wipe()
            }
        }
    }

    fun toggleNotes() {
        if (notesVisible) {
            revealedNotes.wipe()
            notesVisible = false
            return
        }
        viewModelScope.launch {
            val secrets = runCatching { repository.revealSecrets(id) }.getOrNull() ?: return@launch
            try {
                revealedNotes.setSecret(secrets.notes)
                notesVisible = true
            } finally {
                secrets.wipe()
            }
        }
    }

    fun copyPassword(onCopied: () -> Unit) {
        viewModelScope.launch {
            val secrets = runCatching { repository.revealSecrets(id) }.getOrNull() ?: return@launch
            try {
                clipboard.copySensitive(secrets.password)
                onCopied()
            } finally {
                secrets.wipe()
            }
        }
    }

    fun copyUsername(onCopied: () -> Unit) {
        val username = summary?.username ?: return
        username.toCharArray().useThenWipe(clipboard::copySensitive)
        onCopied()
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (runCatching { repository.delete(id) }.isSuccess) onDeleted()
        }
    }

    override fun onCleared() {
        revealedPassword.wipe()
        revealedNotes.wipe()
    }
}
