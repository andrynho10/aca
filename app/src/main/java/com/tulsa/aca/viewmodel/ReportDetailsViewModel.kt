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

    // CACHÉ PERSISTENTE DURANTE TODA LA SESIÓN DE LA APP
    companion object {
        private val cacheReportes = mutableMapOf<String, ReporteCompleto>()
        private val cacheActivos = mutableMapOf<Int, Activo>()
        private val cachePlantillas = mutableMapOf<Int, PlantillaChecklist>()

        // Función para limpiar caché si es necesario (ejemplo: al hacer logout)
        fun limpiarCache() {
            cacheReportes.clear()
            cacheActivos.clear()
            cachePlantillas.clear()
        }
    }

    fun cargarDetallesReporte(reporteId: String) {
        // Verificar si ya tenemos este reporte en caché
        val reporteCacheado = cacheReportes[reporteId]

        if (reporteCacheado != null) {
            // Verificar si también tenemos los datos relacionados en caché
            val activoCacheado = cacheActivos[reporteCacheado.reporte.activoId]
            val plantillaCacheada = cachePlantillas[reporteCacheado.reporte.plantillaId]

            if (activoCacheado != null && plantillaCacheada != null) {
                android.util.Log.d("ReportDetailsVM", "Usando reporte COMPLETO desde caché: $reporteId")

                _uiState.value = ReportDetailsUiState(
                    reporteCompleto = reporteCacheado,
                    activo = activoCacheado,
                    plantilla = plantillaCacheada,
                    isLoading = false,
                    error = null
                )
                return
            }
        }

        android.util.Log.d("ReportDetailsVM", "Cargando reporte desde servidor: $reporteId")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Obtener reporte completo (solo si no está en caché)
                val reporteCompleto = reporteCacheado ?: reporteRepository.obtenerReporteCompleto(reporteId)

                if (reporteCompleto != null) {
                    // Guardar reporte en caché si es nuevo
                    if (reporteCacheado == null) {
                        cacheReportes[reporteId] = reporteCompleto
                    }

                    // Obtener activo (desde caché si está disponible)
                    val activo = cacheActivos[reporteCompleto.reporte.activoId]
                        ?: activoRepository.obtenerActivoPorId(reporteCompleto.reporte.activoId)?.also {
                            cacheActivos[reporteCompleto.reporte.activoId] = it
                        }

                    // Obtener plantilla (desde caché si está disponible)
                    val plantilla = cachePlantillas[reporteCompleto.reporte.plantillaId]
                        ?: plantillaRepository.obtenerPlantillaCompleta(reporteCompleto.reporte.plantillaId)?.also {
                            cachePlantillas[reporteCompleto.reporte.plantillaId] = it
                        }

                    android.util.Log.d("ReportDetailsVM", "Reporte cargado y guardado en caché: $reporteId")
                    android.util.Log.d("ReportDetailsVM", "Caché actual: ${cacheReportes.size} reportes, ${cacheActivos.size} activos, ${cachePlantillas.size} plantillas")

                    _uiState.value = _uiState.value.copy(
                        reporteCompleto = reporteCompleto,
                        activo = activo,
                        plantilla = plantilla,
                        isLoading = false,
                        error = null
                    )

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

    // Método para forzar recarga (elimina del caché y vuelve a cargar)
    fun forzarRecarga(reporteId: String) {
        cacheReportes.remove(reporteId)
        cargarDetallesReporte(reporteId)
    }

    // Información de debugging del caché
    fun obtenerInfoCache(): String {
        return "Caché: ${cacheReportes.size} reportes, ${cacheActivos.size} activos, ${cachePlantillas.size} plantillas"
    }
}