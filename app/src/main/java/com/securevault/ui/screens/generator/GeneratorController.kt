package com.securevault.ui.screens.generator

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.securevault.security.clipboard.SecureClipboard
import com.securevault.security.crypto.useThenWipe
import com.securevault.security.password.GeneratorOptions
import com.securevault.security.password.PasswordGenerator
import com.securevault.ui.components.setSecret
import com.securevault.ui.components.toCharArray
import com.securevault.ui.components.wipe

/**
 * State holder shared by the standalone generator screen and the generator sheet in the edit
 * screen. The generated password only ever exists as a CharArray (wiped immediately) and inside
 * the [output] TextFieldState.
 */
class GeneratorController(private val generator: PasswordGenerator) {

    val output = TextFieldState()

    var options by mutableStateOf(GeneratorOptions())
        private set

    val entropyBits: Double get() = generator.entropyBits(options)

    fun update(newOptions: GeneratorOptions) {
        options = newOptions
        regenerate()
    }

    fun regenerate() {
        if (!options.isValid) {
            output.wipe()
            return
        }
        generator.generate(options).useThenWipe(output::setSecret)
    }

    fun copy(clipboard: SecureClipboard) {
        if (output.text.isEmpty()) return
        output.toCharArray().useThenWipe(clipboard::copySensitive)
    }

    /** Moves the generated value into [target] (e.g. the edit form's password field). */
    fun applyTo(target: TextFieldState) {
        output.toCharArray().useThenWipe(target::setSecret)
    }

    fun wipe() = output.wipe()
}
