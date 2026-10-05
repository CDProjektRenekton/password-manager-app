package com.securevault.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Reports user activity to the auto-lock watchdog. Soft-keyboard typing is delivered to the IME's
 * own window, so Activity.onUserInteraction() never sees it; text fields report edits here.
 */
val LocalUserActivity = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Password input built on [BasicSecureTextField] + [TextFieldState]:
 *  - the text lives in an editable char buffer (not an immutable String in a ViewModel/Bundle);
 *  - the state is owned by a ViewModel, never by rememberSaveable, so it's never written into the
 *    saved-instance-state Bundle (which the system may persist to disk);
 *  - keyboards get KeyboardType.Password, which disables learning/suggestions in Gboard & co;
 *  - copy/cut from the field are disabled by BasicSecureTextField.
 */
@Composable
fun SecurePasswordField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    ReportEdits(state)
    FieldFrame(label = label, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicSecureTextField(
                state = state,
                textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = imeAction,
                ),
                onKeyboardAction = { onSubmit?.invoke() },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Hide password" else "Show password",
                )
            }
            trailing?.invoke()
        }
    }
}

/** Plain (non-secret) text input, also [TextFieldState]-based and ViewModel-owned. */
@Composable
fun VaultTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    ReportEdits(state)
    FieldFrame(label = label, modifier = modifier) {
        BasicTextField(
            state = state,
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 3),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportEdits(state: TextFieldState) {
    val onActivity = LocalUserActivity.current
    LaunchedEffect(state) {
        // Emits a (cheap, immutable) snapshot reference per edit; we only use it as a signal.
        snapshotFlow { state.text }.collect { onActivity() }
    }
}

@Composable
private fun FieldFrame(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}
