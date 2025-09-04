package com.tulsa.aca.data.supabase

import com.tulsa.aca.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage


object SupabaseClient {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://syhtjgufhzcfqoekpfcp.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN5aHRqZ3VmaHpjZnFvZWtwZmNwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTY5MTgxMzUsImV4cCI6MjA3MjQ5NDEzNX0.XY5D9nj3L4Q9Q7SIFJvCklJtcKTN_nS7RvmU2oKeXXU"
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}