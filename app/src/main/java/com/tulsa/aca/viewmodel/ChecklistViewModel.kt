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
import com.tulsa.aca.data.repository.HorometroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RespuestaChecklistItem(
    val preguntaId: Int,
    val respuesta: Boolean? = null,
    val comentario: String = "",
    val fotos: List<Uri> = emptyList()
)

class ChecklistViewModel : ViewModel() {
    private val plantillaRepository = PlantillaRepository()
    private val reporteRepository = ReporteRepository()
    private val horometroRepository = HorometroRepository()

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

    private val _timestampInicio = MutableStateFlow<Instant?>(null)
    private val _horometroInicial = MutableStateFlow<Float?>(null)
    val horometroInicial: StateFlow<Float?> = _horometroInicial.asStateFlow()

    private val _turnoActual = MutableStateFlow<Int?>(null)
    val turnoActual: StateFlow<Int?> = _turnoActual.asStateFlow()

    private val _ultimoHorometro = MutableStateFlow<Float?>(null)
    val ultimoHorometro: StateFlow<Float?> = _ultimoHorometro.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _errorValidacion = MutableStateFlow<String?>(null)
    val errorValidacion: StateFlow<String?> = _errorValidacion.asStateFlow()

    fun actualizarHorometroInicial(horometro: Float?) {
        _horometroInicial.value = horometro
        android.util.Log.d("ChecklistViewModel", "Horómetro inicial actualizado: $horometro")
    }

    fun actualizarTurno(turno: Int?) {
        _turnoActual.value = turno
        android.util.Log.d("ChecklistViewModel", "Turno actualizado: $turno")
    }
    fun validarHorometroInicial(
        activoId: Int,
        horometroInicial: Float,
        onValido: () -> Unit,
        onInvalido: (String) -> Unit
    ) {
        _isValidating.value = true
        _errorValidacion.value = null

        viewModelScope.launch {
            try {
                android.util.Log.d("ChecklistViewModel", "Validando horómetro inicial: $horometroInicial para activo $activoId")

                val resultado = horometroRepository.validarHorometroInicial(activoId, horometroInicial)

                if (resultado.valido) {
                    android.util.Log.d("ChecklistViewModel", "✅ Horómetro inicial válido")
                    _isValidating.value = false
                    _ultimoHorometro.value = resultado.ultimoHorometro
                    _errorValidacion.value = null
                    onValido()
                } else {
                    val error = resultado.error ?: "Horómetro inválido"
                    android.util.Log.e("ChecklistViewModel", "❌ $error")
                    _isValidating.value = false
                    _ultimoHorometro.value = resultado.ultimoHorometro
                    _errorValidacion.value = error
                    onInvalido(error)
                }

            } catch (e: Exception) {
                val errorMsg = "Error validando horómetro: ${e.message}"
                android.util.Log.e("ChecklistViewModel", "❌ $errorMsg", e)
                _isValidating.value = false
                _errorValidacion.value = errorMsg
                onInvalido(errorMsg)
            }
        }
    }

    fun clearSaveStates() {
        _saveSuccess.value = false
        _saveError.value = null
        _timestampInicio.value = null
        _horometroInicial.value = null
        _turnoActual.value = null
        _ultimoHorometro.value = null
        _errorValidacion.value = null
    }

