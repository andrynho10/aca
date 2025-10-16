package com.tulsa.aca.data.local.dao

import androidx.room.*
import com.tulsa.aca.data.local.entities.ActivoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acceso a datos de activos en cache local
 */
@Dao
interface ActivoDao {

    @Query("SELECT * FROM activos_cache ORDER BY nombre ASC")
    suspend fun getAllActivos(): List<ActivoEntity>

    @Query("SELECT * FROM activos_cache ORDER BY nombre ASC")
    fun getAllActivosFlow(): Flow<List<ActivoEntity>>

    @Query("SELECT * FROM activos_cache WHERE id = :id")
    suspend fun getActivoById(id: Int): ActivoEntity?

    @Query("SELECT * FROM activos_cache WHERE codigoQr = :codigoQr LIMIT 1")
    suspend fun getActivoByQR(codigoQr: String): ActivoEntity?

    @Query("SELECT * FROM activos_cache WHERE tipo LIKE '%' || :tipo || '%'")
    suspend fun getActivosByTipo(tipo: String): List<ActivoEntity>

    @Query("SELECT * FROM activos_cache WHERE nombre LIKE '%' || :nombre || '%'")
    suspend fun getActivosByNombre(nombre: String): List<ActivoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivo(activo: ActivoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivos(activos: List<ActivoEntity>)

    @Update
    suspend fun updateActivo(activo: ActivoEntity)

    @Delete
    suspend fun deleteActivo(activo: ActivoEntity)

    @Query("DELETE FROM activos_cache WHERE id = :id")
    suspend fun deleteActivoById(id: Int)

    @Query("DELETE FROM activos_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM activos_cache")
    suspend fun getCount(): Int

    @Query("SELECT * FROM activos_cache WHERE lastSyncTimestamp < :timestamp")
    suspend fun getActivosOutdated(timestamp: Long): List<ActivoEntity>
}
