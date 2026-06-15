package com.tulsa.aca.utils

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Gestor centralizado para caché thread-safe sin memory leaks
 * Utiliza WeakReferences para evitar mantener referencias fuertes
 */
object CacheManager {

    // THREAD-SAFE: CopyOnWriteArrayList + WeakReference
    private val cacheableInstances = CopyOnWriteArrayList<WeakReference<Cacheable>>()

    /**
     * Registra un componente cacheable
     * Se auto-limpia cuando el objeto es recolectado por GC
     */
    fun registrar(cacheable: Cacheable) {
        limpiarReferenciasNulas() // Limpiar antes de agregar
        cacheableInstances.add(WeakReference(cacheable))
        android.util.Log.d("CacheManager", "Componente registrado. Total: ${cacheableInstances.size}")
    }

    /**
     * Remueve un componente específico del registro
     */
    fun desregistrar(cacheable: Cacheable) {
        cacheableInstances.removeIf { ref ->
            val instance = ref.get()
            instance == null || instance == cacheable
        }
    }

    /**
     * Limpia todos los cachés de componentes activos
     */
    fun limpiarTodosLosCaches() {
        android.util.Log.d("CacheManager", "Iniciando limpieza completa de cachés...")

        limpiarReferenciasNulas()

        var cacheablesLimpiados = 0
        cacheableInstances.forEach { weakRef ->
            weakRef.get()?.let { cacheable ->
                try {
                    cacheable.limpiarCache()
                    cacheablesLimpiados++
                } catch (e: Exception) {
                    android.util.Log.e("CacheManager", "Error limpiando caché: ${e.message}")
                }
            }
        }

        android.util.Log.d("CacheManager", "$cacheablesLimpiados cachés limpiados")

        // Limpiar caché legacy si existe
        limpiarCachesLegacy()
    }

    /**
     * Obtiene información de todos los cachés registrados
     */
    fun obtenerInfoCaches(): String {
        limpiarReferenciasNulas()

        val info = StringBuilder()
        info.appendLine("=== INFORMACIÓN DE CACHÉS ===")
        info.appendLine("Componentes registrados: ${cacheableInstances.size}")

        cacheableInstances.forEachIndexed { index, weakRef ->
            weakRef.get()?.let { cacheable ->
                info.appendLine("[$index] ${cacheable.javaClass.simpleName}: ${cacheable.obtenerInfoCache()}")
            }
        }

        return info.toString()
    }

    /**
     * Limpia referencias nulas (WeakReferences donde el objeto fue recolectado)
     */
    private fun limpiarReferenciasNulas() {
        val antes = cacheableInstances.size
        cacheableInstances.removeIf { it.get() == null }
        val despues = cacheableInstances.size

        if (antes != despues) {
            android.util.Log.d("CacheManager", "${antes - despues} referencias nulas removidas")
        }
    }

    /**
     * Limpieza de emergencia
     */
    fun limpiezaDeEmergencia() {
        android.util.Log.w("CacheManager", "    LIMPIEZA DE EMERGENCIA")
        try {
            limpiarTodosLosCaches()
            cacheableInstances.clear()
            limpiarCachesLegacy()
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error crítico en limpieza de emergencia: ${e.message}", e)
        }
    }

    /**
     * Compatibilidad con sistema legacy
     */
    // Usa reflexión para no crear dependencias de compilación directas con ViewModels de módulos opcionales
    @Suppress("TRY_CATCH_ON_SEALED_CLASSES")
    private fun limpiarCachesLegacy() {
        try {
            // Importar dinámicamente para evitar dependencias rígidas
            val reportDetailsClass = Class.forName("com.tulsa.aca.viewmodel.ReportDetailsViewModel")
            val limpiarCacheMethod = reportDetailsClass.getDeclaredMethod("limpiarCache")
            limpiarCacheMethod.invoke(null)

            val supervisorClass = Class.forName("com.tulsa.aca.viewmodel.SupervisorViewModel")
            val limpiarCacheTodasMethod = supervisorClass.getDeclaredMethod("limpiarCacheTodasLasInstancias")
            limpiarCacheTodasMethod.invoke(null)

            android.util.Log.d("CacheManager", "Cachés legacy limpiados")
        } catch (e: Exception) {
            android.util.Log.d("CacheManager", "Cache legacy no encontrado o ya migrado")
        }
    }
}