package com.securevault.ui.screens.generator

import androidx.lifecycle.ViewModel
import com.securevault.security.clipboard.SecureClipboard
import com.securevault.security.password.PasswordGenerator
import com.securevault.security.password.PasswordStrengthEstimator

class GeneratorViewModel(
    generator: PasswordGenerator,
    private val clipboard: SecureClipboard,
    val estimator: PasswordStrengthEstimator,
) : ViewModel() {

    val controller = GeneratorController(generator).also { it.regenerate() }

    fun copy() = controller.copy(clipboard)

    override fun onCleared() = controller.wipe()
}