    fun cargarPlantillaCompleta(templateId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _timestampInicio.value = Instant.now()


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
                // OBTENER TIMESTAMPS EN UTC DIRECTAMENTE
                val timestampFinUTC = Instant.now()
                val timestampInicioUTC = _timestampInicio.value ?: timestampFinUTC

                val (inicioUtcNormalizado, finUtcNormalizado) =
                    if (timestampInicioUTC <= timestampFinUTC) {
                        timestampInicioUTC to timestampFinUTC
                    } else {
                        android.util.Log.w(
                            "ChecklistViewModel",
                            "Timestamp de inicio posterior al fin; corrigiendo orden. inicio=$timestampInicioUTC fin=$timestampFinUTC"
                        )
                        timestampFinUTC to timestampInicioUTC
                    }

                // Calcular duracion en minutos sin permitir negativos
                val duracionMinutos = Duration
                    .between(inicioUtcNormalizado, finUtcNormalizado)
                    .toMinutes()
                    .coerceAtLeast(0)
                    .toInt()

                val timestampInicioIso = DateTimeFormatter
                    .ISO_INSTANT
                    .format(inicioUtcNormalizado)
                val timestampFinIso = DateTimeFormatter
                    .ISO_INSTANT
                    .format(finUtcNormalizado)
                val zonaDispositivo = ZoneId.systemDefault()
                val inicioZona = inicioUtcNormalizado.atZone(zonaDispositivo)
                val finZona = finUtcNormalizado.atZone(zonaDispositivo)

                // LOGS DE DEBUG
                android.util.Log.d("ChecklistViewModel", "Finalizando inspeccion:")
                android.util.Log.d("ChecklistViewModel", "   Inicio UTC: $inicioUtcNormalizado")
                android.util.Log.d("ChecklistViewModel", "   Fin UTC: $finUtcNormalizado")
                android.util.Log.d("ChecklistViewModel", "   Timestamp inicio enviado (ISO_INSTANT): $timestampInicioIso")
                android.util.Log.d("ChecklistViewModel", "   Timestamp fin enviado (ISO_INSTANT): $timestampFinIso")
                android.util.Log.d(
                    "ChecklistViewModel",
                    "   Timestamp inicio en zona ${zonaDispositivo.id}: ${
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(inicioZona)
                    }"
                )
                android.util.Log.d(
                    "ChecklistViewModel",
                    "   Timestamp fin en zona ${zonaDispositivo.id}: ${
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(finZona)
                    }"
                )
                android.util.Log.d("ChecklistViewModel", "   Duracion: $duracionMinutos minutos")

                // Verificar zona horaria del dispositivo
                val zonaHoraria = java.util.TimeZone.getDefault().id
                android.util.Log.d("ChecklistViewModel", "   Zona horaria dispositivo: $zonaHoraria")

                // DEBUG RESPUESTAS
                android.util.Log.d("ChecklistViewModel", "📋 PREPARANDO RESPUESTAS:")
                android.util.Log.d("ChecklistViewModel", "   Total: ${_respuestas.value.size}")

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

                // Convertir respuestas
                val respuestasConFotos = _respuestas.value.values.map { item ->
                    RespuestaConFotos(
                        respuesta = RespuestaReporte(
                            preguntaId = item.preguntaId,
                            reporteId = "",
                            respuesta = item.respuesta ?: true,
                            comentario = item.comentario.takeIf { it.isNotBlank() }
                        ),
                        fotos = item.fotos
                    )
                }

                android.util.Log.d("ChecklistViewModel", "📤 ENVIANDO AL REPOSITORY:")
                android.util.Log.d("ChecklistViewModel", "   Activo: $assetId")
                android.util.Log.d("ChecklistViewModel", "   Usuario: $userId")
                android.util.Log.d("ChecklistViewModel", "   Plantilla: $templateId")
                android.util.Log.d("ChecklistViewModel", "   Respuestas: ${respuestasConFotos.size}")
                android.util.Log.d("ChecklistViewModel", "   Horómetro inicial: ${_horometroInicial.value}")
                android.util.Log.d("ChecklistViewModel", "   Turno: ${_turnoActual.value}")

                // ENVIAR CON FORMATO ISO STRING
                val exito = reporteRepository.crearReporteConTimestampsYHorometro(
                    context = context,
                    activoId = assetId,
                    usuarioId = userId,
                    plantillaId = templateId,
                    respuestasConFotos = respuestasConFotos,
                    timestampInicio = timestampInicioIso,  // ISO 8601 en UTC
                    timestampFin = timestampFinIso,        // ISO 8601 en UTC
                    duracionMinutos = duracionMinutos,
                    horometroInicial = _horometroInicial.value,
                    turno = _turnoActual.value
                )

                android.util.Log.d("ChecklistViewModel", "📤 RESULTADO: ${if (exito) "✅ ÉXITO" else "❌ FALLÓ"}")

                if (exito) {
                    _saveSuccess.value = true
                    onSuccess()
                } else {
                    onError("Error al guardar el checklist. Intenta nuevamente.")
                }

            } catch (e: Exception) {
                android.util.Log.e("ChecklistViewModel", "❌ ERROR: ${e.message}", e)
                onError("Error inesperado: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }
}

