package com.tulsa.aca.data.session

import com.tulsa.aca.data.models.Usuario

object UserSession {
    private var currentUser: Usuario? = null

    fun login(usuario: Usuario) {
        currentUser = usuario
        android.util.Log.d("UserSession", "Usuario logueado: ${usuario.nombreCompleto}")
    }

    fun logout() {
        android.util.Log.d("UserSession", "Cerrando sesión de: ${currentUser?.nombreCompleto}")
        currentUser = null
    }

    fun getCurrentUser(): Usuario {
        return currentUser ?: Usuario(
            id = "operador-test-001",
            nombreCompleto = "Usuario de Prueba",
            email = "test@test.com",
            rol = "OPERADOR"
        )
    }

    fun isLoggedIn(): Boolean {
        val loggedIn = currentUser != null
        android.util.Log.d("UserSession", "¿Usuario logueado? $loggedIn")
        return loggedIn
    }
}