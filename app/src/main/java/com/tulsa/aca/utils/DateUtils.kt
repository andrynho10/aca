package com.tulsa.aca.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val displayFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun formatTimestamp(timestamp: String?): String {
        return timestamp?.let {
            try {
                val date = timestampFormat.parse(it)
                date?.let { displayFormat.format(it) } ?: "Fecha inválida"
            } catch (e: Exception) {
                "Fecha inválida"
            }
        } ?: "Sin fecha"
    }

    // FUNCIÓN para parsear timestamps
    fun parseTimestamp(timestamp: String): Date {
        return try {
            timestampFormat.parse(timestamp) ?: Date()
        } catch (e: Exception) {
            Date() // Fallback a fecha actual
        }
    }

    // FUNCIÓN para formatear solo fecha (sin hora)
    fun formatDateOnly(date: Date): String {
        return dateOnlyFormat.format(date)
    }

    // FUNCIÓN para parsear fecha desde string dd/MM/yyyy
    fun parseDateOnly(dateString: String): Date? {
        return try {
            dateOnlyFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    // FUNCIONES para fechas predefinidas
    fun getToday(): Date = Calendar.getInstance().time

    fun getYesterday(): Date = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }.time

    fun getStartOfWeek(): Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    fun getStartOfMonth(): Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    fun getDaysAgo(days: Int): Date = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
    }.time
}