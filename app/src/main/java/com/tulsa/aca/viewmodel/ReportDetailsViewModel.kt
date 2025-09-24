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
import java.util.concurrent.ConcurrentHashMap

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

    // CACHÉ SIMPLE Y THREAD-SAFE - PERSISTE ENTRE NAVEGACIONES
    companion object {
        // THREAD-SAFE: ConcurrentHashMap en lugar de mutableMapOf
        private val cacheReportes = ConcurrentHashMap<String, ReporteCompleto>()
        private val cacheActivos = ConcurrentHashMap<Int, Activo>()
        private val cachePlantillas = ConcurrentHashMap<Int, PlantillaChecklist>()

        // COMPATIBILIDAD CON CACHEMANAGER
        fun limpiarCache() {
            val reportesAntes = cacheReportes.size
            val activosAntes = cacheActivos.size
            val plantillasAntes = cachePlantillas.size

            cacheReportes.clear()
            cacheActivos.clear()
            cachePlantillas.clear()

            android.util.Log.d("ReportDetailsVM",
                "Caché limpiado: $reportesAntes reportes, $activosAntes activos, $plantillasAntes plantillas")
        }

        fun obtenerTamanoCache(): String {
            return "Reportes: ${cacheReportes.size}, Activos: ${cacheActivos.size}, Plantillas: ${cachePlantillas.size}"
        }
    }

    // MÉTODO PRINCIPAL - SIMPLE Y DIRECTO
    fun cargarDetallesReporte(reporteId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // USAR CACHÉ SIMPLE
                val reporteCompleto = obtenerReporteDesdeCacheOCargar(reporteId)

                if (reporteCompleto != null) {
                    val activo = obtenerActivoDesdeCacheOCargar(reporteCompleto.reporte.activoId)
                    val plantilla = obtenerPlantillaDesdeCacheOCargar(reporteCompleto.reporte.plantillaId)

                    _uiState.value = _uiState.value.copy(
                        reporteCompleto = reporteCompleto,
                        activo = activo,
                        plantilla = plantilla,
                        isLoading = false
                    )

                    android.util.Log.d("ReportDetailsVM", "Reporte $reporteId cargado (desde ${if (cacheReportes.containsKey(reporteId)) "caché" else "servidor"})")
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
                    error = "Error cargando reporte: ${e.message}"
                )
            }
        }
    }

    // MÉTODOS AUXILIARES SIMPLES
    private suspend fun obtenerReporteDesdeCacheOCargar(reporteId: String): ReporteCompleto? {
        // 1. Intentar desde caché
        cacheReportes[reporteId]?.let {
            android.util.Log.d("ReportDetailsVM", "Reporte $reporteId desde caché")
            return it
        }

        // 2. Cargar desde servidor
        android.util.Log.d("ReportDetailsVM", "Cargando reporte $reporteId desde servidor")
        return reporteRepository.obtenerReporteCompleto(reporteId)?.also { reporte ->
            cacheReportes[reporteId] = reporte
        }
    }

    private suspend fun obtenerActivoDesdeCacheOCargar(activoId: Int): Activo? {
        cacheActivos[activoId]?.let { return it }
        return activoRepository.obtenerActivoPorId(activoId)?.also { activo ->
            cacheActivos[activoId] = activo
        }
    }

    private suspend fun obtenerPlantillaDesdeCacheOCargar(plantillaId: Int): PlantillaChecklist? {
        cachePlantillas[plantillaId]?.let { return it }
        return plantillaRepository.obtenerPlantillaCompleta(plantillaId)?.also { plantilla ->
            cachePlantillas[plantillaId] = plantilla
        }
    }
}