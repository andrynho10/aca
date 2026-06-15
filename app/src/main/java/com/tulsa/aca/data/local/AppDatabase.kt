package com.tulsa.aca.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tulsa.aca.data.local.dao.*
import com.tulsa.aca.data.local.entities.*

/**
 * Base de datos Room principal de la aplicación
 * Maneja cache de datos y cola de sincronización para soporte offline
 */
@Database(
    entities = [
        ActivoEntity::class,
        PlantillaEntity::class,
        CategoriaEntity::class,
        PreguntaEntity::class,
        ReportePendienteEntity::class,
        FotoPendienteEntity::class,
        CambioPendienteEntity::class,
        SyncStatusEntity::class,
        DraftChecklistEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // DAOs
    abstract fun activoDao(): ActivoDao
    abstract fun plantillaDao(): PlantillaDao
    abstract fun reportePendienteDao(): ReportePendienteDao
    abstract fun syncStatusDao(): SyncStatusDao
    abstract fun draftChecklistDao(): DraftChecklistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtiene la instancia singleton de la base de datos
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aca_offline_database"
                )
                    // En desarrollo: borra y recrea la BD si cambia el esquema; en producción reemplazar por migraciones explícitas
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Limpia la instancia de la base de datos (útil para testing)
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
