package com.oryctes.micontabilidad.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
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
                onNavigateToImport = {
                    navController.navigate(Screen.Import.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToAccounts = {
                    navController.navigate(Screen.Accounts.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToTransactions = {
                    navController.navigate(Screen.Transactions.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToAccountTransactions = { accountId ->
                    navController.navigate("${Screen.Transactions.route}?accountId=$accountId") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onNavigateToSummaries = {
                    navController.navigate(Screen.Summaries.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(Screen.Accounts.route) {
            AccountsScreen(
                onNavigateToAccountTransactions = { accountId ->
                    navController.navigate("${Screen.Transactions.route}?accountId=$accountId") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
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
            route = "${Screen.Transactions.route}?filter={filter}&accountId={accountId}",
            arguments = listOf(
                androidx.navigation.navArgument("filter") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("accountId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val filterParam = backStackEntry.arguments?.getString("filter")
            val accountIdParam = backStackEntry.arguments?.getString("accountId")?.toLongOrNull()
            val initialFilter = filterParam?.let {
                try {
                    com.oryctes.micontabilidad.ui.transactions.TransactionTypeFilter.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }
            TransactionsScreen(
                initialAccountId = accountIdParam,
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
