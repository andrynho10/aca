package com.tulsa.aca.data.local.dao

import androidx.room.*
import com.tulsa.aca.data.local.entities.CategoriaEntity
import com.tulsa.aca.data.local.entities.PlantillaEntity
import com.tulsa.aca.data.local.entities.PreguntaEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para plantillas de checklist
 */
@Dao
interface PlantillaDao {

    // ===== PLANTILLAS =====
    @Query("SELECT * FROM plantillas_cache WHERE activa = 1 ORDER BY nombre ASC")
    suspend fun getAllPlantillasActivas(): List<PlantillaEntity>

    @Query("SELECT * FROM plantillas_cache ORDER BY createdAt DESC")
    suspend fun getAllPlantillas(): List<PlantillaEntity>

    @Query("SELECT * FROM plantillas_cache WHERE id = :id")
    suspend fun getPlantillaById(id: Int): PlantillaEntity?

    @Query("SELECT * FROM plantillas_cache WHERE tipoActivo = :tipoActivo AND activa = 1")
    suspend fun getPlantillasByTipo(tipoActivo: String): List<PlantillaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlantilla(plantilla: PlantillaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlantillas(plantillas: List<PlantillaEntity>)

    @Update
    suspend fun updatePlantilla(plantilla: PlantillaEntity)

    @Delete
    suspend fun deletePlantilla(plantilla: PlantillaEntity)

    @Query("DELETE FROM plantillas_cache")
    suspend fun deleteAllPlantillas()

    // ===== CATEGORÍAS =====
    @Query("SELECT * FROM categorias_cache WHERE plantillaId = :plantillaId ORDER BY orden ASC")
    suspend fun getCategoriasByPlantilla(plantillaId: Int): List<CategoriaEntity>

    @Query("SELECT * FROM categorias_cache WHERE id = :id")
    suspend fun getCategoriaById(id: Int): CategoriaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoria(categoria: CategoriaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategorias(categorias: List<CategoriaEntity>)

    @Update
    suspend fun updateCategoria(categoria: CategoriaEntity)

    @Delete
    suspend fun deleteCategoria(categoria: CategoriaEntity)

    @Query("DELETE FROM categorias_cache WHERE plantillaId = :plantillaId")
    suspend fun deleteCategoriasByPlantilla(plantillaId: Int)

    @Query("DELETE FROM categorias_cache")
    suspend fun deleteAllCategorias()

    // ===== PREGUNTAS =====
    @Query("SELECT * FROM preguntas_cache WHERE categoriaId = :categoriaId ORDER BY orden ASC")
    suspend fun getPreguntasByCategoria(categoriaId: Int): List<PreguntaEntity>

    @Query("SELECT * FROM preguntas_cache WHERE id = :id")
    suspend fun getPreguntaById(id: Int): PreguntaEntity?

    @Query("""
        SELECT p.* FROM preguntas_cache p
        INNER JOIN categorias_cache c ON p.categoriaId = c.id
        WHERE c.plantillaId = :plantillaId
        ORDER BY c.orden ASC, p.orden ASC
    """)
    suspend fun getPreguntasByPlantilla(plantillaId: Int): List<PreguntaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPregunta(pregunta: PreguntaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreguntas(preguntas: List<PreguntaEntity>)

    @Update
    suspend fun updatePregunta(pregunta: PreguntaEntity)

    @Delete
    suspend fun deletePregunta(pregunta: PreguntaEntity)

    @Query("DELETE FROM preguntas_cache WHERE categoriaId = :categoriaId")
    suspend fun deletePreguntasByCategoria(categoriaId: Int)

    @Query("DELETE FROM preguntas_cache")
    suspend fun deleteAllPreguntas()

    // ===== UTILIDADES =====
    @Query("SELECT COUNT(*) FROM plantillas_cache")
    suspend fun getPlantillasCount(): Int

    @Query("SELECT DISTINCT tipoActivo FROM plantillas_cache")
    suspend fun getTiposDeActivo(): List<String>
}
