package com.tulsa.aca

import android.app.Application
import com.tulsa.aca.workers.SyncWorker

/**
 * Clase Application personalizada para inicialización de servicios globales
 */
class ACAApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializar sincronización periódica en background
        SyncWorker.schedulePeriodic(this)

        android.util.Log.d("ACAApplication", "✅ Aplicación inicializada con soporte offline")
    }
}
