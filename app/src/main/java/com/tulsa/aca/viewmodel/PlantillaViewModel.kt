package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.repository.PlantillaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlantillaViewModel : ViewModel() {
    private val repository = PlantillaRepository()

    private val _plantillas = MutableStateFlow<List<PlantillaChecklist>>(emptyList())
    val plantillas: StateFlow<List<PlantillaChecklist>> = _plantillas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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