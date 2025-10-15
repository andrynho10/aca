package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.repository.ActivoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActivoViewModel : ViewModel() {
    private val repository = ActivoRepository()

    private val _activos = MutableStateFlow<List<Activo>>(emptyList())
    val activos: StateFlow<List<Activo>> = _activos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activoSeleccionado = MutableStateFlow<Activo?>(null)
    val activoSeleccionado: StateFlow<Activo?> = _activoSeleccionado.asStateFlow()

    private val _qrScanError = MutableStateFlow<String?>(null)
    val qrScanError: StateFlow<String?> = _qrScanError.asStateFlow()

    fun cargarActivos() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val activosCargados = repository.obtenerTodosLosActivos()
                // Ordenar alfabéticamente por nombre
                _activos.value = activosCargados.sortedBy { it.nombre }
            } catch (e: Exception) {
                _activos.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun seleccionarActivo(activo: Activo) {
        _activoSeleccionado.value = activo
    }

    fun buscarActivoPorQR(codigoQr: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _qrScanError.value = null
            try {
                val activo = repository.obtenerActivoPorQR(codigoQr)
                if (activo != null) {
                    _activoSeleccionado.value = activo
                } else {
                    _qrScanError.value = "No se encontró un activo con el código: $codigoQr"
                }
            } catch (e: Exception) {
                _qrScanError.value = "Error al buscar el activo: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun limpiarSeleccion() {
        _activoSeleccionado.value = null
    }

    fun limpiarErrorQR() {
        _qrScanError.value = null
    }
}