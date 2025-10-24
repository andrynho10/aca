package com.tulsa.aca.data.repository

import android.os.Build
import android.util.Log
import com.tulsa.aca.data.models.FcmToken
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class FcmTokenRepository {
    private val client = SupabaseClient.client

    companion object {
        private const val TAG = "FcmTokenRepository"
    }

    /**
     * Guarda o actualiza el token FCM del usuario actual
     */
    suspend fun guardarToken(usuarioId: String, token: String): Boolean {
        return try {
            // Obtener información del dispositivo
            val dispositivoInfo = obtenerInfoDispositivo()

            // Verificar si ya existe un token para este usuario
            val tokenExistente = client.from("fcm_tokens")
                .select {
                    filter {
                        FcmToken::usuarioId eq usuarioId
                    }
                }
                .decodeSingleOrNull<FcmToken>()

            if (tokenExistente != null) {
                // Actualizar token existente
                client.from("fcm_tokens").update({
                    set("token", token)
                    set("dispositivo_info", dispositivoInfo)
                }) {
                    filter {
                        FcmToken::usuarioId eq usuarioId
                    }
                }
                Log.d(TAG, "Token FCM actualizado para usuario: $usuarioId")
            } else {
                // Insertar nuevo token
                val nuevoToken = mapOf(
                    "usuario_id" to usuarioId,
                    "token" to token,
                    "dispositivo_info" to dispositivoInfo
                )

                client.from("fcm_tokens").insert(nuevoToken)
                Log.d(TAG, "Nuevo token FCM guardado para usuario: $usuarioId")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando token FCM: ${e.message}", e)
            false
        }
    }

    /**
     * Elimina el token FCM del usuario (útil al cerrar sesión)
     */
    suspend fun eliminarToken(usuarioId: String): Boolean {
        return try {
            client.from("fcm_tokens").delete {
                filter {
                    FcmToken::usuarioId eq usuarioId
                }
            }
            Log.d(TAG, "Token FCM eliminado para usuario: $usuarioId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando token FCM: ${e.message}", e)
            false
        }
    }

    /**
     * Obtiene el token FCM guardado para un usuario
     */
    suspend fun obtenerToken(usuarioId: String): FcmToken? {
        return try {
            client.from("fcm_tokens")
                .select {
                    filter {
                        FcmToken::usuarioId eq usuarioId
                    }
                }
                .decodeSingleOrNull<FcmToken>()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo token FCM: ${e.message}", e)
            null
        }
    }

    /**
     * Obtiene información del dispositivo para logging
     */
    private fun obtenerInfoDispositivo(): String {
        return buildString {
            append("Marca: ${Build.MANUFACTURER}, ")
            append("Modelo: ${Build.MODEL}, ")
            append("Android: ${Build.VERSION.RELEASE}, ")
            append("SDK: ${Build.VERSION.SDK_INT}")
        }
    }
}
