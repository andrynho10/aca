# Modo Offline - Documentación

## 🎯 Resumen

Se ha implementado un **sistema completo de soporte offline** en la aplicación ACA utilizando **Room Database**. Esto permite que los operadores trabajen sin conexión a Internet, con sincronización automática cuando se recupera la conectividad.

---

## ✨ Características Implementadas

### 1. **Cache Local de Datos**
- ✅ **Activos**: Cache completo de grúas y maquinaria
- ✅ **Plantillas de Checklist**: Todas las plantillas con categorías y preguntas
- ✅ **Sincronización Inteligente**: Actualización automática cada 5 minutos cuando hay conexión

### 2. **Cola de Sincronización de Reportes**
- ✅ **Creación Offline**: Los reportes se guardan localmente si no hay conexión
- ✅ **Sincronización Automática**: Se suben al servidor cuando se detecta conexión
- ✅ **Persistencia de Fotos**: Las fotos se guardan localmente y se suben posteriormente
- ✅ **Reintentos Automáticos**: Sistema de reintentos con backoff exponencial

### 3. **Monitoreo de Red**
- ✅ **Detección en Tiempo Real**: Monitoreo continuo del estado de conectividad
- ✅ **Flow Reactivo**: Los componentes pueden observar cambios de conectividad
- ✅ **Sincronización Automática**: Cuando se recupera conexión, sincroniza datos pendientes

### 4. **Sincronización en Background**
- ✅ **WorkManager**: Sincronización periódica cada 15 minutos
- ✅ **Restricciones Inteligentes**: Solo sincroniza cuando hay conexión
- ✅ **Política de Reintentos**: Hasta 3 intentos con backoff exponencial

---

## 🏗️ Arquitectura

### Estructura de Capas

```
┌─────────────────────────────────────┐
│         UI Layer (Compose)          │
│  ViewModels + Screens + Components  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│     Offline Repositories Layer      │
│  OfflineActivoRepository            │
│  OfflinePlantillaRepository         │
│  OfflineReporteRepository           │
└──────────────┬──────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼─────┐   ┌──────▼─────────┐
│  Room DB   │   │  Supabase API  │
│  (Local)   │   │   (Remote)     │
└────────────┘   └────────────────┘
```

### Componentes Principales

#### 1. **AppDatabase** ([AppDatabase.kt](app/src/main/java/com/tulsa/aca/data/local/AppDatabase.kt))
Base de datos Room principal con 8 tablas:
- `activos_cache`: Cache de activos
- `plantillas_cache`: Cache de plantillas
- `categorias_cache`: Cache de categorías
- `preguntas_cache`: Cache de preguntas
- `reportes_pendientes`: Cola de reportes sin sincronizar
- `fotos_pendientes`: Cola de fotos sin subir
- `cambios_pendientes`: Cola de cambios CRUD pendientes
- `sync_status`: Estado de sincronización por entidad

#### 2. **NetworkMonitor** ([NetworkMonitor.kt](app/src/main/java/com/tulsa/aca/utils/NetworkMonitor.kt))
Monitorea el estado de conectividad en tiempo real usando `ConnectivityManager`.

```kotlin
val networkMonitor = NetworkMonitor(context)

// Flow reactivo
networkMonitor.isConnected.collect { connected ->
    if (connected) {
        // Hay conexión
    }
}

// Check sincrónico
val isConnected = networkMonitor.isCurrentlyConnected()
```

#### 3. **SyncManager** ([SyncManager.kt](app/src/main/java/com/tulsa/aca/data/sync/SyncManager.kt))
Gestiona la sincronización bidireccional entre Room y Supabase.

```kotlin
val syncManager = SyncManager(context)

// Sincronizar todo
syncManager.syncAll(forceSync = true)

// Sincronizar solo activos
syncManager.syncActivos()

// Sincronizar solo plantillas
syncManager.syncPlantillas()

// Sincronizar reportes pendientes
syncManager.syncReportesPendientes()
```

#### 4. **SyncWorker** ([SyncWorker.kt](app/src/main/java/com/tulsa/aca/workers/SyncWorker.kt))
Worker de WorkManager para sincronización en background.

```kotlin
// Programar sincronización periódica (cada 15 min)
SyncWorker.schedulePeriodic(context)

// Sincronizar ahora (one-time)
SyncWorker.syncNow(context)

// Cancelar sincronización periódica
SyncWorker.cancelPeriodicSync(context)
```

---

## 📦 Repositorios Offline

### OfflineActivoRepository

Estrategia **offline-first** para activos:

