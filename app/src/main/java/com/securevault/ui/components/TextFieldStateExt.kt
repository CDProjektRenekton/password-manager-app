package com.securevault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import com.securevault.security.crypto.SecureMemory
import java.nio.CharBuffer

/** Puts [chars] into the field without creating a String in app code. Caller still wipes [chars]. */
@OptIn(ExperimentalFoundationApi::class)
fun TextFieldState.setSecret(chars: CharArray) {
    edit { replace(0, length, CharBuffer.wrap(chars)) }
    undoState.clearHistory()
}

/** Clears the field and its undo history (which would otherwise retain previous values). */
@OptIn(ExperimentalFoundationApi::class)
fun TextFieldState.wipe() {
    clearText()
    undoState.clearHistory()
}

/** Snapshot of the current text as a wipeable array. Caller must wipe it. */
fun TextFieldState.toCharArray(): CharArray = SecureMemory.toCharArray(text)
