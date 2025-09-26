package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import java.util.Calendar
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
    LaunchedEffect(key1 = Unit) {
        supervisorViewModel.cargarDatosDesdeDetalle()
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
                    onRefresh = { supervisorViewModel.forzarRecarga() },
                    onLoadMore = { supervisorViewModel.cargarMasReportes() }
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
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
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
                                tint = MaterialTheme.colorScheme.primary,
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
                        operadores = uiState.operadores,
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
                        text = "Reportes Recientes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    // MOSTRAR CONTADOR ACTUALIZADO
                    Text(
                        text = if (uiState.totalReportesDisponibles > 0) {
                            "Mostrando ${uiState.reportesMostrados} de ${uiState.totalReportesDisponibles}"
                        } else {
                            "Sin reportes"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                // BOTÓN "CARGAR MÁS" (si hay más reportes disponibles)
                if (uiState.puedeCargarMas) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = onLoadMore,
                                enabled = !uiState.isLoadingMore,
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                if (uiState.isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cargando...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = "Cargar más",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cargar 15 más")
                                }
                            }
                        }
                    }
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
    operadores: List<Usuario>,
    filtrosActuales: FiltrosReporte,
    onFiltersChanged: (FiltrosReporte) -> Unit,
    onClearFilters: () -> Unit
) {
    var activoExpandido by remember { mutableStateOf(false) }
    var operadorExpandido by remember { mutableStateOf(false) }
    var quickFilterExpandido by remember { mutableStateOf(false) }

    // ESTADOS PARA DATE PICKERS
    var showDatePickerDesde by remember { mutableStateOf(false) }
    var showDatePickerHasta by remember { mutableStateOf(false) }

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
                    Text("Limpiar todo")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // FILTROS RÁPIDOS POR FECHA
            Text(
                text = "Período de tiempo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = esSameDay(filtrosActuales.fechaDesde, DateUtils.getToday()) &&
                                esSameDay(filtrosActuales.fechaHasta, DateUtils.getToday()),
                        onClick = {
                            val hoy = DateUtils.getToday()
                            onFiltersChanged(filtrosActuales.copy(
                                fechaDesde = hoy,
                                fechaHasta = hoy
                            ))
                        },
                        label = { Text("Hoy") }
                    )
                }

                item {
                    FilterChip(
                        selected = esSameDay(filtrosActuales.fechaDesde, DateUtils.getYesterday()) &&
                                esSameDay(filtrosActuales.fechaHasta, DateUtils.getYesterday()),
                        onClick = {
                            val ayer = DateUtils.getYesterday()
                            onFiltersChanged(filtrosActuales.copy(
                                fechaDesde = ayer,
                                fechaHasta = ayer
                            ))
                        },
                        label = { Text("Ayer") }
                    )
                }

                item {
                    FilterChip(
                        selected = esSameDay(filtrosActuales.fechaDesde, DateUtils.getStartOfWeek()) &&
                                esSameDay(filtrosActuales.fechaHasta, DateUtils.getToday()),
                        onClick = {
                            onFiltersChanged(filtrosActuales.copy(
                                fechaDesde = DateUtils.getStartOfWeek(),
                                fechaHasta = DateUtils.getToday()
                            ))
                        },
                        label = { Text("Esta Semana") }
                    )
                }

                item {
                    FilterChip(
                        selected = esSameDay(filtrosActuales.fechaDesde, DateUtils.getDaysAgo(7)) &&
                                esSameDay(filtrosActuales.fechaHasta, DateUtils.getToday()),
                        onClick = {
                            onFiltersChanged(filtrosActuales.copy(
                                fechaDesde = DateUtils.getDaysAgo(7),
                                fechaHasta = DateUtils.getToday()
                            ))
                        },
                        label = { Text("Últimos 7 días") }
                    )
                }

                item {
                    FilterChip(
                        selected = esSameDay(filtrosActuales.fechaDesde, DateUtils.getStartOfMonth()) &&
                                esSameDay(filtrosActuales.fechaHasta, DateUtils.getToday()),
                        onClick = {
                            onFiltersChanged(filtrosActuales.copy(
                                fechaDesde = DateUtils.getStartOfMonth(),
                                fechaHasta = DateUtils.getToday()
                            ))
                        },
                        label = { Text("Este Mes") }
                    )
                }

                item {
                    FilterChip(
                        selected = esSameDay(filtrosActuales.fechaDesde, DateUtils.getDaysAgo(30)) &&
                                esSameDay(filtrosActuales.fechaHasta, DateUtils.getToday()),
                        onClick = {
                            onFiltersChanged(filtrosActuales.copy(
                                fechaDesde = DateUtils.getDaysAgo(30),
                                fechaHasta = DateUtils.getToday()
                            ))
                        },
                        label = { Text("Últimos 30 días") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SELECTORES DE FECHA PERSONALIZADOS
            Text(
                text = "Fechas personalizadas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fecha Desde
                OutlinedTextField(
                    value = filtrosActuales.fechaDesde?.let { DateUtils.formatDateOnly(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Desde") },
                    placeholder = { Text("dd/mm/aaaa") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePickerDesde = true }) {
                            Icon(Icons.Default.DateRange, "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                // Fecha Hasta
                OutlinedTextField(
                    value = filtrosActuales.fechaHasta?.let { DateUtils.formatDateOnly(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Hasta") },
                    placeholder = { Text("dd/mm/aaaa") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePickerHasta = true }) {
                            Icon(Icons.Default.DateRange, "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Botón para limpiar fechas
            if (filtrosActuales.fechaDesde != null || filtrosActuales.fechaHasta != null) {
                TextButton(
                    onClick = {
                        onFiltersChanged(filtrosActuales.copy(
                            fechaDesde = null,
                            fechaHasta = null
                        ))
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Limpiar fechas",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpiar fechas")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Checkbox "Solo con problemas"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = filtrosActuales.soloConProblemas,
                    onCheckedChange = { checked ->
                        onFiltersChanged(filtrosActuales.copy(soloConProblemas = checked))
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Solo reportes con problemas",
                    style = MaterialTheme.typography.bodyMedium
                )
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

            // Filtro por operador
            ExposedDropdownMenuBox(
                expanded = operadorExpandido,
                onExpandedChange = { operadorExpandido = !operadorExpandido }
            ) {
                OutlinedTextField(
                    value = filtrosActuales.operadorSeleccionado?.nombreCompleto ?: "Todos los operadores",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Operador") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = operadorExpandido)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = operadorExpandido,
                    onDismissRequest = { operadorExpandido = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todos los operadores") },
                        onClick = {
                            onFiltersChanged(filtrosActuales.copy(operadorSeleccionado = null))
                            operadorExpandido = false
                        }
                    )
                    operadores.forEach { operador ->
                        DropdownMenuItem(
                            text = { Text(operador.nombreCompleto) },
                            onClick = {
                                onFiltersChanged(filtrosActuales.copy(operadorSeleccionado = operador))
                                operadorExpandido = false
                            }
                        )
                    }
                }
            }
        }
    }
    // DATE PICKERS
    if (showDatePickerDesde) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerDesde = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Date(millis)
                        onFiltersChanged(filtrosActuales.copy(fechaDesde = selectedDate))
                    }
                    showDatePickerDesde = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDesde = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDatePickerHasta) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerHasta = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Date(millis)
                        onFiltersChanged(filtrosActuales.copy(fechaHasta = selectedDate))
                    }
                    showDatePickerHasta = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerHasta = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// FUNCIÓN AUXILIAR para comparar fechas por día
private fun esSameDay(date1: Date?, date2: Date?): Boolean {
    if (date1 == null || date2 == null) return false

    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }

    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
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
                text = "Los reportes aparecerán aquí cuando los operadores completen inspecciones",
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
            // REORGANIZAR HEADER
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

            // INDICADOR DE PROBLEMAS SEPARADO Y MÁS VISIBLE
            if (reporteCompleto.tieneProblemas) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                "Con Problemas",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Tiene problemas",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.height(32.dp) // Altura un poco más generosa
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp)) // Más espacio antes de los detalles

            // DETALLES DEL REPORTE
            Text(
                text = "📅 ${DateUtils.formatTimestamp(reporteCompleto.reporte.timestampCompletado)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "👤 ${reporteCompleto.usuario?.nombreCompleto ?: "Usuario ID: ${reporteCompleto.reporte.usuarioId}"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "🔧 ${reporteCompleto.activo?.nombre ?: "Activo ID: ${reporteCompleto.reporte.activoId}"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "📋 ${reporteCompleto.plantilla?.nombre ?: "Plantilla ID: ${reporteCompleto.reporte.plantillaId}"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}