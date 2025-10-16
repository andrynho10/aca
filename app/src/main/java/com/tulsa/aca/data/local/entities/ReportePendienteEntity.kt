package com.tulsa.aca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad para almacenar reportes que no se han podido sincronizar con el servidor
 * Estos reportes se crean en modo offline y se sincronizan cuando hay conexión
 */
@Entity(tableName = "reportes_pendientes")
data class ReportePendienteEntity(
    @PrimaryKey
    val id: String, // UUID del reporte
    val activoId: Int,
    val usuarioId: String,
    val plantillaId: Int,
    val timestampInicio: String,
    val timestampCompletado: String,
    val duracionMinutos: Int,
    val horometroInicial: Float?,
    val turno: Int?,

    // JSON serializado de las respuestas
    val respuestasJson: String,

    // Estado de sincronización
    val intentosSincronizacion: Int = 0,
    val ultimoIntento: Long? = null,
    val errorSincronizacion: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entidad para almacenar fotos que no se han podido subir
 */
@Entity(tableName = "fotos_pendientes")
data class FotoPendienteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reporteId: String, // UUID del reporte local
    val respuestaIndex: Int, // Índice de la respuesta en el JSON
    val localUri: String, // URI local de la foto
    val preguntaId: Int,
    val subida: Boolean = false,
    val urlStorage: String? = null, // URL una vez subida
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entidad para rastrear cambios locales en activos que deben sincronizarse
 */
@Entity(tableName = "cambios_pendientes")
data class CambioPendienteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tipoEntidad: String, // "activo", "plantilla", etc.
    val entidadId: String,
    val operacion: String, // "INSERT", "UPDATE", "DELETE"
    val datosJson: String, // Datos serializados de la entidad
    val intentosSincronizacion: Int = 0,
    val ultimoIntento: Long? = null,
    val errorSincronizacion: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entidad para almacenar el estado de sincronización general
 */
@Entity(tableName = "sync_status")
data class SyncStatusEntity(
    @PrimaryKey
    val entidad: String, // "activos", "plantillas", etc.
    val ultimaSincronizacion: Long,
    val sincronizacionExitosa: Boolean,
    val mensajeError: String?
)
