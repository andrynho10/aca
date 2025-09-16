package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.utils.DateUtils
import com.tulsa.aca.viewmodel.EstadisticasSupervisor
import com.tulsa.aca.viewmodel.FiltrosReporte
import com.tulsa.aca.viewmodel.ReporteCompleto
import com.tulsa.aca.viewmodel.SupervisorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorPanelScreen(
    onNavigateBack: () -> Unit,
    onViewReportDetails: (String) -> Unit,
    onNavigateToActivosCrud: () -> Unit,
    onNavigateToPlantillasCrud: () -> Unit,
    modifier: Modifier = Modifier,
    supervisorViewModel: SupervisorViewModel = viewModel()
) {
    val uiState by supervisorViewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    // Cargar datos al inicializar
    LaunchedEffect(Unit) {
        supervisorViewModel.cargarDatosSupervisor()
    }

    // Manejo de errores
    val currentError = uiState.error
    LaunchedEffect(currentError) {
        if (currentError != null) {
            supervisorViewModel.limpiarError()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Panel de Supervisor") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            },
            actions = {
                IconButton(onClick = { showFilters = !showFilters }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtros"
                    )
                }
            }
        )

        when {
            uiState.isLoading -> {
                LoadingContent()
            }
            currentError != null -> {
                ErrorContent(
                    error = currentError,
                    onRetry = { supervisorViewModel.cargarDatosSupervisor() }
                )
            }
            else -> {
                SupervisorContent(
                    uiState = uiState,
                    showFilters = showFilters,
                    onFiltersChanged = { filtros ->
                        supervisorViewModel.aplicarFiltros(filtros)
                    },
                    onClearFilters = {
                        supervisorViewModel.limpiarFiltros()
                    },
                    onViewReportDetails = onViewReportDetails,
                    onNavigateToActivosCrud = onNavigateToActivosCrud,
                    onNavigateToPlantillasCrud = onNavigateToPlantillasCrud,
                    onRefresh = { supervisorViewModel.forzarRecarga() }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Cargando panel de supervisor...")
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SupervisorContent(
    uiState: com.tulsa.aca.viewmodel.SupervisorUiState,
    showFilters: Boolean,
    onFiltersChanged: (FiltrosReporte) -> Unit,
    onClearFilters: () -> Unit,
    onViewReportDetails: (String) -> Unit,
    onNavigateToActivosCrud: () -> Unit,
    onNavigateToPlantillasCrud: () -> Unit,
    onRefresh: () -> Unit
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = onRefresh
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Estadísticas generales
            item {
                EstadisticasCard(estadisticas = uiState.estadisticas)
            }

            // Acciones de gestión
            item {
                Text(
                    text = "Acciones de Gestión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElevatedCard(
                        onClick = onNavigateToActivosCrud,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = "Gestionar Activos",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gestionar\nActivos",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    ElevatedCard(
                        onClick = onNavigateToPlantillasCrud,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = "Gestionar Checklist",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gestionar\nChecklist",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Panel de filtros
            if (showFilters) {
                item {
                    FiltrosCard(
                        activos = uiState.activos,
                        operarios = uiState.operarios,
                        filtrosActuales = uiState.filtros,
                        onFiltersChanged = onFiltersChanged,
                        onClearFilters = onClearFilters
                    )
                }
            }

            // Lista de reportes
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reportes Recientes (${uiState.reportes.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.reportes.isEmpty()) {
                item {
                    EmptyReportsCard()
                }
            } else {
                items(uiState.reportes) { reporteCompleto ->
                    SupervisorReporteCard(
                        reporteCompleto = reporteCompleto,
                        onViewDetails = {
                            reporteCompleto.reporte.id?.let { onViewReportDetails(it) }
                        }
                    )
                }
            }
        }

        // Indicador de pull-to-refresh
        PullRefreshIndicator(
            refreshing = uiState.isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun EstadisticasCard(
    estadisticas: EstadisticasSupervisor
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Estadísticas Generales",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EstadisticaItem(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    valor = estadisticas.totalReportes.toString(),
                    etiqueta = "Total Reportes"
                )
                EstadisticaItem(
                    icon = Icons.Default.Today,
                    valor = estadisticas.reportesHoy.toString(),
                    etiqueta = "Hoy"
                )
                EstadisticaItem(
                    icon = Icons.Default.DateRange,
                    valor = estadisticas.reportesEstaSemana.toString(),
                    etiqueta = "Esta Semana"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EstadisticaItem(
                    icon = Icons.Default.Inventory,
                    valor = estadisticas.activosInspeccionados.toString(),
                    etiqueta = "Activos"
                )
                EstadisticaItem(
                    icon = Icons.Default.Warning,
                    valor = estadisticas.reportesConProblemas.toString(),
                    etiqueta = "Con Problemas"
                )
                EstadisticaItem(
                    icon = Icons.Default.CheckCircle,
                    valor = (estadisticas.totalReportes - estadisticas.reportesConProblemas).toString(),
                    etiqueta = "Sin Problemas"
                )
            }
        }
    }
}

@Composable
private fun EstadisticaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valor: String,
    etiqueta: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = etiqueta,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = valor,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltrosCard(
    activos: List<Activo>,
    operarios: List<Usuario>,
    filtrosActuales: FiltrosReporte,
    onFiltersChanged: (FiltrosReporte) -> Unit,
    onClearFilters: () -> Unit
) {
    var activoExpandido by remember { mutableStateOf(false) }
    var operarioExpandido by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtros",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onClearFilters) {
                    Text("Limpiar")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filtro por activo
            ExposedDropdownMenuBox(
                expanded = activoExpandido,
                onExpandedChange = { activoExpandido = !activoExpandido }
            ) {
                OutlinedTextField(
                    value = filtrosActuales.activoSeleccionado?.nombre ?: "Todos los activos",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Activo") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = activoExpandido)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = activoExpandido,
                    onDismissRequest = { activoExpandido = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todos los activos") },
                        onClick = {
                            onFiltersChanged(filtrosActuales.copy(activoSeleccionado = null))
                            activoExpandido = false
                        }
                    )
                    activos.forEach { activo ->
                        DropdownMenuItem(
                            text = { Text(activo.nombre) },
                            onClick = {
                                onFiltersChanged(filtrosActuales.copy(activoSeleccionado = activo))
                                activoExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filtro por operario
            ExposedDropdownMenuBox(
                expanded = operarioExpandido,
                onExpandedChange = { operarioExpandido = !operarioExpandido }
            ) {
                OutlinedTextField(
                    value = filtrosActuales.operarioSeleccionado?.nombreCompleto ?: "Todos los operarios",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Operario") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = operarioExpandido)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = operarioExpandido,
                    onDismissRequest = { operarioExpandido = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todos los operarios") },
                        onClick = {
                            onFiltersChanged(filtrosActuales.copy(operarioSeleccionado = null))
                            operarioExpandido = false
                        }
                    )
                    operarios.forEach { operario ->
                        DropdownMenuItem(
                            text = { Text(operario.nombreCompleto) },
                            onClick = {
                                onFiltersChanged(filtrosActuales.copy(operarioSeleccionado = operario))
                                operarioExpandido = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReportsCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Assignment,
                contentDescription = "Sin reportes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay reportes disponibles",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Los reportes aparecerán aquí cuando los operarios completen inspecciones",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SupervisorReporteCard(
    reporteCompleto: ReporteCompleto,
    onViewDetails: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Inspección",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${reporteCompleto.reporte.id?.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onViewDetails,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Ver detalles",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ver Detalles")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FECHA CORREGIDA
            Text(
                text = "📅 ${DateUtils.formatTimestamp(reporteCompleto.reporte.timestampCompletado)}",
                style = MaterialTheme.typography.bodyMedium
            )

            // OPERARIO CON NOMBRE
            Text(
                text = "👤 ${reporteCompleto.usuario?.nombreCompleto ?: "Usuario ID: ${reporteCompleto.reporte.usuarioId}"}",
                style = MaterialTheme.typography.bodyMedium
            )

            // ACTIVO CON NOMBRE
            Text(
                text = "🔧 ${reporteCompleto.activo?.nombre ?: "Activo ID: ${reporteCompleto.reporte.activoId}"}",
                style = MaterialTheme.typography.bodyMedium
            )

            // PLANTILLA CON NOMBRE
            Text(
                text = "📋 ${reporteCompleto.plantilla?.nombre ?: "Plantilla ID: ${reporteCompleto.reporte.plantillaId}"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}