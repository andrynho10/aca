# AppACA - Aplicación Móvil de Inspección de Grúas

<div align="center">

**Aplicación Android para inspecciones de grúas horquilla**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7.8-green.svg)](https://developer.android.com/jetpack/compose)
[![Android API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Version](https://img.shields.io/badge/version-1.0.3-orange.svg)](https://github.com/yourorg/AppACA)

</div>

---

## 📋 Tabla de Contenidos

- [Descripción](#descripción)
- [Características](#características)
- [Capturas de Pantalla](#capturas-de-pantalla)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Configuración](#configuración)
- [Arquitectura](#arquitectura)
- [Tecnologías](#tecnologías)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Uso](#uso)
- [Desarrollo](#desarrollo)
- [Testing](#testing)
- [Build y Release](#build-y-release)
- [Troubleshooting](#troubleshooting)
- [Contribuir](#contribuir)
- [Sistema Completo](#sistema-completo)

---

## 📱 Descripción

**AppACA** es la aplicación móvil del **Sistema de Control de Inspección de Grúas Horquilla** . Permite a los operadores realizar inspecciones digitales mediante checklists personalizables, capturar evidencia fotográfica de problemas detectados, y controlar el horómetro de las grúas.

Esta aplicación funciona con una **estrategia offline-first**, permitiendo a los operadores realizar inspecciones incluso sin conexión a internet, sincronizando automáticamente los datos cuando la conexión se restablece.

### Componentes del Sistema ACA

AppACA es parte de un sistema completo que incluye:
- **AppACA** (este repositorio): Aplicación móvil para operadores
- **[webaca](../webaca)**: Dashboard web para supervisores
- **Supabase Backend**: Base de datos, autenticación, storage y edge functions

> 📖 Para ver la documentación completa del sistema, consulta el [README principal](../README.md)

---

## ✨ Características

### 🔐 Autenticación
- Login con email y contraseña (Supabase Auth)
- Validación de roles (OPERADOR/SUPERVISOR)
- Opción "Recordarme"
- Logout con limpieza completa de caché

### 🏗️ Gestión de Activos (Grúas)
- Listado de grúas (operativas, standby, inactivas)
- CRUD completo (solo supervisores)
- **Scanner QR** para identificación rápida
- Visualización de historial de cambios

### ✅ Sistema de Checklist de Inspección
- Selección de plantilla según tipo de grúa
- Respuestas BUENO/MALO con comentarios obligatorios para MALO
- **Captura de fotos** para evidencia de problemas
- Guardado automático en **borradores temporales**
- Cálculo de **score de cumplimiento** en tiempo real
- Duración de inspección rastreada automáticamente

### ⏱️ Control de Horómetro
- Registro de horómetro inicial al comenzar
- Detección de turno (1, 2, 3)
- Cierre de horómetro al finalizar
- Cálculo automático de horas de uso
- Listado de horómetros pendientes de cierre

### 📋 Gestión de Plantillas
- Creación de plantillas personalizadas
- Categorización de preguntas
- Ordenamiento de ítems
- Activación/desactivación de plantillas

### 👔 Panel de Supervisor
- Visualización resumida de reportes recientes
- Acceso a detalles de inspecciones
- Estadísticas básicas

### 📶 Sincronización Offline
- **Estrategia Offline-First**: Toda la funcionalidad disponible sin conexión
- Cache local con Room SQLite
- Sincronización automática al detectar conexión
- Cola de reportes pendientes
- Indicador visual de estado de sincronización
- Reintentos automáticos en caso de fallo

### 🔔 Notificaciones Push
- Integración con Firebase Cloud Messaging
- Registro automático de token FCM
- Recepción de alertas y avisos

---

## 📸 Capturas de Pantalla

<div align="center">
<table>
  <tr>
    <td><b>Login</b></td>
    <td><b>Home</b></td>
    <td><b>Scanner QR</b></td>
  </tr>
  <tr>
    <td><i>Autenticación con Supabase</i></td>
    <td><i>Pantalla principal</i></td>
    <td><i>Escaneo de código QR</i></td>
  </tr>
  <tr>
    <td><b>Checklist</b></td>
    <td><b>Captura de Foto</b></td>
    <td><b>Reporte</b></td>
  </tr>
  <tr>
    <td><i>Inspección en progreso</i></td>
    <td><i>Evidencia fotográfica</i></td>
    <td><i>Detalle de reporte</i></td>
  </tr>
</table>
</div>

---

## 📋 Requisitos

### Requisitos de Sistema

- **Sistema Operativo:** Android 8.0 (API 26) o superior
- **Espacio:** Mínimo 50 MB
- **Cámara:** Necesaria para captura de fotos y escaneo QR
- **Conexión:** WiFi o datos móviles (funciona offline)

### Requisitos de Desarrollo

- **Android Studio:** Hedgehog (2023.1.1) o superior
- **JDK:** 17 o superior
- **Android SDK:** API 26 - API 35
- **Gradle:** 8.5+ (incluido en Android Studio)
- **Git:** Para control de versiones

### Dependencias del Backend

- **Cuenta de Supabase:** Para base de datos, auth y storage
- **Firebase Project:** Para notificaciones push (opcional)

---

## 🚀 Instalación

### 1. Clonar el Repositorio

```bash
git clone https://github.com/yourorg/AppACA.git
cd AppACA
```

### 2. Abrir en Android Studio

1. Abrir Android Studio
2. File → Open
3. Seleccionar la carpeta `AppACA`
4. Esperar a que Gradle sincronice las dependencias

### 3. Configurar Variables de Entorno

Crear archivo `local.properties` en la raíz del proyecto:

```properties
sdk.dir=/path/to/Android/Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key-here
```

> ⚠️ **IMPORTANTE:** Nunca commitear `local.properties` al repositorio. Este archivo ya está en `.gitignore`.

### 4. Configurar Firebase (Opcional)

Para habilitar notificaciones push:

1. Crear proyecto en [Firebase Console](https://console.firebase.google.com/)
2. Agregar app Android
3. Descargar `google-services.json`
4. Colocar en `app/google-services.json`

Si no necesitas notificaciones push, puedes omitir este paso. La app funcionará normalmente sin FCM.

### 5. Sincronizar Gradle

```bash
# En Android Studio: File → Sync Project with Gradle Files
# O desde terminal:
./gradlew build
```

---

## ⚙️ Configuración

### Archivo `build.gradle.kts` (Módulo App)

```kotlin
android {
    namespace = "com.tulsa.aca"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tulsa.aca"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        // Configuración de Supabase desde local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField("String", "SUPABASE_URL", "\"${properties.getProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${properties.getProperty("SUPABASE_ANON_KEY")}\"")
    }
}
```

### Permisos (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<uses-feature
    android:name="android.hardware.camera"
    android:required="false" />
```

---

## 🏗️ Arquitectura

### Patrón de Arquitectura

AppACA sigue el patrón **MVVM (Model-View-ViewModel)** con **Repository Pattern**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ARQUITECTURA APPACA                          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────┐
│   UI Layer          │
│   (Jetpack Compose) │
│                     │
│  - Screens (16)     │
│  - Components       │
│  - Navigation       │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  ViewModel Layer    │
│  (State Management) │
│                     │
│  - ViewModels       │
│  - UI State         │
│  - Events           │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Repository Layer   │
│  (Business Logic)   │
│                     │
│  - ActivoRepository │
│  - ReporteRepository│
│  - AuthRepository   │
│  - etc.             │
└──────────┬──────────┘
           │
           ├──────────────┬──────────────┐
           ▼              ▼              ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Local        │  │ Remote       │  │ Sync         │
│ Data Source  │  │ Data Source  │  │ Manager      │
│              │  │              │  │              │
│ - Room DB    │  │ - Supabase   │  │ - WorkManager│
│ - Entities   │  │ - Postgrest  │  │ - Network    │
│ - DAOs       │  │ - Auth       │  │   Monitor    │
│              │  │ - Storage    │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

### Estrategia Offline-First

```
LECTURA (Read):
Cache Local → Si no está disponible → Servidor → Actualizar Cache

ESCRITURA (Write):
Guardar Local → Marcar como Pendiente → Sincronizar cuando haya conexión
```

### Componentes Principales

1. **UI Layer (Jetpack Compose)**
   - 16 pantallas principales
   - Componentes reutilizables (QRScanner, PhotoCapture, etc.)
   - Navegación declarativa

2. **Data Layer**
   - **Room Database**: Cache local, reportes pendientes, borradores
   - **Supabase Client**: API REST, Auth, Storage
   - **SyncManager**: Sincronización automática

3. **Domain Layer**
   - Modelos de datos (Activo, Reporte, Usuario, etc.)
   - Lógica de negocio (cálculo de score, validaciones)

---

## 🛠️ Tecnologías

### Core

| Tecnología | Versión | Uso |
|------------|---------|-----|
| Kotlin | 2.0.21 | Lenguaje de programación |
| Jetpack Compose | 1.7.8 | UI Framework declarativo |
| Compose Material3 | Latest | Componentes Material Design 3 |

### Arquitectura

| Tecnología | Versión | Uso |
|------------|---------|-----|
| Navigation Compose | 2.8.5 | Navegación entre pantallas |
| Lifecycle ViewModel | 2.8.7 | Gestión de estado |
| Room | 2.6.1 | Base de datos local SQLite |
| WorkManager | 2.9.1 | Tareas en background |

### Networking

| Tecnología | Versión | Uso |
|------------|---------|-----|
| Supabase Kotlin | 3.1.4 | Cliente Supabase (Postgrest, Auth, Storage) |
| Ktor Client | 3.1.1 | Cliente HTTP |
| Kotlinx Serialization | 1.7.3 | Serialización JSON |

### Funcionalidades

| Tecnología | Versión | Uso |
|------------|---------|-----|
| ZXing Android Embedded | 4.3.0 | Scanner QR |
| Coil Compose | 2.7.0 | Carga de imágenes |
| Zoomable | 1.6.1 | Zoom en imágenes |
| Accompanist Permissions | 0.36.0 | Manejo de permisos |
| Firebase Messaging | Latest | Notificaciones Push |

### Testing

| Tecnología | Versión | Uso |
|------------|---------|-----|
| JUnit | 4.13.2 | Unit testing |
| Mockito | Latest | Mocking |
| Espresso | Latest | UI testing |

---

## 📁 Estructura del Proyecto

```
AppACA/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/tulsa/aca/
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/              # Room Database
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── dao/           # Data Access Objects
│   │   │   │   │   │   │   ├── ActivoDao.kt
│   │   │   │   │   │   │   ├── DraftChecklistDao.kt
│   │   │   │   │   │   │   ├── PlantillaDao.kt
│   │   │   │   │   │   │   ├── ReportePendienteDao.kt
│   │   │   │   │   │   │   └── SyncStatusDao.kt
│   │   │   │   │   │   └── entities/      # Room Entities
│   │   │   │   │   │       ├── ActivoEntity.kt
│   │   │   │   │   │       ├── DraftChecklistEntity.kt
│   │   │   │   │   │       └── ...
│   │   │   │   │   │
│   │   │   │   │   ├── models/            # Data Models
│   │   │   │   │   │   ├── Models.kt
│   │   │   │   │   │   └── HistorialModels.kt
│   │   │   │   │   │
│   │   │   │   │   ├── repository/        # Repositories
│   │   │   │   │   │   ├── ActivoRepository.kt
│   │   │   │   │   │   ├── AuthRepository.kt
│   │   │   │   │   │   ├── PlantillaRepository.kt
│   │   │   │   │   │   ├── ReporteRepository.kt
│   │   │   │   │   │   ├── HorometroRepository.kt
│   │   │   │   │   │   ├── StorageRepository.kt
│   │   │   │   │   │   └── ...
│   │   │   │   │   │
│   │   │   │   │   ├── session/           # Session Management
│   │   │   │   │   │   └── UserSession.kt
│   │   │   │   │   │
│   │   │   │   │   ├── supabase/          # Supabase Client
│   │   │   │   │   │   └── SupabaseClient.kt
│   │   │   │   │   │
│   │   │   │   │   └── sync/              # Synchronization
│   │   │   │   │       ├── SyncManager.kt
│   │   │   │   │       └── ConnectivitySyncTrigger.kt
│   │   │   │   │
│   │   │   │   ├── ui/
│   │   │   │   │   ├── screens/           # 16 Screens
│   │   │   │   │   │   ├── LoginScreen.kt
│   │   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   │   ├── AssetListScreen.kt
│   │   │   │   │   │   ├── ChecklistScreen.kt     # (1196 líneas)
│   │   │   │   │   │   ├── ChecklistSelectionScreen.kt
│   │   │   │   │   │   ├── PlantillaEditorScreen.kt
│   │   │   │   │   │   ├── ActivosCrudScreen.kt
│   │   │   │   │   │   ├── CerrarHorometroScreen.kt
│   │   │   │   │   │   ├── ReportDetailsScreen.kt
│   │   │   │   │   │   ├── SupervisorPanelScreen.kt
│   │   │   │   │   │   └── ...
│   │   │   │   │   │
│   │   │   │   │   ├── components/        # Reusable Components
│   │   │   │   │   │   ├── QRScannerComponent.kt
│   │   │   │   │   │   ├── PhotoCaptureComponent.kt
│   │   │   │   │   │   └── NetworkStatusIndicator.kt
│   │   │   │   │   │
│   │   │   │   │   └── navigation/        # Navigation
│   │   │   │   │       └── Navigation.kt
│   │   │   │   │
│   │   │   │   ├── services/              # Background Services
│   │   │   │   │   └── MyFirebaseMessagingService.kt
│   │   │   │   │
│   │   │   │   ├── utils/                 # Utilities
│   │   │   │   │   ├── CacheManager.kt
│   │   │   │   │   ├── FcmManager.kt
│   │   │   │   │   ├── NetworkMonitor.kt
│   │   │   │   │   └── PreferencesManager.kt
│   │   │   │   │
│   │   │   │   └── MainActivity.kt         # Main Activity
│   │   │   │
│   │   │   ├── res/                        # Resources
│   │   │   │   ├── drawable/              # Icons, images
│   │   │   │   ├── values/                # Strings, colors, themes
│   │   │   │   └── xml/                   # XML configs
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── test/                           # Unit Tests
│   │   └── androidTest/                    # Instrumented Tests
│   │
│   ├── build.gradle.kts                    # App-level Gradle
│   └── proguard-rules.pro                  # ProGuard config
│
├── gradle/
├── build.gradle.kts                        # Project-level Gradle
├── settings.gradle.kts
├── gradle.properties
├── local.properties                        # ⚠️ NO COMMITEAR
├── google-services.json                    # ⚠️ NO COMMITEAR (opcional)
└── README.md                               # Este archivo
```

**Total:** 78 archivos Kotlin

---

## 🎯 Uso

### Flujo Básico de Inspección

1. **Login**
   - Abrir app
   - Ingresar email y contraseña
   - Marcar "Recordarme" (opcional)

2. **Seleccionar Grúa**
   - Desde Home, presionar "Iniciar Inspección"
   - Escanear código QR de la grúa
   - O buscar manualmente en la lista

3. **Seleccionar Plantilla**
   - Sistema muestra plantillas compatibles con el tipo de grúa
   - Seleccionar plantilla de checklist

4. **Iniciar Inspección**
   - Ingresar horómetro inicial
   - Seleccionar turno (1, 2, 3)
   - Presionar "Iniciar"

5. **Responder Checklist**
   - Para cada pregunta:
     - Seleccionar BUENO o MALO
     - Si es MALO: agregar comentario de observación (requerido)
     - Opcionalmente adjuntar foto(s)
   - Guardado automático en borrador

6. **Finalizar Inspección**
   - Revisar respuestas
   - Presionar "Finalizar"
   - Ver score de cumplimiento calculado

7. **Cerrar Horómetro**
   - Ingresar horómetro final
   - Sistema calcula horas de uso
   - Presionar "Cerrar"

8. **Sincronización**
   - Si hay conexión: sincroniza automáticamente
   - Si no hay conexión: queda en cola de pendientes
   - Indicador visual muestra estado

### Modo Offline

La app funciona completamente sin conexión:

- ✅ Leer datos cacheados (grúas, plantillas)
- ✅ Crear inspecciones
- ✅ Capturar fotos (guardadas localmente)
- ✅ Ver reportes anteriores
- ✅ Gestionar horómetros

Al recuperar conexión, todos los datos se sincronizan automáticamente.

---

## 💻 Desarrollo

### Setup del Entorno

```bash
# 1. Instalar Android Studio
# Descargar desde: https://developer.android.com/studio

# 2. Configurar SDK
# En Android Studio: Tools → SDK Manager
# Instalar: Android SDK 26-35

# 3. Configurar emulador (opcional)
# Tools → AVD Manager → Create Virtual Device

# 4. Clonar y abrir proyecto
git clone https://github.com/yourorg/AppACA.git
cd AppACA
# Abrir en Android Studio
```

### Comandos Útiles

```bash
# Limpiar build
./gradlew clean

# Compilar debug
./gradlew assembleDebug

# Compilar release
./gradlew assembleRelease

# Instalar en dispositivo conectado
./gradlew installDebug

# Ver dependencias
./gradlew :app:dependencies

# Lint check
./gradlew lint

# Format código
./gradlew ktlintFormat
```

### Debugging

**Logs:**
```kotlin
// En código
import android.util.Log

Log.d("TAG", "Debug message")
Log.e("TAG", "Error message")

// Ver en Logcat (Android Studio)
// Filtrar por tag o paquete: com.tulsa.aca
```

**Inspección de Base de Datos:**
```bash
# Usar Database Inspector en Android Studio
# View → Tool Windows → App Inspection → Database Inspector

# O exportar DB desde dispositivo
adb pull /data/data/com.tulsa.aca/databases/aca_database.db
```

### Agregar Nueva Pantalla

1. Crear archivo en `ui/screens/`:
```kotlin
@Composable
fun NewScreen(navController: NavController) {
    // Implementación
}
```

2. Agregar ruta en `Navigation.kt`:
```kotlin
composable("new_screen") {
    NewScreen(navController)
}
```

3. Navegar desde otra pantalla:
```kotlin
navController.navigate("new_screen")
```

### Agregar Nuevo Repository

1. Crear interface en `data/repository/`:
```kotlin
class NewRepository(
    private val supabaseClient: SupabaseClient,
    private val dao: NewDao
) {
    suspend fun getData(): List<NewModel> {
        // Implementación offline-first
        return try {
            val remote = supabaseClient.from("table").select().decodeList<NewModel>()
            dao.insertAll(remote)
            remote
        } catch (e: Exception) {
            dao.getAll()
        }
    }
}
```

2. Inyectar en ViewModel:
```kotlin
class NewViewModel(
    private val repository: NewRepository
) : ViewModel() {
    // Usar repository
}
```

---

## 🧪 Testing

### Unit Tests

```bash
# Ejecutar todos los tests
./gradlew test

# Ejecutar tests específicos
./gradlew test --tests com.tulsa.aca.data.repository.ActivoRepositoryTest

# Ver reporte
# Abre: app/build/reports/tests/testDebugUnitTest/index.html
```

### Instrumented Tests (UI)

```bash
# Ejecutar en dispositivo/emulador conectado
./gradlew connectedAndroidTest

# Ver reporte
# Abre: app/build/reports/androidTests/connected/index.html
```

### Ejemplo de Test

```kotlin
@Test
fun `calcular score de cumplimiento correctamente`() {
    val totalRespuestas = 10
    val respuestasMalas = 2

    val score = ((totalRespuestas - respuestasMalas).toFloat() / totalRespuestas) * 100

    assertEquals(80.0f, score, 0.01f)
}
```

---

## 📦 Build y Release

### Build de Debug

```bash
./gradlew assembleDebug
```

APK generado en: `app/build/outputs/apk/debug/app-debug.apk`

### Build de Release

1. Crear keystore (solo primera vez):
```bash
keytool -genkey -v -keystore aca-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias aca-key
```

2. Configurar en `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../aca-release-key.jks")
            storePassword = "your-store-password"
            keyAlias = "aca-key"
            keyPassword = "your-key-password"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(...)
        }
    }
}
```

3. Compilar release:
```bash
./gradlew assembleRelease
```

APK generado en: `app/build/outputs/apk/release/app-release.apk`

### Incrementar Versión

En `app/build.gradle.kts`:
```kotlin
defaultConfig {
    versionCode = 5        // Incrementar en 1
    versionName = "1.0.4"  // Incrementar según semver
}
```

### Distribución

**Google Play Store:**
1. Crear cuenta de desarrollador
2. Crear app en Play Console
3. Subir APK/AAB firmado
4. Completar ficha de la app
5. Publicar

**Distribución Interna:**
1. Compartir APK directamente
2. O usar Firebase App Distribution
3. O usar MDM (Mobile Device Management)

---

## 🔧 Troubleshooting

### Problema: App no sincroniza

**Diagnóstico:**
- Verificar indicador de red en la app
- Revisar Logcat para errores

**Solución:**
```kotlin
// Forzar sincronización manual
SyncManager.syncAll(forceSync = true)

// Verificar estado de red
val isConnected = NetworkMonitor.isConnected()
```

### Problema: Fotos no se guardan

**Diagnóstico:**
- Verificar permisos de cámara
- Revisar espacio disponible

**Solución:**
```kotlin
// Solicitar permisos en tiempo de ejecución
val cameraPermission = rememberPermissionState(
    android.Manifest.permission.CAMERA
)

if (!cameraPermission.status.isGranted) {
    cameraPermission.launchPermissionRequest()
}
```

### Problema: Error de autenticación

**Diagnóstico:**
- Verificar credenciales en `local.properties`
- Verificar conexión a Supabase

**Solución:**
```bash
# Verificar SUPABASE_URL y SUPABASE_ANON_KEY
cat local.properties

# Probar conexión manualmente
curl https://your-project.supabase.co/rest/v1/
```

### Problema: Build falla

**Diagnóstico:**
```bash
# Limpiar y rebuild
./gradlew clean
./gradlew build --stacktrace
```

**Soluciones comunes:**
- Invalidar caché: File → Invalidate Caches / Restart
- Actualizar Gradle Wrapper: `./gradlew wrapper --gradle-version 8.5`
- Sincronizar proyecto: File → Sync Project with Gradle Files

---

## 🤝 Contribuir

### Guía de Contribución

1. Fork el proyecto
2. Crear branch feature: `git checkout -b feature/nueva-funcionalidad`
3. Hacer commits: `git commit -m 'feat: agregar nueva funcionalidad'`
4. Push al branch: `git push origin feature/nueva-funcionalidad`
5. Crear Pull Request

### Convención de Commits

Usar [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: nueva funcionalidad
fix: corrección de bug
docs: cambios en documentación
style: formato de código
refactor: refactorización
test: agregar tests
chore: tareas de mantenimiento
```

### Estilo de Código

- Seguir [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Usar ktlint para formateo: `./gradlew ktlintFormat`
- Mantener funciones pequeñas (< 50 líneas)
- Documentar funciones públicas con KDoc

---

## 📚 Sistema Completo

AppACA es parte del **Sistema ACA** completo. Para entender cómo se integra con los demás componentes:

### Documentación Relacionada

- **[README Principal](../README.md)**: Documentación completa del sistema
- **[webaca README](../webaca/README.md)**: Dashboard web para supervisores
- **[Supabase Setup](../README.md#instalación-y-configuración)**: Configuración del backend

### Arquitectura del Sistema

```
┌──────────────┐          ┌──────────────┐
│   AppACA     │          │   webaca     │
│   (Mobile)   │          │   (Web)      │
└──────┬───────┘          └──────┬───────┘
       │                         │
       │    ┌────────────────┐   │
       └────►   SUPABASE     ◄───┘
            │   (Backend)    │
            └────────────────┘
```

### Flujo de Datos

1. **Operador** usa AppACA para crear inspección
2. **AppACA** sincroniza con Supabase
3. **Supabase** dispara Edge Function para enviar email
4. **Supervisor** ve reporte en tiempo real en webaca
5. **webaca** permite análisis y exportación de datos

---

## 📧 Contacto

Para soporte técnico o consultas:

- **Email:** andresamaya.06@gmail.com
- **Issues:** [GitHub Issues](https://github.com/yourorg/AppACA/issues)

---

## 📄 Licencia

[Especificar licencia]

---

## 📝 Changelog

### v1.0.3 (Actual)
- Sistema completo de inspecciones
- Sincronización offline mejorada
- Control de horómetro
- Scanner QR integrado

### v1.0.2
- Mejoras de performance
- Corrección de bugs de sincronización
- Optimización de carga de imágenes

### v1.0.1
- Primera versión estable
- Funcionalidades básicas de inspección

---

<div align="center">

[⬆ Volver arriba](#appaca---aplicación-móvil-de-inspección-de-grúas)

</div>
