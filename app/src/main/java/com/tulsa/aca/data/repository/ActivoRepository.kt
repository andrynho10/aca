package com.tulsa.aca.data.repository

import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*

class ActivoRepository {
    private val client = SupabaseClient.client

    suspend fun obtenerTodosLosActivos(): List<Activo> {
        return try {
            client.from("activos").select().decodeList<Activo>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun obtenerActivoPorId(id: Int): Activo? {
        return try {
            client.from("activos").select {
                filter {
                    Activo::id eq id
                }
            }.decodeSingle<Activo>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun obtenerActivoPorQR(codigoQr: String): Activo? {
        return try {
            client.from("activos").select {
                filter {
                    Activo::codigoQr eq codigoQr
                }
            }.decodeSingle<Activo>()
        } catch (e: Exception) {
            null
        }
    }
}