```kotlin
val repository = OfflineActivoRepository(context)

// Obtener todos los activos (primero del cache, luego actualiza desde servidor)
val activos = repository.obtenerTodosLosActivos()

// Buscar por QR (funciona offline)
val activo = repository.obtenerActivoPorQR("QR123")

// Observar cambios en tiempo real
repository.observarActivos().collect { activos ->
    // Actualización reactiva
}

// Forzar sincronización
repository.sincronizarConServidor()
```

### OfflinePlantillaRepository

Acceso offline a plantillas completas:

```kotlin
val repository = OfflinePlantillaRepository(context)

// Obtener plantilla completa con categorías y preguntas
val plantilla = repository.obtenerPlantillaCompleta(plantillaId)

// Funciona completamente offline después de la primera carga
val plantillas = repository.obtenerPlantillasPorTipoActivo("Grúa Horquilla")
```

### OfflineReporteRepository

Creación de reportes offline con cola de sincronización:

```kotlin
val repository = OfflineReporteRepository(context)

// Crear reporte (funciona offline)
val result = repository.crearReporteConTimestampsYHorometro(
    activoId = activoId,
    usuarioId = usuarioId,
    plantillaId = plantillaId,
    respuestasConFotos = respuestasConFotos,
    timestampInicio = inicio,
    timestampFin = fin,
    duracionMinutos = duracion,
    horometroInicial = horometro,
    turno = turno
)

when {
    result.isSuccess -> {
        val reporteId = result.getOrNull()
        // Reporte creado (online o guardado en cola)
    }
    result.isFailure -> {
        // Error al guardar
    }
}

// Observar reportes pendientes
repository.observarReportesPendientes().collect { count ->
    // Número de reportes sin sincronizar
}

// Verificar conectividad
if (repository.isConnected()) {
    // Hay conexión
}
```

---

## 🔄 Flujo de Trabajo Offline

### Escenario 1: Usuario Sin Conexión

1. **Inicio de Sesión**: Requiere conexión (primera vez)
2. **Ver Lista de Activos**: Se muestran desde cache local
3. **Escanear QR**: Se busca en cache local
4. **Abrir Checklist**: Se carga plantilla desde cache
5. **Completar Checklist**: Se guarda en cola offline
6. **Confirmación**: Usuario ve mensaje "Guardado (se sincronizará cuando haya conexión)"

### Escenario 2: Recuperación de Conexión

1. **Detecta Conexión**: NetworkMonitor emite `true`
2. **WorkManager Activa**: SyncWorker se ejecuta automáticamente
3. **Sincroniza Cache**: Actualiza activos y plantillas
4. **Sincroniza Reportes Pendientes**: Sube reportes guardados offline
5. **Sube Fotos**: Sube fotos locales asociadas a reportes
6. **Notifica Éxito**: Usuario puede ver que sus reportes están sincronizados

---

## 🛠️ Uso en ViewModels

### Ejemplo: ViewModel con Soporte Offline

```kotlin
class ActivoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OfflineActivoRepository(application)

    // Estado de conectividad
    val isConnected = repository.observarConectividad()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Lista de activos (reactiva)
    val activos = repository.observarActivos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Cargar activos
    fun cargarActivos() {
        viewModelScope.launch {
            repository.obtenerTodosLosActivos()
        }
    }

    // Forzar sincronización
    fun sincronizar() {
        viewModelScope.launch {
            val success = repository.sincronizarConServidor()
            if (success) {
                // Mostrar mensaje de éxito
            }
        }
    }
}
```

---

## 📱 Indicadores de UI Sugeridos

### 1. **Badge de Reportes Pendientes**

```kotlin
@Composable
fun ReportesPendientesBadge() {
    val repository = remember { OfflineReporteRepository(LocalContext.current) }
    val pendientes by repository.observarReportesPendientes().collectAsState(initial = 0)

    if (pendientes > 0) {
        Badge(
            backgroundColor = Color.Red,
            content = { Text("$pendientes") }
        )
    }
}
```

### 2. **Indicador de Conectividad**

```kotlin
@Composable
fun ConectividadIndicator() {
    val networkMonitor = remember { NetworkMonitor(LocalContext.current) }
    val isConnected by networkMonitor.isConnected.collectAsState(initial = true)

    Row {
        Icon(
            imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
            tint = if (isConnected) Color.Green else Color.Gray
        )
        Text(if (isConnected) "Online" else "Offline")
    }
}
```

### 3. **Botón de Sincronización Manual**

```kotlin
@Composable
fun SyncButton() {
    val context = LocalContext.current
    var isSyncing by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            isSyncing = true
            SyncWorker.syncNow(context)
            // Observar estado con WorkManager
        }
    ) {
        if (isSyncing) {
            CircularProgressIndicator()
        } else {
            Icon(Icons.Default.Sync)
        }
    }
}
```

---

## 🔍 Debugging y Logs

