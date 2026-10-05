package com.securevault.ui.screens.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securevault.R
import com.securevault.data.repository.CredentialSummary
import com.securevault.security.biometric.BiometricAuthenticator
import com.securevault.ui.components.containerViewModel
import com.securevault.ui.components.findFragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onGenerator: () -> Unit,
) {
    val vm = containerViewModel { VaultViewModel(it.credentialRepository, it.vaultSession) }
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    val activity = LocalContext.current.findFragmentActivity()
    val authenticator = remember(activity) { BiometricAuthenticator(activity) }
    val enrollTitle = stringResource(R.string.biometric_enroll_title)
    val enrollSubtitle = stringResource(R.string.biometric_enroll_subtitle)
    val cancel = stringResource(android.R.string.cancel)

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    IconButton(onClick = onGenerator) { Icon(Icons.Filled.Password, "Password generator") }
                    IconButton(onClick = vm::lockNow) { Icon(Icons.Filled.Lock, "Lock vault") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Biometric unlock") },
                            enabled = authenticator.isAvailable(),
                            trailingIcon = { Switch(checked = vm.biometricEnabled, onCheckedChange = null) },
                            onClick = {
                                menuOpen = false
                                if (vm.biometricEnabled) {
                                    vm.disableBiometric()
                                } else {
                                    vm.biometricEnrollmentCipherOrNull()?.let { cipher ->
                                        authenticator.authenticate(
                                            cipher = cipher,
                                            title = enrollTitle,
                                            subtitle = enrollSubtitle,
                                            negativeButtonText = cancel,
                                            onSuccess = vm::completeBiometricEnrollment,
                                            onError = { code, msg ->
                                                if (!BiometricAuthenticator.isUserCancellation(code)) vm.message = msg.toString()
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add credential") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Search title, website or username") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Clear, "Clear search") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isEmpty()) "No credentials yet. Tap + to add one." else "No matches.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item -> CredentialRow(item) { onOpen(item.id) } }
                }
            }
        }
    }
}

@Composable
private fun CredentialRow(item: CredentialSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Key, null) },
            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    listOf(item.username, item.url).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}
