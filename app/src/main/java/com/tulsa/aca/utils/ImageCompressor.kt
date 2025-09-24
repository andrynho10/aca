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

/**
 * IMAGECOMPRESSOR OPTIMIZADO SIN MEMORY LEAKS
 * - Elimina bitmap.recycle() deprecated y peligroso
 * - Mejor manejo de memoria y recursos
 * - Thread-safe y eficiente
 */
object ImageCompressor {

    // CONFIGURACIÓN CENTRALIZADA
    private const val DEFAULT_MAX_WIDTH = 1024
    private const val DEFAULT_MAX_HEIGHT = 1024
    private const val DEFAULT_QUALITY = 85
    private const val MIN_QUALITY = 10
    private const val MAX_QUALITY = 100

    /**
     * MÉTODO PRINCIPAL - Comprime imagen sin bitmap.recycle()
     * @param context Contexto de la aplicación
     * @param imageUri URI de la imagen original
     * @param maxWidth Ancho máximo en píxeles
     * @param maxHeight Alto máximo en píxeles
     * @param quality Calidad JPEG (10-100)
     * @return Uri del archivo comprimido o null si hay error
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxHeight: Int = DEFAULT_MAX_HEIGHT,
        quality: Int = DEFAULT_QUALITY
    ): Uri? = withContext(Dispatchers.IO) {

        // VALIDACIÓN DE PARÁMETROS
        val validQuality = quality.coerceIn(MIN_QUALITY, MAX_QUALITY)
        val validMaxWidth = maxWidth.coerceAtLeast(100)
        val validMaxHeight = maxHeight.coerceAtLeast(100)

        android.util.Log.d("ImageCompressor", "Iniciando compresión: ${validMaxWidth}x${validMaxHeight}, calidad: $validQuality%")

        try {
            // 1. LEER IMAGEN ORIGINAL CON MEJOR MANEJO DE RECURSOS
            val originalBitmap = loadBitmapSafely(context, imageUri)
                ?: return@withContext null.also {
                    android.util.Log.e("ImageCompressor", "No se pudo cargar la imagen original")
                }

            android.util.Log.d("ImageCompressor", "Imagen cargada: ${originalBitmap.width}x${originalBitmap.height}")

            // 2. ROTAR SI ES NECESARIO (SIN RECYCLE)
            val rotatedBitmap = rotateImageIfRequired(context, originalBitmap, imageUri)

            // 3. REDIMENSIONAR SI ES NECESARIO
            val finalBitmap = resizeBitmapIfNeeded(rotatedBitmap, validMaxWidth, validMaxHeight)

            // 4. COMPRIMIR Y GUARDAR
            val resultUri = compressAndSaveBitmap(context, finalBitmap, validQuality)

            // 5. LOG DE RESULTADOS
            if (resultUri != null) {
                val originalSize = getFileSizeKB(context, imageUri)
                val compressedSize = getFileSizeKB(context, resultUri)
                val reduccion = if (originalSize > 0) ((originalSize - compressedSize) * 100 / originalSize) else 0

                android.util.Log.d("ImageCompressor", "Compresión exitosa: ${originalSize}KB → ${compressedSize}KB (${reduccion}% reducción)")
            }

            return@withContext resultUri

        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageCompressor", "OutOfMemoryError durante compresión: ${e.message}")
            // Forzar garbage collection
            System.gc()
            null
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error durante compresión: ${e.message}", e)
            null
        }
    }

    /**
     * CARGA BITMAP DE FORMA SEGURA SIN BITMAP.RECYCLE()
     */
    private fun loadBitmapSafely(context: Context, imageUri: Uri): Bitmap? {
        return try {
            // USAR USE PARA MANEJO AUTOMÁTICO DE RECURSOS
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->

                // OPCIONES PARA OPTIMIZACIÓN DE MEMORIA
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                // Primer pase: obtener dimensiones sin cargar el bitmap
                BitmapFactory.decodeStream(inputStream, null, options)

                // CALCULAR SAMPLE SIZE PARA EVITAR OutOfMemoryError
                val sampleSize = calculateSampleSize(options, DEFAULT_MAX_WIDTH, DEFAULT_MAX_HEIGHT)

                // Segundo pase: cargar bitmap con sample size óptimo
                context.contentResolver.openInputStream(imageUri)?.use { secondInputStream ->
                    val loadOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inJustDecodeBounds = false
                        inPreferredConfig = Bitmap.Config.RGB_565 // Menos memoria que ARGB_8888
                    }

                    BitmapFactory.decodeStream(secondInputStream, null, loadOptions)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error cargando bitmap: ${e.message}")
            null
        }
    }

