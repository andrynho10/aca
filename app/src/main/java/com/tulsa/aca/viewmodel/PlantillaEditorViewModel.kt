package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.CategoriaPlantilla
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.PreguntaPlantilla
import com.tulsa.aca.data.repository.PlantillaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlantillaEditorUiState(
    val plantilla: PlantillaChecklist? = null,
    val categorias: List<CategoriaPlantilla> = emptyList(),
    val categoriaSeleccionada: CategoriaPlantilla? = null,
    val preguntaSeleccionada: PreguntaPlantilla? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val showCreateCategoriaDialog: Boolean = false,
    val showEditCategoriaDialog: Boolean = false,
    val showDeleteCategoriaDialog: Boolean = false,
    val showCreatePreguntaDialog: Boolean = false,
    val showEditPreguntaDialog: Boolean = false,
    val showDeletePreguntaDialog: Boolean = false
)

class PlantillaEditorViewModel : ViewModel() {
    private val plantillaRepository = PlantillaRepository()

    private val _uiState = MutableStateFlow(PlantillaEditorUiState())
    val uiState: StateFlow<PlantillaEditorUiState> = _uiState.asStateFlow()

    fun cargarPlantilla(plantillaId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val plantillaCompleta = plantillaRepository.obtenerPlantillaCompleta(plantillaId)
                _uiState.value = _uiState.value.copy(
                    plantilla = plantillaCompleta,
                    categorias = plantillaCompleta?.categorias ?: emptyList(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar plantilla: ${e.message}"
                )
            }
        }
    }

    // ========================================
    // GESTIÓN DE CATEGORÍAS
    // ========================================

    fun mostrarDialogoCrearCategoria() {
        _uiState.value = _uiState.value.copy(
            showCreateCategoriaDialog = true,
            categoriaSeleccionada = null
        )
    }

    fun mostrarDialogoEditarCategoria(categoria: CategoriaPlantilla) {
        _uiState.value = _uiState.value.copy(
            showEditCategoriaDialog = true,
            categoriaSeleccionada = categoria
        )
    }

    fun mostrarDialogoEliminarCategoria(categoria: CategoriaPlantilla) {
        _uiState.value = _uiState.value.copy(
            showDeleteCategoriaDialog = true,
            categoriaSeleccionada = categoria
        )
    }

    fun crearCategoria(nombre: String) {
        val plantilla = _uiState.value.plantilla ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val nuevaCategoria = CategoriaPlantilla(
                    id = 0, // Se generará automáticamente
                    plantillaId = plantilla.id,
                    nombre = nombre.trim(),
                    orden = _uiState.value.categorias.size + 1
                )

                val categoriaId = plantillaRepository.crearCategoria(nuevaCategoria)
                if (categoriaId != null) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showCreateCategoriaDialog = false
                    )
                    cargarPlantilla(plantilla.id) // Recargar para mostrar cambios
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al crear la categoría"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al crear categoría: ${e.message}"
                )
            }
        }
    }

    fun actualizarCategoria(categoria: CategoriaPlantilla, nuevoNombre: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val categoriaActualizada = categoria.copy(nombre = nuevoNombre.trim())
                val success = plantillaRepository.actualizarCategoria(categoriaActualizada)

                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showEditCategoriaDialog = false
                    )
                    cargarPlantilla(_uiState.value.plantilla?.id ?: return@launch)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al actualizar la categoría"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al actualizar categoría: ${e.message}"
                )
            }
        }
    }

    fun eliminarCategoria() {
        val categoria = _uiState.value.categoriaSeleccionada ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)

            try {
                val success = plantillaRepository.eliminarCategoria(categoria.id)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        showDeleteCategoriaDialog = false
                    )
                    cargarPlantilla(_uiState.value.plantilla?.id ?: return@launch)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        error = "Error al eliminar la categoría"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = "Error al eliminar categoría: ${e.message}"
                )
            }
        }
    }

    // ========================================
    // GESTIÓN DE PREGUNTAS
    // ========================================

    fun mostrarDialogoCrearPregunta(categoria: CategoriaPlantilla) {
        _uiState.value = _uiState.value.copy(
            showCreatePreguntaDialog = true,
            categoriaSeleccionada = categoria,
            preguntaSeleccionada = null
        )
    }

    fun mostrarDialogoEditarPregunta(pregunta: PreguntaPlantilla) {
        // Encontrar la categoría de esta pregunta
        val categoria = _uiState.value.categorias.find { cat ->
            cat.preguntas.any { it.id == pregunta.id }
        }

        _uiState.value = _uiState.value.copy(
            showEditPreguntaDialog = true,
            categoriaSeleccionada = categoria,
            preguntaSeleccionada = pregunta
        )
    }

    fun mostrarDialogoEliminarPregunta(pregunta: PreguntaPlantilla) {
        _uiState.value = _uiState.value.copy(
            showDeletePreguntaDialog = true,
            preguntaSeleccionada = pregunta
        )
    }

    fun crearPregunta(texto: String, tipoRespuesta: String = "BUENO_MALO") {
        val categoria = _uiState.value.categoriaSeleccionada ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val nuevaPregunta = PreguntaPlantilla(
                    id = 0, // Se generará automáticamente
                    categoriaId = categoria.id,
                    texto = texto.trim(),
                    tipoRespuesta = tipoRespuesta,
                    orden = categoria.preguntas.size + 1
                )

                val preguntaId = plantillaRepository.crearPregunta(nuevaPregunta)
                if (preguntaId != null) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showCreatePreguntaDialog = false
                    )
                    cargarPlantilla(_uiState.value.plantilla?.id ?: return@launch)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al crear la pregunta"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al crear pregunta: ${e.message}"
                )
            }
        }
    }

    fun actualizarPregunta(pregunta: PreguntaPlantilla, nuevoTexto: String, nuevoTipo: String = "BUENO_MALO") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val preguntaActualizada = pregunta.copy(
                    texto = nuevoTexto.trim(),
                    tipoRespuesta = nuevoTipo
                )
                val success = plantillaRepository.actualizarPregunta(preguntaActualizada)

                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showEditPreguntaDialog = false
                    )
                    cargarPlantilla(_uiState.value.plantilla?.id ?: return@launch)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al actualizar la pregunta"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al actualizar pregunta: ${e.message}"
                )
            }
        }
    }

    fun eliminarPregunta() {
        val pregunta = _uiState.value.preguntaSeleccionada ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)

            try {
                val success = plantillaRepository.eliminarPregunta(pregunta.id)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        showDeletePreguntaDialog = false
                    )
                    cargarPlantilla(_uiState.value.plantilla?.id ?: return@launch)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        error = "Error al eliminar la pregunta"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = "Error al eliminar pregunta: ${e.message}"
                )
            }
        }
    }

    // ========================================
    // UTILIDADES
    // ========================================

    fun cerrarDialogos() {
        _uiState.value = _uiState.value.copy(
            showCreateCategoriaDialog = false,
            showEditCategoriaDialog = false,
            showDeleteCategoriaDialog = false,
            showCreatePreguntaDialog = false,
            showEditPreguntaDialog = false,
            showDeletePreguntaDialog = false,
            categoriaSeleccionada = null,
            preguntaSeleccionada = null
        )
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}