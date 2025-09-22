package com.tulsa.aca.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferencesManager {
    private const val PREF_NAME = "aca_preferences"
    private const val KEY_REMEMBER_USER = "remember_user"
    private const val KEY_SAVED_EMAIL = "saved_email"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Guardar si el usuario quiere ser recordado
    fun setRememberUser(context: Context, remember: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_REMEMBER_USER, remember)
        }

        android.util.Log.d("PreferencesManager", "Recordar usuario: $remember")
    }

    // Verificar si el usuario quiere ser recordado
    fun shouldRememberUser(context: Context): Boolean {
        val remember = getPreferences(context).getBoolean(KEY_REMEMBER_USER, false)
        android.util.Log.d("PreferencesManager", "¿Debe recordar usuario?: $remember")
        return remember
    }

    // Guardar el email del usuario
    fun saveUserEmail(context: Context, email: String) {
        getPreferences(context).edit {
            putString(KEY_SAVED_EMAIL, email)
        }

        android.util.Log.d("PreferencesManager", "Email guardado: $email")
    }

    // Obtener el email guardado
    fun getSavedUserEmail(context: Context): String {
        val email = getPreferences(context).getString(KEY_SAVED_EMAIL, "") ?: ""
        android.util.Log.d("PreferencesManager", "Email recuperado: $email")
        return email
    }

    // Limpiar datos guardados (cuando no se quiere recordar)
    fun clearSavedUser(context: Context) {
        getPreferences(context).edit {
            putBoolean(KEY_REMEMBER_USER, false)
                .putString(KEY_SAVED_EMAIL, "")
        }

        android.util.Log.d("PreferencesManager", "Datos de usuario limpiados")
    }
}