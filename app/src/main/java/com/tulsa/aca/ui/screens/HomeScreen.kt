package com.tulsa.aca.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.R
import com.tulsa.aca.data.session.UserSession
import com.tulsa.aca.ui.components.NetworkStatusChip
import com.tulsa.aca.utils.HorometroNotificationHelper
import com.tulsa.aca.viewmodel.HorometroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAssetList: () -> Unit,
    onNavigateToQRScanner: () -> Unit,
    onNavigateToSupervisorPanel: () -> Unit,
    onNavigateToHorometrosPendientes: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // OBTENER EL USUARIO PRIMERO
    val currentUser = UserSession.getCurrentUser()

    // VERIFICAR SI HAY USUARIO LOGUEADO
    if (currentUser == null) {
        LaunchedEffect(Unit) {
            android.util.Log.e("HomeScreen", "No hay usuario logueado en HomeScreen - haciendo logout automático")
            onLogout()
        }
        return // No renderizar nada si no hay usuario
    }

    val isSupervisor = currentUser.rol == "SUPERVISOR"

    // Estado para el diálogo de créditos
    var showAboutDialog by remember { mutableStateOf(false) }

    // LOG PARA DEBUG
    android.util.Log.d("HomeScreen", "Usuario actual: ${currentUser.nombreCompleto}, Rol: ${currentUser.rol}")

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Checklist Inspección\nGrúas Horquilla",
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Mostrar rol del usuario al lado del título
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = currentUser.rol,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.offset(x = (-4).dp) // Acercar más al título
                    )
                }
            },
            actions = {
                // Indicador de estado de red (solo ícono)
                NetworkStatusChip(showLabel = false)

                Spacer(modifier = Modifier.width(0.dp))

                // Botón "Acerca de"
                IconButton(onClick = { showAboutDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Acerca de"
                    )
                }

                // Botón logout
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Cerrar Sesión"
                    )
                }
            }
        )

        // CONTENIDO PRINCIPAL
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_empresa),
                contentDescription = "Logo TULSA",
                modifier = Modifier
                    .size(120.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Saludo personalizado
            Text(
                text = "Bienvenido/a",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = currentUser.nombreCompleto,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (isSupervisor) {
                // VISTA PARA SUPERVISORES - Solo panel de supervisión
                ElevatedCard(
                    onClick = onNavigateToSupervisorPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = "Panel de Supervisor",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Panel de Supervisor",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Ver reportes, estadísticas y análisis completos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val horometroViewModel: HorometroViewModel = viewModel()
                val horometroUiState by horometroViewModel.uiState.collectAsState()
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    horometroViewModel.cargarPendientes()
                }

                LaunchedEffect(horometroUiState.pendientes) {
                    if (horometroUiState.pendientes.isNotEmpty()) {
                        HorometroNotificationHelper.showPendingHorometrosNotification(
                            context,
                            horometroUiState.pendientes.size
                        )
                    } else {
                        HorometroNotificationHelper.cancelPendingHorometrosNotification(context)
                    }
                }

                if (horometroUiState.pendientes.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable {
                                // Navegar a la pantalla de pendientes
                                onNavigateToHorometrosPendientes()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Pendiente",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "⚠️ Horómetros Pendientes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Tienes ${horometroUiState.pendientes.size} horómetros por cerrar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Ir",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                // VISTA PARA OPERADORES - Opciones de inspección
                Text(
                    text = "Selecciona cómo quieres identificar la grúa:",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Botón para lista de activos
                ElevatedCard(
                    onClick = onNavigateToAssetList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Lista de grúas",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(
                                text = "Lista de Grúas",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Buscar y seleccionar manualmente",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Botón para escáner QR
                ElevatedCard(
                    onClick = onNavigateToQRScanner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Escáner QR",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(
                                text = "Escáner QR",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Escanear código QR del activo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Diálogo de créditos
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Acerca de",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Checklist Inspección de Grúas Horquilla",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Logo
                        Image(
                            painter = painterResource(id = R.drawable.logo_empresa),
                            contentDescription = "Logo ACA",
                            modifier = Modifier
                                .size(120.dp)
                                .padding(vertical = 8.dp),
                            contentScale = ContentScale.Fit
                        )

                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )

                        // Información de la app
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Versión 1.0.3",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Sistema de gestión de inspecciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )

                        // Créditos
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Desarrollado por",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Andrés Amaya Garcés",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Desarrollador",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )

                        // Información adicional
                        Text(
                            text = "© ${java.time.Year.now().value} Tulsa S.A.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}
