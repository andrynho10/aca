package com.tulsa.aca.data.repository

import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

/**
 * Maneja autenticación con Supabase Auth y resolución del perfil de usuario en la tabla `usuarios`
 */
class AuthRepository {
    private val client = SupabaseClient.client
    private val usuarioRepository = UsuarioRepository()

    /**
     * Autentica al usuario con Supabase Auth y enriquece el objeto con datos de la tabla `usuarios`
     * (rol, nombre completo, etc.) usando el UUID de Auth como clave de join
     */
    suspend fun login(email: String, password: String): Result<Usuario> {
        return try {
            // 1. Autenticar con Supabase Auth
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // 2. Obtener el usuario autenticado de Auth
            val authUser = client.auth.currentUserOrNull()

            if (authUser != null) {
                // 3. Buscar información adicional en nuestra tabla usuarios usando el UUID
                val usuario = usuarioRepository.obtenerUsuarioPorId(authUser.id)

                if (usuario != null) {
                    // 4. Crear usuario completo con email de auth
                    val usuarioCompleto = Usuario(
                        id = usuario.id,
                        nombreCompleto = usuario.nombreCompleto,
                        rol = usuario.rol,
                        createdAt = usuario.createdAt,
                        email = authUser.email ?: email
                    )
                    Result.success(usuarioCompleto)
                } else {
                    Result.failure(Exception("Usuario no encontrado en la base de datos"))
                }
            } else {
                Result.failure(Exception("Error en la autenticación"))
            }

        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error en login: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Cierra la sesión activa en Supabase Auth */
    suspend fun logout(): Result<Unit> {
        return try {
            client.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser() = client.auth.currentUserOrNull()

    fun isLoggedIn() = client.auth.currentUserOrNull() != null
}