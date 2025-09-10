package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.repository.ActivoRepository
import com.tulsa.aca.data.repository.PlantillaRepository
import com.tulsa.aca.data.repository.ReporteCompleto
import com.tulsa.aca.data.repository.ReporteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportDetailsUiState(
    val reporteCompleto: ReporteCompleto? = null,
    val activo: Activo? = null,
    val plantilla: PlantillaChecklist? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ReportDetailsViewModel : ViewModel() {
    private val reporteRepository = ReporteRepository()
    private val activoRepository = ActivoRepository()
    private val plantillaRepository = PlantillaRepository()

    private val _uiState = MutableStateFlow(ReportDetailsUiState())
    val uiState: StateFlow<ReportDetailsUiState> = _uiState.asStateFlow()

    fun cargarDetallesReporte(reporteId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Obtener reporte completo
                val reporteCompleto = reporteRepository.obtenerReporteCompleto(reporteId)

                if (reporteCompleto != null) {
                    // Obtener información del activo
                    val activo = activoRepository.obtenerActivoPorId(reporteCompleto.reporte.activoId)

                    // Obtener información de la plantilla
                    val plantilla = plantillaRepository.obtenerPlantillaCompleta(reporteCompleto.reporte.plantillaId)

                    _uiState.value = _uiState.value.copy(
                        reporteCompleto = reporteCompleto,
                        activo = activo,
                        plantilla = plantilla,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se encontró el reporte especificado"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar los detalles del reporte: ${e.message}"
                )
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}