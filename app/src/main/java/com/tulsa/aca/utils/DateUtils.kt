package com.tulsa.aca.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    // Formato para mostrar al usuario: dd/MM/yyyy HH:mm
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault() // Usar zona horaria del dispositivo
    }

    // Formato para mostrar solo la fecha: dd/MM/yyyy
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    // Formato para mostrar solo la hora: HH:mm
    private val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Convierte un timestamp de Supabase a formato legible
     * Maneja tanto ISO strings como timestamps en milisegundos
     */
    fun formatTimestamp(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "Fecha no disponible"

        return try {
            val date = parseTimestamp(timestamp)
            date?.let { displayFormat.format(it) } ?: "Fecha inválida"
        } catch (e: Exception) {
            android.util.Log.w("DateUtils", "Error formateando timestamp: $timestamp", e)
            "Fecha inválida"
        }
    }

    /**
     * Convierte un timestamp a solo fecha (dd/MM/yyyy)
     */
    fun formatDateOnly(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "Fecha no disponible"

        return try {
            val date = parseTimestamp(timestamp)
            date?.let { dateOnlyFormat.format(it) } ?: "Fecha inválida"
        } catch (e: Exception) {
            "Fecha inválida"
        }
    }

    /**
     * Convierte un timestamp a solo hora (HH:mm)
     */
    fun formatTimeOnly(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "Hora no disponible"

        return try {
            val date = parseTimestamp(timestamp)
            date?.let { timeOnlyFormat.format(it) } ?: "Hora inválida"
        } catch (e: Exception) {
            "Hora inválida"
        }
    }

    /**
     * Parsea diferentes formatos de timestamp que puede enviar Supabase
     */
    private fun parseTimestamp(timestamp: String): Date? {
        return try {
            when {
                // Formato ISO con Z (UTC): 2024-01-15T10:30:00.000Z
                timestamp.contains("T") && timestamp.endsWith("Z") -> {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                    isoFormat.parse(timestamp)
                }

                // Formato ISO sin Z: 2024-01-15T10:30:00.000
                timestamp.contains("T") && timestamp.contains(".") -> {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
                    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                    isoFormat.parse(timestamp)
                }

                // Formato ISO simple: 2024-01-15T10:30:00
                timestamp.contains("T") -> {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                    isoFormat.parse(timestamp)
                }

                // Formato de solo fecha: 2024-01-15
                timestamp.contains("-") && !timestamp.contains(" ") -> {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    dateFormat.parse(timestamp)
                }

                // Timestamp en milisegundos (como string)
                timestamp.all { it.isDigit() } -> {
                    Date(timestamp.toLong())
                }

                // Formato con espacio: 2024-01-15 10:30:00
                timestamp.contains(" ") -> {
                    val spaceFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    spaceFormat.parse(timestamp)
                }

                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.w("DateUtils", "Error parseando timestamp: $timestamp", e)
            null
        }
    }

    /**
     * Para debugging - muestra información sobre el timestamp
     */
    fun debugTimestamp(timestamp: String): String {
        return "Timestamp original: '$timestamp', Parseado: ${parseTimestamp(timestamp)}, Formateado: ${formatTimestamp(timestamp)}"
    }
}