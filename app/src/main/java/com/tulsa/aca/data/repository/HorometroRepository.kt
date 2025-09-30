package com.tulsa.aca.data.repository

import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.HorometroPendiente
import com.tulsa.aca.data.models.ReporteInspeccion
import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.supabase.SupabaseClient
import com.tulsa.aca.viewmodel.InfoReporteCierre
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject

data class ResultadoCierre(
    val success: Boolean,
    val horasUso: Float? = null,
    val mensaje: String? = null,
    val error: String? = null
)

class HorometroRepository {
    private val client = SupabaseClient.client

    /**
     * Obtener horómetros pendientes de cierre para un usuario
     * Usa la vista v_horometros_pendientes creada en SQL
     */
    suspend fun obtenerHorometrosPendientes(usuarioId: String): List<HorometroPendiente> {
        return try {
            android.util.Log.d("HorometroRepo", "📋 Obteniendo horómetros pendientes para usuario: $usuarioId")

            // Consultar la vista optimizada
            val result = client.from("v_horometros_pendientes")
                .select()
                .decodeList<HorometroPendiente>()

            android.util.Log.d("HorometroRepo", "✅ Encontrados ${result.size} horómetros pendientes")

            result.forEach { pendiente ->
                android.util.Log.d("HorometroRepo", """
                    - Reporte: ${pendiente.reporteId}
                    - Grúa: ${pendiente.grua}
                    - Horómetro inicial: ${pendiente.horometroInicial}
                    - Horas desde reporte: ${pendiente.horasDesdeReporte}
                """.trimIndent())
            }

            result

        } catch (e: Exception) {
            android.util.Log.e("HorometroRepo", "❌ Error obteniendo pendientes: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Obtener información de un reporte para cerrar el horómetro
     */
    suspend fun obtenerInfoReporteParaCierre(reporteId: String): InfoReporteCierre? {
        return try {
            android.util.Log.d("HorometroRepo", "📄 Obteniendo info del reporte: $reporteId")

            // Obtener reporte con JOIN a activo
            val reporte = client.from("reportes_inspeccion")
                .select(
                    columns = Columns.raw("""
                        id,
                        activo_id,
                        horometro_inicial,
                        turno,
                        activos!inner(
                            id,
                            nombre
                        )
                    """.trimIndent())
                ) {
                    filter {
                        eq("id", reporteId)
                    }
                }
                .decodeSingleOrNull<JsonObject>()

            if (reporte != null) {
                val activoJson = reporte["activos"]?.jsonObject
                val gruaNombre = activoJson?.get("nombre")?.jsonPrimitive?.content ?: "Desconocida"
                val horometroInicial = reporte["horometro_inicial"]?.jsonPrimitive?.float ?: 0f
                val turno = reporte["turno"]?.jsonPrimitive?.content?.toIntOrNull()

                android.util.Log.d("HorometroRepo", """
                    ✅ Info obtenida:
                    - Grúa: $gruaNombre
                    - Horómetro inicial: $horometroInicial
                    - Turno: $turno
                """.trimIndent())

                InfoReporteCierre(
                    reporteId = reporteId,
                    grua = gruaNombre,
                    horometroInicial = horometroInicial,
                    turno = turno
                )
            } else {
                android.util.Log.e("HorometroRepo", "❌ No se encontró el reporte")
                null
            }

        } catch (e: Exception) {
            android.util.Log.e("HorometroRepo", "❌ Error obteniendo info: ${e.message}", e)
            null
        }
    }

    /**
     * Cerrar horómetro llamando a la función SQL cerrar_horometro()
     */
    suspend fun cerrarHorometro(
        reporteId: String,
        usuarioId: String,
        horometroFinal: Float,
        observaciones: String? = null
    ): ResultadoCierre {
        return try {
            android.util.Log.d("HorometroRepo", """
                🔧 Cerrando horómetro:
                - Reporte: $reporteId
                - Usuario: $usuarioId
                - Horómetro final: $horometroFinal
            """.trimIndent())

            // Llamar a la función SQL
            val params = buildJsonObject {
                put("p_reporte_id", reporteId)
                put("p_horometro_final", horometroFinal)
                put("p_usuario_id", usuarioId)
                observaciones?.let { put("p_observaciones", it) }
            }

            val resultado = client.postgrest.rpc("cerrar_horometro", params)
                .decodeAs<JsonObject>()

            // Parsear resultado
            val success = resultado["success"]?.jsonPrimitive?.boolean ?: false
            val error = resultado["error"]?.jsonPrimitive?.content
            val horasUso = resultado["horas_uso"]?.jsonPrimitive?.float
            val mensaje = resultado["mensaje"]?.jsonPrimitive?.content

            if (success) {
                android.util.Log.d("HorometroRepo", """
                    ✅ Horómetro cerrado exitosamente
                    - Horas de uso: $horasUso
                    - Mensaje: $mensaje
                """.trimIndent())

                ResultadoCierre(
                    success = true,
                    horasUso = horasUso,
                    mensaje = mensaje
                )
            } else {
                android.util.Log.e("HorometroRepo", "❌ Error: $error")
                ResultadoCierre(
                    success = false,
                    error = error
                )
            }

        } catch (e: Exception) {
            android.util.Log.e("HorometroRepo", "❌ Excepción cerrando horómetro: ${e.message}", e)
            ResultadoCierre(
                success = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Obtener el último horómetro de un activo (útil para validaciones)
     */
    suspend fun obtenerUltimoHorometro(activoId: Int): Float? {
        return try {
            val activo = client.from("activos")
                .select {
                    filter {
                        eq("id", activoId)
                    }
                }
                .decodeSingleOrNull<Activo>()

            activo?.horometroActual
        } catch (e: Exception) {
            android.util.Log.e("HorometroRepo", "Error obteniendo último horómetro: ${e.message}", e)
            null
        }
    }

    /**
     * Verificar si un reporte tiene horómetro pendiente
     */
    suspend fun tieneHorometroPendiente(reporteId: String): Boolean {
        return try {
            val reporte = client.from("reportes_inspeccion")
                .select {
                    filter {
                        eq("id", reporteId)
                    }
                }
                .decodeSingleOrNull<ReporteInspeccion>()

            reporte?.horometroPendiente ?: false
        } catch (e: Exception) {
            android.util.Log.e("HorometroRepo", "Error verificando pendiente: ${e.message}", e)
            false
        }
    }
}