package com.tulsa.aca.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.RespuestaReporte
import com.tulsa.aca.data.repository.OfflinePlantillaRepository
import com.tulsa.aca.data.repository.OfflineReporteRepository
import com.tulsa.aca.data.repository.RespuestaConFotos
import com.tulsa.aca.data.repository.HorometroRepository
import com.tulsa.aca.data.repository.DraftChecklistRepository
import com.tulsa.aca.data.repository.DraftChecklistData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Representa el estado de una respuesta individual del checklist, incluyendo fotos adjuntas */
data class RespuestaChecklistItem(
    val preguntaId: Int,
    val respuesta: Boolean? = null,  // null = sin responder; true = Bueno; false = Malo
    val comentario: String = "",
    val fotos: List<Uri> = emptyList()
)

/**
 * ViewModel del flujo de inspección; orquesta plantilla, respuestas, auto-guardado de draft y envío de reporte
 */
class ChecklistViewModel(application: Application) : AndroidViewModel(application) {
    private val plantillaRepository = OfflinePlantillaRepository(application)
    private val reporteRepository = OfflineReporteRepository(application)
    private val horometroRepository = HorometroRepository()
    private val draftRepository = DraftChecklistRepository(application)

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Estado de conectividad expuesto a la UI para mostrar el badge offline
    val isConnected = reporteRepository.observarConectividad()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Número de reportes pendientes de sincronización expuesto a la UI
    val reportesPendientes = reporteRepository.observarReportesPendientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Job del ciclo de auto-guardado; se cancela al completar o destruir el ViewModel
    private var autoSaveJob: Job? = null
    private var draftId: String? = null
    private var currentAssetId: Int? = null
    private var currentUserId: String? = null
    private var currentTemplateId: Int? = null

    private val _draftRecuperado = MutableStateFlow<DraftChecklistData?>(null)
    val draftRecuperado: StateFlow<DraftChecklistData?> = _draftRecuperado.asStateFlow()

    fun actualizarHorometroInicial(horometro: Float?) {
        _horometroInicial.value = horometro
        android.util.Log.d("ChecklistViewModel", "Horómetro inicial actualizado: $horometro")
    }

    fun actualizarTurno(turno: Int?) {
        _turnoActual.value = turno
        android.util.Log.d("ChecklistViewModel", "Turno actualizado: $turno")
    }

    fun actualizarBusqueda(query: String) {
        _searchQuery.value = query
    }

