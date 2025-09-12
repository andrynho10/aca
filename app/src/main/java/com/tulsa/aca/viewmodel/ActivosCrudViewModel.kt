package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.repository.ActivoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivosCrudUiState(
    val activos: List<Activo> = emptyList(),
    val activoSeleccionado: Activo? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false
)

class ActivosCrudViewModel : ViewModel() {
    private val activoRepository = ActivoRepository()

    private val _uiState = MutableStateFlow(ActivosCrudUiState())
    val uiState: StateFlow<ActivosCrudUiState> = _uiState.asStateFlow()

    init {
        cargarActivos()
    }

    fun cargarActivos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val activos = activoRepository.obtenerTodosLosActivos()
                _uiState.value = _uiState.value.copy(
                    activos = activos,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar activos: ${e.message}"
                )
            }
        }
    }

    fun buscarActivos(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (query.isBlank()) {
            cargarActivos()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Buscar por nombre y por tipo
                val activosPorNombre = activoRepository.buscarActivosPorNombre(query)
                val activosPorTipo = activoRepository.buscarActivosPorTipo(query)

                // Combinar resultados sin duplicados
                val activosEncontrados = (activosPorNombre + activosPorTipo)
                    .distinctBy { it.id }

                _uiState.value = _uiState.value.copy(
                    activos = activosEncontrados,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al buscar activos: ${e.message}"
                )
            }
        }
    }

    fun mostrarDialogoCrear() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            activoSeleccionado = null
        )
    }

    fun mostrarDialogoEditar(activo: Activo) {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            activoSeleccionado = activo
        )
    }

    fun mostrarDialogoEliminar(activo: Activo) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            activoSeleccionado = activo
        )
    }

    fun cerrarDialogos() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            showEditDialog = false,
            showDeleteDialog = false,
            activoSeleccionado = null
        )
    }

    fun crearActivo(activo: Activo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                // Verificar que el código QR sea único
                val esUnico = activoRepository.verificarCodigoQRUnico(activo.codigoQr)
                if (!esUnico) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Ya existe un activo con el código QR: ${activo.codigoQr}"
                    )
                    return@launch
                }

                val success = activoRepository.crearActivo(activo)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showCreateDialog = false
                    )
                    cargarActivos() // Recargar lista
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al crear el activo"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al crear activo: ${e.message}"
                )
            }
        }
    }

    fun actualizarActivo(activo: Activo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            try {
                // Verificar que el código QR sea único (excluyendo el activo actual)
                val esUnico = activoRepository.verificarCodigoQRUnico(activo.codigoQr, activo.id)
                if (!esUnico) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Ya existe otro activo con el código QR: ${activo.codigoQr}"
                    )
                    return@launch
                }

                val success = activoRepository.actualizarActivo(activo)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        showEditDialog = false
                    )
                    cargarActivos() // Recargar lista
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Error al actualizar el activo"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al actualizar activo: ${e.message}"
                )
            }
        }
    }

    fun eliminarActivo() {
        val activo = _uiState.value.activoSeleccionado ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)

            try {
                val success = activoRepository.eliminarActivo(activo.id ?: return@launch)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        showDeleteDialog = false
                    )
                    cargarActivos() // Recargar lista
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        error = "Error al eliminar el activo"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = "Error al eliminar activo: ${e.message}"
                )
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}