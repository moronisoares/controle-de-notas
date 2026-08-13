package com.example.controlenotas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.controlenotas.data.AppDatabase
import com.example.controlenotas.ui.AddInvoiceScreen
import com.example.controlenotas.ui.InvoiceListScreen
import com.example.controlenotas.ui.InvoiceViewModel
import com.example.controlenotas.ui.InvoiceViewModelFactory
import com.example.controlenotas.ui.MonthlySummaryScreen
import com.example.controlenotas.ui.theme.ControleNotasTheme

class MainActivity : ComponentActivity() {

    /** Arquivo recebido de outro app ("Abrir com" / compartilhar um PDF). */
    private var incomingFile by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingFile = extractSharedFile(intent)

        val dao = AppDatabase.getInstance(applicationContext).invoiceDao()
        setContent {
            ControleNotasTheme {
                AppNavHost(
                    factory = InvoiceViewModelFactory(dao),
                    incomingFile = incomingFile,
                    onIncomingHandled = { incomingFile = null }
                )
            }
        }
    }

    /** O app usa launchMode singleTask: novos PDFs chegam por aqui. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedFile(intent)?.let { incomingFile = it }
    }

    private fun extractSharedFile(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    }
}

@Composable
private fun AppNavHost(
    factory: InvoiceViewModelFactory,
    incomingFile: Uri?,
    onIncomingHandled: () -> Unit
) {
    val navController = rememberNavController()
    val viewModel: InvoiceViewModel = viewModel(factory = factory)

    NotificationPermissionRequest()

    // Arquivo pendente a ser anexado na tela de nova nota.
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(incomingFile) {
        val uri = incomingFile ?: return@LaunchedEffect
        pendingImport = uri
        onIncomingHandled()
        navController.navigate(ROUTE_ADD) { launchSingleTop = true }
    }

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
                    onAddClick = { navController.navigate(ROUTE_ADD) },
                    onInvoiceClick = { invoice -> navController.navigate("edit/${invoice.id}") }
                )
            }
            composable(ROUTE_SUMMARY) {
                MonthlySummaryScreen(viewModel = viewModel)
            }
            composable(ROUTE_ADD) {
                AddInvoiceScreen(
                    viewModel = viewModel,
                    importUri = pendingImport,
                    onDone = {
                        pendingImport = null
                        navController.popBackStack()
                    },
                    onBack = {
                        pendingImport = null
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = ROUTE_EDIT,
                arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("invoiceId") ?: -1L
                val invoice = viewModel.getInvoice(id)
                if (invoice == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    AddInvoiceScreen(
                        viewModel = viewModel,
                        existing = invoice,
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/** Android 13+ exige permissão para mostrar o aviso de exportação concluída. */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sem permissão o app segue funcionando, apenas sem notificação */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val ROUTE_LIST = "list"
private const val ROUTE_SUMMARY = "summary"
private const val ROUTE_ADD = "add"
private const val ROUTE_EDIT = "edit/{invoiceId}"

private fun NavDestination?.isRoute(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

private fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
