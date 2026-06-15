package com.tulsa.aca.data.sync

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tulsa.aca.data.repository.OfflineReporteRepository
import com.tulsa.aca.utils.NetworkMonitor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Observa cambios de conectividad y sincroniza automáticamente
 * cuando se recupera la conexión
 */
class ConnectivitySyncTrigger(private val context: Context) {

    private val networkMonitor = NetworkMonitor(context)
    private val reporteRepository = OfflineReporteRepository(context)
    private val syncManager = SyncManager(context)

    /**
     * Inicia la observación de conectividad
     * Debe llamarse desde Application.onCreate()
     */
    fun iniciar() {
        android.util.Log.d("ConnectivitySyncTrigger", "Iniciando observación de conectividad...")

        ProcessLifecycleOwner.get().lifecycleScope.launch {
            networkMonitor.isConnected
                .distinctUntilChanged() // Solo reacciona a transiciones de estado, no a repeticiones
                .drop(1) // Ignora el estado inicial para no sincronizar en cada arranque de la app
                .collect { isConnected ->
                    android.util.Log.d("ConnectivitySyncTrigger", "Cambio de conectividad: $isConnected")

                    if (isConnected) {
                        // Se recuperó la conexión
                        android.util.Log.d("ConnectivitySyncTrigger", "Conexión recuperada, iniciando sincronización...")
                        sincronizarTodo()
                    } else {
                        android.util.Log.d("ConnectivitySyncTrigger", "Conexión perdida")
                    }
                }
        }
    }

    /**
     * Sincroniza todo cuando se recupera la conexión
     */
    private suspend fun sincronizarTodo() {
        try {
            // 1. Verificar si hay reportes pendientes
            val pendientesCount = reporteRepository.getReportesPendientesCount()

            if (pendientesCount > 0) {
                android.util.Log.d("ConnectivitySyncTrigger", "═══════════════════════════════════════")
                android.util.Log.d("ConnectivitySyncTrigger", "SINCRONIZACIÓN AUTOMÁTICA")
                android.util.Log.d("ConnectivitySyncTrigger", "Reportes pendientes: $pendientesCount")
                android.util.Log.d("ConnectivitySyncTrigger", "═══════════════════════════════════════")

                // 2. Sincronizar reportes pendientes
                val resultado = reporteRepository.forzarSincronizacion()

                if (resultado.isSuccess) {
                    val sincronizados = resultado.getOrNull() ?: 0
                    android.util.Log.d("ConnectivitySyncTrigger", "Reportes sincronizados: $sincronizados")
                } else {
                    android.util.Log.w("ConnectivitySyncTrigger", "Error sincronizando reportes: ${resultado.exceptionOrNull()?.message}")
                }
            } else {
                android.util.Log.d("ConnectivitySyncTrigger", "No hay reportes pendientes")
            }

            // 3. Sincronizar cache (activos y plantillas)
            android.util.Log.d("ConnectivitySyncTrigger", "Actualizando cache de activos y plantillas...")
            syncManager.syncActivos(forceSync = false)
            syncManager.syncPlantillas(forceSync = false)

            android.util.Log.d("ConnectivitySyncTrigger", "═══════════════════════════════════════")
            android.util.Log.d("ConnectivitySyncTrigger", "SINCRONIZACIÓN AUTOMÁTICA COMPLETADA")
            android.util.Log.d("ConnectivitySyncTrigger", "═══════════════════════════════════════")

        } catch (e: Exception) {
            android.util.Log.e("ConnectivitySyncTrigger", "Error en sincronización automática: ${e.message}", e)
        }
    }
}
