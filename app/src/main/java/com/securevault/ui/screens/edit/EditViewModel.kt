package com.securevault.ui.screens.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securevault.data.repository.CredentialDraft
import com.securevault.data.repository.CredentialRepository
import com.securevault.security.clipboard.SecureClipboard
import com.securevault.security.password.PasswordGenerator
import com.securevault.security.password.PasswordStrengthEstimator
import com.securevault.ui.components.setSecret
import com.securevault.ui.components.toCharArray
import com.securevault.ui.components.wipe
import com.securevault.ui.screens.generator.GeneratorController
import kotlinx.coroutines.launch

/**
 * Add/edit form. All five fields are ViewModel-owned TextFieldStates: they survive rotation but
 * are never put into the saved-instance-state Bundle, so plaintext can't leak to disk through
 * Android's task-restore persistence.
 */
class EditViewModel(
    private val id: String?,
    private val repository: CredentialRepository,
    private val clipboard: SecureClipboard,
    generator: PasswordGenerator,
    val estimator: PasswordStrengthEstimator,
) : ViewModel() {

    val title = TextFieldState()
    val url = TextFieldState()
    val username = TextFieldState()
    val password = TextFieldState()
    val notes = TextFieldState()

    val generator = GeneratorController(generator)

    val isNew: Boolean get() = id == null
    var loading by mutableStateOf(id != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (id != null) {
            viewModelScope.launch {
                runCatching {
                    val summary = repository.getSummary(id) ?: throw NoSuchElementException(id)
                    val secrets = repository.revealSecrets(id) ?: throw NoSuchElementException(id)
                    try {
                        title.fill(summary.title)
                        url.fill(summary.url)
                        username.fill(summary.username)
                        password.setSecret(secrets.password)
                        notes.setSecret(secrets.notes)
                    } finally {
                        secrets.wipe()
                    }
                }.onFailure { error = "Couldn't load credential." }
                loading = false
            }
        }
    }

    fun openGenerator() {
        if (generator.output.text.isEmpty()) generator.regenerate()
    }

    fun useGeneratedPassword() = generator.applyTo(password)

    fun copyGenerated() = generator.copy(clipboard)

    fun save(onSaved: (String) -> Unit) {
        if (saving) return
        if (title.text.isBlank()) {
            error = "Title is required."
            return
        }
        saving = true
        error = null
        // The repository encrypts and then wipes every array in the draft.
        val draft = CredentialDraft(
            id = id,
            title = title.toCharArray(),
            url = url.toCharArray(),
            username = username.toCharArray(),
            password = password.toCharArray(),
            notes = notes.toCharArray(),
        )
        viewModelScope.launch {
            runCatching { repository.save(draft) }
                .onSuccess { savedId ->
                    wipeAll()
                    onSaved(savedId)
                }
                .onFailure { error = "Couldn't save: ${it.javaClass.simpleName}" }
            saving = false
        }
    }

    private fun wipeAll() {
        listOf(title, url, username, password, notes).forEach { it.wipe() }
        generator.wipe()
    }

    override fun onCleared() = wipeAll()

    private fun TextFieldState.fill(value: String) = edit { replace(0, length, value) }
}
