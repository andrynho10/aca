package com.tulsa.aca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tulsa.aca.data.models.PlantillaChecklist

/**
 * Entidad Room para cache local de plantillas de checklist
 */
@Entity(tableName = "plantillas_cache")
data class PlantillaEntity(
    @PrimaryKey
    val id: Int,
    val nombre: String,
    val tipoActivo: String,
    val activa: Boolean,
    val createdAt: String?,
    val lastSyncTimestamp: Long
) {
    fun toPlantilla(): PlantillaChecklist {
        return PlantillaChecklist(
            id = id,
            nombre = nombre,
            tipoActivo = tipoActivo,
            activa = activa,
            createdAt = createdAt,
            categorias = emptyList() // Las categorías se cargan por separado
        )
    }

    companion object {
        fun fromPlantilla(plantilla: PlantillaChecklist, syncTimestamp: Long = System.currentTimeMillis()): PlantillaEntity {
            return PlantillaEntity(
                id = plantilla.id,
                nombre = plantilla.nombre,
                tipoActivo = plantilla.tipoActivo,
                activa = plantilla.activa,
                createdAt = plantilla.createdAt,
                lastSyncTimestamp = syncTimestamp
            )
        }
    }
}

/**
 * Entidad Room para categorías de plantillas
 */
@Entity(
    tableName = "categorias_cache"
)
data class CategoriaEntity(
    @PrimaryKey
    val id: Int,
    val plantillaId: Int,
    val nombre: String,
    val orden: Int,
    val createdAt: String?,
    val lastSyncTimestamp: Long
)

/**
 * Entidad Room para preguntas de plantillas
 */
@Entity(
    tableName = "preguntas_cache"
)
data class PreguntaEntity(
    @PrimaryKey
    val id: Int,
    val categoriaId: Int,
    val texto: String,
    val tipoRespuesta: String,
    val orden: Int,
    val createdAt: String?,
    val lastSyncTimestamp: Long
)
