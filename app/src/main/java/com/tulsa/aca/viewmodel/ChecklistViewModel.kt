package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.RespuestaReporte
import com.tulsa.aca.data.repository.PlantillaRepository
import com.tulsa.aca.data.repository.ReporteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RespuestaChecklistItem(
    val preguntaId: Int,
    val respuesta: Boolean? = null,
    val comentario: String = ""
)

class ChecklistViewModel : ViewModel() {
    private val plantillaRepository = PlantillaRepository()
    private val reporteRepository = ReporteRepository()

    private val _plantillaCompleta = MutableStateFlow<PlantillaChecklist?>(null)
    val plantillaCompleta: StateFlow<PlantillaChecklist?> = _plantillaCompleta.asStateFlow()

    private val _respuestas = MutableStateFlow<Map<Int, RespuestaChecklistItem>>(emptyMap())
    val respuestas: StateFlow<Map<Int, RespuestaChecklistItem>> = _respuestas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun cargarPlantillaCompleta(templateId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val plantilla = plantillaRepository.obtenerPlantillaCompleta(templateId)
                _plantillaCompleta.value = plantilla

                // Inicializar respuestas vacías
                plantilla?.let { initializeResponses(it) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun initializeResponses(plantilla: PlantillaChecklist) {
        val respuestasIniciales = mutableMapOf<Int, RespuestaChecklistItem>()

        plantilla.categorias.forEach { categoria ->
            categoria.preguntas.forEach { pregunta ->
                respuestasIniciales[pregunta.id] = RespuestaChecklistItem(
                    preguntaId = pregunta.id
                )
            }
        }

        _respuestas.value = respuestasIniciales
    }

    fun actualizarRespuesta(preguntaId: Int, respuesta: Boolean) {
        val respuestasActuales = _respuestas.value.toMutableMap()
        val item = respuestasActuales[preguntaId] ?: RespuestaChecklistItem(preguntaId)
        respuestasActuales[preguntaId] = item.copy(respuesta = respuesta)
        _respuestas.value = respuestasActuales
    }

    fun actualizarComentario(preguntaId: Int, comentario: String) {
        val respuestasActuales = _respuestas.value.toMutableMap()
        val item = respuestasActuales[preguntaId] ?: RespuestaChecklistItem(preguntaId)
        respuestasActuales[preguntaId] = item.copy(comentario = comentario)
        _respuestas.value = respuestasActuales
    }

    fun todasLasPreguntasRespondidas(): Boolean {
        return _respuestas.value.all { (_, respuesta) ->
            respuesta.respuesta != null
        }
    }



    fun guardarChecklist(
        assetId: Int,
        userId: String,
        templateId: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {

                val respuestasReporte = _respuestas.value.values.mapNotNull { respuesta ->
                    respuesta.respuesta?.let { resp ->
                        RespuestaReporte(
                            reporteId = "", // Se asignará en el repositorio
                            preguntaId = respuesta.preguntaId,
                            respuesta = resp,
                            comentario = respuesta.comentario.ifBlank { null }
                        )
                    }
                }

                val success = reporteRepository.crearReporte(
                    activoId = assetId,
                    usuarioId = userId,
                    plantillaId = templateId,
                    respuestas = respuestasReporte
                )

                if (success) {
                    onSuccess()
                } else {
                    onError("Error al guardar el checklist")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChecklistApp", "ERROR AL GUARDAR: ${e.message}", e)
                onError("Error: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }
}