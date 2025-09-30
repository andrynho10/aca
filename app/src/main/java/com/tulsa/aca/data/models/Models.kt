package com.tulsa.aca.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Representa un activo físico (maquinaria)
@Serializable
data class Activo(
    val id: Int? = null,
    val nombre: String,
    val modelo: String,
    val tipo: String,
    @SerialName("codigo_qr")
    val codigoQr: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    // NUEVOS CAMPOS
    @SerialName("es_operativa")
    val esOperativa: Boolean = true,
    @SerialName("es_standby")
    val esStandby: Boolean = false,
    @SerialName("horometro_actual")
    val horometroActual: Float? = null
)

// Representa a un usuario de la aplicación
@Serializable
data class Usuario(
    val id: String,
    @SerialName("nombre_completo")
    val nombreCompleto: String,
    val rol: String,
    @SerialName("created_at")
    val createdAt: String? = null,

    // Campos de auth.users que obtendremos por JOIN
    val email: String? = null
)

// --- Modelos para las Plantillas de Checklists ---

// Representa una pregunta específica dentro de una categoría
@Serializable
data class PreguntaPlantilla(
    val id: Int,
    @SerialName("categoria_id")
    val categoriaId: Int,
    val texto: String,
    @SerialName("tipo_respuesta")
    val tipoRespuesta: String = "SI_NO",
    val orden: Int,
    @SerialName("created_at")
    val createdAt: String? = null
)

// Representa una sección del checklist
@Serializable
data class CategoriaPlantilla(
    val id: Int,
    @SerialName("plantilla_id")
    val plantillaId: Int,
    val nombre: String,
    val orden: Int,
    @SerialName("created_at")
    val createdAt: String? = null,
    // Lista de preguntas (se cargará por separado)
    val preguntas: List<PreguntaPlantilla> = emptyList()
)

// Representa la plantilla completa de un checklist
@Serializable
data class PlantillaChecklist(
    val id: Int,
    val nombre: String,
    @SerialName("tipo_activo")
    val tipoActivo: String,
    val activa: Boolean = true,
    @SerialName("created_at")
    val createdAt: String? = null,
    val categorias: List<CategoriaPlantilla> = emptyList()
)

// --- Modelos para los Reportes de Inspección ---

// Representa la respuesta a una pregunta específica en un reporte
@Serializable
data class RespuestaReporte(
    val id: Int? = null,
    @SerialName("reporte_id")
    val reporteId: String,
    @SerialName("pregunta_id")
    val preguntaId: Int,
    val respuesta: Boolean, // true = SÍ/Bueno, false = NO/Malo
    val comentario: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

// Representa un checklist completado y guardado
@Serializable
data class ReporteInspeccion(
    val id: String? = null,
    @SerialName("activo_id")
    val activoId: Int,
    @SerialName("usuario_id")
    val usuarioId: String,
    @SerialName("plantilla_id")
    val plantillaId: Int,
    @SerialName("timestamp_inicio")
    val timestampInicio: String? = null,
    @SerialName("timestamp_completado")
    val timestampCompletado: String? = null,
    @SerialName("duracion_minutos")
    val duracionMinutos: Int? = null,

    // NUEVOS CAMPOS CALCULADOS
    @SerialName("tiene_problemas")
    val tieneProblemas: Boolean = false,
    @SerialName("total_respuestas")
    val totalRespuestas: Int = 0,
    @SerialName("respuestas_malas")
    val respuestasMalas: Int = 0,
    @SerialName("score_cumplimiento")
    val scoreCumplimiento: Float = 100.0f,

    // NUEVOS CAMPOS DE HORÓMETRO
    val turno: Int? = null,
    @SerialName("horometro_inicial")
    val horometroInicial: Float? = null,
    @SerialName("horometro_final")
    val horometroFinal: Float? = null,
    @SerialName("horometro_pendiente")
    val horometroPendiente: Boolean = false,
    @SerialName("horas_uso")
    val horasUso: Float? = null
)

// URLs de fotos asociadas a respuestas
@Serializable
data class FotoRespuesta(
    val id: Int? = null,
    @SerialName("respuesta_id")
    val respuestaId: Int,
    @SerialName("url_storage")
    val urlStorage: String,
    @SerialName("created_at")
    val createdAt: String? = null
)
// NUEVO MODELO Horómetro Pendiente
@Serializable
data class HorometroPendiente(
    @SerialName("reporte_id")
    val reporteId: String,
    val grua: String,
    val operador: String,
    @SerialName("horometro_inicial")
    val horometroInicial: Float,
    @SerialName("timestamp_completado")
    val timestampCompletado: String,
    val turno: Int?,
    @SerialName("horas_desde_reporte")
    val horasDesdeReporte: Double
)
// NUEVO MODELO Dashboard Optimizado
@Serializable
data class DashboardData(
    @SerialName("tipo_metrica")
    val tipoMetrica: String,
    val valor: kotlinx.serialization.json.JsonElement
)
// NUEVO MODELO Estadísticas Generales (desde vista materializada)
@Serializable
data class EstadisticasGenerales(
    @SerialName("total_reportes")
    val totalReportes: Int,
    @SerialName("total_activos")
    val totalActivos: Int,
    @SerialName("score_promedio_global")
    val scorePromedioGlobal: Float,
    @SerialName("reportes_con_problemas")
    val reportesConProblemas: Int,
    @SerialName("porcentaje_con_problemas")
    val porcentajeConProblemas: Float,
    @SerialName("horas_uso_total")
    val horasUsoTotal: Float
)

// NUEVO MODELO Top Problemas
@Serializable
data class TopProblema(
    @SerialName("pregunta_id")
    val preguntaId: Int,
    @SerialName("texto_pregunta")
    val textoPregunta: String,
    @SerialName("total_respuestas")
    val totalRespuestas: Int,
    @SerialName("total_fallo")
    val totalFallo: Int,
    @SerialName("porcentaje_fallo")
    val porcentajeFallo: Float
)