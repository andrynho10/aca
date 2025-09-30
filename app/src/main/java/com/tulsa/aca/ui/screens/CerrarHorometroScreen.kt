package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.viewmodel.HorometroViewModel

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

    LaunchedEffect(reporteId) {
        viewModel.cargarInfoReporte(reporteId)
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
                        Text("Turno: ${reporte.turno}")
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
                    supportingText = {
                        val horas = horometroFinal.toFloatOrNull()?.let {
                            it - reporte.horometroInicial
                        }
                        if (horas != null && horas > 0) {
                            Text("Horas de uso: $horas")
                        }
                    },
                    isError = horometroFinal.isNotEmpty() &&
                            (horometroFinal.toFloatOrNull() ?: 0f) <= reporte.horometroInicial
                )

                // Observaciones (opcional)
                OutlinedTextField(
                    value = observaciones,
                    onValueChange = { observaciones = it },
                    label = { Text("Observaciones (opcional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.weight(1f))

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
                                    // Mostrar error
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
                    Text("Guardar y Cerrar")
                }
            }
        }
    }
}