package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.viewmodel.PlantillasCrudViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantillasCrudScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlantillasCrudViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Si está mostrando detalles, mostrar esa vista
    if (uiState.showDetailView) {
        PlantillaDetailScreen(
            plantilla = uiState.plantillaDetallada,
            onNavigateBack = { viewModel.cerrarDialogos() },
            onEdit = { plantilla ->
                viewModel.cerrarDialogos()
                viewModel.mostrarDialogoEditar(plantilla)
            },
            onEditContent = { plantilla ->
                viewModel.cerrarDialogos()
                onNavigateToEditor(plantilla.id)
            }
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Gestión de Checklists") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.mostrarDialogoCrear() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar Checklist"
                    )
                }
            }
        )

        // Contenido principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Barra de búsqueda
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.buscarPlantillas(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de plantillas
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.plantillas.isEmpty() && uiState.searchQuery.isBlank() -> {
                    EmptyContent(
                        message = "No hay checklists registrados",
                        action = "Crear Primer Checklist",
                        onAction = { viewModel.mostrarDialogoCrear() }
                    )
                }
                uiState.plantillas.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                    EmptyContent(
                        message = "No se encontraron checklists con '${uiState.searchQuery}'",
                        action = "Limpiar búsqueda",
                        onAction = { viewModel.buscarPlantillas("") }
                    )
                }
                else -> {
                    PlantillasList(
                        plantillas = uiState.plantillas,
                        onViewDetails = { viewModel.mostrarDetalles(it) },
                        onEdit = { viewModel.mostrarDialogoEditar(it) },
                        onDelete = { viewModel.mostrarDialogoEliminar(it) }
                    )
                }
            }
        }
    }

    // Mostrar error si existe
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.limpiarError()
        }

        AlertDialog(
            onDismissRequest = { viewModel.limpiarError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.limpiarError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Diálogos
    if (uiState.showCreateDialog) {
        PlantillaDialog(
            title = "Crear Checklist",
            plantilla = null,
            tiposActivo = uiState.tiposActivo,
            onConfirm = { viewModel.crearPlantilla(it) },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    val plantillaSeleccionada = uiState.plantillaSeleccionada
    if (uiState.showEditDialog && plantillaSeleccionada != null) {
        PlantillaDialog(
            title = "Editar Checklist",
            plantilla = plantillaSeleccionada,
            tiposActivo = uiState.tiposActivo,
            onConfirm = { viewModel.actualizarPlantilla(it) },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    if (uiState.showDeleteDialog && plantillaSeleccionada != null) {
        DeletePlantillaDialog(
            plantilla = plantillaSeleccionada,
            onConfirm = { viewModel.eliminarPlantilla() },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isDeleting
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Buscar por nombre o tipo de activo...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Limpiar"
                    )
                }
            }
        },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words
        )
    )
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
            Text("Cargando checklists...")
        }
    }
}

@Composable
private fun EmptyContent(
    message: String,
    action: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Assignment,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(action)
            }
        }
    }
}

@Composable
private fun PlantillasList(
    plantillas: List<PlantillaChecklist>,
    onViewDetails: (PlantillaChecklist) -> Unit,
    onEdit: (PlantillaChecklist) -> Unit,
    onDelete: (PlantillaChecklist) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Checklists (${plantillas.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text("${plantillas.count { it.activa }} activos")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Activos",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = { },
                        label = {
                            Text("${plantillas.count { !it.activa }} inactivos")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Inactivos",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        items(plantillas) { plantilla ->
            PlantillaCard(
                plantilla = plantilla,
                onViewDetails = { onViewDetails(plantilla) },
                onEdit = { onEdit(plantilla) },
                onDelete = { onDelete(plantilla) }
            )
        }
    }
}

@Composable
private fun PlantillaCard(
    plantilla: PlantillaChecklist,
    onViewDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = plantilla.nombre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Para: ${plantilla.tipoActivo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ID: ${plantilla.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Botones de acción
                Row {
                    IconButton(onClick = onViewDetails) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Ver detalles",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlantillaDialog(
    title: String,
    plantilla: PlantillaChecklist?,
    tiposActivo: List<String>,
    onConfirm: (PlantillaChecklist) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var nombre by remember { mutableStateOf(plantilla?.nombre ?: "") }
    var tipoSeleccionado by remember { mutableStateOf(plantilla?.tipoActivo ?: tiposActivo.firstOrNull() ?: "") }
    var activa by remember { mutableStateOf(plantilla?.activa ?: true) }
    var expanded by remember { mutableStateOf(false) }

    val isValid = nombre.isNotBlank() && tipoSeleccionado.isNotBlank()

    AlertDialog(
        onDismissRequest = if (isLoading) { {} } else { onDismiss },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre del Checklist") },
                    placeholder = { Text("Ej: Inspección Diaria Pre-Turno") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                // Dropdown para tipo de activo
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = tipoSeleccionado,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de Activo") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        tiposActivo.forEach { tipo ->
                            DropdownMenuItem(
                                text = { Text(tipo) },
                                onClick = {
                                    tipoSeleccionado = tipo
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Switch para estado activo/inactivo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Estado del checklist:")
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activa) "Activo" else "Inactivo",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = activa,
                            onCheckedChange = { activa = it },
                            enabled = !isLoading
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nuevaPlantilla = plantilla?.copy(
                        nombre = nombre.trim(),
                        tipoActivo = tipoSeleccionado,
                        activa = activa
                    ) ?: PlantillaChecklist(
                        id = 0, // Se generará automáticamente
                        nombre = nombre.trim(),
                        tipoActivo = tipoSeleccionado,
                        activa = activa
                    )
                    // LOG debug
                    android.util.Log.d("PlantillaDialog", "Guardando plantilla: activa=$activa, nombre=${nuevaPlantilla.nombre}")

                    onConfirm(nuevaPlantilla)
                },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (plantilla == null) "Crear" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DeletePlantillaDialog(
    plantilla: PlantillaChecklist,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = if (isLoading) { {} } else { onDismiss },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Advertencia",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Eliminar Checklist") },
        text = {
            Column {
                Text("¿Estás seguro de que quieres eliminar este checklist?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• ${plantilla.nombre}\n• Para: ${plantilla.tipoActivo}\n• Estado: ${if (plantilla.activa) "Activo" else "Inactivo"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Esta acción eliminará también todas las categorías y preguntas asociadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar")
            }
        }
    )
}