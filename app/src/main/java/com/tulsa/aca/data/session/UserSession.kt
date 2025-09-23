package com.tulsa.aca.data.session

import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.repository.AuthRepository
import com.tulsa.aca.data.supabase.SupabaseClient
import com.tulsa.aca.utils.CacheManager
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

    // Logout completo centralizado
    suspend fun logoutCompleto(): Boolean {
        return try {
            android.util.Log.d("UserSession", "=== INICIANDO LOGOUT COMPLETO ===")
            val usuarioActual = currentUser?.nombreCompleto ?: "Usuario desconocido"

            // 1. Cerrar sesión en Supabase
            val authRepository = AuthRepository()
            val result = authRepository.logout()

            if (result.isSuccess) {
                android.util.Log.d("UserSession", "Sesión de '$usuarioActual' cerrada en Supabase")
            } else {
                android.util.Log.w("UserSession", "Error cerrando sesión en Supabase: ${result.exceptionOrNull()?.message}")
                android.util.Log.w("UserSession", "Continuando con logout local...")
            }

            // 2. LIMPIAR TODOS LOS CACHÉS ANTES DE LIMPIAR SESIÓN LOCAL
            android.util.Log.d("UserSession", "🧹 Limpiando cachés para seguridad...")
            CacheManager.limpiarTodosLosCaches()

            // 3. Limpiar sesión local (DESPUÉS de limpiar cachés)
            logout()

            android.util.Log.d("UserSession", "Logout completo exitoso para '$usuarioActual'")
            android.util.Log.d("UserSession", "Datos de sesión anterior completamente eliminados")
            true
        } catch (e: Exception) {
            android.util.Log.e("UserSession", "Error crítico en logout completo: ${e.message}", e)
            // LIMPIEZA DE EMERGENCIA
            try {
                CacheManager.limpiezaDeEmergencia()
                logout() // Limpiar sesión local de todas formas
            } catch (cleanupError: Exception) {
                android.util.Log.e("UserSession", "Error en limpieza de emergencia: ${cleanupError.message}")
            }
            false
        }
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
        return buildString {
            if (currentUser != null) {
                appendLine("Usuario logueado: ${currentUser!!.nombreCompleto} (ID: ${currentUser!!.id})")
            } else {
                appendLine("No hay usuario logueado")
            }
            appendLine(CacheManager.obtenerInfoCaches())
        }
    }
    fun verificarEstadoAutenticacion(): String {
        val supabaseUser = SupabaseClient.client.auth.currentUserOrNull()
        val sessionUser = getCurrentUser()

        return buildString {
            appendLine("=== ESTADO DE AUTENTICACIÓN ===")
            appendLine("Supabase Auth: ${if (supabaseUser != null) "Logueado (${supabaseUser.email})" else "No logueado"}")
            appendLine("UserSession: ${if (sessionUser != null) "${sessionUser.nombreCompleto} (${sessionUser.id})" else "No hay usuario"}")
            appendLine("Consistente: ${(supabaseUser != null && sessionUser != null) || (supabaseUser == null && sessionUser == null)}")
        }
    }

}