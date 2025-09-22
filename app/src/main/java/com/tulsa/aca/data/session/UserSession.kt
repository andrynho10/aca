package com.tulsa.aca.data.session

import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

object UserSession {
    private var currentUser: Usuario? = null

    fun login(usuario: Usuario) {
        currentUser = usuario
        android.util.Log.d("UserSession", "Usuario logueado: ${usuario.nombreCompleto} (${usuario.id})")
    }

    fun logout() {
        android.util.Log.d("UserSession", "Cerrando sesión de: ${currentUser?.nombreCompleto}")
        currentUser = null
    }

    // Función principal - devuelve null si no hay usuario
    fun getCurrentUser(): Usuario? = currentUser

    // Para casos donde DEBE haber usuario (crashea si no hay)
    fun requireCurrentUser(): Usuario {
        return currentUser ?: throw IllegalStateException(
            "USUARIO NO AUTENTICADO - Esto indica un bug en el flujo de login"
        )
    }

    fun isLoggedIn(): Boolean = currentUser != null

    // Para debugging - NUNCA usar en producción
    @Deprecated("Solo para debugging - no usar en producción")
    fun getCurrentUserOrDebug(): Usuario {
        return currentUser ?: Usuario(
            id = "-DEBUG-USER-",
            nombreCompleto = "DEBUG - Usuario no logueado",
            email = "debug@test.com",
            rol = "OPERADOR"
        )
    }

    // Función de utilidad para logs
    fun debugCurrentUserStatus(): String {
        return if (currentUser != null) {
            "Usuario logueado: ${currentUser!!.nombreCompleto} (ID: ${currentUser!!.id})"
        } else {
            "No hay usuario logueado"
        }
    }
    fun verificarEstadoAutenticacion(): String {
        val supabaseUser = SupabaseClient.client.auth.currentUserOrNull()
        val sessionUser = UserSession.getCurrentUser()

        return buildString {
            appendLine("=== ESTADO DE AUTENTICACIÓN ===")
            appendLine("Supabase Auth: ${if (supabaseUser != null) "Logueado (${supabaseUser.email})" else "No logueado"}")
            appendLine("UserSession: ${if (sessionUser != null) "${sessionUser.nombreCompleto} (${sessionUser.id})" else "No hay usuario"}")
            appendLine("Consistente: ${(supabaseUser != null && sessionUser != null) || (supabaseUser == null && sessionUser == null)}")
        }
    }

}