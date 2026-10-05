package com.securevault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.securevault.SecureVaultApp
import com.securevault.di.AppContainer

/** Creates a ViewModel scoped to the current NavBackStackEntry, wired from the AppContainer. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as SecureVaultApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
