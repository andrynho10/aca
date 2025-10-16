package com.tulsa.aca.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tulsa.aca.data.local.AppDatabase
import com.tulsa.aca.data.local.dao.DraftChecklistDao
import com.tulsa.aca.data.local.entities.DraftChecklistEntity
import com.tulsa.aca.viewmodel.RespuestaChecklistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*

/**
 * Repositorio para manejar drafts (borradores) de checklist
 * Permite auto-guardar, recuperar y limpiar drafts automáticamente
 */
class DraftChecklistRepository(context: Context) {

    private val dao: DraftChecklistDao = AppDatabase.getDatabase(context).draftChecklistDao()
    private val gson = Gson()

    companion object {
        private const val TAG = "DraftChecklistRepo"
        private const val MAX_DRAFT_AGE_MS = 24 * 60 * 60 * 1000L // 24 horas
    }

    /**
     * Guarda un draft del checklist en progreso
     */
    suspend fun guardarDraft(
        draftId: String,
        activoId: Int,
        usuarioId: String,
        plantillaId: Int,
        respuestas: Map<Int, RespuestaChecklistItem>,
        timestampInicio: String,
        horometroInicial: Float? = null,
        turno: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Guardando draft: $draftId")

            // Convertir respuestas a JSON
            val respuestasSerializadas = respuestas.mapValues { (_, item) ->
                mapOf(
                    "preguntaId" to item.preguntaId,
                    "respuesta" to item.respuesta,
                    "comentario" to item.comentario,
                    "fotos" to item.fotos.map { it.toString() }
                )
            }

            val respuestasJson = gson.toJson(respuestasSerializadas)

            val draft = DraftChecklistEntity(
                id = draftId,
                activoId = activoId,
                usuarioId = usuarioId,
                plantillaId = plantillaId,
                respuestasJson = respuestasJson,
                horometroInicial = horometroInicial,
                turno = turno,
                timestampInicio = timestampInicio,
                ultimoGuardado = System.currentTimeMillis()
            )

            dao.saveDraft(draft)

            Log.d(TAG, "✅ Draft guardado exitosamente")
            Result.success(draftId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando draft: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene el draft más reciente para un usuario, activo y plantilla
     */
    suspend fun obtenerDraftReciente(
        usuarioId: String,
        activoId: Int,
        plantillaId: Int
    ): DraftChecklistData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Buscando draft para usuario=$usuarioId, activo=$activoId, plantilla=$plantillaId")

            val draft = dao.getLatestDraft(usuarioId, activoId, plantillaId)

            if (draft == null) {
                Log.d(TAG, "❌ No se encontró draft")
                return@withContext null
            }

            // Verificar que no sea muy antiguo
            val edad = System.currentTimeMillis() - draft.ultimoGuardado
            if (edad > MAX_DRAFT_AGE_MS) {
                Log.d(TAG, "⏰ Draft muy antiguo (${edad / 1000 / 60} minutos), eliminando...")
                dao.deleteDraft(draft)
                return@withContext null
            }

            Log.d(TAG, "✅ Draft encontrado: ${draft.id}")

            // Deserializar respuestas
            val respuestasMap = deserializarRespuestas(draft.respuestasJson)

            DraftChecklistData(
                draftId = draft.id,
                activoId = draft.activoId,
                usuarioId = draft.usuarioId,
                plantillaId = draft.plantillaId,
                respuestas = respuestasMap,
                timestampInicio = draft.timestampInicio,
                horometroInicial = draft.horometroInicial,
                turno = draft.turno,
                ultimoGuardado = draft.ultimoGuardado
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo draft: ${e.message}", e)
            null
        }
    }

    /**
     * Elimina un draft específico por ID
     */
    suspend fun eliminarDraft(draftId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Eliminando draft: $draftId")
            dao.deleteDraftById(draftId)
            Log.d(TAG, "✅ Draft eliminado")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error eliminando draft: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Elimina todos los drafts para un usuario, activo y plantilla
     */
    suspend fun eliminarDraftsPorPlantilla(
        usuarioId: String,
        activoId: Int,
        plantillaId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Eliminando drafts para usuario=$usuarioId, activo=$activoId, plantilla=$plantillaId")
            dao.deleteDraftsByTemplate(usuarioId, activoId, plantillaId)
            Log.d(TAG, "✅ Drafts eliminados")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error eliminando drafts: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Limpia drafts antiguos (más de 24 horas)
     */
    suspend fun limpiarDraftsAntiguos(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val timestampLimite = System.currentTimeMillis() - MAX_DRAFT_AGE_MS

            Log.d(TAG, "🧹 Limpiando drafts antiguos (antes de ${Date(timestampLimite)})")

            val draftsAntiguos = dao.getAllDrafts().filter { it.ultimoGuardado < timestampLimite }
            val count = draftsAntiguos.size

            dao.deleteOldDrafts(timestampLimite)

            Log.d(TAG, "✅ $count drafts antiguos eliminados")
            Result.success(count)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error limpiando drafts antiguos: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Deserializa el JSON de respuestas a un Map de RespuestaChecklistItem
     */
    private fun deserializarRespuestas(json: String): Map<Int, RespuestaChecklistItem> {
        try {
            val type = object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
            val data: Map<String, Map<String, Any?>> = gson.fromJson(json, type)

            return data.mapKeys { it.key.toInt() }.mapValues { (_, value) ->
                val preguntaId = (value["preguntaId"] as? Double)?.toInt() ?: 0
                val respuesta = value["respuesta"] as? Boolean
                val comentario = value["comentario"] as? String ?: ""
                val fotosStrings = (value["fotos"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val fotos = fotosStrings.map { Uri.parse(it) }

                RespuestaChecklistItem(
                    preguntaId = preguntaId,
                    respuesta = respuesta,
                    comentario = comentario,
                    fotos = fotos
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deserializando respuestas: ${e.message}", e)
            return emptyMap()
        }
    }
}

/**
 * Datos de un draft recuperado
 */
data class DraftChecklistData(
    val draftId: String,
    val activoId: Int,
    val usuarioId: String,
    val plantillaId: Int,
    val respuestas: Map<Int, RespuestaChecklistItem>,
    val timestampInicio: String,
    val horometroInicial: Float?,
    val turno: Int?,
    val ultimoGuardado: Long
)
