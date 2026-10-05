package com.securevault.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.securevault.ui.components.containerViewModel
import com.securevault.ui.screens.generator.copiedMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    id: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val vm = containerViewModel { DetailsViewModel(id, it.credentialRepository, it.secureClipboard) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val notifyCopied: () -> Unit = { scope.launch { snackbar.showSnackbar(copiedMessage()) } }

    LaunchedEffect(vm.notFound) { if (vm.notFound) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.summary?.title.orEmpty(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { onEdit(id) }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val summary = vm.summary ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlainRow("Website / app", summary.url.ifBlank { "—" })
            PlainRow("Username", summary.username.ifBlank { "—" }) {
                if (summary.username.isNotBlank()) {
                    IconButton(onClick = { vm.copyUsername(notifyCopied) }) { Icon(Icons.Filled.ContentCopy, "Copy username") }
                }
            }
            SecretRow(
                label = "Password",
                visible = vm.passwordVisible,
                state = vm.revealedPassword,
                onToggle = vm::togglePassword,
                onCopy = { vm.copyPassword(notifyCopied) },
            )
            SecretRow(
                label = "Notes",
                visible = vm.notesVisible,
                state = vm.revealedNotes,
                onToggle = vm::toggleNotes,
                onCopy = null,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete credential?") },
            text = { Text("This permanently removes it from the vault.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlainRow(label: String, value: String, action: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            action()
        }
    }
}

@Composable
private fun SecretRow(
    label: String,
    visible: Boolean,
    state: TextFieldState,
    onToggle: () -> Unit,
    onCopy: (() -> Unit)?,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (visible) {
                    // Disabled read-only field: shows the secret but offers no select/copy, so the
                    // only copy path is the auto-clearing SecureClipboard.
                    BasicTextField(
                        state = state,
                        readOnly = true,
                        enabled = false,
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                } else {
                    Text("••••••••••••", style = MaterialTheme.typography.bodyLarge)
                }
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (visible) "Hide $label" else "Show $label",
                )
            }
            onCopy?.let { IconButton(onClick = it) { Icon(Icons.Filled.ContentCopy, "Copy $label") } }
        }
    }
}
