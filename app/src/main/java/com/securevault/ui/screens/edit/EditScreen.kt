package com.securevault.ui.screens.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.securevault.ui.components.PasswordStrengthMeter
import com.securevault.ui.components.SecurePasswordField
import com.securevault.ui.components.VaultTextField
import com.securevault.ui.components.containerViewModel
import com.securevault.ui.screens.generator.GeneratorPanel
import com.securevault.ui.screens.generator.copiedMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    id: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm = containerViewModel {
        EditViewModel(id, it.credentialRepository, it.secureClipboard, it.passwordGenerator, it.strengthEstimator)
    }
    var showGenerator by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New credential" else "Edit credential") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
                actions = {
                    IconButton(onClick = { vm.save(onSaved) }, enabled = !vm.saving && !vm.loading) {
                        Icon(Icons.Filled.Check, "Save")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            VaultTextField(vm.title, "Title")
            VaultTextField(vm.url, "Website / app URL", keyboardType = KeyboardType.Uri)
            VaultTextField(vm.username, "Username / email", keyboardType = KeyboardType.Email)
            SecurePasswordField(
                state = vm.password,
                label = "Password",
                imeAction = ImeAction.Next,
                trailing = {
                    IconButton(onClick = {
                        vm.openGenerator()
                        showGenerator = true
                    }) { Icon(Icons.Filled.AutoAwesome, "Generate password") }
                },
            )
            PasswordStrengthMeter(vm.password, vm.estimator)
            VaultTextField(vm.notes, "Secure notes", singleLine = false)
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.save(onSaved) },
                enabled = !vm.saving && !vm.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (showGenerator) {
        ModalBottomSheet(onDismissRequest = { showGenerator = false }, sheetState = sheetState) {
            GeneratorPanel(
                controller = vm.generator,
                estimator = vm.estimator,
                onCopy = {
                    vm.copyGenerated()
                    scope.launch { snackbar.showSnackbar(copiedMessage()) }
                },
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                primaryAction = {
                    Button(
                        onClick = {
                            vm.useGeneratedPassword()
                            scope.launch { sheetState.hide() }.invokeOnCompletion { showGenerator = false }
                        },
                        enabled = vm.generator.options.isValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Use this password") }
                },
            )
        }
    }
}
