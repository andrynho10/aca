package com.tulsa.aca.data.repository

import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.CategoriaPlantilla
import com.tulsa.aca.data.models.PreguntaPlantilla
import com.tulsa.aca.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.Order

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
                order(column = "orden", order = Order.ASCENDING)  // <- Sintaxis correcta
            }.decodeList<CategoriaPlantilla>()

            // Obtener preguntas para cada categoría
            val categoriasConPreguntas = categorias.map { categoria ->
                val preguntas = client.from("preguntas_plantilla").select {
                    filter {
                        PreguntaPlantilla::categoriaId eq categoria.id
                    }
                    order(column = "orden", order = Order.ASCENDING)  // <- Sintaxis correcta
                }.decodeList<PreguntaPlantilla>()

                categoria.copy(preguntas = preguntas)
            }

            plantilla.copy(categorias = categoriasConPreguntas)
        } catch (e: Exception) {
            null
        }
    }
}