    /** Devuelve preguntas agrupadas por categoría; aplica filtro de texto si el usuario está buscando */
    fun obtenerPreguntasFiltradas(): List<Pair<com.tulsa.aca.data.models.CategoriaPlantilla, List<com.tulsa.aca.data.models.PreguntaPlantilla>>> {
        val plantilla = _plantillaCompleta.value ?: return emptyList()
        val query = _searchQuery.value.trim()

        if (query.isEmpty()) {
            // Sin búsqueda: retornar todas las categorías con sus preguntas
            return plantilla.categorias.map { categoria ->
                categoria to categoria.preguntas
            }
        }

        // Con búsqueda: omite categorías donde ninguna pregunta coincide con el texto
        return plantilla.categorias.mapNotNull { categoria ->
            val preguntasFiltradas = categoria.preguntas.filter { pregunta ->
                pregunta.texto.contains(query, ignoreCase = true)
            }
            if (preguntasFiltradas.isNotEmpty()) {
                categoria to preguntasFiltradas
            } else {
                null
            }
        }
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
                val resultado = horometroRepository.validarHorometroInicial(activoId, horometroInicial)

                if (resultado.valido) {
                    _isValidating.value = false
                    _ultimoHorometro.value = resultado.ultimoHorometro
                    _errorValidacion.value = null
                    onValido()
                } else {
                    val error = resultado.error ?: "Horómetro inválido"
                    android.util.Log.e("ChecklistViewModel", "Horómetro inválido para activo $activoId: $error")
                    _isValidating.value = false
                    _ultimoHorometro.value = resultado.ultimoHorometro
                    _errorValidacion.value = error
                    onInvalido(error)
                }

            } catch (e: Exception) {
                val errorMsg = "Error validando horómetro: ${e.message}"
                android.util.Log.e("ChecklistViewModel", errorMsg, e)
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

    /**
     * Inicia el auto-guardado periódico cada 30 segundos
     */
    private fun iniciarAutoGuardado() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(10_000) // 10 segundos

                val assetId = currentAssetId
                val userId = currentUserId
                val templateId = currentTemplateId
                val timestampInicio = _timestampInicio.value

                if (assetId != null && userId != null && templateId != null && timestampInicio != null) {
                    guardarDraftAutomatico(assetId, userId, templateId, timestampInicio)
                }
            }
        }
        android.util.Log.d("ChecklistViewModel", "Auto-guardado iniciado")
    }

    /**
     * Detiene el auto-guardado
     */
    private fun detenerAutoGuardado() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        android.util.Log.d("ChecklistViewModel", "Auto-guardado detenido")
    }

    /**
     * Guarda un draft automáticamente
     */
    private suspend fun guardarDraftAutomatico(
        assetId: Int,
        userId: String,
        templateId: Int,
        timestampInicio: Instant
    ) {
        try {
            val id = draftId ?: UUID.randomUUID().toString().also { draftId = it }
            val timestampInicioIso = DateTimeFormatter.ISO_INSTANT.format(timestampInicio)

            draftRepository.guardarDraft(
                draftId = id,
                activoId = assetId,
                usuarioId = userId,
                plantillaId = templateId,
                respuestas = _respuestas.value,
                timestampInicio = timestampInicioIso,
                horometroInicial = _horometroInicial.value,
                turno = _turnoActual.value
            )
        } catch (e: Exception) {
            android.util.Log.e("ChecklistViewModel", "Error en auto-guardado de draft", e)
        }
    }

    /**
     * Busca y recupera un draft existente
     */
    suspend fun buscarDraftExistente(
        assetId: Int,
        userId: String,
        templateId: Int
    ) {
        try {
            val draft = draftRepository.obtenerDraftReciente(userId, assetId, templateId)
            _draftRecuperado.value = draft
        } catch (e: Exception) {
            android.util.Log.e("ChecklistViewModel", "Error buscando draft existente", e)
            _draftRecuperado.value = null
        }
    }

    /**
     * Restaura un draft recuperado
     */
    fun restaurarDraft(draft: DraftChecklistData) {
        if (draft.respuestas.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "Draft ${draft.draftId} no tiene respuestas, posible corrupción")
            _draftRecuperado.value = null
            return
        }

        val validasEnDraft = draft.respuestas.values.count { it.respuesta != null }
        val currentSize = _respuestas.value.size

        if (currentSize > 0) {
            val currentValidas = _respuestas.value.values.count { it.respuesta != null }
            if (currentValidas >= validasEnDraft) {
                _draftRecuperado.value = null
                return
            }
        }

        draftId = draft.draftId
        _respuestas.value = draft.respuestas
        _timestampInicio.value = Instant.parse(draft.timestampInicio)
        _horometroInicial.value = draft.horometroInicial
        _turnoActual.value = draft.turno

        if (_respuestas.value.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "Respuestas vacías tras restaurar draft ${draft.draftId}")
        }

        _draftRecuperado.value = null
    }

    /**
     * Descarta un draft recuperado y comienza uno nuevo
     */
    suspend fun descartarDraft(draft: DraftChecklistData) {
        draftRepository.eliminarDraft(draft.draftId)
        _draftRecuperado.value = null
    }

    /**
     * Limpia el draft después de completar exitosamente el checklist
     */
    private suspend fun limpiarDraftAlCompletar() {
        val assetId = currentAssetId
        val userId = currentUserId
        val templateId = currentTemplateId

        if (assetId != null && userId != null && templateId != null) {
            draftRepository.eliminarDraftsPorPlantilla(userId, assetId, templateId)
        }
    }

    /**
     * Fuerza la sincronización manual de reportes pendientes
     */
    fun forzarSincronizacion() {
        viewModelScope.launch {
            try {
                val resultado = reporteRepository.forzarSincronizacion()
                if (resultado.isFailure) {
                    android.util.Log.e("ChecklistViewModel", "Error en sincronización manual: ${resultado.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChecklistViewModel", "Error forzando sincronización", e)
            }
        }
    }

    fun cargarPlantillaCompleta(templateId: Int, assetId: Int, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _timestampInicio.value = Instant.now()
            currentAssetId = assetId
            currentUserId = userId
            currentTemplateId = templateId

            try {
                val plantilla = plantillaRepository.obtenerPlantillaCompleta(templateId)

                if (plantilla == null) {
                    android.util.Log.e("ChecklistViewModel", "Plantilla $templateId no encontrada en repository")
                    _isLoading.value = false
                    return@launch
                }

                _plantillaCompleta.value = plantilla

                if (_respuestas.value.isEmpty()) {
                    initializeResponses(plantilla)
                }

                iniciarAutoGuardado()

            } catch (e: Exception) {
                android.util.Log.e("ChecklistViewModel", "Error cargando plantilla $templateId", e)
            } finally {
                _isLoading.value = false
            }
        }
    }



    /** Pre-carga todas las preguntas con respuesta "Bueno" (true) para que el operador solo marque las malas */
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

        if (respuestasIniciales.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "Plantilla ${plantilla.id} no tiene preguntas")
            return
        }

        if (_respuestas.value.isEmpty()) {
            _respuestas.value = respuestasIniciales
        }
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

    /**
     * Valida que todas las respuestas "Malo" tengan comentario Y foto (ambas obligatorias)
     * @return Par: (esValido, mensaje de error)
     */
    fun validarRespuestasMalas(): Pair<Boolean, String?> {
        val respuestasMalasSinValidar = _respuestas.value.filter { (_, respuesta) ->
            respuesta.respuesta == false &&
            (respuesta.comentario.isBlank() || respuesta.fotos.isEmpty())
        }

        if (respuestasMalasSinValidar.isNotEmpty()) {
            val preguntasIds = respuestasMalasSinValidar.keys.joinToString(", ")
            val mensaje = if (respuestasMalasSinValidar.size == 1) {
                "La pregunta con respuesta MALO debe tener comentario y al menos una foto."
            } else {
                "Las preguntas con respuesta MALO deben tener comentario y al menos una foto. " +
                "Revisa las preguntas que marcaste como MALO."
            }
            android.util.Log.w("ChecklistViewModel", "Validación fallida: Preguntas sin validar: $preguntasIds")
            return false to mensaje
        }

        return true to null
    }

    fun guardarChecklist(
        assetId: Int,
        userId: String,
        templateId: Int,
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_respuestas.value.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "Mapa de respuestas vacío al intentar guardar checklist (activo=$assetId, plantilla=$templateId)")
            onError("Error interno: No hay respuestas registradas. Por favor, reinicie la inspección.")
            return
        }

        if (!todasLasPreguntasRespondidas()) {
            val sinResponder = _respuestas.value.values.count { it.respuesta == null }
            android.util.Log.w("ChecklistViewModel", "Hay $sinResponder preguntas sin responder")
            onError("Por favor completa todas las preguntas antes de enviar.")
            return
        }

        // Validar que las respuestas "Malo" tengan comentario O foto
        val (validacionOk, errorValidacion) = validarRespuestasMalas()
        if (!validacionOk) {
            android.util.Log.w("ChecklistViewModel", "Validación de respuestas malas fallida: $errorValidacion")
            onError(errorValidacion ?: "Error de validación")
            return
        }

        viewModelScope.launch {
            _isSaving.value = true

            try {
                // OBTENER TIMESTAMPS EN UTC DIRECTAMENTE
                val timestampFinUTC = Instant.now()
                val timestampInicioUTC = _timestampInicio.value ?: timestampFinUTC

                // Guarda contra relojes del sistema desincronizados: garantiza inicio <= fin
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

                val nulas = _respuestas.value.values.count { it.respuesta == null }
                if (nulas > 0) {
                    android.util.Log.w("ChecklistViewModel", "$nulas respuestas con valor null al guardar checklist")
                }

                // Detecta corrupción de estado: el número de respuestas debe coincidir exactamente con las preguntas de la plantilla
                val plantilla = _plantillaCompleta.value
                if (plantilla != null) {
                    val esperadas = plantilla.categorias.sumOf { it.preguntas.size }
                    val actuales = _respuestas.value.size
                    if (actuales != esperadas) {
                        val idsEsperados = plantilla.categorias.flatMap { it.preguntas }.map { it.id }.toSet()
                        val faltantes = idsEsperados - _respuestas.value.keys
                        val sobrantes = _respuestas.value.keys - idsEsperados
                        throw IllegalStateException(
                            "Mismatch de respuestas: $actuales actuales vs $esperadas esperadas. " +
                            "Faltantes: ${faltantes.size}, Sobrantes: ${sobrantes.size}"
                        )
                    }
                }

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

                // ENVIAR CON FORMATO ISO STRING (funciona online y offline)
                val resultado = reporteRepository.crearReporteConTimestampsYHorometro(
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

                if (resultado.isSuccess) {
                    detenerAutoGuardado()
                    limpiarDraftAlCompletar()
                    _saveSuccess.value = true
                    onSuccess()
                } else {
                    val error = resultado.exceptionOrNull()?.message ?: "Error desconocido"
                    android.util.Log.e("ChecklistViewModel", "Error al guardar checklist: $error")
                    onError("Error al guardar el checklist: $error")
                }

            } catch (e: Exception) {
                android.util.Log.e("ChecklistViewModel", "Error inesperado al guardar checklist", e)
                onError("Error inesperado: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }
}

