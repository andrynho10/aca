package com.tulsa.aca.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.repository.OfflinePlantillaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlantillaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OfflinePlantillaRepository(application)

    private val _plantillas = MutableStateFlow<List<PlantillaChecklist>>(emptyList())
    val plantillas: StateFlow<List<PlantillaChecklist>> = _plantillas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Estado de conectividad
    val isConnected = repository.observarConectividad()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun cargarPlantillasPorTipo(tipoActivo: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _plantillas.value = repository.obtenerPlantillasPorTipoActivo(tipoActivo)
            } finally {
                _isLoading.value = false
            }
        }
    }
}