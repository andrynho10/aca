package com.tulsa.aca

import android.app.Application
import com.google.firebase.FirebaseApp
import com.tulsa.aca.data.sync.ConnectivitySyncTrigger
import com.tulsa.aca.workers.SyncWorker

/**
 * Clase Application personalizada para inicialización de servicios globales
 */
class ACAApplication : Application() {

    private lateinit var connectivitySyncTrigger: ConnectivitySyncTrigger

    override fun onCreate() {
        super.onCreate()

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        android.util.Log.d("ACAApplication", "✅ Firebase inicializado")

        // Inicializar sincronización periódica en background (cada 15 min)
        SyncWorker.schedulePeriodic(this)
        android.util.Log.d("ACAApplication", "✅ WorkManager programado (sincronización cada 15 min)")

        // Inicializar sincronización automática al recuperar conexión
        connectivitySyncTrigger = ConnectivitySyncTrigger(this)
        connectivitySyncTrigger.iniciar()
        android.util.Log.d("ACAApplication", "✅ Sincronización automática al recuperar conexión activada")

        android.util.Log.d("ACAApplication", "═══════════════════════════════════════")
        android.util.Log.d("ACAApplication", "✅ APLICACIÓN INICIALIZADA")
        android.util.Log.d("ACAApplication", "📴 Soporte offline completo activado")
        android.util.Log.d("ACAApplication", "🔄 Sincronización automática configurada")
        android.util.Log.d("ACAApplication", "🔔 Notificaciones push configuradas (FCM)")
        android.util.Log.d("ACAApplication", "═══════════════════════════════════════")
    }
}
