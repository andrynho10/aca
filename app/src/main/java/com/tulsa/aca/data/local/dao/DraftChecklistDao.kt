package com.tulsa.aca.data.local.dao

import androidx.room.*
import com.tulsa.aca.data.local.entities.DraftChecklistEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para manejar borradores de checklist
 * Permite guardar, cargar y limpiar drafts automáticamente
 */
@Dao
interface DraftChecklistDao {

    /**
     * Obtiene un draft específico por ID
     */
    @Query("SELECT * FROM drafts_checklist WHERE id = :draftId")
    suspend fun getDraftById(draftId: String): DraftChecklistEntity?

    /**
     * Obtiene todos los drafts para un usuario y activo específico
     */
    @Query("SELECT * FROM drafts_checklist WHERE usuarioId = :usuarioId AND activoId = :activoId ORDER BY ultimoGuardado DESC")
    suspend fun getDraftsByUserAndAsset(usuarioId: String, activoId: Int): List<DraftChecklistEntity>

    /**
     * Obtiene el draft más reciente para un usuario, activo y plantilla específica
     */
    @Query("SELECT * FROM drafts_checklist WHERE usuarioId = :usuarioId AND activoId = :activoId AND plantillaId = :plantillaId ORDER BY ultimoGuardado DESC LIMIT 1")
    suspend fun getLatestDraft(usuarioId: String, activoId: Int, plantillaId: Int): DraftChecklistEntity?

    /**
     * Obtiene todos los drafts (para limpieza)
     */
    @Query("SELECT * FROM drafts_checklist")
    suspend fun getAllDrafts(): List<DraftChecklistEntity>

    /**
     * Verifica si existe un draft para un usuario, activo y plantilla
     */
    @Query("SELECT COUNT(*) > 0 FROM drafts_checklist WHERE usuarioId = :usuarioId AND activoId = :activoId AND plantillaId = :plantillaId")
    suspend fun existsDraft(usuarioId: String, activoId: Int, plantillaId: Int): Boolean

    /**
     * Inserta o actualiza un draft (auto-guardado cada 10 segundos)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftChecklistEntity)

    /**
     * Actualiza solo los campos mutables de un draft (respuestas, timestamps)
     */
    @Update
    suspend fun updateDraft(draft: DraftChecklistEntity)

    /**
     * Elimina un draft específico
     */
    @Delete
    suspend fun deleteDraft(draft: DraftChecklistEntity)

    /**
     * Elimina un draft por ID
     */
    @Query("DELETE FROM drafts_checklist WHERE id = :draftId")
    suspend fun deleteDraftById(draftId: String)

    /**
     * Elimina todos los drafts antiguos (más de 24 horas)
     * Utiliza System.currentTimeMillis() - 86400000ms (24 horas)
     */
    @Query("DELETE FROM drafts_checklist WHERE ultimoGuardado < :timestampLimite")
    suspend fun deleteOldDrafts(timestampLimite: Long)

    /**
     * Elimina todos los drafts para una plantilla específica
     * (útil después de completar y enviar un checklist)
     */
    @Query("DELETE FROM drafts_checklist WHERE usuarioId = :usuarioId AND activoId = :activoId AND plantillaId = :plantillaId")
    suspend fun deleteDraftsByTemplate(usuarioId: String, activoId: Int, plantillaId: Int)

    /**
     * Obtiene el recuento de drafts
     */
    @Query("SELECT COUNT(*) FROM drafts_checklist")
    suspend fun getDraftCount(): Int

    /**
     * Observa si existe un draft para un usuario, activo y plantilla (en Flow)
     */
    @Query("SELECT COUNT(*) > 0 FROM drafts_checklist WHERE usuarioId = :usuarioId AND activoId = :activoId AND plantillaId = :plantillaId")
    fun existsDraftFlow(usuarioId: String, activoId: Int, plantillaId: Int): Flow<Boolean>
}
