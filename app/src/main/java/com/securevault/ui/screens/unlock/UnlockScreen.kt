package com.securevault.ui.screens.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.securevault.R
import com.securevault.security.biometric.BiometricAuthenticator
import com.securevault.ui.components.SecurePasswordField
import com.securevault.ui.components.containerViewModel
import com.securevault.ui.components.findFragmentActivity

@Composable
fun UnlockScreen() {
    val vm = containerViewModel { UnlockViewModel(it.vaultSession) }
    val activity = LocalContext.current.findFragmentActivity()
    val authenticator = remember(activity) { BiometricAuthenticator(activity) }
    val title = stringResource(R.string.biometric_unlock_title)
    val subtitle = stringResource(R.string.biometric_unlock_subtitle)
    val negative = stringResource(R.string.biometric_negative)
    val canUseBiometric = vm.biometricEnabled && authenticator.isAvailable()

    val promptBiometric = {
        vm.biometricCipherOrNull()?.let { cipher ->
            authenticator.authenticate(
                cipher = cipher,
                title = title,
                subtitle = subtitle,
                negativeButtonText = negative,
                onSuccess = vm::unlockWithBiometric,
                onError = { code, msg -> if (!BiometricAuthenticator.isUserCancellation(code)) vm.onBiometricError(msg) },
            )
        }
        Unit
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !vm.autoPromptConsumed) {
            vm.autoPromptConsumed = true
            promptBiometric()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Vault locked", style = MaterialTheme.typography.headlineMedium)
        SecurePasswordField(vm.password, "Master password", onSubmit = vm::unlockWithPassword)
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::unlockWithPassword, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Unlock")
        }
        if (canUseBiometric) {
            OutlinedButton(onClick = promptBiometric, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Fingerprint, null)
                Text("  Unlock with biometrics")
            }
        }
    }
}
