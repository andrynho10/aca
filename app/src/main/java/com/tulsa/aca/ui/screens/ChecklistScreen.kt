package com.tulsa.aca.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.data.models.CategoriaPlantilla
import com.tulsa.aca.data.models.PreguntaPlantilla
import com.tulsa.aca.data.session.UserSession
import com.tulsa.aca.ui.components.PhotoCaptureComponent
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
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val todasRespondidas = remember(respuestas) {
        viewModel.todasLasPreguntasRespondidas()
    }

    // Cargar plantilla completa al inicio
    LaunchedEffect(templateId) {
        viewModel.cargarPlantillaCompleta(templateId)
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
                    )
                    {
                        // Mostrar error si existe
                        errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(
                            onClick = {
                                viewModel.guardarChecklist(
                                    assetId = assetId,
                                    userId = UserSession.getCurrentUser().id,
                                    templateId = templateId,
                                    context = context,
                                    onSuccess = onChecklistCompleted,
                                    onError = { error ->
                                        errorMessage = error
                                    }
                                )
                            },
                            enabled = todasRespondidas && !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completar"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isSaving) "Guardando..."
                                else "Completar Checklist"
                            )
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
                        onFotosChanged(pregunta.id, fotos) // Nueva función
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
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = pregunta.texto,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Botones Sí/No
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onRespuestaChanged(true) },
                modifier = Modifier.weight(1f),
                colors = if (respuesta?.respuesta == true) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Bueno")
            }

            OutlinedButton(
                onClick = { onRespuestaChanged(false) },
                modifier = Modifier.weight(1f),
                colors = if (respuesta?.respuesta == false) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                } else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Malo")
            }
        }

        // Campo de comentario (especialmente útil para respuestas negativas)
        if (respuesta?.respuesta == false) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = respuesta.comentario,
                onValueChange = onComentarioChanged,
                label = { Text("Comentario (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            PhotoCaptureComponent(
                photos = respuesta.fotos,
                onPhotosChanged = onFotosChanged
            )
        }
    }
}