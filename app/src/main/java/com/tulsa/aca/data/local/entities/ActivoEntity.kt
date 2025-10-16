package com.tulsa.aca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tulsa.aca.data.models.Activo

/**
 * Entidad Room para cache local de activos
 */
@Entity(tableName = "activos_cache")
data class ActivoEntity(
    @PrimaryKey
    val id: Int,
    val nombre: String,
    val modelo: String,
    val tipo: String,
    val codigoQr: String,
    val createdAt: String?,
    val esOperativa: Boolean,
    val esStandby: Boolean,
    val horometroActual: Float?,
    val lastSyncTimestamp: Long // Timestamp de última sincronización
) {
    /**
     * Convierte la entidad Room a modelo de dominio
     */
    fun toActivo(): Activo {
        return Activo(
            id = id,
            nombre = nombre,
            modelo = modelo,
            tipo = tipo,
            codigoQr = codigoQr,
            createdAt = createdAt,
            esOperativa = esOperativa,
            esStandby = esStandby,
            horometroActual = horometroActual
        )
    }

    companion object {
        /**
         * Crea una entidad Room desde un modelo de dominio
         */
        fun fromActivo(activo: Activo, syncTimestamp: Long = System.currentTimeMillis()): ActivoEntity? {
            return activo.id?.let { id ->
                ActivoEntity(
                    id = id,
                    nombre = activo.nombre,
                    modelo = activo.modelo,
                    tipo = activo.tipo,
                    codigoQr = activo.codigoQr,
                    createdAt = activo.createdAt,
                    esOperativa = activo.esOperativa,
                    esStandby = activo.esStandby,
                    horometroActual = activo.horometroActual,
                    lastSyncTimestamp = syncTimestamp
                )
            }
        }
    }
}
