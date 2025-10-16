package com.tulsa.aca.data.repository

import android.content.Context
import com.tulsa.aca.data.local.AppDatabase
import com.tulsa.aca.data.local.entities.CategoriaEntity
import com.tulsa.aca.data.local.entities.PlantillaEntity
import com.tulsa.aca.data.local.entities.PreguntaEntity
import com.tulsa.aca.data.models.CategoriaPlantilla
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.PreguntaPlantilla
import com.tulsa.aca.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio offline-first para plantillas de checklist
 * - Prioriza datos del cache local
 * - Sincroniza con servidor cuando hay conexión
 * - Funciona completamente offline para lectura
 */
class OfflinePlantillaRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val plantillaDao = database.plantillaDao()
    private val networkMonitor = NetworkMonitor(context)
    private val remoteRepository = PlantillaRepository()

    /**
     * Obtiene plantillas por tipo de activo
     */
    suspend fun obtenerPlantillasPorTipoActivo(tipoActivo: String): List<PlantillaChecklist> {
        return try {
            // 1. Obtener del cache local
            val cached = plantillaDao.getPlantillasByTipo(tipoActivo).map { it.toPlantilla() }

            // 2. Si hay conexión, intentar actualizar en background
            if (networkMonitor.isCurrentlyConnected()) {
                try {
                    val remote = remoteRepository.obtenerPlantillasPorTipoActivo(tipoActivo)
                    if (remote.isNotEmpty()) {
                        // Actualizar cache
                        val timestamp = System.currentTimeMillis()
                        val entities = remote.map { PlantillaEntity.fromPlantilla(it, timestamp) }
                        plantillaDao.insertPlantillas(entities)
                        return remote
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OfflinePlantillaRepository", "Error actualizando desde servidor, usando cache")
                }
            }

            cached
        } catch (e: Exception) {
            android.util.Log.e("OfflinePlantillaRepository", "Error obteniendo plantillas: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Obtiene una plantilla completa con sus categorías y preguntas
     */
    suspend fun obtenerPlantillaCompleta(plantillaId: Int): PlantillaChecklist? {
        return try {
            android.util.Log.d("OfflinePlantillaRepository", "🔍 Buscando plantilla completa ID: $plantillaId")

            // 1. Intentar del cache local
            val cachedPlantilla = plantillaDao.getPlantillaById(plantillaId)?.toPlantilla()

            if (cachedPlantilla != null) {
                android.util.Log.d("OfflinePlantillaRepository", "✅ Plantilla encontrada en cache")
                // Cargar categorías y preguntas desde cache
                val categorias = plantillaDao.getCategoriasByPlantilla(plantillaId)
                android.util.Log.d("OfflinePlantillaRepository", "📋 Categorías en cache: ${categorias.size}")

                val categoriasConPreguntas = categorias.map { categoriaEntity ->
                    val preguntas = plantillaDao.getPreguntasByCategoria(categoriaEntity.id)
                    android.util.Log.d("OfflinePlantillaRepository", "   └─ Categoría '${categoriaEntity.nombre}': ${preguntas.size} preguntas")

                    CategoriaPlantilla(
                        id = categoriaEntity.id,
                        plantillaId = categoriaEntity.plantillaId,
                        nombre = categoriaEntity.nombre,
                        orden = categoriaEntity.orden,
                        createdAt = categoriaEntity.createdAt,
                        preguntas = preguntas.map { preguntaEntity ->
                            PreguntaPlantilla(
                                id = preguntaEntity.id,
                                categoriaId = preguntaEntity.categoriaId,
                                texto = preguntaEntity.texto,
                                tipoRespuesta = preguntaEntity.tipoRespuesta,
                                orden = preguntaEntity.orden,
                                createdAt = preguntaEntity.createdAt
                            )
                        }
                    )
                }

                return cachedPlantilla.copy(categorias = categoriasConPreguntas)
            }

            // 2. Si no está en cache y hay conexión, buscar en servidor
            android.util.Log.d("OfflinePlantillaRepository", "📴 No encontrada en cache, verificando conexión...")
            val isConnected = networkMonitor.isCurrentlyConnected()
            android.util.Log.d("OfflinePlantillaRepository", "🌐 Conectado: $isConnected")

            if (isConnected) {
                android.util.Log.d("OfflinePlantillaRepository", "📡 Consultando servidor...")
                val remote = remoteRepository.obtenerPlantillaCompleta(plantillaId)

                if (remote != null) {
                    android.util.Log.d("OfflinePlantillaRepository", "✅ Plantilla obtenida del servidor: ${remote.categorias.size} categorías")
                    // Guardar en cache para próxima vez
                    guardarPlantillaCompletaEnCache(remote)
                    return remote
                } else {
                    android.util.Log.w("OfflinePlantillaRepository", "⚠️ Servidor retornó null")
                }
            } else {
                android.util.Log.w("OfflinePlantillaRepository", "❌ Sin conexión y sin cache")
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("OfflinePlantillaRepository", "❌ Error obteniendo plantilla completa: ${e.message}", e)
            null
        }
    }

    /**
     * Obtiene todas las plantillas
     */
    suspend fun obtenerTodasLasPlantillas(): List<PlantillaChecklist> {
        return try {
            val cached = plantillaDao.getAllPlantillas().map { it.toPlantilla() }

            if (networkMonitor.isCurrentlyConnected()) {
                try {
                    val remote = remoteRepository.obtenerTodasLasPlantillas()
                    if (remote.isNotEmpty()) {
                        val timestamp = System.currentTimeMillis()
                        val entities = remote.map { PlantillaEntity.fromPlantilla(it, timestamp) }
                        plantillaDao.insertPlantillas(entities)
                        return remote
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OfflinePlantillaRepository", "Error actualizando desde servidor, usando cache")
                }
            }

            cached
        } catch (e: Exception) {
            android.util.Log.e("OfflinePlantillaRepository", "Error obteniendo todas las plantillas: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Crea una nueva plantilla
     */
    suspend fun crearPlantilla(plantilla: PlantillaChecklist): Int? {
        return if (networkMonitor.isCurrentlyConnected()) {
            try {
                val plantillaId = remoteRepository.crearPlantilla(plantilla)
                plantillaId?.let { id ->
                    // Actualizar cache
                    val timestamp = System.currentTimeMillis()
                    plantillaDao.insertPlantilla(
                        PlantillaEntity.fromPlantilla(plantilla.copy(id = id), timestamp)
                    )
                }
                plantillaId
            } catch (e: Exception) {
                android.util.Log.e("OfflinePlantillaRepository", "Error creando plantilla: ${e.message}", e)
                null
            }
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede crear plantillas offline.")
            null
        }
    }

    /**
     * Actualiza una plantilla existente
     */
    suspend fun actualizarPlantilla(plantilla: PlantillaChecklist): Boolean {
        return if (networkMonitor.isCurrentlyConnected()) {
            try {
                val success = remoteRepository.actualizarPlantilla(plantilla)
                if (success) {
                    // Actualizar cache
                    val timestamp = System.currentTimeMillis()
                    plantillaDao.updatePlantilla(PlantillaEntity.fromPlantilla(plantilla, timestamp))
                }
                success
            } catch (e: Exception) {
                android.util.Log.e("OfflinePlantillaRepository", "Error actualizando plantilla: ${e.message}", e)
                false
            }
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede actualizar plantillas offline.")
            false
        }
    }

    /**
     * Elimina una plantilla
     */
    suspend fun eliminarPlantilla(id: Int): Boolean {
        return if (networkMonitor.isCurrentlyConnected()) {
            try {
                val success = remoteRepository.eliminarPlantilla(id)
                if (success) {
                    // Eliminar del cache
                    plantillaDao.getPlantillaById(id)?.let { plantilla ->
                        plantillaDao.deletePlantilla(plantilla)
                    }
                    // Eliminar categorías y preguntas relacionadas
                    plantillaDao.deleteCategoriasByPlantilla(id)
                }
                success
            } catch (e: Exception) {
                android.util.Log.e("OfflinePlantillaRepository", "Error eliminando plantilla: ${e.message}", e)
                false
            }
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede eliminar plantillas offline.")
            false
        }
    }

    /**
     * Crea una nueva categoría
     */
    suspend fun crearCategoria(categoria: CategoriaPlantilla): Int? {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.crearCategoria(categoria)
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede crear categorías offline.")
            null
        }
    }

    /**
     * Actualiza una categoría
     */
    suspend fun actualizarCategoria(categoria: CategoriaPlantilla): Boolean {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.actualizarCategoria(categoria)
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede actualizar categorías offline.")
            false
        }
    }

    /**
     * Elimina una categoría
     */
    suspend fun eliminarCategoria(id: Int): Boolean {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.eliminarCategoria(id)
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede eliminar categorías offline.")
            false
        }
    }

    /**
     * Crea una nueva pregunta
     */
    suspend fun crearPregunta(pregunta: PreguntaPlantilla): Int? {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.crearPregunta(pregunta)
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede crear preguntas offline.")
            null
        }
    }

    /**
     * Actualiza una pregunta
     */
    suspend fun actualizarPregunta(pregunta: PreguntaPlantilla): Boolean {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.actualizarPregunta(pregunta)
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede actualizar preguntas offline.")
            false
        }
    }

    /**
     * Elimina una pregunta
     */
    suspend fun eliminarPregunta(id: Int): Boolean {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.eliminarPregunta(id)
        } else {
            android.util.Log.w("OfflinePlantillaRepository", "Sin conexión. No se puede eliminar preguntas offline.")
            false
        }
    }

    /**
     * Busca plantillas por nombre
     */
    suspend fun buscarPlantillasPorNombre(nombre: String): List<PlantillaChecklist> {
        return if (networkMonitor.isCurrentlyConnected()) {
            remoteRepository.buscarPlantillasPorNombre(nombre)
        } else {
            // Buscar en cache local (implementación simplificada)
            emptyList()
        }
    }

    /**
     * Busca plantillas por tipo
     */
    suspend fun buscarPlantillasPorTipo(tipo: String): List<PlantillaChecklist> {
        return plantillaDao.getPlantillasByTipo(tipo).map { it.toPlantilla() }
    }

    /**
     * Obtiene los tipos de activo disponibles
     */
    suspend fun obtenerTiposDeActivo(): List<String> {
        return try {
            if (networkMonitor.isCurrentlyConnected()) {
                remoteRepository.obtenerTiposDeActivo()
            } else {
                plantillaDao.getTiposDeActivo()
            }
        } catch (e: Exception) {
            plantillaDao.getTiposDeActivo()
        }
    }

    /**
     * Observa el estado de conectividad en tiempo real
     */
    fun observarConectividad(): Flow<Boolean> {
        return networkMonitor.isConnected
    }

    /**
     * Guarda una plantilla completa en el cache local
     */
    private suspend fun guardarPlantillaCompletaEnCache(plantilla: PlantillaChecklist) {
        try {
            val timestamp = System.currentTimeMillis()

            // Guardar plantilla
            plantillaDao.insertPlantilla(PlantillaEntity.fromPlantilla(plantilla, timestamp))

            // Guardar categorías y preguntas
            plantilla.categorias.forEach { categoria ->
                val categoriaEntity = CategoriaEntity(
                    id = categoria.id,
                    plantillaId = categoria.plantillaId,
                    nombre = categoria.nombre,
                    orden = categoria.orden,
                    createdAt = categoria.createdAt,
                    lastSyncTimestamp = timestamp
                )
                plantillaDao.insertCategoria(categoriaEntity)

                categoria.preguntas.forEach { pregunta ->
                    val preguntaEntity = PreguntaEntity(
                        id = pregunta.id,
                        categoriaId = pregunta.categoriaId,
                        texto = pregunta.texto,
                        tipoRespuesta = pregunta.tipoRespuesta,
                        orden = pregunta.orden,
                        createdAt = pregunta.createdAt,
                        lastSyncTimestamp = timestamp
                    )
                    plantillaDao.insertPregunta(preguntaEntity)
                }
            }

            android.util.Log.d("OfflinePlantillaRepository", "✅ Plantilla ${plantilla.id} guardada en cache")
        } catch (e: Exception) {
            android.util.Log.e("OfflinePlantillaRepository", "Error guardando plantilla en cache: ${e.message}", e)
        }
    }

    /**
     * Fuerza la sincronización con el servidor
     */
    suspend fun sincronizarConServidor(): Boolean {
        return try {
            if (!networkMonitor.isCurrentlyConnected()) {
                return false
            }

            val remotePlantillas = remoteRepository.obtenerTodasLasPlantillas()
            val timestamp = System.currentTimeMillis()

            remotePlantillas.forEach { plantilla ->
                val plantillaCompleta = remoteRepository.obtenerPlantillaCompleta(plantilla.id)
                plantillaCompleta?.let {
                    guardarPlantillaCompletaEnCache(it)
                }
            }

            android.util.Log.d("OfflinePlantillaRepository", "✅ Sincronización exitosa: ${remotePlantillas.size} plantillas")
            true

        } catch (e: Exception) {
            android.util.Log.e("OfflinePlantillaRepository", "❌ Error en sincronización: ${e.message}", e)
            false
        }
    }
}
