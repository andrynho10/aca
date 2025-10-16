package com.tulsa.aca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad para guardar borradores de checklist en progreso
 * Se auto-guarda cada 10 segundos mientras el usuario está completando el checklist
 * Se elimina después de 24 horas sin uso o cuando se completa y envía el checklist
 */
@Entity(tableName = "drafts_checklist")
data class DraftChecklistEntity(
    @PrimaryKey
    val id: String, // UUID único del draft (mismo formato que reporte)
    val activoId: Int,
    val usuarioId: String,
    val plantillaId: Int,

    // JSON serializado de las respuestas parciales
    val respuestasJson: String,

    // Información de horómetro y turno (opcional)
    val horometroInicial: Float? = null,
    val turno: Int? = null,

    // Timestamps de inicio del checklist
    val timestampInicio: String, // ISO 8601 en UTC

    // Timestamps de auto-guardado
    val ultimoGuardado: Long = System.currentTimeMillis(), // Para limpieza de drafts antiguos
    val createdAt: Long = System.currentTimeMillis()
)
