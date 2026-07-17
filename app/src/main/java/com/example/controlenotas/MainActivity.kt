package com.example.controlenotas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.controlenotas.data.AppDatabase
import com.example.controlenotas.ui.AddInvoiceScreen
import com.example.controlenotas.ui.InvoiceListScreen
import com.example.controlenotas.ui.InvoiceViewModel
import com.example.controlenotas.ui.InvoiceViewModelFactory
import com.example.controlenotas.ui.MonthlySummaryScreen
import com.example.controlenotas.ui.theme.ControleNotasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.getInstance(applicationContext).invoiceDao()
        setContent {
            ControleNotasTheme {
                AppNavHost(factory = InvoiceViewModelFactory(dao))
            }
        }
    }
}

@Composable
private fun AppNavHost(factory: InvoiceViewModelFactory) {
    val navController = rememberNavController()
    val viewModel: InvoiceViewModel = viewModel(factory = factory)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute == ROUTE_LIST || currentRoute == ROUTE_SUMMARY

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination.isRoute(ROUTE_LIST),
                        onClick = { navController.navigateTab(ROUTE_LIST) },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null
                            )
                        },
                        label = { Text("Notas") }
                    )
                    NavigationBarItem(
                        selected = currentDestination.isRoute(ROUTE_SUMMARY),
                        onClick = { navController.navigateTab(ROUTE_SUMMARY) },
                        icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                        label = { Text("Resumo") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LIST,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_LIST) {
                InvoiceListScreen(
                    viewModel = viewModel,
                    onAddClick = { navController.navigate(ROUTE_ADD) }
                )
            }
            composable(ROUTE_SUMMARY) {
                MonthlySummaryScreen(viewModel = viewModel)
            }
            composable(ROUTE_ADD) {
                AddInvoiceScreen(
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private const val ROUTE_LIST = "list"
private const val ROUTE_SUMMARY = "summary"
private const val ROUTE_ADD = "add"

private fun NavDestination?.isRoute(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

private fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
