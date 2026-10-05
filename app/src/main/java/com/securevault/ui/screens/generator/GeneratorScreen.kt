package com.securevault.ui.screens.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.securevault.security.clipboard.SecureClipboard
import com.securevault.security.password.PasswordGenerator
import com.securevault.security.password.PasswordStrengthEstimator
import com.securevault.ui.components.PasswordStrengthMeter
import com.securevault.ui.components.containerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(onBack: () -> Unit) {
    val vm = containerViewModel { GeneratorViewModel(it.passwordGenerator, it.secureClipboard, it.strengthEstimator) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        GeneratorPanel(
            controller = vm.controller,
            estimator = vm.estimator,
            onCopy = {
                vm.copy()
                scope.launch { snackbar.showSnackbar(copiedMessage()) }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

fun copiedMessage() = "Copied. Clipboard clears in ${SecureClipboard.CLEAR_DELAY_MILLIS / 1000} s."

/** Generator UI (used full-screen and inside the edit screen's bottom sheet). */
@Composable
fun GeneratorPanel(
    controller: GeneratorController,
    estimator: PasswordStrengthEstimator,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: @Composable (() -> Unit)? = null,
) {
    val options = controller.options
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Disabled + read-only: renders the value but blocks selection/copy, so copying
                // can only happen through the auto-clearing SecureClipboard.
                BasicTextField(
                    state = controller.output,
                    readOnly = true,
                    enabled = false,
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = controller::regenerate) { Icon(Icons.Filled.Refresh, "Regenerate") }
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copy password") }
            }
        }

        PasswordStrengthMeter(controller.output, estimator)
        Text(
            "≈ ${controller.entropyBits.roundToInt()} bits of entropy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Length: ${options.length}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = options.length.toFloat(),
            onValueChange = { controller.update(options.copy(length = it.roundToInt())) },
            valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
            steps = PasswordGenerator.MAX_LENGTH - PasswordGenerator.MIN_LENGTH - 1,
        )

        OptionSwitch("Uppercase (A-Z)", options.uppercase) { controller.update(options.copy(uppercase = it)) }
        OptionSwitch("Lowercase (a-z)", options.lowercase) { controller.update(options.copy(lowercase = it)) }
        OptionSwitch("Numbers (0-9)", options.digits) { controller.update(options.copy(digits = it)) }
        OptionSwitch("Symbols (!#$%…)", options.symbols) { controller.update(options.copy(symbols = it)) }
        OptionSwitch("Avoid look-alikes (I l 1 O 0)", options.excludeAmbiguous) {
            controller.update(options.copy(excludeAmbiguous = it))
        }
        if (!options.isValid) {
            Text("Select at least one character set.", color = MaterialTheme.colorScheme.error)
        }
        primaryAction?.let {
            Spacer(Modifier.padding(4.dp))
            it()
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
