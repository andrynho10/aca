package com.tulsa.aca.data.local.dao

import androidx.room.*
import com.tulsa.aca.data.local.entities.ReportePendienteEntity
import com.tulsa.aca.data.local.entities.FotoPendienteEntity
import com.tulsa.aca.data.local.entities.CambioPendienteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para manejar la cola de sincronización de reportes offline
 */
@Dao
interface ReportePendienteDao {

    // ===== REPORTES PENDIENTES =====
    @Query("SELECT * FROM reportes_pendientes ORDER BY createdAt ASC")
    suspend fun getAllReportesPendientes(): List<ReportePendienteEntity>

    @Query("SELECT * FROM reportes_pendientes ORDER BY createdAt ASC")
    fun getAllReportesPendientesFlow(): Flow<List<ReportePendienteEntity>>

    @Query("SELECT COUNT(*) FROM reportes_pendientes")
    suspend fun getReportesPendientesCount(): Int

    @Query("SELECT COUNT(*) FROM reportes_pendientes")
    fun getReportesPendientesCountFlow(): Flow<Int>

    @Query("SELECT * FROM reportes_pendientes WHERE id = :reporteId")
    suspend fun getReportePendiente(reporteId: String): ReportePendienteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReportePendiente(reporte: ReportePendienteEntity)

    @Update
    suspend fun updateReportePendiente(reporte: ReportePendienteEntity)

    @Delete
    suspend fun deleteReportePendiente(reporte: ReportePendienteEntity)

    @Query("DELETE FROM reportes_pendientes WHERE id = :reporteId")
    suspend fun deleteReportePendienteById(reporteId: String)

    @Query("UPDATE reportes_pendientes SET intentosSincronizacion = intentosSincronizacion + 1, ultimoIntento = :timestamp, errorSincronizacion = :error WHERE id = :reporteId")
    suspend fun registrarIntentoFallido(reporteId: String, timestamp: Long, error: String)

    // ===== FOTOS PENDIENTES =====
    @Query("SELECT * FROM fotos_pendientes WHERE reporteId = :reporteId")
    suspend fun getFotosPendientesByReporte(reporteId: String): List<FotoPendienteEntity>

    @Query("SELECT * FROM fotos_pendientes WHERE subida = 0 ORDER BY createdAt ASC")
    suspend fun getFotosPendientesNoSubidas(): List<FotoPendienteEntity>

    @Query("SELECT COUNT(*) FROM fotos_pendientes WHERE subida = 0")
    suspend fun getFotosPendientesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFotoPendiente(foto: FotoPendienteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFotosPendientes(fotos: List<FotoPendienteEntity>)

    @Update
    suspend fun updateFotoPendiente(foto: FotoPendienteEntity)

    @Query("UPDATE fotos_pendientes SET subida = 1, urlStorage = :urlStorage WHERE id = :fotoId")
    suspend fun marcarFotoComoSubida(fotoId: Long, urlStorage: String)

    @Delete
    suspend fun deleteFotoPendiente(foto: FotoPendienteEntity)

    @Query("DELETE FROM fotos_pendientes WHERE reporteId = :reporteId")
    suspend fun deleteFotosPendientesByReporte(reporteId: String)

    // ===== CAMBIOS PENDIENTES =====
    @Query("SELECT * FROM cambios_pendientes ORDER BY createdAt ASC")
    suspend fun getAllCambiosPendientes(): List<CambioPendienteEntity>

    @Query("SELECT * FROM cambios_pendientes WHERE tipoEntidad = :tipo ORDER BY createdAt ASC")
    suspend fun getCambiosPendientesByTipo(tipo: String): List<CambioPendienteEntity>

    @Query("SELECT COUNT(*) FROM cambios_pendientes")
    suspend fun getCambiosPendientesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCambioPendiente(cambio: CambioPendienteEntity)

    @Update
    suspend fun updateCambioPendiente(cambio: CambioPendienteEntity)

    @Delete
    suspend fun deleteCambioPendiente(cambio: CambioPendienteEntity)

    @Query("DELETE FROM cambios_pendientes WHERE id = :id")
    suspend fun deleteCambioPendienteById(id: Long)

    @Query("UPDATE cambios_pendientes SET intentosSincronizacion = intentosSincronizacion + 1, ultimoIntento = :timestamp, errorSincronizacion = :error WHERE id = :cambioId")
    suspend fun registrarIntentoCambioFallido(cambioId: Long, timestamp: Long, error: String)
}