    /**
     * CALCULA SAMPLE SIZE ÓPTIMO PARA EVITAR OOM
     */
    private fun calculateSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        android.util.Log.d("ImageCompressor", "Sample size calculado: $inSampleSize para ${width}x${height}")
        return inSampleSize
    }

    /**
     * ROTA IMAGEN SIN BITMAP.RECYCLE()
     */
    private fun rotateImageIfRequired(context: Context, bitmap: Bitmap, imageUri: Uri): Bitmap {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> {
                        android.util.Log.d("ImageCompressor", "Rotando imagen 90°")
                        rotateBitmap(bitmap, 90f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_180 -> {
                        android.util.Log.d("ImageCompressor", "Rotando imagen 180°")
                        rotateBitmap(bitmap, 180f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> {
                        android.util.Log.d("ImageCompressor", "Rotando imagen 270°")
                        rotateBitmap(bitmap, 270f)
                    }
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error leyendo EXIF: ${e.message}")
            bitmap
        }
    }

    /**
     * ROTA BITMAP USANDO MATRIX
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageCompressor", "OOM rotando bitmap, devolviendo original")
            bitmap
        }
    }

    /**
     * REDIMENSIONA SOLO SI ES NECESARIO
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // SI YA ES PEQUEÑO, NO REDIMENSIONAR
        if (width <= maxWidth && height <= maxHeight) {
            android.util.Log.d("ImageCompressor", "Imagen ya tiene tamaño adecuado: ${width}x${height}")
            return bitmap
        }

        // CALCULAR ESCALA MANTENIENDO PROPORCIÓN
        val scaleWidth = maxWidth.toFloat() / width
        val scaleHeight = maxHeight.toFloat() / height
        val scale = min(scaleWidth, scaleHeight)

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        android.util.Log.d("ImageCompressor", "Redimensionando: ${width}x${height} → ${newWidth}x${newHeight}")

        return try {
            bitmap.scale(newWidth, newHeight, true)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageCompressor", "OOM redimensionando, devolviendo original")
            bitmap
        }
    }

    /**
     * COMPRIME Y GUARDA EL BITMAP FINAL
     */
    private fun compressAndSaveBitmap(context: Context, bitmap: Bitmap, quality: Int): Uri? {
        return try {
            // COMPRIMIR A JPEG
            val outputStream = ByteArrayOutputStream()
            val success = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            if (!success) {
                android.util.Log.e("ImageCompressor", "Fallo al comprimir bitmap")
                return null
            }

            val compressedBytes = outputStream.toByteArray()
            outputStream.close()

            // GUARDAR EN ARCHIVO TEMPORAL CON NOMBRE ÚNICO
            val timestamp = System.currentTimeMillis()
            val tempFile = File(context.cacheDir, "compressed_${timestamp}_q${quality}.jpg")

            tempFile.outputStream().use { fileOutputStream ->
                fileOutputStream.write(compressedBytes)
                fileOutputStream.flush()
            }

            Uri.fromFile(tempFile)

        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error guardando archivo comprimido: ${e.message}", e)
            null
        }
    }

    /**
     * VERSIÓN MEJORADA DE getFileSizeKB
     */
    fun getFileSizeKB(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong() / 1024
            } ?: 0L
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error obteniendo tamaño: ${e.message}")
            0L
        }
    }

    /**
     * MÉTODO UTILITARIO - Limpiar archivos temporales antiguos
     */
    fun limpiarCacheImagenes(context: Context, horasAntiguas: Long = 24) {
        try {
            val cacheDir = context.cacheDir
            val limiteTiempo = System.currentTimeMillis() - (horasAntiguas * 60 * 60 * 1000)

            var archivosLimpiados = 0
            cacheDir.listFiles { file ->
                file.name.startsWith("compressed_") && file.lastModified() < limiteTiempo
            }?.forEach { file ->
                if (file.delete()) {
                    archivosLimpiados++
                }
            }

            if (archivosLimpiados > 0) {
                android.util.Log.d("ImageCompressor", "Limpiados $archivosLimpiados archivos temporales antiguos")
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error limpiando caché: ${e.message}")
        }
    }

    /**
     * MÉTODO UTILITARIO - Información de memoria disponible
     */
    fun obtenerInfoMemoria(): String {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory

        return "Memoria: ${usedMemory}MB usados / ${totalMemory}MB total / ${maxMemory}MB máx"
    }
}