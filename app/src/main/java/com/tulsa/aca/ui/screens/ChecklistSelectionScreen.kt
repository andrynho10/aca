package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
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
    onChecklistSelected: (Int, Int) -> Unit, // assetId, templateId
    modifier: Modifier = Modifier,
    activoViewModel: ActivoViewModel = viewModel(),
    plantillaViewModel: PlantillaViewModel = viewModel()
) {
    val activos by activoViewModel.activos.collectAsState()
    val plantillas by plantillaViewModel.plantillas.collectAsState()
    val isLoading by plantillaViewModel.isLoading.collectAsState()

    // Buscar el activo seleccionado
    val activo = remember(activos, assetId) {
        activos.find { it.id == assetId }
    }

    // Cargar activos si no están cargados
    LaunchedEffect(Unit) {
        if (activos.isEmpty()) {
            activoViewModel.cargarActivos()
        }
    }

    // Cargar plantillas cuando tengamos el activo
    LaunchedEffect(activo) {
        activo?.let {
            plantillaViewModel.cargarPlantillasPorTipo(it.tipo)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
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

        // Información del activo
        activo?.let {
            AssetInfoCard(
                activo = it,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }

        // Lista de checklists disponibles
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(plantillas) { plantilla ->
                ChecklistCard(
                    plantilla = plantilla,
                    onClick = {
                        onChecklistSelected(assetId, plantilla.id)
                    }
                )
            }
        }

        // Mensaje si no hay checklists
        if (!isLoading && plantillas.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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