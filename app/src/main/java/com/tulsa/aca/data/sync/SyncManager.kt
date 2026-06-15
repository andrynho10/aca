package com.tulsa.aca.data.sync

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tulsa.aca.data.local.AppDatabase
import com.tulsa.aca.data.local.entities.*
import com.tulsa.aca.data.models.*
import com.tulsa.aca.data.repository.*
import com.tulsa.aca.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestiona la sincronización entre la base de datos local (Room) y el servidor (Supabase)
 * Implementa la estrategia "offline-first" donde los datos se leen primero del cache local
 */
class SyncManager(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val networkMonitor = NetworkMonitor(context)
    private val gson = Gson()

    // Repositorios remotos
    private val activoRepository = ActivoRepository()
    private val plantillaRepository = PlantillaRepository()
    private val reporteRepository = ReporteRepository()
    private val storageRepository = StorageRepository()

    // DAOs locales
    private val activoDao = database.activoDao()
    private val plantillaDao = database.plantillaDao()
    private val reportePendienteDao = database.reportePendienteDao()
    private val syncStatusDao = database.syncStatusDao()

    // Estado de sincronización
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: Flow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: Flow<SyncStatus> = _syncStatus.asStateFlow()

    companion object {
        // El cache se considera válido por 5 minutos; pasado ese tiempo se fuerza re-sync
        private const val SYNC_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutos
        private const val MAX_RETRY_ATTEMPTS = 3

        // Claves para entidades
        const val ENTITY_ACTIVOS = "activos"
        const val ENTITY_PLANTILLAS = "plantillas"
    }

    /**
     * Sincroniza todos los activos desde el servidor al cache local
     */
    suspend fun syncActivos(forceSync: Boolean = false): Result<Unit> {
        return try {
            if (!forceSync && !shouldSync(ENTITY_ACTIVOS)) {
                return Result.success(Unit)
            }

            if (!networkMonitor.isCurrentlyConnected()) {
                return Result.failure(Exception("Sin conexión a Internet"))
            }

            android.util.Log.d("SyncManager", "Iniciando sincronización de activos...")

            val activos = activoRepository.obtenerTodosLosActivos()
            val timestamp = System.currentTimeMillis()

            // Convertir a entidades locales
            val entities = activos.mapNotNull { activo ->
                ActivoEntity.fromActivo(activo, timestamp)
            }

            // Guardar en cache local
            activoDao.insertActivos(entities)

            // Actualizar estado de sincronización
            syncStatusDao.insertSyncStatus(
                SyncStatusEntity(
                    entidad = ENTITY_ACTIVOS,
                    ultimaSincronizacion = timestamp,
                    sincronizacionExitosa = true,
                    mensajeError = null
                )
            )

            android.util.Log.d("SyncManager", "Activos sincronizados: ${entities.size}")
            Result.success(Unit)

        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error sincronizando activos: ${e.message}", e)
            syncStatusDao.updateSyncTimestamp(
                entidad = ENTITY_ACTIVOS,
                timestamp = System.currentTimeMillis(),
                exitosa = false,
                error = e.message
            )
            Result.failure(e)
        }
    }

    /**
     * Sincroniza plantillas completas (con categorías y preguntas) desde el servidor
     */
    suspend fun syncPlantillas(forceSync: Boolean = false): Result<Unit> {
        return try {
            if (!forceSync && !shouldSync(ENTITY_PLANTILLAS)) {
                return Result.success(Unit)
            }

            if (!networkMonitor.isCurrentlyConnected()) {
                return Result.failure(Exception("Sin conexión a Internet"))
            }

            android.util.Log.d("SyncManager", "Iniciando sincronización de plantillas...")

            val plantillas = plantillaRepository.obtenerTodasLasPlantillas()
            val timestamp = System.currentTimeMillis()

            plantillas.forEach { plantilla ->
                // Obtener plantilla completa con categorías y preguntas
                val plantillaCompleta = plantillaRepository.obtenerPlantillaCompleta(plantilla.id)

                plantillaCompleta?.let { pc ->
                    // Guardar plantilla
                    plantillaDao.insertPlantilla(
                        PlantillaEntity.fromPlantilla(pc, timestamp)
                    )

                    // Guardar categorías
                    pc.categorias.forEach { categoria ->
                        plantillaDao.insertCategoria(
                            CategoriaEntity(
                                id = categoria.id,
                                plantillaId = categoria.plantillaId,
                                nombre = categoria.nombre,
                                orden = categoria.orden,
                                createdAt = categoria.createdAt,
                                lastSyncTimestamp = timestamp
                            )
                        )

                        // Guardar preguntas
                        categoria.preguntas.forEach { pregunta ->
                            plantillaDao.insertPregunta(
                                PreguntaEntity(
                                    id = pregunta.id,
                                    categoriaId = pregunta.categoriaId,
                                    texto = pregunta.texto,
                                    tipoRespuesta = pregunta.tipoRespuesta,
                                    orden = pregunta.orden,
                                    createdAt = pregunta.createdAt,
                                    lastSyncTimestamp = timestamp
                                )
                            )
                        }
                    }
                }
            }

            syncStatusDao.insertSyncStatus(
                SyncStatusEntity(
                    entidad = ENTITY_PLANTILLAS,
                    ultimaSincronizacion = timestamp,
                    sincronizacionExitosa = true,
                    mensajeError = null
                )
            )

            android.util.Log.d("SyncManager", "Plantillas sincronizadas: ${plantillas.size}")
            Result.success(Unit)

        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error sincronizando plantillas: ${e.message}", e)
            syncStatusDao.updateSyncTimestamp(
                entidad = ENTITY_PLANTILLAS,
                timestamp = System.currentTimeMillis(),
                exitosa = false,
                error = e.message
            )
            Result.failure(e)
        }
    }

    /**
     * Sincroniza reportes pendientes al servidor
     */
    suspend fun syncReportesPendientes(): Result<SyncResult> {
        if (!networkMonitor.isCurrentlyConnected()) {
            return Result.failure(Exception("Sin conexión a Internet"))
        }

        _isSyncing.value = true
        _syncStatus.value = SyncStatus.Syncing("Sincronizando reportes pendientes...")

        val result = SyncResult()

        try {
            val reportesPendientes = reportePendienteDao.getAllReportesPendientes()

            android.util.Log.d("SyncManager", "Reportes pendientes a sincronizar: ${reportesPendientes.size}")

            reportesPendientes.forEach { reportePendiente ->
                try {
                    // Intentar sincronizar reporte
                    val success = syncSingleReporte(reportePendiente)

                    if (success) {
                        result.reportesSincronizados++
                        // Eliminar reporte de la cola
                        reportePendienteDao.deleteReportePendienteById(reportePendiente.id)
                        android.util.Log.d("SyncManager", "Reporte ${reportePendiente.id} sincronizado")
                    } else {
                        result.reportesFallidos++
                        // Registrar intento fallido
                        reportePendienteDao.registrarIntentoFallido(
                            reporteId = reportePendiente.id,
                            timestamp = System.currentTimeMillis(),
                            error = "Error al sincronizar"
                        )
                    }

                } catch (e: Exception) {
                    result.reportesFallidos++
                    android.util.Log.e("SyncManager", "Error sincronizando reporte ${reportePendiente.id}: ${e.message}", e)
                    reportePendienteDao.registrarIntentoFallido(
                        reporteId = reportePendiente.id,
                        timestamp = System.currentTimeMillis(),
                        error = e.message ?: "Error desconocido"
                    )
                }
            }

            _syncStatus.value = SyncStatus.Success(result)
            return Result.success(result)

        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error en sincronización general: ${e.message}", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Error desconocido")
            return Result.failure(e)

        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Sincroniza un reporte individual con sus fotos
     */
    private suspend fun syncSingleReporte(reportePendiente: ReportePendienteEntity): Boolean {
        return try {
            // 1. Deserializar respuestas
            val respuestasType = object : TypeToken<List<RespuestaReporte>>() {}.type
            val respuestas: List<RespuestaReporte> = gson.fromJson(reportePendiente.respuestasJson, respuestasType)

            // 2. Obtener fotos pendientes
            val fotosPendientes = reportePendienteDao.getFotosPendientesByReporte(reportePendiente.id)

            // 3. Reagrupar fotos por índice de respuesta para reconstruir el mapa original
            val fotosMap = mutableMapOf<Int, List<Uri>>() // respuestaIndex -> List<Uri>
            fotosPendientes.forEach { foto ->
                val uris = fotosMap.getOrDefault(foto.respuestaIndex, emptyList()).toMutableList()
                uris.add(Uri.parse(foto.localUri))
                fotosMap[foto.respuestaIndex] = uris
            }

            // 4. Crear lista de RespuestaConFotos
            val respuestasConFotos = respuestas.mapIndexed { index, respuesta ->
                RespuestaConFotos(
                    respuesta = respuesta,
                    fotos = fotosMap[index] ?: emptyList()
                )
            }

            // 5. Crear reporte en el servidor
            val success = reporteRepository.crearReporteConTimestampsYHorometro(
                context = context,
                activoId = reportePendiente.activoId,
                usuarioId = reportePendiente.usuarioId,
                plantillaId = reportePendiente.plantillaId,
                respuestasConFotos = respuestasConFotos,
                timestampInicio = reportePendiente.timestampInicio,
                timestampFin = reportePendiente.timestampCompletado,
                duracionMinutos = reportePendiente.duracionMinutos,
                horometroInicial = reportePendiente.horometroInicial,
                turno = reportePendiente.turno
            )

            if (success) {
                // Eliminar fotos locales de la cola
                reportePendienteDao.deleteFotosPendientesByReporte(reportePendiente.id)
            }

            success

        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error sincronizando reporte individual: ${e.message}", e)
            false
        }
    }

    /**
     * Determina si una entidad necesita sincronizarse
     */
    private suspend fun shouldSync(entidad: String): Boolean {
        val status = syncStatusDao.getSyncStatus(entidad) ?: return true
        val age = System.currentTimeMillis() - status.ultimaSincronizacion
        return age > SYNC_MAX_AGE_MS || !status.sincronizacionExitosa
    }

    /**
     * Ejecuta sincronización completa de todas las entidades
     */
    suspend fun syncAll(forceSync: Boolean = false): Result<Unit> {
        if (!networkMonitor.isCurrentlyConnected()) {
            return Result.failure(Exception("Sin conexión a Internet"))
        }

        _isSyncing.value = true
        _syncStatus.value = SyncStatus.Syncing("Sincronizando datos...")

        return try {
            // Sincronizar activos
            syncActivos(forceSync)

            // Sincronizar plantillas
            syncPlantillas(forceSync)

            // Sincronizar reportes pendientes
            syncReportesPendientes()

            _syncStatus.value = SyncStatus.Success(SyncResult())
            Result.success(Unit)

        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Error desconocido")
            Result.failure(e)

        } finally {
            _isSyncing.value = false
        }
    }
}

/**
 * Estados de sincronización
 */
sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Syncing(val message: String) : SyncStatus()
    data class Success(val result: SyncResult) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

/**
 * Resultado de sincronización
 */
data class SyncResult(
    var reportesSincronizados: Int = 0,
    var reportesFallidos: Int = 0,
    var fotosSincronizadas: Int = 0,
    var fotosFallidas: Int = 0
)
