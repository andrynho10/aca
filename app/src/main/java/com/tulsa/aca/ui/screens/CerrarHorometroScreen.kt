package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.viewmodel.HorometroViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CerrarHorometroScreen(
    reporteId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HorometroViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var horometroFinal by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Calcular horas de uso y tiempo transcurrido estimado
    val horasUso = remember(horometroFinal, uiState.reporteActual) {
        val final = horometroFinal.toFloatOrNull()
        val inicial = uiState.reporteActual?.horometroInicial
        if (final != null && inicial != null && final > inicial) {
            final - inicial
        } else null
    }

    LaunchedEffect(reporteId) {
        viewModel.cargarInfoReporte(reporteId)
    }

    // Mostrar errores en Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.limpiarError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cerrar Horómetro") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.reporteActual != null) {
                val reporte = uiState.reporteActual!!

                // Info del reporte
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Información del Reporte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Grúa: ${reporte.grua}")
                        Text("Horómetro Inicial: ${reporte.horometroInicial}")
                        reporte.turno?.let { Text("Turno: $it") }
                    }
                }

                // Advertencias de validación
                val final = horometroFinal.toFloatOrNull()
                val esInvalido = final != null && final <= reporte.horometroInicial

                if (esInvalido) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "El horómetro final debe ser mayor al inicial (${reporte.horometroInicial})",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Info de horas de uso calculadas
                if (horasUso != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column {
                                    Text(
                                        "Horas de uso que se registrarán:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "$horasUso horas",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "Equivalente a ${(horasUso * 60).roundToInt()} minutos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Input horómetro final
                OutlinedTextField(
                    value = horometroFinal,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d*$"))) {
                            horometroFinal = it
                        }
                    },
                    label = { Text("Horómetro Final *") },
                    placeholder = { Text("Ej: ${reporte.horometroInicial + 5}") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = esInvalido
                )

                // Observaciones (opcional)
                OutlinedTextField(
                    value = observaciones,
                    onValueChange = { observaciones = it },
                    label = { Text("Observaciones (opcional)") },
                    placeholder = { Text("Ej: Horómetro marcaba X al finalizar") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.weight(1f))

                // Info importante sobre validaciones
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "Las horas de uso no pueden exceder el tiempo real transcurrido desde el inicio de la inspección.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // Botón guardar
                Button(
                    onClick = {
                        val horometroFinalValue = horometroFinal.toFloatOrNull()
                        if (horometroFinalValue != null && horometroFinalValue > reporte.horometroInicial) {
                            viewModel.cerrarHorometro(
                                reporteId = reporteId,
                                horometroFinal = horometroFinalValue,
                                observaciones = observaciones.takeIf { it.isNotBlank() },
                                onSuccess = {
                                    onNavigateBack()
                                },
                                onError = { error ->
                                    // El error se muestra por el Snackbar
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = horometroFinal.isNotEmpty() &&
                            (horometroFinal.toFloatOrNull() ?: 0f) > reporte.horometroInicial &&
                            !uiState.isLoadingAction
                ) {
                    if (uiState.isLoadingAction) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isLoadingAction) "Guardando..." else "Guardar y Cerrar")
                }
            } else if (uiState.error != null) {
                // Estado de error
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No se pudo cargar el reporte",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Volver")
                    }
                }
            }
        }
    }
}