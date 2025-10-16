package com.tulsa.aca.data.local.dao

import androidx.room.*
import com.tulsa.aca.data.local.entities.SyncStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para rastrear el estado de sincronización de diferentes entidades
 */
@Dao
interface SyncStatusDao {

    @Query("SELECT * FROM sync_status WHERE entidad = :entidad")
    suspend fun getSyncStatus(entidad: String): SyncStatusEntity?

    @Query("SELECT * FROM sync_status WHERE entidad = :entidad")
    fun getSyncStatusFlow(entidad: String): Flow<SyncStatusEntity?>

    @Query("SELECT * FROM sync_status")
    suspend fun getAllSyncStatus(): List<SyncStatusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncStatus(status: SyncStatusEntity)

    @Update
    suspend fun updateSyncStatus(status: SyncStatusEntity)

    @Query("UPDATE sync_status SET ultimaSincronizacion = :timestamp, sincronizacionExitosa = :exitosa, mensajeError = :error WHERE entidad = :entidad")
    suspend fun updateSyncTimestamp(
        entidad: String,
        timestamp: Long,
        exitosa: Boolean,
        error: String?
    )

    @Delete
    suspend fun deleteSyncStatus(status: SyncStatusEntity)

    @Query("DELETE FROM sync_status WHERE entidad = :entidad")
    suspend fun deleteSyncStatusByEntidad(entidad: String)

    @Query("DELETE FROM sync_status")
    suspend fun deleteAll()

    /**
     * Verifica si necesita sincronizar (última sync hace más de X milisegundos)
     */
    @Query("SELECT * FROM sync_status WHERE entidad = :entidad AND (ultimaSincronizacion + :maxAge) < :currentTime")
    suspend fun needsSync(entidad: String, maxAge: Long, currentTime: Long): SyncStatusEntity?
}
