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

    // Caché para evitar recargas innecesarias
    private var lastLoadedReportId: String? = null
    private var dataLoadedSuccessfully: Boolean = false

    fun cargarDetallesReporte(reporteId: String) {
        // Si ya cargamos este reporte exitosamente, no volver a cargarlo
        if (lastLoadedReportId == reporteId &&
            dataLoadedSuccessfully &&
            _uiState.value.reporteCompleto != null &&
            _uiState.value.error == null &&
            !_uiState.value.isLoading) {

            android.util.Log.d("ReportDetailsVM", "Usando datos cacheados para reporte: $reporteId")
            return
        }

        android.util.Log.d("ReportDetailsVM", "Cargando datos frescos para reporte: $reporteId")
        lastLoadedReportId = reporteId
        dataLoadedSuccessfully = false

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Obtener reporte completo
                val reporteCompleto = reporteRepository.obtenerReporteCompleto(reporteId)

                android.util.Log.d("ReportDetailsVM", "Reporte cargado: ${reporteCompleto != null}")
                android.util.Log.d("ReportDetailsVM", "Número de respuestas: ${reporteCompleto?.respuestas?.size ?: 0}")
                android.util.Log.d("ReportDetailsVM", "Número de fotos totales: ${reporteCompleto?.fotos?.values?.sumOf { it.size } ?: 0}")

                reporteCompleto?.fotos?.forEach { (respuestaId, fotos) ->
                    android.util.Log.d("ReportDetailsVM", "Respuesta $respuestaId tiene ${fotos.size} fotos: $fotos")
                }

                if (reporteCompleto != null) {
                    // Obtener información del activo
                    val activo = activoRepository.obtenerActivoPorId(reporteCompleto.reporte.activoId)

                    // Obtener información de la plantilla
                    val plantilla = plantillaRepository.obtenerPlantillaCompleta(reporteCompleto.reporte.plantillaId)

                    _uiState.value = _uiState.value.copy(
                        reporteCompleto = reporteCompleto,
                        activo = activo,
                        plantilla = plantilla,
                        isLoading = false,
                        error = null
                    )

                    dataLoadedSuccessfully = true
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo cargar el reporte"
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("ReportDetailsVM", "Error cargando reporte: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar el reporte: ${e.message}"
                )
            }
        }
    }

    // Method para forzar recarga si es necesario
    fun forzarRecarga(reporteId: String) {
        lastLoadedReportId = null
        dataLoadedSuccessfully = false
        cargarDetallesReporte(reporteId)
    }
}