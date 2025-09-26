package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.viewmodel.ActivoViewModel
import com.tulsa.aca.viewmodel.PlantillaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistSelectionScreen(
    assetId: Int,
    onNavigateBack: () -> Unit,
    onViewHistory: (Int) -> Unit,
    onChecklistSelected: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    activoViewModel: ActivoViewModel = viewModel(),
    plantillaViewModel: PlantillaViewModel = viewModel()
) {
    // Estados existentes...
    val activos by activoViewModel.activos.collectAsState()
    val plantillas by plantillaViewModel.plantillas.collectAsState()
    val isLoading by plantillaViewModel.isLoading.collectAsState()

    // 🆕 NUEVO: Estado para dialog de confirmación
    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedTemplateId by remember { mutableStateOf<Int?>(null) }

    val activo = remember(activos, assetId) {
        activos.find { it.id == assetId }
    }

    // LaunchedEffects existentes...
    LaunchedEffect(Unit) {
        if (activos.isEmpty()) {
            activoViewModel.cargarActivos()
        }
    }

    LaunchedEffect(activo) {
        activo?.let {
            plantillaViewModel.cargarPlantillasPorTipo(it.tipo)
        }
    }

    // 🆕 NUEVO: Dialog de confirmación inicio
    if (showConfirmDialog) {
        ConfirmacionInicioInspeccionDialog(
            nombreActivo = activo?.nombre ?: "Activo",
            onConfirmar = {
                showConfirmDialog = false
                selectedTemplateId?.let { templateId ->
                    // AQUÍ SE REGISTRA EL INICIO DEL TIMESTAMP
                    onChecklistSelected(assetId, templateId)
                }
            },
            onCancelar = {
                showConfirmDialog = false
                selectedTemplateId = null
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // TopAppBar existente...
        TopAppBar(
            title = { Text("Seleccionar Checklist") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            }
        )

        // AssetInfoCard existente...
        activo?.let {
            AssetInfoCardWithHistory(
                activo = it,
                onViewHistory = { onViewHistory(assetId) },
                modifier = Modifier.padding(16.dp)
            )
        }

        // Loading indicator existente...
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }

        // MODIFICADO: Lista de checklists - MOSTRAR DIALOG ANTES
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(plantillas) { plantilla ->
                ChecklistCard(
                    plantilla = plantilla,
                    onClick = {
                        // 🆕 EN LUGAR DE NAVEGAR DIRECTO, MOSTRAR DIALOG
                        selectedTemplateId = plantilla.id
                        showConfirmDialog = true
                    }
                )
            }
        }

        // Mensaje vacío existente...
        if (!isLoading && plantillas.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No hay checklists disponibles",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Para el tipo: ${activo?.tipo ?: "Desconocido"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// NUEVO COMPOSABLE: Dialog de confirmación
@Composable
private fun ConfirmacionInicioInspeccionDialog(
    nombreActivo: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Assignment,
                contentDescription = "Iniciar Inspección",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Iniciar Inspección",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Vas a iniciar la inspección de:",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = nombreActivo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Recomendación:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Es más valorable tomarse el tiempo necesario para realizar una inspección completa y detallada que hacerlo rápidamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• Revisa cada punto cuidadosamente\n• Toma fotos de cualquier problema\n• Agrega comentarios detallados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Iniciar Inspección")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun AssetInfoCardWithHistory(
    activo: Activo,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Activo Seleccionado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = activo.nombre,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Tipo: ${activo.tipo} • Modelo: ${activo.modelo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // BOTÓN PARA VER HISTORIAL - ESTA ES LA PARTE NUEVA
            OutlinedButton(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Ver historial",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Historial de Inspecciones")
            }
        }
    }
}

// Mantener por si acaso
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
                text = "Activo Seleccionado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = activo.nombre,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Tipo: ${activo.tipo} • Modelo: ${activo.modelo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChecklistCard(
    plantilla: PlantillaChecklist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Assignment,
                contentDescription = "Checklist",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = plantilla.nombre,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Para activos tipo: ${plantilla.tipoActivo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}