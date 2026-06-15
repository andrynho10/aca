package com.tulsa.aca.data.supabase

import com.tulsa.aca.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage


/**
 * Singleton que provee el cliente Supabase compartido en toda la app
 * URL y clave anónima se inyectan desde BuildConfig para no quedar en el código fuente
 */
object SupabaseClient {

    private const val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    // Instancia única con Auth, Postgrest (base de datos) y Storage (fotos) habilitados
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}