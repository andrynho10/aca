package com.tulsa.aca.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min
import androidx.core.graphics.scale

object ImageCompressor {

    /**
     * Comprime una imagen y la guarda en un archivo temporal
     * @param context Contexto de la aplicación
     * @param imageUri URI de la imagen original
     * @param maxWidth Ancho máximo en píxeles (default: 1024)
     * @param maxHeight Alto máximo en píxeles (default: 1024)
     * @param quality Calidad JPEG (0-100, default: 85)
     * @return Uri del archivo comprimido o null si hay error
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024,
        quality: Int = 85
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // 1. Leer la imagen original
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return@withContext null

            // 2. Obtener orientación EXIF para rotación correcta
            val rotatedBitmap = rotateImageIfRequired(context, originalBitmap, imageUri)

            // 3. Redimensionar si es necesario
            val resizedBitmap = resizeBitmap(rotatedBitmap, maxWidth, maxHeight)

            // 4. Comprimir a JPEG
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val compressedBytes = outputStream.toByteArray()
            outputStream.close()

            // 5. Guardar en archivo temporal
            val tempFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            val fileOutputStream = FileOutputStream(tempFile)
            fileOutputStream.write(compressedBytes)
            fileOutputStream.close()

            // 6. Limpiar memoria
            if (rotatedBitmap != originalBitmap) {
                rotatedBitmap.recycle()
            }
            originalBitmap.recycle()
            resizedBitmap.recycle()

            // 7. Retornar URI del archivo comprimido
            Uri.fromFile(tempFile)

        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error al comprimir imagen: ${e.message}", e)
            null
        }
    }

    /**
     * Rota la imagen según la información EXIF
     */
    private fun rotateImageIfRequired(context: Context, bitmap: Bitmap, imageUri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val exif = inputStream?.let { ExifInterface(it) }
            inputStream?.close()

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error al rotar imagen: ${e.message}")
            bitmap
        }
    }

    /**
     * Rota un bitmap por los grados especificados
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Redimensiona un bitmap manteniendo la proporción
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Si ya es menor que el máximo, no redimensionar
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        // Calcular nueva escala manteniendo proporción
        val scaleWidth = maxWidth.toFloat() / width
        val scaleHeight = maxHeight.toFloat() / height
        val scale = min(scaleWidth, scaleHeight)

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    /**
     * Obtiene el tamaño de un archivo en KB
     */
    fun getFileSizeKB(context: Context, uri: Uri): Long {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.available()?.toLong() ?: 0L
            inputStream?.close()
            bytes / 1024 // Convertir a KB
        } catch (e: Exception) {
            0L
        }
    }
}