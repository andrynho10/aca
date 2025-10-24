package com.tulsa.aca.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tulsa.aca.MainActivity
import com.tulsa.aca.R
import com.tulsa.aca.ui.navigation.Screen

/**
 * Helper para mostrar notificaciones de reportes de inspección con problemas
 */
object ReporteNotificationHelper {

    const val EXTRA_REPORTE_ID = "com.tulsa.aca.EXTRA_REPORTE_ID"
    const val EXTRA_ACTIVO_ID = "com.tulsa.aca.EXTRA_ACTIVO_ID"

    private const val CHANNEL_ID = "reportes_problemas_channel"
    private const val CHANNEL_NAME = "Reportes con Problemas"
    private const val CHANNEL_DESCRIPTION = "Notificaciones cuando se detectan problemas en reportes de inspección"

    private fun ensureChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Muestra una notificación local de reporte con problemas
     * Útil para mostrar notificaciones desde la app cuando está en foreground
     */
    fun showReporteProblemasNotification(
        context: Context,
        reporteId: String,
        activoId: Int,
        activoNombre: String,
        usuarioNombre: String,
        preguntasFallidas: List<String>
    ) {
        // Verificar permisos de notificaciones en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                android.util.Log.w(
                    "ReporteNotification",
                    "Permiso de notificaciones no concedido. No se mostrará la alerta."
                )
                return
            }
        }

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            android.util.Log.w(
                "ReporteNotification",
                "Notificaciones deshabilitadas por el usuario."
            )
            return
        }

        ensureChannel(context)

        // Crear intent para abrir el detalle del reporte
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REPORTE_ID, reporteId)
            putExtra(EXTRA_ACTIVO_ID, activoId)
            // Puedes agregar una ruta específica si tienes una pantalla de detalle de reportes
            // putExtra(HorometroNotificationHelper.EXTRA_TARGET_ROUTE, Screen.DetalleReporte.route)
        }

        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            context,
            reporteId.hashCode(),
            intent,
            pendingIntentFlags
        )

        // Construir el texto descriptivo de los problemas
        val problemasTexto = when {
            preguntasFallidas.isEmpty() -> "Problemas detectados"
            preguntasFallidas.size <= 3 -> preguntasFallidas.joinToString(", ")
            else -> "${preguntasFallidas.take(3).joinToString(", ")} y ${preguntasFallidas.size - 3} más"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle("Problema en $activoNombre")
            .setContentText("$usuarioNombre reportó: $problemasTexto")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$usuarioNombre reportó problemas:\n• ${preguntasFallidas.joinToString("\n• ")}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(reporteId.hashCode(), notification)
    }

    /**
     * Cancela una notificación específica de reporte
     */
    fun cancelReporteNotification(context: Context, reporteId: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(reporteId.hashCode())
    }

    /**
     * Cancela todas las notificaciones de reportes
     */
    fun cancelAllReporteNotifications(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()
    }
}
