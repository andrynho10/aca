package com.tulsa.aca.data.repository

import android.content.Context
import com.tulsa.aca.data.local.AppDatabase
import com.tulsa.aca.data.local.entities.ActivoEntity
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repositorio offline-first para activos
 * - Prioriza datos del cache local
 * - Sincroniza con servidor cuando hay conexión
 * - Funciona completamente offline
 */
class OfflineActivoRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val activoDao = database.activoDao()
    private val networkMonitor = NetworkMonitor(context)
    private val remoteRepository = ActivoRepository()

    /**
     * Obtiene todos los activos
     * Estrategia: Primero del cache, luego intenta actualizar desde servidor
     */
    suspend fun obtenerTodosLosActivos(): List<Activo> {
        return try {
            // 1. Obtener del cache local
            val cachedActivos = activoDao.getAllActivos().map { it.toActivo() }

            // 2. Si hay conexión, intentar actualizar en background
            if (networkMonitor.isCurrentlyConnected()) {
                try {
                    val remoteActivos = remoteRepository.obtenerTodosLosActivos()
                    if (remoteActivos.isNotEmpty()) {
                        // Actualizar cache
                        val entities = remoteActivos.mapNotNull { activo ->
                            ActivoEntity.fromActivo(activo, System.currentTimeMillis())
                        }
                        activoDao.insertActivos(entities)
                        return remoteActivos
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OfflineActivoRepository", "Error actualizando desde servidor, usando cache: ${e.message}")
                }
            }

            // 3. Retornar cache si no se pudo actualizar o no hay conexión
            cachedActivos

        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error obteniendo activos: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Flow reactivo de todos los activos desde el cache local
     */
    fun observarActivos(): Flow<List<Activo>> {
        return activoDao.getAllActivosFlow().map { entities ->
            entities.map { it.toActivo() }
        }
    }

    /**
     * Obtiene un activo por ID
     */
    suspend fun obtenerActivoPorId(id: Int): Activo? {
        return try {
            // Intentar del cache primero
            val cached = activoDao.getActivoById(id)?.toActivo()

            // Si no está en cache y hay conexión, buscar en servidor
            if (cached == null && networkMonitor.isCurrentlyConnected()) {
                val remote = remoteRepository.obtenerActivoPorId(id)
                remote?.let { activo ->
                    // Guardar en cache
                    ActivoEntity.fromActivo(activo, System.currentTimeMillis())?.let {
                        activoDao.insertActivo(it)
                    }
                }
                return remote
            }

            cached
        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error obteniendo activo: ${e.message}", e)
            null
        }
    }

    /**
     * Busca un activo por código QR
     */
    suspend fun obtenerActivoPorQR(codigoQr: String): Activo? {
        return try {
            // Intentar del cache primero
            val cached = activoDao.getActivoByQR(codigoQr)?.toActivo()

            // Si no está en cache y hay conexión, buscar en servidor
            if (cached == null && networkMonitor.isCurrentlyConnected()) {
                val remote = remoteRepository.obtenerActivoPorQR(codigoQr)
                remote?.let { activo ->
                    // Guardar en cache
                    ActivoEntity.fromActivo(activo, System.currentTimeMillis())?.let {
                        activoDao.insertActivo(it)
                    }
                }
                return remote
            }

            cached
        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error buscando activo por QR: ${e.message}", e)
            null
        }
    }

    /**
     * Crea un nuevo activo
     * Si no hay conexión, se guarda en cola de sincronización
     */
    suspend fun crearActivo(activo: Activo): Boolean {
        return try {
            if (networkMonitor.isCurrentlyConnected()) {
                // Si hay conexión, crear directamente en servidor
                val success = remoteRepository.crearActivo(activo)
                if (success) {
                    // Actualizar cache local
                    activo.id?.let { id ->
                        val nuevoActivo = remoteRepository.obtenerActivoPorId(id)
                        nuevoActivo?.let {
                            ActivoEntity.fromActivo(it, System.currentTimeMillis())?.let { entity ->
                                activoDao.insertActivo(entity)
                            }
                        }
                    }
                }
                success
            } else {
                // TODO: Sin conexión, guardar en cola de sincronización
                android.util.Log.w("OfflineActivoRepository", "Sin conexión. La creación de activos offline aún no está implementada.")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error creando activo: ${e.message}", e)
            false
        }
    }

    /**
     * Actualiza un activo existente
     */
    suspend fun actualizarActivo(activo: Activo): Boolean {
        return try {
            if (networkMonitor.isCurrentlyConnected()) {
                // Si hay conexión, actualizar en servidor
                val success = remoteRepository.actualizarActivo(activo)
                if (success) {
                    // Actualizar cache local
                    ActivoEntity.fromActivo(activo, System.currentTimeMillis())?.let {
                        activoDao.updateActivo(it)
                    }
                }
                success
            } else {
                // TODO: Sin conexión, guardar en cola de sincronización
                android.util.Log.w("OfflineActivoRepository", "Sin conexión. La actualización de activos offline aún no está implementada.")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error actualizando activo: ${e.message}", e)
            false
        }
    }

    /**
     * Elimina un activo
     */
    suspend fun eliminarActivo(id: Int): Boolean {
        return try {
            if (networkMonitor.isCurrentlyConnected()) {
                val success = remoteRepository.eliminarActivo(id)
                if (success) {
                    // Eliminar del cache local
                    activoDao.deleteActivoById(id)
                }
                success
            } else {
                android.util.Log.w("OfflineActivoRepository", "Sin conexión. No se puede eliminar activos offline.")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error eliminando activo: ${e.message}", e)
            false
        }
    }

    /**
     * Verifica si un código QR es único
     */
    suspend fun verificarCodigoQRUnico(codigoQr: String, excludeId: Int? = null): Boolean {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.verificarCodigoQRUnico(codigoQr, excludeId)
        } else {
            // Si no hay conexión, verificar en cache local
            val activo = activoDao.getActivoByQR(codigoQr)
            activo == null || activo.id == excludeId
        }
    }

    /**
     * Busca activos por tipo
     */
    suspend fun buscarActivosPorTipo(tipo: String): List<Activo> {
        return try {
            val cached = activoDao.getActivosByTipo(tipo).map { it.toActivo() }

            if (networkMonitor.isCurrentlyConnected()) {
                try {
                    val remote = remoteRepository.buscarActivosPorTipo(tipo)
                    if (remote.isNotEmpty()) {
                        return remote
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OfflineActivoRepository", "Error buscando en servidor, usando cache")
                }
            }

            cached
        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error buscando activos por tipo: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Busca activos por nombre
     */
    suspend fun buscarActivosPorNombre(nombre: String): List<Activo> {
        return try {
            val cached = activoDao.getActivosByNombre(nombre).map { it.toActivo() }

            if (networkMonitor.isCurrentlyConnected()) {
                try {
                    val remote = remoteRepository.buscarActivosPorNombre(nombre)
                    if (remote.isNotEmpty()) {
                        return remote
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OfflineActivoRepository", "Error buscando en servidor, usando cache")
                }
            }

            cached
        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "Error buscando activos por nombre: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Observa el estado de conectividad en tiempo real
     */
    fun observarConectividad(): Flow<Boolean> {
        return networkMonitor.isConnected
    }

    /**
     * Fuerza la sincronización con el servidor
     */
    suspend fun sincronizarConServidor(): Boolean {
        return try {
            if (!networkMonitor.isCurrentlyConnected()) {
                return false
            }

            val remoteActivos = remoteRepository.obtenerTodosLosActivos()
            val timestamp = System.currentTimeMillis()

            val entities = remoteActivos.mapNotNull { activo ->
                ActivoEntity.fromActivo(activo, timestamp)
            }

            activoDao.insertActivos(entities)
            android.util.Log.d("OfflineActivoRepository", "✅ Sincronización exitosa: ${entities.size} activos")
            true

        } catch (e: Exception) {
            android.util.Log.e("OfflineActivoRepository", "❌ Error en sincronización: ${e.message}", e)
            false
        }
    }
}
