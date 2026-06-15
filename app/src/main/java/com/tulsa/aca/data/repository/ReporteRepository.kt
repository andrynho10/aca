package com.tulsa.aca.data.repository

import android.content.Context
import android.net.Uri
import com.tulsa.aca.data.models.FotoRespuesta
import com.tulsa.aca.data.models.ReporteInspeccion
import com.tulsa.aca.data.models.RespuestaReporte
import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.util.UUID

data class RespuestaConFotos(
    val respuesta: RespuestaReporte,
    val fotos: List<Uri>
)
// Data class para reporte completo con detalles
data class ReporteCompleto(
    val reporte: ReporteInspeccion,
    val usuario: Usuario?,
    val respuestas: List<RespuestaReporte>,
    val fotos: Map<Int, List<String>> // Map de respuestaId -> List de URLs de fotos
)
class ReporteRepository {
    private val client = SupabaseClient.client
    private val storageRepository = StorageRepository()

    suspend fun crearReporte(
        activoId: Int,
        usuarioId: String,
        plantillaId: Int,
        respuestas: List<RespuestaReporte>
    ): Boolean {
        return try {
            val reporteId = UUID.randomUUID().toString()

            // Crear el reporte principal
            val reporte = ReporteInspeccion(
                id = reporteId,
                activoId = activoId,
                usuarioId = usuarioId,
                plantillaId = plantillaId
            )

            client.from("reportes_inspeccion").insert(reporte)

            // Insertar todas las respuestas
            val respuestasConReporte = respuestas.map {
                it.copy(reporteId = reporteId)
            }

            client.from("respuestas_reporte").insert(respuestasConReporte)

            true
        } catch (e: Exception) {
            android.util.Log.e("ChecklistApp", "ERROR EN REPOSITORIO: ${e.message}", e)
            false
        }
    }

