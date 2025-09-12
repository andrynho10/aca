package com.tulsa.aca.data.session

import com.tulsa.aca.data.models.Usuario

object UserSession {
    // Usuario hardcodeado temporal - se cambia cuando se implemente auth real
    private val currentUser = Usuario(
        id = "e5d351ff-f07a-49c3-87d8-185c58706c75",
        nombreCompleto = "Juan Pérez", // Nombre temporal
        rol = "SUPERVISOR" // Cambiar a "SUPERVISOR" para probar vista de supervisor
    )

    fun getCurrentUser(): Usuario = currentUser

    fun isOperario(): Boolean = currentUser.rol == "OPERARIO"

    fun isSupervisor(): Boolean = currentUser.rol == "SUPERVISOR"

    // Función temporal para cambiar rol (solo para testing)
    fun setUserRole(role: String) {
        // En producción, esto vendrá de la autenticación real
        // Por ahora queda comentado para evitar cambios accidentales
        // currentUser = currentUser.copy(rol = role)
    }
}