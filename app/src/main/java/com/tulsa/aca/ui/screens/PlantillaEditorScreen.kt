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
import com.tulsa.aca.data.models.CategoriaPlantilla
import com.tulsa.aca.data.models.PreguntaPlantilla
import com.tulsa.aca.viewmodel.PlantillaEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantillaEditorScreen(
    plantillaId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlantillaEditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Cargar plantilla al inicializar
    LaunchedEffect(plantillaId) {
        viewModel.cargarPlantilla(plantillaId)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Editor: ${uiState.plantilla?.nombre ?: "Cargando..."}",
                    maxLines = 1
                )
            },
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
                    onClick = { viewModel.mostrarDialogoCrearCategoria() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar Categoría"
                    )
                }
            }
        )

        // Contenido principal
        when {
            uiState.isLoading -> {
                LoadingEditorContent()
            }
            else -> {
                val error = uiState.error
                if (error != null) {
                    ErrorEditorContent(
                        error = error,
                        onRetry = { viewModel.cargarPlantilla(plantillaId) }
                    )
                } else if (uiState.plantilla != null) {
                    EditorContent(
                        categorias = uiState.categorias,
                        onCreateCategoria = { viewModel.mostrarDialogoCrearCategoria() },
                        onEditCategoria = { viewModel.mostrarDialogoEditarCategoria(it) },
                        onDeleteCategoria = { viewModel.mostrarDialogoEliminarCategoria(it) },
                        onCreatePregunta = { viewModel.mostrarDialogoCrearPregunta(it) },
                        onEditPregunta = { viewModel.mostrarDialogoEditarPregunta(it) },
                        onDeletePregunta = { viewModel.mostrarDialogoEliminarPregunta(it) }
                    )
                }
            }
        }
    }

    // Manejo de errores
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

    // Diálogos de categorías
    if (uiState.showCreateCategoriaDialog) {
        CategoriaDialog(
            title = "Crear Categoría",
            categoria = null,
            onConfirm = { nombre: String -> viewModel.crearCategoria(nombre) },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    val categoriaSeleccionada = uiState.categoriaSeleccionada
    if (uiState.showEditCategoriaDialog && categoriaSeleccionada != null) {
        CategoriaDialog(
            title = "Editar Categoría",
            categoria = categoriaSeleccionada,
            onConfirm = { nombre: String ->
                viewModel.actualizarCategoria(categoriaSeleccionada, nombre)
            },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    if (uiState.showDeleteCategoriaDialog && categoriaSeleccionada != null) {
        DeleteCategoriaDialog(
            categoria = categoriaSeleccionada,
            onConfirm = { viewModel.eliminarCategoria() },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isDeleting
        )
    }

    // Diálogos de preguntas
    if (uiState.showCreatePreguntaDialog) {
        PreguntaDialog(
            title = "Crear Pregunta",
            pregunta = null,
            onConfirm = { texto: String, tipo: String -> // Especificar tipos
                viewModel.crearPregunta(texto, tipo)
            },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    val preguntaSeleccionada = uiState.preguntaSeleccionada
    if (uiState.showEditPreguntaDialog && preguntaSeleccionada != null) {
        PreguntaDialog(
            title = "Editar Pregunta",
            pregunta = preguntaSeleccionada,
            onConfirm = { texto: String, tipo: String -> // Especificar tipos
                viewModel.actualizarPregunta(preguntaSeleccionada, texto, tipo)
            },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isSaving
        )
    }

    if (uiState.showDeletePreguntaDialog && preguntaSeleccionada != null) {
        DeletePreguntaDialog(
            pregunta = preguntaSeleccionada,
            onConfirm = { viewModel.eliminarPregunta() },
            onDismiss = { viewModel.cerrarDialogos() },
            isLoading = uiState.isDeleting
        )
    }
}

@Composable
private fun LoadingEditorContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Cargando editor...")
        }
    }
}

@Composable
private fun ErrorEditorContent(
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
private fun EditorContent(
    categorias: List<CategoriaPlantilla>,
    onCreateCategoria: () -> Unit,
    onEditCategoria: (CategoriaPlantilla) -> Unit,
    onDeleteCategoria: (CategoriaPlantilla) -> Unit,
    onCreatePregunta: (CategoriaPlantilla) -> Unit,
    onEditPregunta: (PreguntaPlantilla) -> Unit,
    onDeletePregunta: (PreguntaPlantilla) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header con estadísticas
        item {
            EditorStatsCard(categorias = categorias)
        }

        // Instrucciones
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Información",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Instrucciones de Edición",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Organiza tu checklist en categorías lógicas\n• Cada categoría puede tener múltiples preguntas\n• Las preguntas se responden como BUENO/MALO\n• Usa el botón + para agregar categorías",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (categorias.isEmpty()) {
            // Estado vacío
            item {
                EmptyEditorContent(onCreateCategoria = onCreateCategoria)
            }
        } else {
            // Lista de categorías
            items(categorias) { categoria ->
                CategoriaEditorCard(
                    categoria = categoria,
                    onEditCategoria = { onEditCategoria(categoria) },
                    onDeleteCategoria = { onDeleteCategoria(categoria) },
                    onCreatePregunta = { onCreatePregunta(categoria) },
                    onEditPregunta = onEditPregunta,
                    onDeletePregunta = onDeletePregunta
                )
            }
        }
    }
}

@Composable
private fun EditorStatsCard(categorias: List<CategoriaPlantilla>) {
    val totalCategorias = categorias.size
    val totalPreguntas = categorias.sumOf { it.preguntas.size }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Folder,
                value = totalCategorias.toString(),
                label = "Categorías",
                color = MaterialTheme.colorScheme.primary
            )

            StatItem(
                icon = Icons.Default.Quiz,
                value = totalPreguntas.toString(),
                label = "Preguntas",
                color = MaterialTheme.colorScheme.secondary
            )

            StatItem(
                icon = Icons.Default.CheckCircle,
                value = if (totalCategorias > 0) "Listo" else "Vacío",
                label = "Estado",
                color = if (totalCategorias > 0)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun EmptyEditorContent(onCreateCategoria: () -> Unit) {
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
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = "Sin categorías",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Checklist Vacío",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Comienza agregando la primera categoría para organizar tus preguntas de inspección",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCreateCategoria,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear Primera Categoría")
            }
        }
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ========================================
// COMPONENTE CATEGORÍA EDITOR
// ========================================

@Composable
private fun CategoriaEditorCard(
    categoria: CategoriaPlantilla,
    onEditCategoria: () -> Unit,
    onDeleteCategoria: () -> Unit,
    onCreatePregunta: () -> Unit,
    onEditPregunta: (PreguntaPlantilla) -> Unit,
    onDeletePregunta: (PreguntaPlantilla) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header de categoría
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Categoría",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = categoria.nombre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Orden: ${categoria.orden} • ${categoria.preguntas.size} preguntas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEditCategoria) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar categoría",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = onDeleteCategoria) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar categoría",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón para agregar pregunta
            OutlinedButton(
                onClick = onCreatePregunta,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agregar Pregunta")
            }

            // Lista de preguntas
            if (categoria.preguntas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                categoria.preguntas.forEachIndexed { index, pregunta ->
                    PreguntaEditorItem(
                        pregunta = pregunta,
                        numero = index + 1,
                        onEdit = { onEditPregunta(pregunta) },
                        onDelete = { onDeletePregunta(pregunta) }
                    )

                    if (index < categoria.preguntas.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreguntaEditorItem(
    pregunta: PreguntaPlantilla,
    numero: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Número de pregunta
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = numero.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Contenido de pregunta
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = pregunta.texto,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = pregunta.tipoRespuesta,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Orden: ${pregunta.orden}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Botones de acción
            Column {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar pregunta",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar pregunta",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ========================================
// DIÁLOGOS
// ========================================

@Composable
private fun CategoriaDialog(
    title: String,
    categoria: CategoriaPlantilla?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var nombre by remember { mutableStateOf(categoria?.nombre ?: "") }
    val isValid = nombre.isNotBlank()

    AlertDialog(
        onDismissRequest = if (isLoading) { {} } else { onDismiss },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre de la Categoría") },
                    placeholder = { Text("Ej: Inspección Visual, Funcionamiento") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Agrupa preguntas relacionadas bajo esta categoría para mejor organización.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nombre) },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (categoria == null) "Crear" else "Guardar")
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
private fun PreguntaDialog(
    title: String,
    pregunta: PreguntaPlantilla?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var texto by remember { mutableStateOf(pregunta?.texto ?: "") }
    var tipoRespuesta by remember { mutableStateOf(pregunta?.tipoRespuesta ?: "BUENO_MALO") }
    val isValid = texto.isNotBlank()

    AlertDialog(
        onDismissRequest = if (isLoading) { {} } else { onDismiss },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Texto de la Pregunta") },
                    placeholder = { Text("Ej: ¿El estado de las horquillas es bueno?") },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    maxLines = 3
                )

                // Selector de tipo de respuesta (por ahora solo BUENO_MALO)
                OutlinedTextField(
                    value = "BUENO / MALO",
                    onValueChange = { },
                    label = { Text("Tipo de Respuesta") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloqueado"
                        )
                    }
                )

                Text(
                    text = "Las preguntas se responden como BUENO (conforme) o MALO (requiere atención).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(texto, tipoRespuesta) },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (pregunta == null) "Crear" else "Guardar")
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
private fun DeleteCategoriaDialog(
    categoria: CategoriaPlantilla,
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
        title = { Text("Eliminar Categoría") },
        text = {
            Column {
                Text("¿Estás seguro de que quieres eliminar esta categoría?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• ${categoria.nombre}\n• ${categoria.preguntas.size} preguntas incluidas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Esta acción eliminará la categoría y todas sus preguntas. No se puede deshacer.",
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

@Composable
private fun DeletePreguntaDialog(
    pregunta: PreguntaPlantilla,
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
        title = { Text("Eliminar Pregunta") },
        text = {
            Column {
                Text("¿Estás seguro de que quieres eliminar esta pregunta?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"${pregunta.texto}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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