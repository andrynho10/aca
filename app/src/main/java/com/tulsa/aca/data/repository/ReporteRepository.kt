package com.tulsa.aca.data.repository

import android.content.Context
import android.net.Uri
import com.tulsa.aca.data.models.FotoRespuesta
import com.tulsa.aca.data.models.ReporteInspeccion
import com.tulsa.aca.data.models.RespuestaReporte
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID

data class RespuestaConFotos(
    val respuesta: RespuestaReporte,
    val fotos: List<Uri>
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
}