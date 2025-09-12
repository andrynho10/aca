package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.viewmodel.ActivosCrudViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivosCrudScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivosCrudViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Gestión de Activos") },
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
                        contentDescription = "Agregar Activo"
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
                onQueryChange = { viewModel.buscarActivos(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de activos
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.activos.isEmpty() && uiState.searchQuery.isBlank() -> {
                    EmptyContent(
                        message = "No hay activos registrados",
                        action = "Agregar Primer Activo",
                        onAction = { viewModel.mostrarDialogoCrear() }
                    )
                }
                uiState.activos.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                    EmptyContent(
                        message = "No se encontraron activos con '${uiState.searchQuery}'",
                        action = "Limpiar búsqueda",
                        onAction = { viewModel.buscarActivos("") }
                    )
                }
                else -> {
                    ActivosList(
                        activos = uiState.activos,
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
            // Aquí podrías mostrar un SnackBar
            viewModel.limpiarError()
        }

        // Mostrar error en un AlertDialog temporal
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
        ActivoDialog(
            title = "Crear Activo",
            activo = null,
            onConfirm = { viewModel.crearActivo(it) },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    val activoSeleccionado = uiState.activoSeleccionado

    if (uiState.showEditDialog && activoSeleccionado != null) {
        ActivoDialog(
            title = "Editar Activo",
            activo = activoSeleccionado,
            onConfirm = { viewModel.actualizarActivo(it) },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    if (uiState.showDeleteDialog && activoSeleccionado != null) {
        DeleteConfirmDialog(
            activo = activoSeleccionado,
            onConfirm = { viewModel.eliminarActivo() },
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
            Text("Cargando activos...")
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
                imageVector = Icons.Default.Inventory2,
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
private fun ActivosList(
    activos: List<Activo>,
    onEdit: (Activo) -> Unit,
    onDelete: (Activo) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Activos (${activos.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(activos) { activo ->
            ActivoCard(
                activo = activo,
                onEdit = { onEdit(activo) },
                onDelete = { onDelete(activo) }
            )
        }
    }
}

@Composable
private fun ActivoCard(
    activo: Activo,
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
                    Text(
                        text = activo.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                        text = "QR: ${activo.codigoQr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary
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

@Composable
private fun ActivoDialog(
    title: String,
    activo: Activo?,
    onConfirm: (Activo) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var nombre by remember { mutableStateOf(activo?.nombre ?: "") }
    var modelo by remember { mutableStateOf(activo?.modelo ?: "") }
    var tipo by remember { mutableStateOf(activo?.tipo ?: "") }
    var codigoQr by remember { mutableStateOf(activo?.codigoQr ?: "") }

    val isValid = nombre.isNotBlank() &&
            modelo.isNotBlank() &&
            tipo.isNotBlank() &&
            codigoQr.isNotBlank()

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
                    label = { Text("Nombre del Activo") },
                    placeholder = { Text("Ej: Grúa Horquilla 01") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                OutlinedTextField(
                    value = tipo,
                    onValueChange = { tipo = it },
                    label = { Text("Tipo") },
                    placeholder = { Text("Ej: Grúa Horquilla") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )

                OutlinedTextField(
                    value = modelo,
                    onValueChange = { modelo = it },
                    label = { Text("Modelo") },
                    placeholder = { Text("Ej: Genérico") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = codigoQr,
                    onValueChange = { codigoQr = it },
                    label = { Text("Código QR") },
                    placeholder = { Text("Ej: TULSA-GH-01") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nuevoActivo = activo?.copy(
                        nombre = nombre.trim(),
                        modelo = modelo.trim(),
                        tipo = tipo.trim(),
                        codigoQr = codigoQr.trim()
                    ) ?: Activo(
                        nombre = nombre.trim(),
                        modelo = modelo.trim(),
                        tipo = tipo.trim(),
                        codigoQr = codigoQr.trim()
                    )
                    onConfirm(nuevoActivo)
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
                Text(if (activo == null) "Crear" else "Guardar")
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
private fun DeleteConfirmDialog(
    activo: Activo,
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
        title = { Text("Eliminar Activo") },
        text = {
            Column {
                Text("¿Estás seguro de que quieres eliminar este activo?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• ${activo.nombre}\n• Tipo: ${activo.tipo}\n• Modelo: ${activo.modelo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Esta acción no se puede deshacer.",
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