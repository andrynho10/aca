package com.tulsa.aca.data.repository

import android.content.Context
import android.net.Uri
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class StorageRepository {
    private val client = SupabaseClient.client
    private val storage = client.storage

    /**
     * Sube una foto a Supabase Storage
     * @param context Contexto de la aplicación
     * @param photoUri URI local de la foto
     * @param reporteId ID del reporte para organizar las fotos
     * @param preguntaId ID de la pregunta para mayor organización
     * @return URL pública de la foto subida o null si hay error
     */
    suspend fun subirFoto(
        context: Context,
        photoUri: Uri,
        reporteId: String,
        preguntaId: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Generar nombre único para la foto
            val fileName = "foto_${reporteId}_${preguntaId}_${UUID.randomUUID()}.jpg"

            // Crear el path en el bucket: reportes/reporteId/preguntaId/fileName
            val path = "reportes/$reporteId/$preguntaId/$fileName"

            // Leer los bytes de la foto
            val inputStream: InputStream? = context.contentResolver.openInputStream(photoUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                // Subir la foto al bucket 'fotos-reportes'
                storage.from("fotos-reportes").upload(path, bytes)

                // Obtener la URL pública
                val publicUrl = storage.from("fotos-reportes").publicUrl(path)
                publicUrl
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("StorageRepository", "Error al subir foto: ${e.message}", e)
            null
        }
    }

    /**
     * Sube múltiples fotos y devuelve las URLs
     */
    suspend fun subirFotos(
        context: Context,
        fotos: List<Uri>,
        reporteId: String,
        preguntaId: Int
    ): List<String> = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()

        fotos.forEach { fotoUri ->
            val url = subirFoto(context, fotoUri, reporteId, preguntaId)
            url?.let { urls.add(it) }
        }

        urls
    }
}