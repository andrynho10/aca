package com.tulsa.aca.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.data.models.CategoriaPlantilla
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.PreguntaPlantilla
import com.tulsa.aca.data.session.UserSession
import com.tulsa.aca.ui.components.PhotoCaptureComponent
import com.tulsa.aca.utils.toDisplayText
import com.tulsa.aca.utils.toStatusColor
import com.tulsa.aca.viewmodel.ChecklistViewModel
import com.tulsa.aca.viewmodel.RespuestaChecklistItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    assetId: Int,
    templateId: Int,
    onNavigateBack: () -> Unit,
    onChecklistCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChecklistViewModel = viewModel()
) {
    val context = LocalContext.current
    val plantillaCompleta by viewModel.plantillaCompleta.collectAsState()
    val respuestas by viewModel.respuestas.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showHorometroDialog by remember { mutableStateOf(false) }
    var horometroInput by remember { mutableStateOf("") }

    val todasRespondidas = remember(respuestas) {
        viewModel.todasLasPreguntasRespondidas()
    }
    // MANEJAR ESTADO DE ÉXITO
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            showSuccessDialog = true
        }
    }

    // MANEJAR ERRORES
    LaunchedEffect(saveError) {
        saveError?.let { error ->
            errorMessage = error
        }
    }

    LaunchedEffect(Unit) {
        android.util.Log.d("ChecklistScreen", UserSession.debugCurrentUserStatus())
    }
    // Cargar plantilla completa al inicio
    LaunchedEffect(templateId) {
        viewModel.cargarPlantillaCompleta(templateId)
    }

    // FUNCIÓN PARA EJECUTAR EL GUARDADO
    val ejecutarGuardado = {
        try {
            val currentUser = UserSession.requireCurrentUser()
            android.util.Log.d("ChecklistScreen", "Guardando checklist para usuario: ${currentUser.nombreCompleto} (${currentUser.id})")

            errorMessage = null

            viewModel.guardarChecklist(
                assetId = assetId,
                userId = currentUser.id,
                templateId = templateId,
                context = context,
                onSuccess = { /* Se maneja con LaunchedEffect */ },
                onError = { error ->
                    errorMessage = error
                }
            )
        } catch (e: IllegalStateException) {
            android.util.Log.e("ChecklistScreen", "ERROR: ${e.message}")
            errorMessage = "Error: Usuario no autenticado. Por favor, vuelve a hacer login."
        }
    }

    // Dialog para capturar horómetro
    if (showHorometroDialog) {
        AlertDialog(
            onDismissRequest = { showHorometroDialog = false },
            title = {
                Text("Horómetro Inicial")
            },
            text = {
                Column {
                    Text(
                        "¿Deseas registrar el horómetro inicial de la grúa?",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = horometroInput,
                        onValueChange = {
                            if (it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                horometroInput = it
                            }
                        },
                        label = { Text("Horómetro Inicial (opcional)") },
                        placeholder = { Text("Ej: 1234.5") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text(
                                "Si ingresas el horómetro, deberás cerrarlo después de la inspección.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "El turno se detectará automáticamente según la hora actual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Guardar solo el horómetro (turno se calcula en backend)
                        val horometro = horometroInput.toFloatOrNull()
                        viewModel.actualizarHorometroInicial(horometro)
                        viewModel.actualizarTurno(null) // ⭐ NULL - se calcula en SQL

                        showHorometroDialog = false
                        showConfirmDialog = true
                    }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Saltar el horómetro y continuar sin él
                        viewModel.actualizarHorometroInicial(null)
                        viewModel.actualizarTurno(null)
                        showHorometroDialog = false
                        showConfirmDialog = true
                    }
                ) {
                    Text("Omitir")
                }
            }
        )
    }

    // DIALOG DE CONFIRMACIÓN
    if (showConfirmDialog) {
        ConfirmacionEnvioDialog(
            plantilla = plantillaCompleta,
            respuestas = respuestas,
            onConfirmar = {
                showConfirmDialog = false
                ejecutarGuardado()
            },
            onCancelar = {
                showConfirmDialog = false
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Éxito",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("¡Checklist Completado!")
                }
            },
            text = {
                Column {
                    Text(
                        text = "La inspección ha sido guardada exitosamente.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Todas las respuestas han sido registradas\n" +
                                "• Las fotos han sido subidas correctamente\n" +
                                "• El reporte está disponible para supervisores",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.clearSaveStates()
                        onChecklistCompleted()
                    }
                ) {
                    Text("Continuar")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = plantillaCompleta?.nombre ?: "Checklist",
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
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            plantillaCompleta?.let { plantilla ->
                Column {
                    // Contenido del checklist
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        plantilla.categorias.forEach { categoria ->
                            items(categoria.preguntas) { pregunta ->
                                QuestionItem(
                                    pregunta = pregunta,
                                    respuesta = respuestas[pregunta.id],
                                    onRespuestaChanged = { respuesta ->
                                        viewModel.actualizarRespuesta(pregunta.id, respuesta)
                                    },
                                    onComentarioChanged = { comentario ->
                                        viewModel.actualizarComentario(pregunta.id, comentario)
                                    },
                                    onFotosChanged = { fotos ->
                                        viewModel.actualizarFotos(pregunta.id, fotos)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Espaciado extra para el botón fijo
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }

                    // Botón fijo para completar checklist
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Indicador de progreso
                            if (!todasRespondidas) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Información",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val totalPreguntas = plantilla.categorias.sumOf { it.preguntas.size }
                                    val respondidas = respuestas.values.count { it.respuesta != null }
                                    Text(
                                        text = "Progreso: $respondidas de $totalPreguntas preguntas completadas",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                        // Mostrar error si existe
                        errorMessage?.let { error ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                            Button(
                                onClick = {
                                    if (todasRespondidas && !isSaving) {
                                        showHorometroDialog = true
                                    }
                                },
                                enabled = todasRespondidas && !isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (todasRespondidas)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                when {
                                    isSaving -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Guardando inspección...")
                                    }
                                    !todasRespondidas -> {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Incompleto",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Completar todas las preguntas")
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Completar",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Revisar y Enviar Checklist")
                                    }
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
private fun CategorySection(
    categoria: CategoriaPlantilla,
    respuestas: Map<Int, RespuestaChecklistItem>,
    onRespuestaChanged: (Int, Boolean) -> Unit,
    onComentarioChanged: (Int, String) -> Unit,
    onFotosChanged: (Int, List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = categoria.nombre,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            categoria.preguntas.forEach { pregunta ->
                QuestionItem(
                    pregunta = pregunta,
                    respuesta = respuestas[pregunta.id],
                    onRespuestaChanged = { respuesta ->
                        onRespuestaChanged(pregunta.id, respuesta)
                    },
                    onComentarioChanged = { comentario ->
                        onComentarioChanged(pregunta.id, comentario)
                    },
                    onFotosChanged = { fotos ->
                        onFotosChanged(pregunta.id, fotos)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuestionItem(
    pregunta: PreguntaPlantilla,
    respuesta: RespuestaChecklistItem?,
    onRespuestaChanged: (Boolean) -> Unit,
    onComentarioChanged: (String) -> Unit,
    onFotosChanged: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    // CONTROLADORES PARA EL TECLADO
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = pregunta.texto,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Botones BUENO/MALO
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onRespuestaChanged(true) },
                modifier = Modifier.weight(1f),
                colors = if (respuesta?.respuesta == true) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            ) {
                Text(
                    text = "BUENO",
                    color = if (respuesta?.respuesta == true)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { onRespuestaChanged(false) },
                modifier = Modifier.weight(1f),
                colors = if (respuesta?.respuesta == false) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            ) {
                Text(
                    text = "MALO",
                    color = if (respuesta?.respuesta == false)
                        MaterialTheme.colorScheme.onError
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // CAMPO DE COMENTARIO CON BOTÓN LISTO INTEGRADO
        if (respuesta?.respuesta == false) {
            Spacer(modifier = Modifier.height(8.dp))

            // BOX PARA SUPERPONER EL BOTÓN SOBRE EL TEXTFIELD
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = respuesta.comentario,
                    onValueChange = onComentarioChanged,
                    label = { Text("Comentario (requerido para estado Malo)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 48.dp),
                    maxLines = 4,
                    minLines = 2,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Default
                    )
                )

                // BOTÓN "LISTO"
                if (respuesta.comentario.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Listo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            PhotoCaptureComponent(
                photos = respuesta.fotos,
                onPhotosChanged = onFotosChanged
            )
        }
    }
}

@Composable
private fun ConfirmacionEnvioDialog(
    plantilla: PlantillaChecklist?,
    respuestas: Map<Int, RespuestaChecklistItem>,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    // Calcular estadísticas del checklist
    val totalPreguntas = plantilla?.categorias?.sumOf { it.preguntas.size } ?: 0
    val respuestasRespondidas = respuestas.values.count { it.respuesta != null }
    val respuestasBuenas = respuestas.values.count { it.respuesta == true }
    val respuestasMalas = respuestas.values.count { it.respuesta == false }
    val conComentarios = respuestas.values.count { it.respuesta == false && it.comentario.isNotBlank() }
    val conFotos = respuestas.values.count { it.fotos.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Revisar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Revisar Checklist",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = "Por favor, revisa tu inspección antes de enviarla:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Resumen general
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Resumen de la Inspección",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total preguntas:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$respuestasRespondidas/$totalPreguntas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Estado BUENO:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$respuestasBuenas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Estado MALO:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$respuestasMalas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            if (conComentarios > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Con comentarios:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$conComentarios",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            if (conFotos > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Con fotos:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$conFotos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Alertas si hay problemas
                if (respuestasMalas > 0) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Advertencia",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Esta inspección contiene $respuestasMalas item(s) Malo(s) detectado(s)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Mensaje de confirmación
                item {
                    Text(
                        text = "Al confirmar, esta inspección será:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Enviada al sistema de reportes\n" +
                                "• Visible para los supervisores\n" +
                                "• Guardada permanentemente\n" +
                                "• No podrá ser modificada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "¿Estás seguro de que quieres enviar esta inspección?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sí, Enviar Checklist")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Volver a Revisar")
            }
        }
    )
}