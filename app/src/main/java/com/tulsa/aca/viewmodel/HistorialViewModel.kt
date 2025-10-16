package com.tulsa.aca.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.HistorialUiState
import com.tulsa.aca.data.models.ReporteConUsuario
import com.tulsa.aca.data.repository.OfflineActivoRepository
import com.tulsa.aca.data.repository.OfflineReporteRepository
import com.tulsa.aca.data.repository.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistorialViewModel(application: Application) : AndroidViewModel(application) {
    private val activoRepository = OfflineActivoRepository(application)
    private val reporteRepository = OfflineReporteRepository(application)
    private val usuarioRepository = UsuarioRepository()

    private val _uiState = MutableStateFlow(HistorialUiState())
    val uiState: StateFlow<HistorialUiState> = _uiState.asStateFlow()

    // Estado de conectividad
    val isConnected = reporteRepository.observarConectividad()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun cargarHistorialActivo(activoId: Int, tipoUsuario: String = "OPERADOR") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                tipoUsuario = tipoUsuario
            )

            try {
                // Cargar información del activo
                val activo = activoRepository.obtenerActivoPorId(activoId)

                if (activo != null) {
                    // Cargar historial con LÓGICA DIFERENCIADA POR TIPO DE USUARIO
                    val reportes = when (tipoUsuario) {
                        "OPERADOR" -> {
                            // Operadores: solo las 5 más recientes
                            android.util.Log.d("HistorialVM", "Cargando historial LIMITADO para operador (máximo 5 reportes)")
                            reporteRepository.obtenerHistorialLimitadoPorActivo(activoId, limite = 5)
                        }
                        "SUPERVISOR" -> {
                            // Supervisores: historial completo
                            android.util.Log.d("HistorialVM", "Cargando historial COMPLETO para supervisor")
                            reporteRepository.obtenerHistorialPorActivo(activoId)
                        }
                        else -> {
                            // Por defecto: limitado (para operadores)
                            android.util.Log.d("HistorialVM", "Tipo de usuario desconocido, usando historial limitado")
                            reporteRepository.obtenerHistorialLimitadoPorActivo(activoId, limite = 5)
                        }
                    }

                    // Cargar información de usuarios para cada reporte
                    val reportesConUsuario = reportes.map { reporte ->
                        val usuario = usuarioRepository.obtenerUsuarioPorId(reporte.usuarioId)
                        ReporteConUsuario(reporte, usuario)
                    }

                    android.util.Log.d("HistorialVM", "Historial cargado: ${reportesConUsuario.size} reportes para $tipoUsuario")

                    _uiState.value = _uiState.value.copy(
                        activo = activo,
                        reportes = reportesConUsuario,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se encontró el activo especificado"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar el historial: ${e.message}"
                )
            }
        }
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}