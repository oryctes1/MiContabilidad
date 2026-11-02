package com.oryctes.micontabilidad.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.oryctes.micontabilidad.ui.accounts.AccountsScreen
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
                onNavigateToTransactions = {
                    navController.navigate(Screen.Transactions.route) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }
        composable(Screen.Transactions.route) {
            TransactionsScreen()
        }
        composable(Screen.Summaries.route) {
            SummariesScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