Todos los componentes tienen logging detallado con tags específicos:

- `OfflineActivoRepository`: Operaciones de activos
- `OfflinePlantillaRepository`: Operaciones de plantillas
- `OfflineReporteRepository`: Creación y sincronización de reportes
- `SyncManager`: Estado de sincronización
- `SyncWorker`: Ejecución de sincronización en background
- `NetworkMonitor`: Cambios de conectividad

### Ver logs en Logcat:

```bash
# Ver logs de offline
adb logcat | grep "Offline"

# Ver logs de sincronización
adb logcat | grep "Sync"

# Ver logs de red
adb logcat | grep "NetworkMonitor"
```

---

## ⚙️ Configuración

### Intervalo de Sincronización

Para cambiar el intervalo de sincronización periódica, editar `SyncWorker.kt`:

```kotlin
val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
    repeatInterval = 15, // Cambiar aquí (minutos)
    repeatIntervalTimeUnit = TimeUnit.MINUTES
)
```

### Edad Máxima del Cache

Para cambiar cuándo se considera "viejo" el cache, editar `SyncManager.kt`:

```kotlin
companion object {
    private const val SYNC_MAX_AGE_MS = 5 * 60 * 1000L // Cambiar aquí (ms)
}
```

### Reintentos de Sincronización

Para cambiar el número máximo de reintentos, editar `SyncWorker.kt`:

```kotlin
companion object {
    private const val MAX_RETRY_ATTEMPTS = 3 // Cambiar aquí
}
```

---

## 🧪 Testing

### Probar Modo Offline

1. **Desactivar Conexión**:
   - En el emulador: Settings > Network > Airplane mode
   - En dispositivo real: Activar modo avión

2. **Crear Reportes**:
   - Completar checklist normalmente
   - Verificar que se guarda en cola

3. **Ver Reportes Pendientes**:
   - Verificar badge de reportes pendientes
   - Verificar indicador offline

4. **Restaurar Conexión**:
   - Desactivar modo avión
   - Observar sincronización automática (logs)
   - Verificar que reportes aparecen en servidor

### Verificar Base de Datos

Usar **Database Inspector** de Android Studio:

1. View > Tool Windows > App Inspection
2. Seleccionar tab "Database Inspector"
3. Ver tablas y datos

---

## 📈 Métricas y Monitoreo

### Consultas Útiles en Room

```kotlin
// Número de activos en cache
val count = activoDao.getCount()

// Activos desactualizados (más de 1 hora)
val outdated = activoDao.getActivosOutdated(
    timestamp = System.currentTimeMillis() - 3600000
)

// Reportes con más de 3 intentos fallidos
val reportes = reportePendienteDao.getAllReportesPendientes()
    .filter { it.intentosSincronizacion > 3 }
```

---

## 🚀 Próximos Pasos Sugeridos

1. **UI de Estado de Sincronización**:
   - Pantalla dedicada mostrando estado de sincronización
   - Lista de reportes pendientes
   - Opción de sincronización manual

2. **Notificaciones**:
   - Notificar cuando reportes se sincronicen exitosamente
   - Alertar si hay muchos reportes pendientes

3. **Estadísticas**:
   - Dashboard de uso offline
   - Tiempo promedio sin conexión
   - Número de reportes creados offline

4. **Optimizaciones**:
   - Compresión de fotos antes de guardar localmente
   - Limpieza automática de cache antiguo
   - Sincronización diferencial (solo cambios)

---

## ❓ Preguntas Frecuentes

**Q: ¿Qué pasa si cierro la app con reportes pendientes?**
A: Los reportes se mantienen en la base de datos local. WorkManager continuará intentando sincronizarlos incluso si la app está cerrada.

**Q: ¿Cuánto espacio ocupa el cache?**
A: Depende del número de activos y plantillas. Típicamente < 5 MB para 100 activos y 20 plantillas.

**Q: ¿Puedo borrar el cache manualmente?**
A: Sí, en Settings > Apps > ACA > Storage > Clear Data. Pero perderás reportes pendientes no sincronizados.

**Q: ¿Funciona el login offline?**
A: No, el login requiere conexión. Una vez autenticado, la sesión se mantiene localmente.

**Q: ¿Puedo crear/editar activos offline?**
A: Actualmente solo lectura offline. La creación/edición requiere conexión (puede implementarse con cola de sincronización).

---

## 📞 Soporte

Para problemas o preguntas sobre el modo offline, revisar:

1. Logs de la aplicación (tag "Offline", "Sync")
2. Base de datos local (Database Inspector)
3. Estado de WorkManager (Logcat tag "WM")

---

## 📄 Licencia

Este módulo es parte de la aplicación ACA y sigue la misma licencia del proyecto principal.
