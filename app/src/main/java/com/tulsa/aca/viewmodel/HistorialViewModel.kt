package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.ReporteInspeccion
import com.tulsa.aca.data.repository.ActivoRepository
import com.tulsa.aca.data.repository.ReporteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistorialUiState(
    val activo: Activo? = null,
    val reportes: List<ReporteInspeccion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HistorialViewModel : ViewModel() {
    private val activoRepository = ActivoRepository()
    private val reporteRepository = ReporteRepository()

    private val _uiState = MutableStateFlow(HistorialUiState())
    val uiState: StateFlow<HistorialUiState> = _uiState.asStateFlow()

    fun cargarHistorialActivo(activoId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Cargar información del activo
                val activo = activoRepository.obtenerActivoPorId(activoId)

                if (activo != null) {
                    // Cargar historial de reportes
                    val reportes = reporteRepository.obtenerHistorialPorActivo(activoId)

                    _uiState.value = _uiState.value.copy(
                        activo = activo,
                        reportes = reportes,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se encontró el activo especificado"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar el historial: ${e.message}"
                )
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}