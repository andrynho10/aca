package com.tulsa.aca.data.repository

import com.tulsa.aca.data.models.ReporteInspeccion
import com.tulsa.aca.data.models.RespuestaReporte
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID

class ReporteRepository {
    private val client = SupabaseClient.client

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

    suspend fun obtenerHistorialPorActivo(activoId: Int): List<ReporteInspeccion> {
        return try {
            client.from("reportes_inspeccion").select {
                filter {
                    ReporteInspeccion::activoId eq activoId
                }
                order(column = "timestamp_completado", order = Order.DESCENDING)  // <- Sintaxis correcta
            }.decodeList<ReporteInspeccion>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}