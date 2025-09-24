package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.repository.ActivoRepository
import com.tulsa.aca.data.repository.PlantillaRepository
import com.tulsa.aca.data.repository.ReporteCompleto
import com.tulsa.aca.data.repository.ReporteRepository
import com.tulsa.aca.utils.CacheManager
import com.tulsa.aca.utils.Cacheable
import com.tulsa.aca.utils.ReportCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ReportDetailsUiState(
    val reporteCompleto: ReporteCompleto? = null,
    val activo: Activo? = null,
    val plantilla: PlantillaChecklist? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

// CACHE ENTRY CON TTL
private data class CacheEntry<T>(
    val data: T,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMs: Long): Boolean =
        (System.currentTimeMillis() - timestamp) > ttlMs
}

class ReportDetailsViewModel : ViewModel(), Cacheable {

    private val reporteRepository = ReporteRepository()
    private val activoRepository = ActivoRepository()
    private val plantillaRepository = PlantillaRepository()

    private val _uiState = MutableStateFlow(ReportDetailsUiState())
    val uiState: StateFlow<ReportDetailsUiState> = _uiState.asStateFlow()

    companion object {
        // MÉTODOS LEGACY PARA COMPATIBILIDAD
        @Deprecated("Usar ReportCache directamente")
        fun limpiarCache() {
            ReportCache.limpiarCache()
        }

        @Deprecated("Usar ReportCache.obtenerInfoCache()")
        fun obtenerTamanoCache(): String {
            return ReportCache.obtenerInfoCache()
        }
    }

    init {
        CacheManager.registrar(this)
        android.util.Log.d("ReportDetailsVM", "ReportDetailsViewModel registrado")
    }

    // IMPLEMENTACIÓN CACHEABLE (DELEGA A REPORTCACHE)
    override fun limpiarCache() {
        ReportCache.limpiarCache()
        _uiState.value = ReportDetailsUiState()
    }

    override fun obtenerInfoCache(): String {
        return ReportCache.obtenerInfoCache()
    }

    override fun esCacheExpirado(): Boolean {
        return ReportCache.esCacheExpirado()
    }

    override fun onCleared() {
        super.onCleared()
        CacheManager.desregistrar(this)
        // NO limpiar ReportCache aquí - es persistente
        android.util.Log.d("ReportDetailsVM", "ReportDetailsViewModel destruido")
    }

    // MÉTODO PRINCIPAL CON CACHÉ SINGLETON
    fun cargarDetallesReporte(reporteId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // USAR CACHÉ SINGLETON PERSISTENTE
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
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo cargar el reporte"
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error cargando reporte: ${e.message}"
                )
            }
        }
    }

    // MÉTODOS QUE USAN EL CACHÉ SINGLETON
    private suspend fun obtenerReporteDesdeCacheOCargar(reporteId: String): ReporteCompleto? {
        // 1. Intentar desde caché
        ReportCache.getReporte(reporteId)?.let { return it }

        // 2. Cargar desde servidor
        android.util.Log.d("ReportDetailsVM", "Cargando reporte $reporteId desde servidor")
        return reporteRepository.obtenerReporteCompleto(reporteId)?.also { reporte ->
            ReportCache.putReporte(reporteId, reporte)
        }
    }

    private suspend fun obtenerActivoDesdeCacheOCargar(activoId: Int): Activo? {
        ReportCache.getActivo(activoId)?.let { return it }

        return activoRepository.obtenerActivoPorId(activoId)?.also { activo ->
            ReportCache.putActivo(activoId, activo)
        }
    }

    private suspend fun obtenerPlantillaDesdeCacheOCargar(plantillaId: Int): PlantillaChecklist? {
        ReportCache.getPlantilla(plantillaId)?.let { return it }

        return plantillaRepository.obtenerPlantillaCompleta(plantillaId)?.also { plantilla ->
            ReportCache.putPlantilla(plantillaId, plantilla)
        }
    }
}