package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.ReporteConUsuario
import com.tulsa.aca.viewmodel.HistorialViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetHistoryScreen(
    assetId: Int,
    onNavigateBack: () -> Unit,
    onNewInspection: () -> Unit,
    modifier: Modifier = Modifier,
    historialViewModel: HistorialViewModel = viewModel()
    ) {
    val uiState by historialViewModel.uiState.collectAsState()

    // Cargar datos al inicializar la pantalla
    LaunchedEffect(assetId) {
        historialViewModel.cargarHistorialActivo(assetId)
    }

    // Mostrar error si existe - extraemos la variable aquí
    val currentError = uiState.error
    LaunchedEffect(currentError) {
        if (currentError != null) {
            // Aquí podrías mostrar un SnackBar si lo deseas
            historialViewModel.limpiarError()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Historial del Activo") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            }
        )

        // Contenido principal
        when {
            uiState.isLoading -> {
                LoadingContent()
            }
            currentError != null -> {
                ErrorContent(
                    error = currentError,
                    onRetry = { historialViewModel.cargarHistorialActivo(assetId) }
                )
            }
            else -> {
                HistoryContent(
                    activo = uiState.activo,
                    reportes = uiState.reportes,
                    onNewInspection = onNewInspection,
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
            Text("Cargando historial...")
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
                imageVector = Icons.Default.Info,
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

@Composable
private fun HistoryContent(
    activo: Activo?,
    reportes: List<ReporteConUsuario>,
    onNewInspection: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Información del activo
        item {
            activo?.let {
                AssetInfoCard(activo = it)
            }
        }

        // Botón para nueva inspección
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = "Nueva inspección",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Realizar Nueva Inspección",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNewInspection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar Checklist")
                    }
                }
            }
        }

        // Título del historial
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Historial",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Historial de Inspecciones",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Lista de reportes
        if (reportes.isEmpty()) {
            item {
                EmptyHistoryCard()
            }
        } else {
            items(reportes) { reporteConUsuario ->
                ReporteCard(
                    reporteConUsuario = reporteConUsuario
                )
            }
        }
    }
}

@Composable
private fun AssetInfoCard(
    activo: Activo,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Información del Activo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = activo.nombre,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Tipo: ${activo.tipo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Modelo: ${activo.modelo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Código QR: ${activo.codigoQr}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
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
                imageVector = Icons.Default.History,
                contentDescription = "Sin historial",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay inspecciones previas",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Esta será la primera inspección de este activo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReporteCard(
    reporteConUsuario: ReporteConUsuario,
    modifier: Modifier = Modifier,
) {
    // Formateo de fecha en hora local de Chile
    val dateFormat = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("America/Santiago") // Hora de Chile
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
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
                        style = MaterialTheme.typography.titleMedium
                    )
                    reporteConUsuario.reporte.id?.let { reporteId ->
                        Text(
                            text = "ID: ${reporteId.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    // Estado visual
                    AssistChip(
                        onClick = { },
                        label = { Text("Completada") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = "Completada",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fecha en hora local
            reporteConUsuario.reporte.timestampCompletado?.let { timestamp ->
                val dateText = formatTimestampToLocal(timestamp, dateFormat)
                Text(
                    text = "Fecha: $dateText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Nombre del operario (o ID si no se encuentra el usuario)
            val operarioText = reporteConUsuario.usuario?.nombreCompleto
                ?: "Usuario ID: ${reporteConUsuario.reporte.usuarioId}"
            Text(
                text = "Operario: $operarioText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Plantilla utilizada
            Text(
                text = "Plantilla ID: ${reporteConUsuario.reporte.plantillaId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Función auxiliar para formatear timestamp a hora local de Chile
private fun formatTimestampToLocal(timestamp: String, dateFormat: SimpleDateFormat): String {
    return try {
        // El timestamp viene como ISO string, lo convertimos a Date
        val date = if (timestamp.contains("T")) {
            // Si es ISO format
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")
            isoFormat.parse(timestamp)
        } else {
            // Si es timestamp en milisegundos
            Date(timestamp.toLong())
        }

        date?.let { dateFormat.format(it) } ?: timestamp
    } catch (e: Exception) {
        // Si hay error, intentar con diferentes formatos
        try {
            val date = Date(timestamp.toLong())
            dateFormat.format(date)
        } catch (e2: Exception) {
            timestamp
        }
    }
}