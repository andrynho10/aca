package com.tulsa.aca.data.repository

import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*

class UsuarioRepository {
    private val client = SupabaseClient.client

    suspend fun obtenerUsuarioPorId(usuarioId: String): Usuario? {
        return try {
            client.from("usuarios").select {
                filter {
                    Usuario::id eq usuarioId
                }
            }.decodeSingle<Usuario>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun obtenerTodosLosUsuarios(): List<Usuario> {
        return try {
            client.from("usuarios").select().decodeList<Usuario>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}