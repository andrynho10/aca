package com.tulsa.aca.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Representa un activo físico (maquinaria)
@Serializable
data class Activo(
    val id: Int? = null,
    val nombre: String,
    val modelo: String,
    val tipo: String, // "Montacargas", "Grúa Puente", etc.
    @SerialName("codigo_qr")
    val codigoQr: String,
    @SerialName("created_at")
    val createdAt: String? = null
)

// Representa a un usuario de la aplicación
@Serializable
data class Usuario(
    val id: String,
    @SerialName("nombre_completo")
    val nombreCompleto: String,
    val rol: String // "OPERARIO" o "SUPERVISOR"
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
    val nombre: String, // "Inspección Diaria Pre-Turno"
    @SerialName("tipo_activo")
    val tipoActivo: String, // "Montacargas"
    val activa: Boolean = true,
    @SerialName("created_at")
    val createdAt: String? = null,
    // Lista de categorías (se cargará por separado)
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
    val respuesta: Boolean, // true = SÍ, false = NO
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
    @SerialName("timestamp_completado")
    val timestampCompletado: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
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