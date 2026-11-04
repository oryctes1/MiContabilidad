package com.oryctes.micontabilidad.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.oryctes.micontabilidad.ui.accounts.AccountsScreen
import com.oryctes.micontabilidad.ui.categories.CategoriesScreen
import com.oryctes.micontabilidad.ui.dashboard.DashboardScreen
import com.oryctes.micontabilidad.ui.import.ImportScreen
import com.oryctes.micontabilidad.ui.settings.SettingsScreen
import com.oryctes.micontabilidad.ui.summaries.SummariesScreen
import com.oryctes.micontabilidad.ui.transactions.TransactionsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToImport = { navController.navigate(Screen.Import.route) },
                onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) },
                onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                onNavigateToSummaries = { navController.navigate(Screen.Summaries.route) }
            )
        }
        composable(Screen.Accounts.route) {
            AccountsScreen()
        }
        composable(Screen.Import.route) {
            ImportScreen(
                onNavigateToTransactions = { filterType ->
                    val route = if (filterType != null) {
                        "${Screen.Transactions.route}?filter=${filterType.name}"
                    } else {
                        Screen.Transactions.route
                    }
                    navController.navigate(route) {
                        popUpTo(Screen.Dashboard.route)
                    }
                },
                onNavigateToCategories = {
                    navController.navigate(Screen.Categories.route)
                }
            )
        }
        composable(
            route = "${Screen.Transactions.route}?filter={filter}",
            arguments = listOf(
                androidx.navigation.navArgument("filter") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val filterParam = backStackEntry.arguments?.getString("filter")
            val initialFilter = filterParam?.let {
                try {
                    com.oryctes.micontabilidad.ui.transactions.TransactionTypeFilter.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }
            TransactionsScreen(
                initialFilterType = initialFilter,
                onNavigateToImport = { navController.navigate(Screen.Import.route) }
            )
        }
        composable(Screen.Summaries.route) {
            SummariesScreen()
        }
        composable(Screen.Categories.route) {
            CategoriesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
