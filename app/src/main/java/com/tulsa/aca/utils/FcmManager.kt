package com.tulsa.aca.utils

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.tulsa.aca.data.repository.FcmTokenRepository
import kotlinx.coroutines.tasks.await

/**
 * Gestor centralizado de Firebase Cloud Messaging
 */
object FcmManager {
    private const val TAG = "FcmManager"
    private val fcmTokenRepository = FcmTokenRepository()

    /**
     * Obtiene el token FCM actual y lo guarda en Supabase para el usuario especificado
     */
    suspend fun registerFcmToken(usuarioId: String): Boolean {
        return try {
            // Obtener el token FCM actual
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Token FCM obtenido: $token")

            // Guardar el token en Supabase
            val success = fcmTokenRepository.guardarToken(usuarioId, token)

            if (success) {
                Log.d(TAG, "Token FCM registrado exitosamente para usuario: $usuarioId")
            } else {
                Log.e(TAG, "Error registrando token FCM para usuario: $usuarioId")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo o guardando token FCM", e)
            false
        }
    }

    /**
     * Elimina el token FCM del usuario de Supabase (útil al cerrar sesión)
     */
    suspend fun unregisterFcmToken(usuarioId: String): Boolean {
        return try {
            val success = fcmTokenRepository.eliminarToken(usuarioId)

            if (success) {
                Log.d(TAG, "Token FCM eliminado exitosamente para usuario: $usuarioId")
            } else {
                Log.e(TAG, "Error eliminando token FCM para usuario: $usuarioId")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando token FCM", e)
            false
        }
    }

    /**
     * Elimina el token FCM del dispositivo (elimina suscripciones)
     */
    suspend fun deleteFcmToken(): Boolean {
        return try {
            FirebaseMessaging.getInstance().deleteToken().await()
            Log.d(TAG, "Token FCM del dispositivo eliminado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando token FCM del dispositivo", e)
            false
        }
    }
}
