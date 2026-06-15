package com.tulsa.aca.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.tulsa.aca.data.local.AppDatabase
import com.tulsa.aca.data.local.entities.FotoPendienteEntity
import com.tulsa.aca.data.local.entities.ReportePendienteEntity
import com.tulsa.aca.data.models.ReporteInspeccion
import com.tulsa.aca.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Repositorio offline-first para reportes de inspección
 * - Permite crear reportes sin conexión
 * - Los guarda en cola de sincronización
 * - Sincroniza automáticamente cuando hay conexión
 */
class OfflineReporteRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val reportePendienteDao = database.reportePendienteDao()
    private val networkMonitor = NetworkMonitor(context)
    private val remoteRepository = ReporteRepository()
    private val gson = Gson()

    /**
     * Crea un reporte con timestamps y horómetro
     * Si no hay conexión, lo guarda en cola para sincronización posterior
     */
    suspend fun crearReporteConTimestampsYHorometro(
        activoId: Int,
        usuarioId: String,
        plantillaId: Int,
        respuestasConFotos: List<RespuestaConFotos>,
        timestampInicio: String,
        timestampFin: String,
        duracionMinutos: Int,
        horometroInicial: Float? = null,
        turno: Int? = null
    ): Result<String> {
        return try {
            val reporteId = UUID.randomUUID().toString()
            val isConnected = networkMonitor.isCurrentlyConnected()

            android.util.Log.d("OfflineReporteRepository", "Crear reporte $reporteId | conectado=$isConnected")

            if (respuestasConFotos.isEmpty()) {
                android.util.Log.e("OfflineReporteRepository", "ERROR CRÍTICO: No se recibieron respuestas")
                android.util.Log.e("OfflineReporteRepository", "Esto indica un problema en el ViewModel")
                android.util.Log.e("OfflineReporteRepository", "ActivoID: $activoId, UserID: $usuarioId, TemplateID: $plantillaId")
                return Result.failure(IllegalStateException("No se recibieron respuestas del checklist"))
            }

            val respuestasNulas = respuestasConFotos.count { it.respuesta.respuesta == null }
            if (respuestasNulas > 0) {
                android.util.Log.w("OfflineReporteRepository", "$respuestasNulas respuestas con valor null en reporte $reporteId")
            }

            // Con conexión: intenta enviar directamente al servidor; si falla, cae a cola offline
            if (isConnected) {
                val success = remoteRepository.crearReporteConTimestampsYHorometro(
                    context = context,
                    activoId = activoId,
                    usuarioId = usuarioId,
                    plantillaId = plantillaId,
                    respuestasConFotos = respuestasConFotos,
                    timestampInicio = timestampInicio,
                    timestampFin = timestampFin,
                    duracionMinutos = duracionMinutos,
                    horometroInicial = horometroInicial,
                    turno = turno
                )

                if (success) {
                    android.util.Log.d("OfflineReporteRepository", "Reporte $reporteId creado en servidor")
                    return Result.success(reporteId)
                } else {
                    android.util.Log.w("OfflineReporteRepository", "Fallo al crear reporte online, guardando en cola offline")
                }
            }

            android.util.Log.w("OfflineReporteRepository", "Guardando reporte $reporteId en cola offline (activo=$activoId, plantilla=$plantillaId)")

            val respuestasSinFotos = respuestasConFotos.map { it.respuesta }
            val respuestasJson = gson.toJson(respuestasSinFotos)

            if (respuestasJson.isBlank() || respuestasJson == "null" || respuestasJson == "[]") {
                android.util.Log.e("OfflineReporteRepository", "JSON de respuestas inválido para reporte $reporteId: '$respuestasJson'")
                return Result.failure(IllegalStateException("JSON generado es inválido: '$respuestasJson'"))
            }

            // Verifica que el JSON sea deserializable antes de persistir para evitar guardar datos corruptos
            try {
                val testDeserializacion = gson.fromJson(respuestasJson, Array<com.tulsa.aca.data.models.RespuestaReporte>::class.java)
                if (testDeserializacion == null || testDeserializacion.isEmpty()) {
                    android.util.Log.e("OfflineReporteRepository", "JSON no deserializa correctamente para reporte $reporteId")
                    return Result.failure(IllegalStateException("JSON no se puede deserializar correctamente"))
                }
            } catch (e: Exception) {
                android.util.Log.e("OfflineReporteRepository", "Fallo en deserialización de prueba para reporte $reporteId", e)
                return Result.failure(IllegalStateException("JSON generado no es válido: ${e.message}"))
            }

            // Crear entidad de reporte pendiente
            val reportePendiente = ReportePendienteEntity(
                id = reporteId,
                activoId = activoId,
                usuarioId = usuarioId,
                plantillaId = plantillaId,
                timestampInicio = timestampInicio,
                timestampCompletado = timestampFin,
                duracionMinutos = duracionMinutos,
                horometroInicial = horometroInicial,
                turno = turno,
                respuestasJson = respuestasJson,
                intentosSincronizacion = 0,
                ultimoIntento = null,
                errorSincronizacion = null
            )

            reportePendienteDao.insertReportePendiente(reportePendiente)

            // Guarda cada URI de foto local en cola; el SyncManager las subirá cuando haya conexión
            var totalFotosGuardadas = 0
            respuestasConFotos.forEachIndexed { index, respuestaConFotos ->
                respuestaConFotos.fotos.forEach { uri ->
                    val fotoPendiente = FotoPendienteEntity(
                        reporteId = reporteId,
                        respuestaIndex = index,
                        localUri = uri.toString(),
                        preguntaId = respuestaConFotos.respuesta.preguntaId,
                        subida = false,
                        urlStorage = null
                    )
                    reportePendienteDao.insertFotoPendiente(fotoPendiente)
                    totalFotosGuardadas++
                }
            }

            val count = reportePendienteDao.getReportesPendientesCount()
            android.util.Log.d("OfflineReporteRepository", "Reporte $reporteId guardado offline con $totalFotosGuardadas fotos. Pendientes totales: $count")

            Result.success(reporteId)

        } catch (e: Exception) {
            android.util.Log.e("OfflineReporteRepository", "Error creando reporte: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene el número de reportes pendientes de sincronización
     */
    suspend fun getReportesPendientesCount(): Int {
        return reportePendienteDao.getReportesPendientesCount()
    }

    /**
     * Flow que emite el número de reportes pendientes
     */
    fun observarReportesPendientes(): Flow<Int> {
        return reportePendienteDao.getReportesPendientesCountFlow()
    }

    /**
     * Obtiene todos los reportes pendientes
     */
    suspend fun getAllReportesPendientes(): List<ReportePendienteEntity> {
        return reportePendienteDao.getAllReportesPendientes()
    }

    /**
     * Obtiene historial de reportes por activo
     * Solo online por ahora
     */
    suspend fun obtenerHistorialPorActivo(activoId: Int): List<ReporteInspeccion> {
        return if (networkMonitor.isCurrentlyConnected()) {
            try {
                remoteRepository.obtenerHistorialPorActivo(activoId)
            } catch (e: Exception) {
                android.util.Log.e("OfflineReporteRepository", "Error obteniendo historial: ${e.message}", e)
                emptyList()
            }
        } else {
            android.util.Log.w("OfflineReporteRepository", "Sin conexión. No se puede obtener historial.")
            emptyList()
        }
    }

    /**
     * Obtiene historial limitado por activo
     */
    suspend fun obtenerHistorialLimitadoPorActivo(activoId: Int, limite: Int = 5): List<ReporteInspeccion> {
        return if (networkMonitor.isCurrentlyConnected()) {
            try {
                remoteRepository.obtenerHistorialLimitadoPorActivo(activoId, limite)
            } catch (e: Exception) {
                android.util.Log.e("OfflineReporteRepository", "Error obteniendo historial limitado: ${e.message}", e)
                emptyList()
            }
        } else {
            android.util.Log.w("OfflineReporteRepository", "Sin conexión. No se puede obtener historial.")
            emptyList()
        }
    }

    /**
     * Obtiene reportes recientes
     */
    suspend fun obtenerReportesRecientes(limite: Int = 50): List<ReporteInspeccion> {
        return if (networkMonitor.isCurrentlyConnected()) {
            try {
                remoteRepository.obtenerReportesRecientes(limite)
            } catch (e: Exception) {
                android.util.Log.e("OfflineReporteRepository", "Error obteniendo reportes recientes: ${e.message}", e)
                emptyList()
            }
        } else {
            android.util.Log.w("OfflineReporteRepository", "Sin conexión. No se puede obtener reportes recientes.")
            emptyList()
        }
    }

    /**
     * Obtiene reporte completo por ID
     */
    suspend fun obtenerReporteCompleto(reporteId: String): ReporteCompleto? {
        return if (networkMonitor.isCurrentlyConnected()) {
            try {
                remoteRepository.obtenerReporteCompleto(reporteId)
            } catch (e: Exception) {
                android.util.Log.e("OfflineReporteRepository", "Error obteniendo reporte completo: ${e.message}", e)
                null
            }
        } else {
            android.util.Log.w("OfflineReporteRepository", "Sin conexión. No se puede obtener reporte completo.")
            null
        }
    }

    /**
     * Obtiene usuario por ID
     */
    suspend fun obtenerUsuarioPorId(usuarioId: String) = remoteRepository.obtenerUsuarioPorId(usuarioId)

    /**
     * Verifica si hay conexión a Internet
     */
    fun isConnected(): Boolean = networkMonitor.isCurrentlyConnected()

    /**
     * Flow que emite el estado de conectividad
     */
    fun observarConectividad(): Flow<Boolean> = networkMonitor.isConnected

    /**
     * Fuerza la sincronización manual de reportes pendientes
     */
    suspend fun forzarSincronizacion(): Result<Int> {
        return try {
            if (!networkMonitor.isCurrentlyConnected()) {
                android.util.Log.w("OfflineReporteRepository", "Sin conexión. No se puede sincronizar")
                return Result.failure(Exception("Sin conexión a Internet"))
            }

            val reportesPendientes = reportePendienteDao.getAllReportesPendientes()
            android.util.Log.d("OfflineReporteRepository", "Sincronización manual: ${reportesPendientes.size} reportes pendientes")

            var sincronizados = 0

            reportesPendientes.forEach { reportePendiente ->
                    android.util.Log.d("OfflineReporteRepository", "Sincronizando reporte pendiente: ${reportePendiente.id}")

                try {
                    val gson = Gson()
                    val respuestasType = object : com.google.gson.reflect.TypeToken<List<com.tulsa.aca.data.models.RespuestaReporte>>() {}.type
                    val respuestas: List<com.tulsa.aca.data.models.RespuestaReporte> = gson.fromJson(reportePendiente.respuestasJson, respuestasType)

                    // Obtener fotos pendientes
                    val fotosPendientes = reportePendienteDao.getFotosPendientesByReporte(reportePendiente.id)

                    // Crear lista de RespuestaConFotos
                    val fotosMap = mutableMapOf<Int, List<Uri>>()
                    fotosPendientes.forEach { foto ->
                        val uris = fotosMap.getOrDefault(foto.respuestaIndex, emptyList()).toMutableList()
                        uris.add(Uri.parse(foto.localUri))
                        fotosMap[foto.respuestaIndex] = uris
                    }

                    val respuestasConFotos = respuestas.mapIndexed { index, respuesta ->
                        RespuestaConFotos(
                            respuesta = respuesta,
                            fotos = fotosMap[index] ?: emptyList()
                        )
                    }

                    // Intentar crear en el servidor
                    val success = remoteRepository.crearReporteConTimestampsYHorometro(
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
                        reportePendienteDao.deleteReportePendienteById(reportePendiente.id)
                        reportePendienteDao.deleteFotosPendientesByReporte(reportePendiente.id)
                        sincronizados++
                        android.util.Log.d("OfflineReporteRepository", "Reporte ${reportePendiente.id} sincronizado")
                    } else {
                        android.util.Log.w("OfflineReporteRepository", "Fallo al sincronizar reporte ${reportePendiente.id}")
                    }

                } catch (e: Exception) {
                    android.util.Log.e("OfflineReporteRepository", "Error sincronizando ${reportePendiente.id}: ${e.message}", e)
                }
            }

            android.util.Log.d("OfflineReporteRepository", "Sincronización completada: $sincronizados/${reportesPendientes.size} reportes")

            Result.success(sincronizados)

        } catch (e: Exception) {
            android.util.Log.e("OfflineReporteRepository", "Error en sincronización: ${e.message}", e)
            Result.failure(e)
        }
    }
}
