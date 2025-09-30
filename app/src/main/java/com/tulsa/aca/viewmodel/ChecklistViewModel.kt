package com.tulsa.aca.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.RespuestaReporte
import com.tulsa.aca.data.repository.PlantillaRepository
import com.tulsa.aca.data.repository.ReporteRepository
import com.tulsa.aca.data.repository.RespuestaConFotos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RespuestaChecklistItem(
    val preguntaId: Int,
    val respuesta: Boolean? = null,
    val comentario: String = "",
    val fotos: List<Uri> = emptyList()
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

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _timestampInicio = MutableStateFlow<Long?>(null)
    private val _horometroInicial = MutableStateFlow<Float?>(null)
    val horometroInicial: StateFlow<Float?> = _horometroInicial.asStateFlow()

    private val _turnoActual = MutableStateFlow<Int?>(null)
    val turnoActual: StateFlow<Int?> = _turnoActual.asStateFlow()

    fun actualizarHorometroInicial(horometro: Float?) {
        _horometroInicial.value = horometro
        android.util.Log.d("ChecklistViewModel", "Horómetro inicial actualizado: $horometro")
    }

    fun actualizarTurno(turno: Int?) {
        _turnoActual.value = turno
        android.util.Log.d("ChecklistViewModel", "Turno actualizado: $turno")
    }

    // ========== MODIFICAR LA FUNCIÓN clearSaveStates() ==========
    fun clearSaveStates() {
        _saveSuccess.value = false
        _saveError.value = null
        _timestampInicio.value = null
        _horometroInicial.value = null  // ⭐ AGREGAR
        _turnoActual.value = null        // ⭐ AGREGAR
    }

    fun cargarPlantillaCompleta(templateId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _timestampInicio.value = System.currentTimeMillis()

            android.util.Log.d("ChecklistViewModel", "Iniciando inspección - Timestamp: ${_timestampInicio.value}")

            try {
                val plantilla = plantillaRepository.obtenerPlantillaCompleta(templateId)
                _plantillaCompleta.value = plantilla

                // Solo inicializar si aún no hay respuestas (para evitar el limpiado por girar pantalla)
                if (_respuestas.value.isEmpty()) {
                    plantilla?.let { initializeResponses(it) }
                }
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
                    preguntaId = pregunta.id,
                    respuesta = true
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

    fun actualizarFotos(preguntaId: Int, fotos: List<Uri>) {
        val respuestasActuales = _respuestas.value.toMutableMap()
        val item = respuestasActuales[preguntaId] ?: RespuestaChecklistItem(preguntaId)
        respuestasActuales[preguntaId] = item.copy(fotos = fotos)
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
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!todasLasPreguntasRespondidas()) {
            onError("Por favor completa todas las preguntas antes de enviar.")
            return
        }

        viewModelScope.launch {
            _isSaving.value = true

            try {
                // Calcular duración
                val timestampFin = System.currentTimeMillis()
                val timestampInicio = _timestampInicio.value ?: timestampFin
                val duracionMinutos = ((timestampFin - timestampInicio) / 1000 / 60).toInt()

                android.util.Log.d("ChecklistViewModel", "⏰ Finalizando inspección:")
                android.util.Log.d("ChecklistViewModel", "   Inicio: $timestampInicio")
                android.util.Log.d("ChecklistViewModel", "   Fin: $timestampFin")
                android.util.Log.d("ChecklistViewModel", "   Duración: $duracionMinutos minutos")

                // 🆕 DEBUG DETALLADO DE RESPUESTAS
                android.util.Log.d("ChecklistViewModel", "📋 PREPARANDO RESPUESTAS PARA ENVIAR:")
                android.util.Log.d("ChecklistViewModel", "   Total respuestas en memoria: ${_respuestas.value.size}")

                _respuestas.value.forEach { (preguntaId, item) ->
                    val estado = if (item.respuesta == true) "✅ BUENO" else if (item.respuesta == false) "❌ MALO" else "❓ NULL"
                    android.util.Log.d("ChecklistViewModel", "   - Pregunta $preguntaId: $estado")
                    if (item.respuesta == false) {
                        android.util.Log.d("ChecklistViewModel", "     Comentario: '${item.comentario}'")
                    }
                    if (item.fotos.isNotEmpty()) {
                        android.util.Log.d("ChecklistViewModel", "     Fotos: ${item.fotos.size}")
                    }
                }

                // Convertir respuestas con fotos...
                val respuestasConFotos = _respuestas.value.values.map { item ->
                    RespuestaConFotos(
                        respuesta = RespuestaReporte(
                            preguntaId = item.preguntaId,
                            reporteId = "", // Se asignará en el repository
                            respuesta = item.respuesta ?: true,
                            comentario = item.comentario.takeIf { it.isNotBlank() }
                        ),
                        fotos = item.fotos
                    )
                }

                android.util.Log.d("ChecklistViewModel", "📤 ENVIANDO AL REPOSITORY:")
                android.util.Log.d("ChecklistViewModel", "   Activo ID: $assetId")
                android.util.Log.d("ChecklistViewModel", "   Usuario ID: $userId")
                android.util.Log.d("ChecklistViewModel", "   Plantilla ID: $templateId")
                android.util.Log.d("ChecklistViewModel", "   Respuestas: ${respuestasConFotos.size}")

                // Llamar repository con horómetro y turno
                val exito = reporteRepository.crearReporteConTimestampsYHorometro(
                    context = context,
                    activoId = assetId,
                    usuarioId = userId,
                    plantillaId = templateId,
                    respuestasConFotos = respuestasConFotos,
                    timestampInicio = java.time.Instant.ofEpochMilli(timestampInicio).toString(),
                    timestampFin = java.time.Instant.ofEpochMilli(timestampFin).toString(),
                    duracionMinutos = duracionMinutos,
                    horometroInicial = _horometroInicial.value,
                    turno = _turnoActual.value
                )

                android.util.Log.d("ChecklistViewModel", "📤 RESULTADO DEL REPOSITORY: ${if (exito) "✅ ÉXITO" else "❌ FALLÓ"}")

                if (exito) {
                    _saveSuccess.value = true
                    onSuccess()
                } else {
                    onError("Error al guardar el checklist. Intenta nuevamente.")
                }

            } catch (e: Exception) {
                android.util.Log.e("ChecklistViewModel", "ERROR: ${e.message}", e)
                onError("Error inesperado: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }
}