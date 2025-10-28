# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Mantener información de líneas para stack traces útiles
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Serialization - CRÍTICO para Supabase
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tulsa.aca.**$$serializer { *; }
-keepclassmembers class com.tulsa.aca.** {
    *** Companion;
}
-keepclasseswithmembers class com.tulsa.aca.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor Client - necesario para las llamadas HTTP de Supabase
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**
-dontwarn org.slf4j.**

# Supabase
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Room Database - mantener entidades y DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Gson (para serialización en Room)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Modelos de datos - mantener todos los campos
-keep class com.tulsa.aca.data.model.** { *; }
-keep class com.tulsa.aca.data.local.entities.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coil (carga de imágenes)
-keep class coil.** { *; }
-dontwarn coil.**

# ZXing (códigos QR)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Retrofit/OkHttp (si se usan indirectamente)
-dontwarn okhttp3.**
-dontwarn okio.**

# Preservar nombres de clases para debugging
-keep class com.tulsa.aca.** { *; }