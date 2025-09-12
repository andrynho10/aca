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

    // Funciones Supervisor
    suspend fun crearActivo(activo: Activo): Boolean {
        return try {
            client.from("activos").insert(activo)
            true
        } catch (e: Exception) {
            android.util.Log.e("ActivoRepository", "Error creando activo: ${e.message}", e)
            false
        }
    }

    suspend fun actualizarActivo(activo: Activo): Boolean {
        return try {
            activo.id?.let { id ->
                client.from("activos").update(activo) {
                    filter {
                        Activo::id eq id
                    }
                }
                true
            } ?: false
        } catch (e: Exception) {
            android.util.Log.e("ActivoRepository", "Error actualizando activo: ${e.message}", e)
            false
        }
    }

    suspend fun eliminarActivo(id: Int): Boolean {
        return try {
            client.from("activos").delete {
                filter {
                    Activo::id eq id
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("ActivoRepository", "Error eliminando activo: ${e.message}", e)
            false
        }
    }

    suspend fun verificarCodigoQRUnico(codigoQr: String, excludeId: Int? = null): Boolean {
        return try {
            val activos = client.from("activos").select {
                filter {
                    Activo::codigoQr eq codigoQr
                    excludeId?.let {
                        Activo::id neq it
                    }
                }
            }.decodeList<Activo>()

            activos.isEmpty() // true si no existe, false si ya existe
        } catch (e: Exception) {
            false // En caso de error, asumimos que no es único
        }
    }

    suspend fun buscarActivosPorTipo(tipo: String): List<Activo> {
        return try {
            client.from("activos").select {
                filter {
                    Activo::tipo ilike "%$tipo%"
                }
            }.decodeList<Activo>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun buscarActivosPorNombre(nombre: String): List<Activo> {
        return try {
            client.from("activos").select {
                filter {
                    Activo::nombre ilike "%$nombre%"
                }
            }.decodeList<Activo>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}



