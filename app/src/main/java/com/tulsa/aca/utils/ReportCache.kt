package com.tulsa.aca.utils

import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.repository.ReporteCompleto
import java.util.concurrent.ConcurrentHashMap

/**
 * - CACHÉ SINGLETON THREAD-SAFE PARA REPORTES
 * - Persiste entre navegaciones
 * - Thread-safe con ConcurrentHashMap
 * - TTL para expiración automática
 * - Se limpia solo en logout
 */
object ReportCache : Cacheable {

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(ttlMs: Long): Boolean =
            (System.currentTimeMillis() - timestamp) > ttlMs
    }

    // THREAD-SAFE MAPS
    private val reportesCache = ConcurrentHashMap<String, CacheEntry<ReporteCompleto>>()
    private val activosCache = ConcurrentHashMap<Int, CacheEntry<Activo>>()
    private val plantillasCache = ConcurrentHashMap<Int, CacheEntry<PlantillaChecklist>>()

    private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutos para reportes

    init {
        // REGISTRARSE EN EL CACHE MANAGER
        CacheManager.registrar(this)
    }

    // =================== REPORTES ===================

    fun getReporte(reporteId: String): ReporteCompleto? {
        val entry = reportesCache[reporteId]
        return if (entry != null && !entry.isExpired(CACHE_TTL_MS)) {
            android.util.Log.d("ReportCache", "Reporte $reporteId encontrado en caché")
            entry.data
        } else {
            if (entry != null) {
                android.util.Log.d("ReportCache", "Reporte $reporteId expirado, removiendo")
                reportesCache.remove(reporteId)
            }
            null
        }
    }

    fun putReporte(reporteId: String, reporte: ReporteCompleto) {
        reportesCache[reporteId] = CacheEntry(reporte)
        android.util.Log.d("ReportCache", "💾 Reporte $reporteId guardado en caché")
    }

    // =================== ACTIVOS ===================

    fun getActivo(activoId: Int): Activo? {
        val entry = activosCache[activoId]
        return if (entry != null && !entry.isExpired(CACHE_TTL_MS)) {
            entry.data
        } else {
            if (entry != null) activosCache.remove(activoId)
            null
        }
    }

    fun putActivo(activoId: Int, activo: Activo) {
        activosCache[activoId] = CacheEntry(activo)
    }

    // =================== PLANTILLAS ===================

    fun getPlantilla(plantillaId: Int): PlantillaChecklist? {
        val entry = plantillasCache[plantillaId]
        return if (entry != null && !entry.isExpired(CACHE_TTL_MS)) {
            entry.data
        } else {
            if (entry != null) plantillasCache.remove(plantillaId)
            null
        }
    }

    fun putPlantilla(plantillaId: Int, plantilla: PlantillaChecklist) {
        plantillasCache[plantillaId] = CacheEntry(plantilla)
    }

    // =================== CACHEABLE INTERFACE ===================

    override fun limpiarCache() {
        val reportesAntes = reportesCache.size
        val activosAntes = activosCache.size
        val plantillasAntes = plantillasCache.size

        reportesCache.clear()
        activosCache.clear()
        plantillasCache.clear()

        android.util.Log.d("ReportCache",
            "Caché limpiado: $reportesAntes reportes, $activosAntes activos, $plantillasAntes plantillas")
    }

    override fun obtenerInfoCache(): String {
        return "Reportes: ${reportesCache.size}, Activos: ${activosCache.size}, Plantillas: ${plantillasCache.size}"
    }

    override fun esCacheExpirado(): Boolean {
        return reportesCache.values.any { it.isExpired(CACHE_TTL_MS) } ||
                activosCache.values.any { it.isExpired(CACHE_TTL_MS) } ||
                plantillasCache.values.any { it.isExpired(CACHE_TTL_MS) }
    }

    // =================== LIMPIEZA AUTOMÁTICA ===================

    fun limpiarExpirados() {
        val reportesAntes = reportesCache.size
        val activosAntes = activosCache.size
        val plantillasAntes = plantillasCache.size

        reportesCache.entries.removeIf { it.value.isExpired(CACHE_TTL_MS) }
        activosCache.entries.removeIf { it.value.isExpired(CACHE_TTL_MS) }
        plantillasCache.entries.removeIf { it.value.isExpired(CACHE_TTL_MS) }

        val reportesDespues = reportesCache.size
        val activosDespues = activosCache.size
        val plantillasDespues = plantillasCache.size

        if (reportesAntes != reportesDespues || activosAntes != activosDespues || plantillasAntes != plantillasDespues) {
            android.util.Log.d("ReportCache",
                "Limpieza automática: " +
                        "${reportesAntes - reportesDespues} reportes, " +
                        "${activosAntes - activosDespues} activos, " +
                        "${plantillasAntes - plantillasDespues} plantillas expiradas removidas")
        }
    }
}