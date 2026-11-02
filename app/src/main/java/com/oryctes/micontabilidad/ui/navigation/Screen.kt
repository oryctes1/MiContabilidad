package com.oryctes.micontabilidad.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Inicio", Icons.Default.Dashboard)
    data object Accounts : Screen("accounts", "Cuentas", Icons.Default.AccountBalance)
    data object Import : Screen("import", "Importar", Icons.Default.UploadFile)
    data object Transactions : Screen("transactions", "Movimientos", Icons.Default.List)
    data object Summaries : Screen("summaries", "Informes", Icons.Default.BarChart)
    data object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(
            Dashboard,
            Accounts,
            Import,
            Transactions,
            Summaries
        )
    }
}
