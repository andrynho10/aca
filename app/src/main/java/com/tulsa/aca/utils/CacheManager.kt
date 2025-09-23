package com.tulsa.aca.utils

import com.tulsa.aca.viewmodel.ReportDetailsViewModel
import com.tulsa.aca.viewmodel.SupervisorViewModel

/**
 * Gestor centralizado para limpiar todos los cachés de la aplicación
 * Importante para seguridad: evita que datos de un usuario sean visibles para otro
 */
object CacheManager {

    fun limpiarTodosLosCaches() {
        android.util.Log.d("CacheManager", "🧹 Iniciando limpieza completa de cachés...")

        try {
            // 1. Limpiar caché de ReportDetailsViewModel
            ReportDetailsViewModel.limpiarCache()
            android.util.Log.d("CacheManager", "Caché de ReportDetails limpiado")

            // 2. Limpiar caché de SupervisorViewModel
            SupervisorViewModel.limpiarCacheTodasLasInstancias()
            android.util.Log.d("CacheManager", "Caché temporal de Supervisor limpiado")

        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error limpiando cachés: ${e.message}", e)
            // Continuar de todas formas
        }
    }

    private fun limpiarCacheSupervisor() {
        try {
            // Si SupervisorViewModel tuviera función de limpiar caché, la llamaríamos aquí
            // SupervisorViewModel.limpiarCache()
            android.util.Log.d("CacheManager", "Caché de Supervisor limpiado")
        } catch (e: Exception) {
            android.util.Log.w("CacheManager", "Error limpiando caché de Supervisor: ${e.message}")
        }
    }

    /**
     * Función para obtener información de debugging sobre los cachés
     */
    fun obtenerInfoCaches(): String {
        return buildString {
            try {
                appendLine("=== INFORMACIÓN DE CACHÉS ===")

                // Info del caché de ReportDetails
                val infoReportDetails = ReportDetailsViewModel.obtenerTamanoCache()
                appendLine("ReportDetails: $infoReportDetails")

                // INFO DEL CACHÉ DE SUPERVISOR
                val infoSupervisor = SupervisorViewModel.obtenerInfoCache()
                appendLine("Supervisor: $infoSupervisor")

                appendLine("Estado: Cachés funcionando correctamente")
            } catch (e: Exception) {
                appendLine("Error obteniendo info de cachés: ${e.message}")
            }
        }
    }

    /**
     * Función para limpiar cachés de emergencia
     */
    fun limpiezaDeEmergencia() {
        android.util.Log.w("CacheManager", "LIMPIEZA DE EMERGENCIA - Limpiando todos los cachés")

        try {
            // Limpiar cachés normalmente
            limpiarTodosLosCaches()

            // Invalidar cachés temporales por si acaso
            SupervisorViewModel.invalidarCacheTemporal()

            android.util.Log.w("CacheManager", "Limpieza de emergencia completada")
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error crítico en limpieza de emergencia: ${e.message}", e)
        }
    }
}