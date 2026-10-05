package com.securevault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securevault.security.vault.VaultSession
import com.securevault.security.vault.VaultState
import com.securevault.ui.screens.details.DetailsScreen
import com.securevault.ui.screens.edit.EditScreen
import com.securevault.ui.screens.generator.GeneratorScreen
import com.securevault.ui.screens.setup.SetupScreen
import com.securevault.ui.screens.unlock.UnlockScreen
import com.securevault.ui.screens.vault.VaultScreen

object Routes {
    const val SETUP = "setup"
    const val UNLOCK = "unlock"
    const val VAULT = "vault"
    const val DETAILS = "details/{id}"
    const val EDIT = "edit?id={id}"
    const val GENERATOR = "generator"

    fun details(id: String) = "details/$id"
    fun edit(id: String? = null) = if (id == null) "edit" else "edit?id=$id"
}

/**
 * The vault state is the single source of truth for navigation. Whenever the vault locks, the
 * *entire* back stack is popped: every screen's ViewModel is cleared (wiping its TextFieldStates
 * and decrypted arrays) before the unlock screen is shown. This also covers activity recreation
 * after process death, where Navigation would otherwise restore a vault screen while locked.
 */
@Composable
fun AppNavHost(session: VaultSession, navController: NavHostController = rememberNavController()) {
    // collectAsState (not ...WithLifecycle): a lock that happens while backgrounded must pop the
    // back stack immediately, not when the UI next starts.
    val state by session.state.collectAsState()
    val startDestination = remember { gateRouteFor(session.state.value) ?: Routes.VAULT }

    LaunchedEffect(state) {
        val gate = gateRouteFor(state)
        val current = navController.currentDestination?.route
        if (gate != null && current != gate) {
            navController.resetTo(gate)
        } else if (gate == null && (current == Routes.SETUP || current == Routes.UNLOCK)) {
            navController.resetTo(Routes.VAULT)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) { SetupScreen() }
        composable(Routes.UNLOCK) { UnlockScreen() }
        composable(Routes.VAULT) {
            VaultScreen(
                onOpen = { navController.navigate(Routes.details(it)) },
                onAdd = { navController.navigate(Routes.edit()) },
                onGenerator = { navController.navigate(Routes.GENERATOR) },
            )
        }
        composable(Routes.DETAILS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            DetailsScreen(
                id = requireNotNull(entry.arguments?.getString("id")),
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            EditScreen(
                id = entry.arguments?.getString("id"),
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    navController.navigate(Routes.details(savedId)) {
                        popUpTo(Routes.VAULT)
                    }
                },
            )
        }
        composable(Routes.GENERATOR) { GeneratorScreen(onBack = { navController.popBackStack() }) }
    }
}

private fun gateRouteFor(state: VaultState): String? = when (state) {
    VaultState.NotInitialized -> Routes.SETUP
    VaultState.Locked -> Routes.UNLOCK
    is VaultState.Unlocked -> null
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
