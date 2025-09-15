package com.tulsa.aca.data.repository

import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.CategoriaPlantilla
import com.tulsa.aca.data.models.PreguntaPlantilla
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlantillaUpdate(
    val nombre: String,
    @SerialName("tipo_activo")
    val tipoActivo: String,
    val activa: Boolean
)


class PlantillaRepository {
    private val client = SupabaseClient.client

    suspend fun obtenerPlantillasPorTipoActivo(tipoActivo: String): List<PlantillaChecklist> {
        return try {
            client.from("plantillas_checklist").select {
                filter {
                    PlantillaChecklist::tipoActivo eq tipoActivo
                    PlantillaChecklist::activa eq true
                }
            }.decodeList<PlantillaChecklist>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun obtenerPlantillaCompleta(plantillaId: Int): PlantillaChecklist? {
        return try {
            // Obtener plantilla base
            val plantilla = client.from("plantillas_checklist").select {
                filter {
                    PlantillaChecklist::id eq plantillaId
                }
            }.decodeSingle<PlantillaChecklist>()

            // Obtener categorías ordenadas
            val categorias = client.from("categorias_plantilla").select {
                filter {
                    CategoriaPlantilla::plantillaId eq plantillaId
                }
                order(column = "orden", order = Order.ASCENDING)
            }.decodeList<CategoriaPlantilla>()

            // Obtener preguntas para cada categoría
            val categoriasConPreguntas = categorias.map { categoria ->
                val preguntas = client.from("preguntas_plantilla").select {
                    filter {
                        PreguntaPlantilla::categoriaId eq categoria.id
                    }
                    order(column = "orden", order = Order.ASCENDING)
                }.decodeList<PreguntaPlantilla>()

                categoria.copy(preguntas = preguntas)
            }

            plantilla.copy(categorias = categoriasConPreguntas)
        } catch (e: Exception) {
            null
        }
    }
    // ========================================
    // NUEVAS FUNCIONES CRUD
    // ========================================

    suspend fun obtenerTodasLasPlantillas(): List<PlantillaChecklist> {
        return try {
            client.from("plantillas_checklist").select {
                order(column = "created_at", order = Order.DESCENDING)
            }.decodeList<PlantillaChecklist>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun crearPlantilla(plantilla: PlantillaChecklist): Int? {
        return try {
            val plantillaCreada = client.from("plantillas_checklist").insert(plantilla) {
                select()
            }.decodeSingle<PlantillaChecklist>()
            plantillaCreada.id
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error creando plantilla: ${e.message}", e)
            null
        }
    }

    suspend fun actualizarPlantilla(plantilla: PlantillaChecklist): Boolean {
        return try {
            android.util.Log.d("PlantillaRepository", "Iniciando actualización de plantilla: ID=${plantilla.id}, activa=${plantilla.activa}")

            val updateData = PlantillaUpdate(
                nombre = plantilla.nombre,
                tipoActivo = plantilla.tipoActivo,
                activa = plantilla.activa
            )

            android.util.Log.d("PlantillaRepository", "Datos a actualizar: $updateData")

            client.from("plantillas_checklist").update(updateData) {
                filter {
                    eq("id", plantilla.id)
                }
            }

            android.util.Log.d("PlantillaRepository", "Actualización exitosa para plantilla ID=${plantilla.id}")
            true
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error actualizando plantilla ID=${plantilla.id}: ${e.message}", e)
            false
        }
    }

    suspend fun eliminarPlantilla(id: Int): Boolean {
        return try {
            client.from("plantillas_checklist").delete {
                filter {
                    PlantillaChecklist::id eq id
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error eliminando plantilla: ${e.message}", e)
            false
        }
    }

    // ========================================
    // FUNCIONES CRUD PARA CATEGORÍAS
    // ========================================

    suspend fun crearCategoria(categoria: CategoriaPlantilla): Int? {
        return try {
            val categoriaCreada = client.from("categorias_plantilla").insert(categoria) {
                select()
            }.decodeSingle<CategoriaPlantilla>()
            categoriaCreada.id
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error creando categoría: ${e.message}", e)
            null
        }
    }

    suspend fun actualizarCategoria(categoria: CategoriaPlantilla): Boolean {
        return try {
            client.from("categorias_plantilla").update(categoria) {
                filter {
                    CategoriaPlantilla::id eq categoria.id
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error actualizando categoría: ${e.message}", e)
            false
        }
    }


    suspend fun eliminarCategoria(id: Int): Boolean {
        return try {
            client.from("categorias_plantilla").delete {
                filter {
                    CategoriaPlantilla::id eq id
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error eliminando categoría: ${e.message}", e)
            false
        }
    }

    // ========================================
    // FUNCIONES CRUD PARA PREGUNTAS
    // ========================================

    suspend fun crearPregunta(pregunta: PreguntaPlantilla): Int? {
        return try {
            val preguntaCreada = client.from("preguntas_plantilla").insert(pregunta) {
                select()
            }.decodeSingle<PreguntaPlantilla>()
            preguntaCreada.id
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error creando pregunta: ${e.message}", e)
            null
        }
    }

    suspend fun actualizarPregunta(pregunta: PreguntaPlantilla): Boolean {
        return try {
            client.from("preguntas_plantilla").update(pregunta) {
                filter {
                    PreguntaPlantilla::id eq pregunta.id
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error actualizando pregunta: ${e.message}", e)
            false
        }
    }

    suspend fun eliminarPregunta(id: Int): Boolean {
        return try {
            client.from("preguntas_plantilla").delete {
                filter {
                    PreguntaPlantilla::id eq id
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PlantillaRepository", "Error eliminando pregunta: ${e.message}", e)
            false
        }
    }

    // ========================================
    // FUNCIONES AUXILIARES
    // ========================================

    suspend fun buscarPlantillasPorNombre(nombre: String): List<PlantillaChecklist> {
        return try {
            client.from("plantillas_checklist").select {
                filter {
                    PlantillaChecklist::nombre ilike "%$nombre%"
                }
            }.decodeList<PlantillaChecklist>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun buscarPlantillasPorTipo(tipo: String): List<PlantillaChecklist> {
        return try {
            client.from("plantillas_checklist").select {
                filter {
                    PlantillaChecklist::tipoActivo ilike "%$tipo%"
                }
            }.decodeList<PlantillaChecklist>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun obtenerTiposDeActivo(): List<String> {
        return try {
            val plantillas = client.from("plantillas_checklist").select {
                // Solo obtener tipos únicos
            }.decodeList<PlantillaChecklist>()

            plantillas.map { it.tipoActivo }.distinct()
        } catch (e: Exception) {
            listOf("Grúa Horquilla") // Valores por defecto
        }
    }
}