package com.tulsa.aca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.tulsa.aca.data.session.UserSession
import com.tulsa.aca.ui.navigation.Screen
import com.tulsa.aca.ui.screens.ActivosCrudScreen
import com.tulsa.aca.ui.screens.AssetHistoryScreen
import com.tulsa.aca.ui.screens.AssetListScreen
import com.tulsa.aca.ui.screens.ChecklistScreen
import com.tulsa.aca.ui.screens.ChecklistSelectionScreen
import com.tulsa.aca.ui.screens.HomeScreen
import com.tulsa.aca.ui.screens.PlantillaEditorScreen
import com.tulsa.aca.ui.screens.PlantillasCrudScreen
import com.tulsa.aca.ui.screens.ReportDetailsScreen
import com.tulsa.aca.ui.screens.SupervisorPanelScreen
import com.tulsa.aca.ui.theme.ACATheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ACATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ACAApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ACAApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        // Pantalla principal
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAssetList = {
                    navController.navigate(Screen.AssetList.route)
                },
                onNavigateToQRScanner = {
                    navController.navigate(Screen.QRScanner.route)
                },
                onNavigateToSupervisorPanel = {
                    navController.navigate(Screen.SupervisorPanel.route)
                }
            )
        }

        // Pantalla de lista de activos
        composable(Screen.AssetList.route) {
            AssetListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onAssetSelected = { activo ->
                    // Navegar a selección de checklist pasando el ID del activo
                    navController.navigate(
                        Screen.ChecklistSelection.createRoute(activo.id ?: 0)
                    )
                }
            )
        }

        // Pantalla de escáner QR (placeholder por ahora)
        composable(Screen.QRScanner.route) {
            QRScannerPlaceholder(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Pantalla de selección de checklist
        composable(
            route = Screen.ChecklistSelection.route,
            arguments = listOf(navArgument("assetId") { type = NavType.IntType })
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getInt("assetId") ?: 0
            ChecklistSelectionScreen(
                assetId = assetId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onViewHistory = { assetId ->
                    navController.navigate(Screen.AssetHistory.createRoute(assetId))
                },
                onChecklistSelected = { assetId, templateId ->
                    navController.navigate(
                        Screen.Checklist.createRoute(assetId, templateId)
                    )
                }
            )
        }
        // Pantalla de historial del activo
        composable(
            route = Screen.AssetHistory.route,
            arguments = listOf(navArgument("assetId") { type = NavType.IntType })
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getInt("assetId") ?: 0
            AssetHistoryScreen(
                assetId = assetId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNewInspection = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.PlantillasCrud.route) {
            PlantillasCrudScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEditor = { plantillaId ->
                    navController.navigate(Screen.PlantillaEditor.createRoute(plantillaId))
                }
            )
        }
        composable(Screen.SupervisorPanel.route) {
            SupervisorPanelScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onViewReportDetails = { reporteId ->
                    navController.navigate(Screen.ReportDetails.createRoute(reporteId))
                },
                onNavigateToActivosCrud = {
                    navController.navigate(Screen.ActivosCrud.route)
                },
                onNavigateToPlantillasCrud = { // NUEVO
                    navController.navigate(Screen.PlantillasCrud.route)
                }
            )
        }
        // Ruta de detalles del reporte
        composable(
            route = Screen.ReportDetails.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            ReportDetailsScreen(
                reporteId = reportId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        // Ruta CRUD de activos (grúas)
        composable(Screen.ActivosCrud.route) {
            ActivosCrudScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        // Pantalla de checklist
        composable(
            route = Screen.Checklist.route,
            arguments = listOf(
                navArgument("assetId") { type = NavType.IntType },
                navArgument("templateId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getInt("assetId") ?: 0
            val templateId = backStackEntry.arguments?.getInt("templateId") ?: 0
            ChecklistScreen(
                assetId = assetId,
                templateId = templateId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onChecklistCompleted = {
                    // Navegar de vuelta al home después de completar
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }
        // Ruta del editor
        composable(
            route = Screen.PlantillaEditor.route,
            arguments = listOf(navArgument("plantillaId") { type = NavType.IntType })
        ) { backStackEntry ->
            val plantillaId = backStackEntry.arguments?.getInt("plantillaId") ?: 0
            PlantillaEditorScreen(
                plantillaId = plantillaId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// Pantallas placeholder temporales
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerPlaceholder(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Escáner QR") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            }
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Pantalla de Escáner QR\n(En construcción)",
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}



