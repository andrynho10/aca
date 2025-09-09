package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.HistorialUiState
import com.tulsa.aca.data.models.ReporteConUsuario
import com.tulsa.aca.data.repository.ActivoRepository
import com.tulsa.aca.data.repository.ReporteRepository
import com.tulsa.aca.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistorialViewModel : ViewModel() {
    private val activoRepository = ActivoRepository()
    private val reporteRepository = ReporteRepository()
    private val usuarioRepository = UsuarioRepository()

    private val _uiState = MutableStateFlow(HistorialUiState())
    val uiState: StateFlow<HistorialUiState> = _uiState.asStateFlow()

    fun cargarHistorialActivo(activoId: Int, tipoUsuario: String = "OPERARIO") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                tipoUsuario = tipoUsuario
            )

            try {
                // Cargar información del activo
                val activo = activoRepository.obtenerActivoPorId(activoId)

                if (activo != null) {
                    // Cargar historial de reportes
                    val reportes = reporteRepository.obtenerHistorialPorActivo(activoId)

                    // Cargar información de usuarios para cada reporte
                    val reportesConUsuario = reportes.map { reporte ->
                        val usuario = usuarioRepository.obtenerUsuarioPorId(reporte.usuarioId)
                        ReporteConUsuario(reporte, usuario)
                    }

                    _uiState.value = _uiState.value.copy(
                        activo = activo,
                        reportes = reportesConUsuario,
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