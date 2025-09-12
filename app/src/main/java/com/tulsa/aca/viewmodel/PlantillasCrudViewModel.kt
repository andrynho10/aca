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

data class PlantillasCrudUiState(
    val plantillas: List<PlantillaChecklist> = emptyList(),
    val plantillaSeleccionada: PlantillaChecklist? = null,
    val plantillaDetallada: PlantillaChecklist? = null, // Con categorías y preguntas
    val tiposActivo: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showDetailView: Boolean = false
)

class PlantillasCrudViewModel : ViewModel() {
    private val plantillaRepository = PlantillaRepository()

    private val _uiState = MutableStateFlow(PlantillasCrudUiState())
    val uiState: StateFlow<PlantillasCrudUiState> = _uiState.asStateFlow()

    init {
        cargarDatosIniciales()
    }

    private fun cargarDatosIniciales() {
        viewModelScope.launch {
            cargarPlantillas()
            cargarTiposActivo()
        }
    }

    fun cargarPlantillas() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val plantillas = plantillaRepository.obtenerTodasLasPlantillas()
                _uiState.value = _uiState.value.copy(
                    plantillas = plantillas,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar plantillas: ${e.message}"
                )
            }
        }
    }

    private fun cargarTiposActivo() {
        viewModelScope.launch {
            try {
                val tipos = plantillaRepository.obtenerTiposDeActivo()
                _uiState.value = _uiState.value.copy(tiposActivo = tipos)
            } catch (e: Exception) {
                // Usar tipos por defecto si hay error
                _uiState.value = _uiState.value.copy(
                    tiposActivo = listOf("Montacargas", "Grúa Puente", "Carretilla Elevadora")
                )
            }
        }
    }

    fun buscarPlantillas(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (query.isBlank()) {
            cargarPlantillas()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Buscar por nombre y por tipo
                val plantillasPorNombre = plantillaRepository.buscarPlantillasPorNombre(query)
                val plantillasPorTipo = plantillaRepository.buscarPlantillasPorTipo(query)

                // Combinar resultados sin duplicados
                val plantillasEncontradas = (plantillasPorNombre + plantillasPorTipo)
                    .distinctBy { it.id }

                _uiState.value = _uiState.value.copy(
                    plantillas = plantillasEncontradas,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al buscar plantillas: ${e.message}"
                )
            }
        }
    }

    fun mostrarDialogoCrear() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            plantillaSeleccionada = null
        )
    }

    fun mostrarDialogoEditar(plantilla: PlantillaChecklist) {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            plantillaSeleccionada = plantilla
        )
    }

    fun mostrarDialogoEliminar(plantilla: PlantillaChecklist) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            plantillaSeleccionada = plantilla
        )
    }

    fun mostrarDetalles(plantilla: PlantillaChecklist) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val plantillaCompleta = plantillaRepository.obtenerPlantillaCompleta(plantilla.id)

                _uiState.value = _uiState.value.copy(
                    showDetailView = true,
                    plantillaDetallada = plantillaCompleta,
                    plantillaSeleccionada = plantilla,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar detalles: ${e.message}"
                )
            }
        }
    }

    fun cerrarDialogos() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            showEditDialog = false,
            showDeleteDialog = false,
            showDetailView = false,
            plantillaSeleccionada = null,
            plantillaDetallada = null
        )
    }

    fun crearPlantilla(plantilla: PlantillaChecklist) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val plantillaId = plantillaRepository.crearPlantilla(plantilla)
                if (plantillaId != null) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showCreateDialog = false
                    )
                    cargarPlantillas() // Recargar lista
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al crear la plantilla"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al crear plantilla: ${e.message}"
                )
            }
        }
    }

    fun actualizarPlantilla(plantilla: PlantillaChecklist) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                val success = plantillaRepository.actualizarPlantilla(plantilla)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showEditDialog = false
                    )
                    cargarPlantillas() // Recargar lista
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al actualizar la plantilla"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al actualizar plantilla: ${e.message}"
                )
            }
        }
    }

    fun eliminarPlantilla() {
        val plantilla = _uiState.value.plantillaSeleccionada ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)

            try {
                val success = plantillaRepository.eliminarPlantilla(plantilla.id)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        showDeleteDialog = false
                    )
                    cargarPlantillas() // Recargar lista
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        error = "Error al eliminar la plantilla"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = "Error al eliminar plantilla: ${e.message}"
                )
            }
        }
    }

    fun cambiarEstadoPlantilla(plantilla: PlantillaChecklist, activa: Boolean) {
        viewModelScope.launch {
            try {
                val plantillaActualizada = plantilla.copy(activa = activa)
                val success = plantillaRepository.actualizarPlantilla(plantillaActualizada)
                if (success) {
                    cargarPlantillas() // Recargar lista
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error al cambiar estado: ${e.message}"
                )
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}