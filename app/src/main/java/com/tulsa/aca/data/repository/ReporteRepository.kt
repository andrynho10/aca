package com.tulsa.aca.data.repository

import android.content.Context
import android.net.Uri
import com.tulsa.aca.data.models.FotoRespuesta
import com.tulsa.aca.data.models.ReporteInspeccion
import com.tulsa.aca.data.models.RespuestaReporte
import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.Order
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
    suspend fun obtenerReporteCompleto(reporteId: String): ReporteCompleto? {
        return try {
            // 1. Obtener el reporte
            val reporte = client.from("reportes_inspeccion").select {
                filter {
                    ReporteInspeccion::id eq reporteId
                }
            }.decodeSingle<ReporteInspeccion>()

            // 2. Obtener el usuario
            val usuario = obtenerUsuarioPorId(reporte.usuarioId)

            // 3. Obtener todas las respuestas del reporte
            val respuestas = client.from("respuestas_reporte").select {
                filter {
                    RespuestaReporte::reporteId eq reporteId
                }
            }.decodeList<RespuestaReporte>()

            // 4. Obtener todas las fotos asociadas a las respuestas
            val fotosMap = mutableMapOf<Int, List<String>>()
            for (respuesta in respuestas) {
                respuesta.id?.let { respuestaId ->
                    val fotos = client.from("fotos_respuesta").select {
                        filter {
                            FotoRespuesta::respuestaId eq respuestaId
                        }
                    }.decodeList<FotoRespuesta>()

                    if (fotos.isNotEmpty()) {
                        fotosMap[respuestaId] = fotos.map { it.urlStorage }
                    }
                }
            }

            ReporteCompleto(
                reporte = reporte,
                usuario = usuario,
                respuestas = respuestas,
                fotos = fotosMap
            )
        } catch (e: Exception) {
            android.util.Log.e("ChecklistApp", "ERROR AL OBTENER REPORTE COMPLETO: ${e.message}", e)
            null
        }
    }
    suspend fun verificarReportesConProblemas(reporteIds: List<String>): Map<String, Boolean> {
        if (reporteIds.isEmpty()) return emptyMap()

        return try {
            val reportesConProblemas = mutableMapOf<String, Boolean>()

            // Obtener TODAS las respuestas de la tabla
            val todasLasRespuestas = client.from("respuestas_reporte").select {
                // Sin filtro - obtener todas
            }.decodeList<RespuestaReporte>()

            // Filtrar solo las respuestas de los reportes que nos interesan
            val respuestasFiltradas = todasLasRespuestas.filter { it.reporteId in reporteIds }

            // Agrupar por reporte y verificar problemas
            val respuestasPorReporte = respuestasFiltradas.groupBy { it.reporteId }

            reporteIds.forEach { reporteId ->
                val respuestasDelReporte = respuestasPorReporte[reporteId] ?: emptyList()
                val tieneProblemas = respuestasDelReporte.any { !it.respuesta }
                reportesConProblemas[reporteId] = tieneProblemas
            }

            android.util.Log.d("ReporteRepository", "Verificados ${reporteIds.size} reportes de ${todasLasRespuestas.size} respuestas totales")
            reportesConProblemas

        } catch (e: Exception) {
            android.util.Log.e("ReporteRepository", "Error verificando reportes: ${e.message}")
            emptyMap()
        }
    }
}