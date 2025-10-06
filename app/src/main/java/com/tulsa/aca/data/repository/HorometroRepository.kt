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
    val tiempoTranscurrido: Float? = null,
    val mensaje: String? = null,
    val error: String? = null
)

data class ResultadoValidacion(
    val valido: Boolean,
    val ultimoHorometro: Float? = null,
    val error: String? = null
)

class HorometroRepository {
    private val client = SupabaseClient.client

    /**
     * NUEVA: Validar horómetro inicial antes de crear un reporte
     */
    suspend fun validarHorometroInicial(
        activoId: Int,
        horometroInicial: Float
    ): ResultadoValidacion {
        return try {
            android.util.Log.d("HorometroRepo", """
            🔍 Validando horómetro inicial:
            - Activo: $activoId
            - Horómetro inicial: $horometroInicial
        """.trimIndent())

            val params = buildJsonObject {
                put("p_activo_id", activoId)
                put("p_horometro_inicial", horometroInicial)
            }

            val resultado = client.postgrest.rpc("validar_horometro_inicial", params)
                .decodeAs<JsonObject>()

            val valido = resultado["valido"]?.jsonPrimitive?.boolean ?: false
            val error = resultado["error"]?.jsonPrimitive?.content

            // MANEJAR NULL CORRECTAMENTE
            val ultimoHorometro = try {
                resultado["ultimo_horometro"]?.jsonPrimitive?.content?.let { value ->
                    if (value == "null" || value.isEmpty()) {
                        null
                    } else {
                        value.toFloatOrNull()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("HorometroRepo", "No se pudo parsear ultimo_horometro: ${e.message}")
                null
            }

            if (valido) {
                android.util.Log.d("HorometroRepo", "✅ Horómetro inicial válido")
                ResultadoValidacion(
                    valido = true,
                    ultimoHorometro = ultimoHorometro
                )
            } else {
                android.util.Log.e("HorometroRepo", "❌ Horómetro inicial inválido: $error")
                ResultadoValidacion(
                    valido = false,
                    ultimoHorometro = ultimoHorometro,
                    error = error
                )
            }

        } catch (e: Exception) {
            android.util.Log.e("HorometroRepo", "❌ Error validando: ${e.message}", e)
            ResultadoValidacion(
                valido = false,
                error = "Error de conexión: ${e.message}"
            )
        }
    }

    /**
     * Obtener horómetros pendientes de cierre para un usuario
     */
    suspend fun obtenerHorometrosPendientes(usuarioId: String): List<HorometroPendiente> {
        return try {
            android.util.Log.d("HorometroRepo", "📋 Obteniendo horómetros pendientes para usuario: $usuarioId")

            val result = client.from("v_horometros_pendientes")
                .select()
                .decodeList<HorometroPendiente>()

            android.util.Log.d("HorometroRepo", "✅ Encontrados ${result.size} horómetros pendientes")
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

            val reporte = client.from("reportes_inspeccion")
                .select(
                    columns = Columns.raw("""
                        id,
                        activo_id,
                        horometro_inicial,
                        turno,
                        timestamp_inicio,
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

                InfoReporteCierre(
                    reporteId = reporteId,
                    grua = gruaNombre,
                    horometroInicial = horometroInicial,
                    turno = turno
                )
            } else {
                null
            }

        } catch (e: Exception) {
            android.util.Log.e("HorometroRepo", "❌ Error obteniendo info: ${e.message}", e)
            null
        }
    }

    /**
     * Cerrar horómetro con validaciones completas
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

            val params = buildJsonObject {
                put("p_reporte_id", reporteId)
                put("p_horometro_final", horometroFinal)
                put("p_usuario_id", usuarioId)
                observaciones?.let { put("p_observaciones", it) }
            }

            val resultado = client.postgrest.rpc("cerrar_horometro", params)
                .decodeAs<JsonObject>()

            val success = resultado["success"]?.jsonPrimitive?.boolean ?: false
            val error = resultado["error"]?.jsonPrimitive?.content
            val horasUso = resultado["horas_uso"]?.jsonPrimitive?.float
            val tiempoTranscurrido = resultado["tiempo_transcurrido"]?.jsonPrimitive?.float
            val mensaje = resultado["mensaje"]?.jsonPrimitive?.content

            if (success) {
                android.util.Log.d("HorometroRepo", "✅ Horómetro cerrado exitosamente")
                ResultadoCierre(
                    success = true,
                    horasUso = horasUso,
                    tiempoTranscurrido = tiempoTranscurrido,
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
            android.util.Log.e("HorometroRepo", "❌ Excepción: ${e.message}", e)
            ResultadoCierre(
                success = false,
                error = "Error de conexión: ${e.message}"
            )
        }
    }

    /**
     * Obtener el último horómetro de un activo
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