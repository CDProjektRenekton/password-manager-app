package com.securevault.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.securevault.ui.components.PasswordStrengthMeter
import com.securevault.ui.components.SecurePasswordField
import com.securevault.ui.components.containerViewModel

@Composable
fun SetupScreen() {
    val vm = containerViewModel { SetupViewModel(it.vaultSession, it.strengthEstimator) }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Create your vault", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your master password encrypts everything on this device. It is never stored or sent " +
                "anywhere and cannot be recovered - if you forget it, the vault is lost.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecurePasswordField(vm.password, "Master password", imeAction = ImeAction.Next)
        PasswordStrengthMeter(vm.password, vm.estimator)
        SecurePasswordField(vm.confirmation, "Confirm master password", onSubmit = vm::createVault)
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::createVault, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Create vault")
        }
    }
}
