import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    kotlin("plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

android {
    namespace = "com.tulsa.aca"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile") ?: "release.keystore")
            storePassword = keystoreProperties.getProperty("storePassword") ?: ""
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: ""
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
        }
    }

    defaultConfig {
        applicationId = "com.tulsa.aca"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("supabase.anon_key")}\"")

    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true  // Activo: reduce tamaño APK
            isShrinkResources = true  // Activo: elimina recursos no usados
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"), // Básico, no agresivo
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


    // Supabase (versiones conservadoras)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")

    // Ktor
    implementation("io.ktor:ktor-client-android:3.1.1")

    // Serialización
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ViewModel y Compose (versiones conservadoras)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // QR Code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Permisos de cámara
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Manejo de imágenes
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Compresión de imágenes
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Zoom imágenes
    implementation("net.engawapg.lib:zoomable:1.6.1")

    // Pull to refresh
    implementation("androidx.compose.material:material:1.5.4")

    // Room Database para soporte offline
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager para sincronización en background
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Gson para serialización de objetos complejos en Room
    implementation("com.google.code.gson:gson:2.10.1")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
}