    suspend fun crearReporteConFotos(
        context: Context,
        activoId: Int,
        usuarioId: String,
        plantillaId: Int,
        respuestasConFotos: List<RespuestaConFotos>
    ): Boolean {
        return try {
            val reporteId = UUID.randomUUID().toString()

            // 1. Crear el reporte principal
            val reporte = ReporteInspeccion(
                id = reporteId,
                activoId = activoId,
                usuarioId = usuarioId,
                plantillaId = plantillaId
            )

            client.from("reportes_inspeccion").insert(reporte)

            // 2. Insertar respuestas y obtener sus IDs
            val respuestasParaInsertar = respuestasConFotos.map {
                it.respuesta.copy(reporteId = reporteId)
            }

            val respuestasInsertadas = client.from("respuestas_reporte")
                .insert(respuestasParaInsertar) {
                    select()
                }.decodeList<RespuestaReporte>()

            // 3. Subir fotos y crear registros de fotos
            respuestasConFotos.forEachIndexed { index, respuestaConFotos ->
                val respuestaInsertada = respuestasInsertadas[index]
                val fotos = respuestaConFotos.fotos

                if (fotos.isNotEmpty() && respuestaInsertada.id != null) {
                    // Subir fotos a Storage
                    val urlsFotos = storageRepository.subirFotos(
                        context = context,
                        fotos = fotos,
                        reporteId = reporteId,
                        preguntaId = respuestaInsertada.preguntaId
                    )

                    // Crear registros en la tabla fotos_respuesta
                    val fotosRespuesta = urlsFotos.map { url ->
                        FotoRespuesta(
                            respuestaId = respuestaInsertada.id,
                            urlStorage = url
                        )
                    }

                    if (fotosRespuesta.isNotEmpty()) {
                        client.from("fotos_respuesta").insert(fotosRespuesta)
                    }
                }
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("ChecklistApp", "ERROR AL CREAR REPORTE CON FOTOS: ${e.message}", e)
            false
        }
    }
    // Obtener historia por Activo
    suspend fun obtenerHistorialPorActivo(activoId: Int): List<ReporteInspeccion> {
        return try {
            client.from("reportes_inspeccion").select {
                filter {
                    ReporteInspeccion::activoId eq activoId
                }
                order(column = "timestamp_completado", order = Order.DESCENDING)
            }.decodeList<ReporteInspeccion>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun obtenerReportesRecientes(limite: Int = 50): List<ReporteInspeccion> {
        return try {
            android.util.Log.d("ReporteRepository", "Obteniendo $limite reportes más recientes")
            client.from("reportes_inspeccion").select {
                order(column = "timestamp_completado", order = Order.DESCENDING)
                limit(limite.toLong())
            }.decodeList<ReporteInspeccion>()
        } catch (e: Exception) {
            android.util.Log.e("ReporteRepository", "Error obteniendo reportes recientes: ${e.message}", e)
            emptyList()
        }
    }

    // Historial Limitado Operadores
    suspend fun obtenerHistorialLimitadoPorActivo(activoId: Int, limite: Int = 5): List<ReporteInspeccion> {
        return try {
            android.util.Log.d("ReporteRepository", "Obteniendo historial limitado para activo $activoId (máximo $limite reportes)")

            val reportes = client.from("reportes_inspeccion").select {
                filter {
                    ReporteInspeccion::activoId eq activoId
                }
                order(column = "timestamp_completado", order = Order.DESCENDING)
                limit(limite.toLong()) // LIMITAR A LAS N MÁS RECIENTES
            }.decodeList<ReporteInspeccion>()

            android.util.Log.d("ReporteRepository", "Historial limitado obtenido: ${reportes.size} reportes de máximo $limite")
            reportes

        } catch (e: Exception) {
            android.util.Log.e("ReporteRepository", "Error obteniendo historial limitado: ${e.message}", e)
            emptyList()
        }
    }

    // Obtener usuario por ID
    suspend fun obtenerUsuarioPorId(usuarioId: String): Usuario? {
        return try {
            client.from("usuarios").select {
                filter {
                    Usuario::id eq usuarioId
                }
            }.decodeSingle<Usuario>()
        } catch (e: Exception) {
            null
        }
    }
    // Obtener detalles completos del reporte (para supervisores)
    suspend fun obtenerReporteCompleto(reporteId: String): ReporteCompleto? = coroutineScope {
        try {
            // 1. Obtener el reporte principal
            val reporte = client.from("reportes_inspeccion").select {
                filter {
                    ReporteInspeccion::id eq reporteId
                }
                limit(1)
            }.decodeSingle<ReporteInspeccion>()

            // 2. Cargar datos relacionados en paralelo
            val usuarioDeferred = async { obtenerUsuarioPorId(reporte.usuarioId) }

            val respuestas = client.from("respuestas_reporte").select {
                filter {
                    RespuestaReporte::reporteId eq reporteId
                }
            }.decodeList<RespuestaReporte>()

            // 3. Cargar todas las fotos en un solo request
            val fotosMap = if (respuestas.isEmpty()) {
                emptyMap()
            } else {
                val respuestaIds = respuestas.mapNotNull { it.id }.distinct()
                if (respuestaIds.isEmpty()) {
                    emptyMap()
                } else {
                    val fotos = client.from("fotos_respuesta").select {
                        filter { FotoRespuesta::respuestaId isIn respuestaIds }
                    }.decodeList<FotoRespuesta>()

                    if (fotos.isEmpty()) {
                        emptyMap()
                    } else {
                        fotos.groupBy { it.respuestaId }
                            .mapValues { entry -> entry.value.map { it.urlStorage } }
                    }
                }
            }

            ReporteCompleto(
                reporte = reporte,
                usuario = usuarioDeferred.await(),
                respuestas = respuestas,
                fotos = fotosMap
            )
        } catch (e: Exception) {
            android.util.Log.e("ChecklistApp", "ERROR AL OBTENER REPORTE COMPLETO: ${e.message}", e)
            null
        }
    }
    // Actualizar en ReporteRepository.kt
    // YA NO SE NECESITA - Los problemas vienen calculados desde BD
    /* suspend fun verificarReportesConProblemas(reporteIds: List<String>): Map<String, Boolean> {
        if (reporteIds.isEmpty()) return emptyMap()

        android.util.Log.d("ReporteRepository", "🔍 VERIFICANDO PROBLEMAS para ${reporteIds.size} reportes:")
        reporteIds.forEach { id ->
            android.util.Log.d("ReporteRepository", "   - Reporte ID: $id")
        }

        return try {
            // ✅ MÉTODO CORREGIDO: Usar múltiples consultas O método alternativo
            val respuestasFiltradas = mutableListOf<RespuestaReporte>()

            // Opción 1: Consultas individuales (más confiable)
            reporteIds.forEach { reporteId ->
                val respuestasDelReporte = client.from("respuestas_reporte").select {
                    filter {
                        RespuestaReporte::reporteId eq reporteId
                    }
                }.decodeList<RespuestaReporte>()
                respuestasFiltradas.addAll(respuestasDelReporte)
            }

            android.util.Log.d("ReporteRepository", "📋 RESPUESTAS ENCONTRADAS: ${respuestasFiltradas.size}")
            respuestasFiltradas.forEach { respuesta ->
                android.util.Log.d("ReporteRepository", "   - Reporte: ${respuesta.reporteId}, Pregunta: ${respuesta.preguntaId}, Respuesta: ${respuesta.respuesta}")
            }

            // Agrupar por reporte y verificar problemas
            val respuestasPorReporte = respuestasFiltradas.groupBy { it.reporteId }
            val reportesConProblemas = mutableMapOf<String, Boolean>()

            reporteIds.forEach { reporteId ->
                val respuestasDelReporte = respuestasPorReporte[reporteId] ?: emptyList()
                val tieneProblemas = respuestasDelReporte.any { !it.respuesta } // false = MALO

                reportesConProblemas[reporteId] = tieneProblemas

                val estadoProblemas = if (tieneProblemas) "⚠️ CON PROBLEMAS" else "✅ SIN PROBLEMAS"
                android.util.Log.d("ReporteRepository", "📊 Reporte $reporteId: $estadoProblemas (${respuestasDelReporte.size} respuestas)")

                if (tieneProblemas) {
                    val respuestasMalas = respuestasDelReporte.filter { !it.respuesta }
                    android.util.Log.d("ReporteRepository", "   ❌ Respuestas MALO: ${respuestasMalas.size}")
                    respuestasMalas.forEach { mala ->
                        android.util.Log.d("ReporteRepository", "      - Pregunta ${mala.preguntaId}: ${mala.comentario}")
                    }
                }
            }

            android.util.Log.d("ReporteRepository", "🏁 RESULTADO: ${reportesConProblemas.count { it.value }} de ${reporteIds.size} reportes tienen problemas")
            reportesConProblemas

        } catch (e: Exception) {
            android.util.Log.e("ReporteRepository", "❌ ERROR verificando reportes: ${e.message}", e)
            emptyMap()
        }
    }
    */
    suspend fun crearReporteConTimestamps(
        context: Context,
        activoId: Int,
        usuarioId: String,
        plantillaId: Int,
        respuestasConFotos: List<RespuestaConFotos>,
        timestampInicio: String,
        timestampFin: String,
        duracionMinutos: Int
    ): Boolean {
        return try {
            val reporteId = UUID.randomUUID().toString()

            val reporte = ReporteInspeccion(
                id = reporteId,
                activoId = activoId,
                usuarioId = usuarioId,
                plantillaId = plantillaId,
                timestampInicio = timestampInicio,
                timestampCompletado = timestampFin,
                duracionMinutos = duracionMinutos
            )

            client.from("reportes_inspeccion").insert(reporte)

            val respuestasParaInsertar = respuestasConFotos.map {
                it.respuesta.copy(reporteId = reporteId)
            }

            val respuestasInsertadas = client.from("respuestas_reporte")
                .insert(respuestasParaInsertar) {
                    select()
                }.decodeList<RespuestaReporte>()

            respuestasConFotos.forEachIndexed { index, respuestaConFotos ->
                val respuestaInsertada = respuestasInsertadas[index]
                val fotos = respuestaConFotos.fotos

                if (fotos.isNotEmpty() && respuestaInsertada.id != null) {
                    val urlsFotos = storageRepository.subirFotos(
                        context = context,
                        fotos = fotos,
                        reporteId = reporteId,
                        preguntaId = respuestaInsertada.preguntaId
                    )

                    val fotosRespuesta = urlsFotos.map { url ->
                        FotoRespuesta(
                            respuestaId = respuestaInsertada.id,
                            urlStorage = url
                        )
                    }

                    if (fotosRespuesta.isNotEmpty()) {
                        client.from("fotos_respuesta").insert(fotosRespuesta)
                    }
                }
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("ReporteRepository", "Error al crear reporte con timestamps: ${e.message}", e)
            false
        }
    }


    /**
     * NUEVA FUNCIÓN: Crear reporte con timestamps, horómetro y turno
     */
    suspend fun crearReporteConTimestampsYHorometro(
        context: Context,
        activoId: Int,
        usuarioId: String,
        plantillaId: Int,
        respuestasConFotos: List<RespuestaConFotos>,
        timestampInicio: String,
        timestampFin: String,
        duracionMinutos: Int,
        horometroInicial: Float? = null,
        turno: Int? = null
    ): Boolean {
        return try {
            val reporteId = UUID.randomUUID().toString()

            val reporte = ReporteInspeccion(
                id = reporteId,
                activoId = activoId,
                usuarioId = usuarioId,
                plantillaId = plantillaId,
                timestampInicio = timestampInicio,
                timestampCompletado = timestampFin,
                duracionMinutos = duracionMinutos,
                horometroInicial = horometroInicial,
                turno = null,
                horometroPendiente = horometroInicial != null
            )

            client.from("reportes_inspeccion").insert(reporte)

            val respuestasParaInsertar = respuestasConFotos.map {
                it.respuesta.copy(reporteId = reporteId)
            }

            val respuestasInsertadas = client.from("respuestas_reporte")
                .insert(respuestasParaInsertar) {
                    select()
                }.decodeList<RespuestaReporte>()

            respuestasConFotos.forEachIndexed { index, respuestaConFotos ->
                val respuestaInsertada = respuestasInsertadas[index]
                val fotos = respuestaConFotos.fotos

                if (fotos.isNotEmpty() && respuestaInsertada.id != null) {
                    val urlsFotos = storageRepository.subirFotos(
                        context = context,
                        fotos = fotos,
                        reporteId = reporteId,
                        preguntaId = respuestaInsertada.preguntaId
                    )

                    val fotosRespuesta = urlsFotos.map { url ->
                        FotoRespuesta(
                            respuestaId = respuestaInsertada.id,
                            urlStorage = url
                        )
                    }

                    if (fotosRespuesta.isNotEmpty()) {
                        client.from("fotos_respuesta").insert(fotosRespuesta)
                    }
                }
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("ReporteRepository", "Error al crear reporte con horómetro: ${e.message}", e)
            false
        }
    }
}
