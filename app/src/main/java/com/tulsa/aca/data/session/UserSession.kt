package com.tulsa.aca.data.session

import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.repository.AuthRepository
import com.tulsa.aca.utils.CacheManager
import java.util.concurrent.atomic.AtomicReference

object UserSession {
    // THREAD-SAFE: AtomicReference en lugar de var
    private val currentUserRef = AtomicReference<Usuario?>()

    fun login(usuario: Usuario) {
        currentUserRef.set(usuario)
        android.util.Log.d("UserSession", "Usuario logueado: ${usuario.nombreCompleto} (${usuario.id})")
    }

    fun logout() {
        val usuario = currentUserRef.get()
        android.util.Log.d("UserSession", "Cerrando sesión de: ${usuario?.nombreCompleto}")
        currentUserRef.set(null)
    }

    // LOGOUT COMPLETO THREAD-SAFE
    suspend fun logoutCompleto(): Boolean {
        return try {
            android.util.Log.d("UserSession", "=== INICIANDO LOGOUT COMPLETO ===")
            val usuarioActual = currentUserRef.get()?.nombreCompleto ?: "Usuario desconocido"

            // 1. Cerrar sesión en Supabase
            val authRepository = AuthRepository()
            val result = authRepository.logout()

            if (result.isSuccess) {
                android.util.Log.d("UserSession", "Sesión de '$usuarioActual' cerrada en Supabase")
            } else {
                android.util.Log.w("UserSession", "Error cerrando sesión en Supabase: ${result.exceptionOrNull()?.message}")
            }

            // 2. LIMPIAR TODOS LOS CACHÉS
            android.util.Log.d("UserSession", "Limpiando cachés...")
            CacheManager.limpiarTodosLosCaches()

            // 3. Limpiar sesión local
            logout()

            android.util.Log.d("UserSession", "Logout completo exitoso para '$usuarioActual'")
            true
        } catch (e: Exception) {
            android.util.Log.e("UserSession", "Error crítico en logout: ${e.message}", e)
            try {
                CacheManager.limpiezaDeEmergencia()
                logout()
            } catch (cleanupError: Exception) {
                android.util.Log.e("UserSession", "Error en limpieza de emergencia: ${cleanupError.message}")
            }
            false
        }
    }

    // MÉTODOS THREAD-SAFE
    fun getCurrentUser(): Usuario? = currentUserRef.get()

    fun requireCurrentUser(): Usuario {
        return currentUserRef.get() ?: throw IllegalStateException(
            "USUARIO NO AUTENTICADO - Esto indica un bug en el flujo de login"
        )
    }

    fun isLoggedIn(): Boolean = currentUserRef.get() != null

    fun debugCurrentUserStatus(): String {
        val user = currentUserRef.get()
        return buildString {
            if (user != null) {
                appendLine("✅ Usuario logueado: ${user.nombreCompleto} (ID: ${user.id})")
            } else {
                appendLine("❌ No hay usuario logueado")
            }
            appendLine(CacheManager.obtenerInfoCaches())
        }
    }
}