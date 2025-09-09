package com.tulsa.aca.data.models

data class ReporteConUsuario(
    val reporte: ReporteInspeccion,
    val usuario: Usuario?
)

data class HistorialUiState(
    val activo: Activo? = null,
    val reportes: List<ReporteConUsuario> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val tipoUsuario: String = "OPERARIO" // Por defecto operario
)