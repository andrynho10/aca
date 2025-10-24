package com.tulsa.aca.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tulsa.aca.MainActivity
import com.tulsa.aca.R
import com.tulsa.aca.data.repository.AuthRepository
import com.tulsa.aca.data.repository.FcmTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Servicio de Firebase Cloud Messaging para recibir notificaciones push
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "reportes_problemas_channel"
        private const val CHANNEL_NAME = "Reportes con Problemas"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM generado: $token")

        // Guardar el token en Supabase si el usuario está autenticado
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authRepository = AuthRepository()
                val currentUser = authRepository.getCurrentUser()

                if (currentUser != null) {
                    val fcmTokenRepository = FcmTokenRepository()
                    val success = fcmTokenRepository.guardarToken(currentUser.id, token)

                    if (success) {
                        Log.d(TAG, "Token FCM guardado exitosamente en Supabase para usuario: ${currentUser.id}")
                    } else {
                        Log.e(TAG, "Falló el guardado del token FCM")
                    }
                } else {
                    Log.w(TAG, "Usuario no autenticado. Token FCM no guardado en Supabase.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando token FCM", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Mensaje recibido de FCM: ${message.messageId}")

        // Verificar que sea un mensaje de reporte con problemas
        val tipo = message.data["tipo"]
        if (tipo == "reporte_problemas") {
            mostrarNotificacionReporte(message)
        } else {
            // Mostrar notificación genérica si no tiene tipo específico
            message.notification?.let {
                mostrarNotificacionGenerica(it.title, it.body)
            }
        }
    }

    private fun mostrarNotificacionReporte(message: RemoteMessage) {
        val reporteId = message.data["reporte_id"]
        val activoId = message.data["activoId"]
        val activoNombre = message.data["activo_nombre"]
        val usuarioNombre = message.data["usuario_nombre"]
        val preguntasFallidasJson = message.data["preguntas_fallidas"]

        // Usar el título y cuerpo del notification payload
        val titulo = message.notification?.title ?: "Nuevo reporte con problemas"
        val cuerpo = message.notification?.body ?: "Se ha detectado un problema en un activo"

        Log.d(TAG, "Mostrando notificación para reporte: $reporteId")
        Log.d(TAG, "Activo: $activoNombre")
        Log.d(TAG, "Usuario: $usuarioNombre")
        Log.d(TAG, "Preguntas fallidas: $preguntasFallidasJson")

        // Crear intent para abrir la app en el reporte específico
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notificacion_tipo", "reporte_problema")
            putExtra("reporte_id", reporteId)
            putExtra("activo_id", activoId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            reporteId?.hashCode() ?: NOTIFICATION_ID_BASE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Crear canal de notificación (requerido en Android 8+)
        createNotificationChannel()

        // Construir notificación
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        // Mostrar notificación
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = reporteId?.hashCode() ?: NOTIFICATION_ID_BASE
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "Notificación mostrada con ID: $notificationId")
    }

    private fun mostrarNotificacionGenerica(titulo: String?, cuerpo: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_BASE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle(titulo ?: "AppACA")
            .setContentText(cuerpo ?: "Nueva notificación")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_BASE, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notificaciones de reportes de inspección con problemas detectados"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "Canal de notificación creado: $CHANNEL_ID")
        }
    }
}
