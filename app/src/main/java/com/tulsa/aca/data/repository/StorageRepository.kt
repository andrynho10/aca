package com.tulsa.aca.data.repository

import android.content.Context
import android.net.Uri
import com.tulsa.aca.data.supabase.SupabaseClient
import com.tulsa.aca.utils.ImageCompressor
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class StorageRepository {
    private val client = SupabaseClient.client
    private val storage = client.storage

    /**
     * Sube una foto comprimida a Supabase Storage
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
            // Log del tamaño original
            val originalSizeKB = ImageCompressor.getFileSizeKB(context, photoUri)
            android.util.Log.d("StorageRepository", "Tamaño original: ${originalSizeKB}KB")

            // 1. Comprimir la imagen antes de subir
            val compressedUri = ImageCompressor.compressImage(
                context = context,
                imageUri = photoUri,
                maxWidth = 1024,    // Máximo 1024px de ancho
                maxHeight = 1024,   // Máximo 1024px de alto
                quality = 85        // Calidad JPEG 85%
            )

            if (compressedUri == null) {
                android.util.Log.e("StorageRepository", "Error al comprimir imagen")
                return@withContext null
            }

            // Log del tamaño comprimido
            val compressedSizeKB = ImageCompressor.getFileSizeKB(context, compressedUri)
            val reductionPercent = ((originalSizeKB - compressedSizeKB) * 100 / originalSizeKB).toInt()
            android.util.Log.d("StorageRepository",
                "Tamaño comprimido: ${compressedSizeKB}KB (reducción: ${reductionPercent}%)")

            // 2. Generar nombre único para la foto
            val fileName = "foto_${reporteId}_${preguntaId}_${UUID.randomUUID()}.jpg"

            // 3. Crear el path en el bucket: reportes/reporteId/preguntaId/fileName
            val path = "reportes/$reporteId/$preguntaId/$fileName"

            // 4. Leer los bytes de la foto comprimida
            val inputStream: InputStream? = context.contentResolver.openInputStream(compressedUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                // 5. Subir la foto al bucket 'fotos-reportes'
                storage.from("fotos-reportes").upload(path, bytes)

                // 6. Obtener la URL pública
                val publicUrl = storage.from("fotos-reportes").publicUrl(path)

                android.util.Log.d("StorageRepository", "Foto subida exitosamente: $publicUrl")
                publicUrl
            } else {
                android.util.Log.e("StorageRepository", "No se pudieron leer los bytes de la imagen comprimida")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("StorageRepository", "Error al subir foto: ${e.message}", e)
            null
        }
    }

    /**
     * Sube múltiples fotos comprimidas y devuelve las URLs
     */
    suspend fun subirFotos(
        context: Context,
        fotos: List<Uri>,
        reporteId: String,
        preguntaId: Int
    ): List<String> = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()

        android.util.Log.d("StorageRepository", "Subiendo ${fotos.size} fotos para pregunta $preguntaId")

        fotos.forEachIndexed { index, fotoUri ->
            android.util.Log.d("StorageRepository", "Subiendo foto ${index + 1}/${fotos.size}")
            val url = subirFoto(context, fotoUri, reporteId, preguntaId)
            url?.let { urls.add(it) }
        }

        android.util.Log.d("StorageRepository", "Subidas completadas: ${urls.size}/${fotos.size} fotos")
        urls
    }
}