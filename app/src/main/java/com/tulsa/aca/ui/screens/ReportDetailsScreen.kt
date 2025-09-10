package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tulsa.aca.data.models.*
import com.tulsa.aca.utils.DateUtils
import com.tulsa.aca.viewmodel.ReportDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailsScreen(
    reporteId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportDetailsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Cargar detalles al inicializar
    LaunchedEffect(reporteId) {
        viewModel.cargarDetallesReporte(reporteId)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Detalles del Reporte") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            }
        )

        // Extraer valores para evitar smart cast issues
        val isLoading = uiState.isLoading
        val error = uiState.error
        val reporteCompleto = uiState.reporteCompleto

        when {
            isLoading -> {
                ReportDetailsLoadingContent()
            }
            error != null -> {
                ReportDetailsErrorContent(
                    error = error,
                    onRetry = { viewModel.cargarDetallesReporte(reporteId) }
                )
            }
            reporteCompleto != null -> {
                ReportDetailsContent(
                    reporteCompleto = reporteCompleto,
                    activo = uiState.activo,
                    plantilla = uiState.plantilla
                )
            }
        }
    }
}

@Composable
private fun ReportDetailsLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Cargando detalles del reporte...")
        }
    }
}

@Composable
private fun ReportDetailsErrorContent(
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

@Composable
private fun ReportDetailsContent(
    reporteCompleto: com.tulsa.aca.data.repository.ReporteCompleto,
    activo: Activo?,
    plantilla: PlantillaChecklist?
) {
    val dateFormat = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("America/Santiago")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Información general del reporte
        item {
            ReportInfoCard(
                reporte = reporteCompleto.reporte,
                usuario = reporteCompleto.usuario,
                activo = activo,
                plantilla = plantilla,
                dateFormat = dateFormat
            )
        }

        // Estadísticas del reporte
        item {
            ReportStatsCard(respuestas = reporteCompleto.respuestas)
        }

        // Respuestas detalladas
        item {
            Text(
                text = "Respuestas Detalladas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(reporteCompleto.respuestas) { respuesta ->
            RespuestaDetailCard(
                respuesta = respuesta,
                fotos = reporteCompleto.fotos[respuesta.id] ?: emptyList()
            )
        }
    }
}

@Composable
private fun ReportInfoCard(
    reporte: ReporteInspeccion,
    usuario: Usuario?,
    activo: Activo?,
    plantilla: PlantillaChecklist?,
    dateFormat: SimpleDateFormat  // No se usa, pero se mantiene por compatibilidad
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Información General",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ID del reporte
            InfoRow(
                icon = Icons.AutoMirrored.Filled.Assignment,
                label = "ID del Reporte",
                value = reporte.id?.take(12) ?: "N/A"
            )

            // Fecha y hora
            InfoRow(
                icon = Icons.Default.Schedule,
                label = "Fecha y Hora",
                value = DateUtils.formatTimestamp(reporte.timestampCompletado)
            )

            // Operario
            InfoRow(
                icon = Icons.Default.Person,
                label = "Operario",
                value = usuario?.nombreCompleto ?: "Usuario ID: ${reporte.usuarioId}"
            )

            // Activo
            InfoRow(
                icon = Icons.Default.Inventory,
                label = "Activo",
                value = activo?.nombre ?: "Activo ID: ${reporte.activoId}"
            )

            // Tipo de activo
            activo?.let {
                InfoRow(
                    icon = Icons.Default.Category,
                    label = "Tipo",
                    value = "${it.tipo} - ${it.modelo}"
                )
            }

            // Plantilla usada
            InfoRow(
                icon = Icons.Default.Checklist,
                label = "Plantilla",
                value = plantilla?.nombre ?: "Plantilla ID: ${reporte.plantillaId}"
            )
        }
    }
}

@Composable
private fun ReportStatsCard(respuestas: List<RespuestaReporte>) {
    val totalRespuestas = respuestas.size
    val respuestasSi = respuestas.count { it.respuesta }
    val respuestasNo = respuestas.count { !it.respuesta }
    val conComentarios = respuestas.count { !it.comentario.isNullOrBlank() }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Estadísticas del Reporte",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.CheckCircle,
                    value = respuestasSi.toString(),
                    label = "Respuestas SÍ",
                    color = MaterialTheme.colorScheme.primary
                )

                StatItem(
                    icon = Icons.Default.Cancel,
                    value = respuestasNo.toString(),
                    label = "Respuestas NO",
                    color = MaterialTheme.colorScheme.error
                )

                StatItem(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    value = conComentarios.toString(),
                    label = "Con Comentarios",
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barra de progreso visual
            if (totalRespuestas > 0) {
                LinearProgressIndicator(
                    progress = { respuestasSi.toFloat() / totalRespuestas.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "$respuestasSi de $totalRespuestas respuestas positivas (${(respuestasSi.toFloat() / totalRespuestas * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RespuestaDetailCard(
    respuesta: RespuestaReporte,
    fotos: List<String>
) {
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
                    imageVector = if (respuesta.respuesta) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = if (respuesta.respuesta) "Sí" else "No",
                    tint = if (respuesta.respuesta) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Pregunta ID: ${respuesta.preguntaId}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                // Reemplazar Chip con AssistChip
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (respuesta.respuesta) "SÍ" else "NO",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (respuesta.respuesta)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                )
            }

            // Comentario si existe
            if (!respuesta.comentario.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Comentario:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = respuesta.comentario,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Fotos si existen
            if (fotos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Fotos (${fotos.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fotos.take(3).forEach { fotoUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(fotoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Foto de evidencia",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (fotos.size > 3) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${fotos.size - 3}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}