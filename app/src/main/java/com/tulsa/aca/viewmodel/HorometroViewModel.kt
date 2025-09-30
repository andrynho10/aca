package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.HorometroPendiente
import com.tulsa.aca.data.repository.HorometroRepository
import com.tulsa.aca.data.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InfoReporteCierre(
    val reporteId: String,
    val grua: String,
    val horometroInicial: Float,
    val turno: Int?
)

data class HorometroUiState(
    val pendientes: List<HorometroPendiente> = emptyList(),
    val reporteActual: InfoReporteCierre? = null,
    val isLoading: Boolean = false,
    val isLoadingAction: Boolean = false,
    val error: String? = null,
    val mensajeExito: String? = null
)

class HorometroViewModel : ViewModel() {

    private val repository = HorometroRepository()

    private val _uiState = MutableStateFlow(HorometroUiState())
    val uiState: StateFlow<HorometroUiState> = _uiState.asStateFlow()

    /**
     * Cargar horómetros pendientes del usuario actual
     */
    fun cargarPendientes() {
        val usuarioActual = UserSession.getCurrentUser()
        if (usuarioActual == null) {
            android.util.Log.e("HorometroVM", "No hay usuario logueado")
            _uiState.value = _uiState.value.copy(
                error = "Usuario no autenticado"
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                android.util.Log.d("HorometroVM", "Cargando pendientes para usuario: ${usuarioActual.id}")

                val pendientes = repository.obtenerHorometrosPendientes(usuarioActual.id)

                android.util.Log.d("HorometroVM", "✅ Pendientes cargados: ${pendientes.size}")

                _uiState.value = _uiState.value.copy(
                    pendientes = pendientes,
                    isLoading = false
                )

            } catch (e: Exception) {
                android.util.Log.e("HorometroVM", "❌ Error cargando pendientes: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar pendientes: ${e.message}"
                )
            }
        }
    }

    /**
     * Cargar información del reporte para cerrar horómetro
     */
    fun cargarInfoReporte(reporteId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                android.util.Log.d("HorometroVM", "Cargando info del reporte: $reporteId")

                val info = repository.obtenerInfoReporteParaCierre(reporteId)

                if (info != null) {
                    android.util.Log.d("HorometroVM", "✅ Info cargada: ${info.grua}")
                    _uiState.value = _uiState.value.copy(
                        reporteActual = info,
                        isLoading = false
                    )
                } else {
                    android.util.Log.e("HorometroVM", "❌ No se encontró info del reporte")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo cargar la información del reporte"
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("HorometroVM", "❌ Error cargando info: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Cerrar horómetro
     */
    fun cerrarHorometro(
        reporteId: String,
        horometroFinal: Float,
        observaciones: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val usuarioActual = UserSession.getCurrentUser()
        if (usuarioActual == null) {
            onError("Usuario no autenticado")
            return
        }

        _uiState.value = _uiState.value.copy(isLoadingAction = true, error = null)

        viewModelScope.launch {
            try {
                android.util.Log.d("HorometroVM", """
                    Cerrando horómetro:
                    - Reporte: $reporteId
                    - Usuario: ${usuarioActual.id}
                    - Horómetro final: $horometroFinal
                    - Observaciones: $observaciones
                """.trimIndent())

                val resultado = repository.cerrarHorometro(
                    reporteId = reporteId,
                    usuarioId = usuarioActual.id,
                    horometroFinal = horometroFinal,
                    observaciones = observaciones
                )

                if (resultado.success) {
                    android.util.Log.d("HorometroVM", "✅ Horómetro cerrado exitosamente")
                    android.util.Log.d("HorometroVM", "   Mensaje: ${resultado.mensaje}")

                    _uiState.value = _uiState.value.copy(
                        isLoadingAction = false,
                        mensajeExito = resultado.mensaje
                    )

                    // Recargar pendientes
                    cargarPendientes()

                    onSuccess()
                } else {
                    val errorMsg = resultado.error ?: "Error desconocido"
                    android.util.Log.e("HorometroVM", "❌ Error: $errorMsg")

                    _uiState.value = _uiState.value.copy(
                        isLoadingAction = false,
                        error = errorMsg
                    )

                    onError(errorMsg)
                }

            } catch (e: Exception) {
                val errorMsg = "Error al cerrar horómetro: ${e.message}"
                android.util.Log.e("HorometroVM", "❌ $errorMsg", e)

                _uiState.value = _uiState.value.copy(
                    isLoadingAction = false,
                    error = errorMsg
                )

                onError(errorMsg)
            }
        }
    }

    /**
     * Limpiar mensaje de éxito
     */
    fun limpiarMensajeExito() {
        _uiState.value = _uiState.value.copy(mensajeExito = null)
    }

    /**
     * Limpiar error
     */
    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        android.util.Log.d("HorometroVM", "HorometroViewModel destruido")
    }
}