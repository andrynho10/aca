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

    // Caché con tiempo de expiración
    private var datosYaCargados = false
    private var ultimaCargaExitosa = false
    private var ultimaActualizacion = 0L
    private val CACHE_EXPIRY = 5 * 60 * 1000L // 5 minutos para plantillas

    init {
        cargarDatosIniciales()
    }

    private fun cargarDatosIniciales() {
        viewModelScope.launch {
            cargarPlantillas()
            cargarTiposActivo()
        }
    }

    fun cargarPlantillas(forzarRecarga: Boolean = false) {
        val ahora = System.currentTimeMillis()
        val cacheExpirado = (ahora - ultimaActualizacion) > CACHE_EXPIRY

        if (!forzarRecarga &&
            datosYaCargados &&
            ultimaCargaExitosa &&
            !cacheExpirado &&
            _uiState.value.searchQuery.isBlank() &&
            _uiState.value.plantillas.isNotEmpty() &&
            _uiState.value.error == null &&
            !_uiState.value.isLoading) {

            android.util.Log.d("PlantillasCrudVM", "Usando plantillas cacheadas (${(ahora - ultimaActualizacion)/1000}s)")
            return
        }

        android.util.Log.d("PlantillasCrudVM", "Cargando plantillas frescas")
        ultimaCargaExitosa = false

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val plantillas = plantillaRepository.obtenerTodasLasPlantillas()
                _uiState.value = _uiState.value.copy(
                    plantillas = plantillas,
                    isLoading = false
                )
                datosYaCargados = true
                ultimaCargaExitosa = true
                ultimaActualizacion = ahora
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
                _uiState.value = _uiState.value.copy(
                    tiposActivo = listOf("Grúa Horquilla")
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
                val plantillasPorNombre = plantillaRepository.buscarPlantillasPorNombre(query)
                val plantillasPorTipo = plantillaRepository.buscarPlantillasPorTipo(query)

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
                    invalidarCache()
                    cargarPlantillas()
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
                    invalidarCache()
                    cargarPlantillas()
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
                    invalidarCache()
                    cargarPlantillas()
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

    // Method para pull-to-refresh - QUITAR override
    fun forzarRecarga() {
        cargarPlantillas(forzarRecarga = true)
    }

    private fun invalidarCache() {
        datosYaCargados = false
        ultimaCargaExitosa = false
        ultimaActualizacion = 0L
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