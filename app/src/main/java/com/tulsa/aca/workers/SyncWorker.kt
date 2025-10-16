package com.tulsa.aca.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.tulsa.aca.data.sync.SyncManager
import com.tulsa.aca.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker para sincronización en background de datos offline
 * Se ejecuta periódicamente y cuando se detecta conectividad
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val syncManager = SyncManager(context)
    private val networkMonitor = NetworkMonitor(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 Iniciando sincronización en background...")

        // Verificar conectividad
        if (!networkMonitor.isCurrentlyConnected()) {
            Log.d(TAG, "📴 Sin conexión a Internet. Sincronización pospuesta.")
            return@withContext Result.retry()
        }

        return@withContext try {
            // 1. Sincronizar cache de activos
            val activosResult = syncManager.syncActivos(forceSync = false)
            if (activosResult.isSuccess) {
                Log.d(TAG, "✅ Activos sincronizados")
            } else {
                Log.w(TAG, "⚠️ Error sincronizando activos: ${activosResult.exceptionOrNull()?.message}")
            }

            // 2. Sincronizar cache de plantillas
            val plantillasResult = syncManager.syncPlantillas(forceSync = false)
            if (plantillasResult.isSuccess) {
                Log.d(TAG, "✅ Plantillas sincronizadas")
            } else {
                Log.w(TAG, "⚠️ Error sincronizando plantillas: ${plantillasResult.exceptionOrNull()?.message}")
            }

            // 3. Sincronizar reportes pendientes (más importante)
            val reportesResult = syncManager.syncReportesPendientes()
            if (reportesResult.isSuccess) {
                val result = reportesResult.getOrNull()
                Log.d(TAG, "✅ Reportes sincronizados: ${result?.reportesSincronizados} exitosos, ${result?.reportesFallidos} fallidos")
            } else {
                Log.w(TAG, "⚠️ Error sincronizando reportes: ${reportesResult.exceptionOrNull()?.message}")
            }

            // Considerar éxito si al menos los reportes se intentaron sincronizar
            Log.d(TAG, "🏁 Sincronización en background completada")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error durante sincronización: ${e.message}", e)

            // Reintentar si es un error temporal
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "sync_work"
        private const val MAX_RETRY_ATTEMPTS = 3

        /**
         * Programa la sincronización periódica
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Solo con conexión
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = 15, // Cada 15 minutos
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 5, // Con flexibilidad de 5 minutos
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("sync")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Mantener si ya existe
                syncRequest
            )

            Log.d(TAG, "📅 Sincronización periódica programada")
        }

        /**
         * Ejecuta sincronización inmediata (one-time)
         */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag("sync_immediate")
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
            Log.d(TAG, "🚀 Sincronización inmediata solicitada")
        }

        /**
         * Cancela la sincronización periódica
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "🛑 Sincronización periódica cancelada")
        }

        /**
         * Obtiene el estado de la sincronización
         */
        fun getSyncStatus(context: Context) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)
    }
}
