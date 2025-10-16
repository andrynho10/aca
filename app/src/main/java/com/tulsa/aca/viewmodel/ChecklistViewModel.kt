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

data class RespuestaChecklistItem(
    val preguntaId: Int,
    val respuesta: Boolean? = null,
    val comentario: String = "",
    val fotos: List<Uri> = emptyList()
)

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

    // Estado de conectividad
    val isConnected = reporteRepository.observarConectividad()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Número de reportes pendientes de sincronización
    val reportesPendientes = reporteRepository.observarReportesPendientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Auto-guardado
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

    fun obtenerPreguntasFiltradas(): List<Pair<com.tulsa.aca.data.models.CategoriaPlantilla, List<com.tulsa.aca.data.models.PreguntaPlantilla>>> {
        val plantilla = _plantillaCompleta.value ?: return emptyList()
        val query = _searchQuery.value.trim()

        if (query.isEmpty()) {
            // Sin búsqueda: retornar todas las categorías con sus preguntas
            return plantilla.categorias.map { categoria ->
                categoria to categoria.preguntas
            }
        }

        // Con búsqueda: filtrar preguntas que coincidan
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
        android.util.Log.d("ChecklistViewModel", "🔄 Auto-guardado iniciado (cada 30 segundos)")
    }

    /**
     * Detiene el auto-guardado
     */
    private fun detenerAutoGuardado() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        android.util.Log.d("ChecklistViewModel", "⏸️ Auto-guardado detenido")
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

            android.util.Log.d("ChecklistViewModel", "💾 Auto-guardando draft...")

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

            android.util.Log.d("ChecklistViewModel", "✅ Draft auto-guardado exitosamente")
        } catch (e: Exception) {
            android.util.Log.e("ChecklistViewModel", "❌ Error en auto-guardado: ${e.message}", e)
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
            android.util.Log.d("ChecklistViewModel", "🔍 Buscando draft existente...")

            val draft = draftRepository.obtenerDraftReciente(userId, assetId, templateId)

            if (draft != null) {
                android.util.Log.d("ChecklistViewModel", "✅ Draft encontrado: ${draft.draftId}")
                _draftRecuperado.value = draft
            } else {
                android.util.Log.d("ChecklistViewModel", "❌ No se encontró draft previo")
                _draftRecuperado.value = null
            }
        } catch (e: Exception) {
            android.util.Log.e("ChecklistViewModel", "❌ Error buscando draft: ${e.message}", e)
            _draftRecuperado.value = null
        }
    }

    /**
     * Restaura un draft recuperado
     */
    fun restaurarDraft(draft: DraftChecklistData) {
        android.util.Log.d("ChecklistViewModel", "♻️ Restaurando draft: ${draft.draftId}")

        draftId = draft.draftId
        _respuestas.value = draft.respuestas
        _timestampInicio.value = Instant.parse(draft.timestampInicio)
        _horometroInicial.value = draft.horometroInicial
        _turnoActual.value = draft.turno

        // Limpiar el estado de draft recuperado
        _draftRecuperado.value = null

        android.util.Log.d("ChecklistViewModel", "✅ Draft restaurado con ${draft.respuestas.size} respuestas")
    }

    /**
     * Descarta un draft recuperado y comienza uno nuevo
     */
    suspend fun descartarDraft(draft: DraftChecklistData) {
        android.util.Log.d("ChecklistViewModel", "🗑️ Descartando draft: ${draft.draftId}")

        draftRepository.eliminarDraft(draft.draftId)
        _draftRecuperado.value = null

        android.util.Log.d("ChecklistViewModel", "✅ Draft descartado")
    }

    /**
     * Limpia el draft después de completar exitosamente el checklist
     */
    private suspend fun limpiarDraftAlCompletar() {
        val assetId = currentAssetId
        val userId = currentUserId
        val templateId = currentTemplateId

        if (assetId != null && userId != null && templateId != null) {
            android.util.Log.d("ChecklistViewModel", "🧹 Limpiando drafts al completar checklist...")
            draftRepository.eliminarDraftsPorPlantilla(userId, assetId, templateId)
        }
    }

    /**
     * Fuerza la sincronización manual de reportes pendientes
     */
    fun forzarSincronizacion() {
        viewModelScope.launch {
            try {
                android.util.Log.d("ChecklistViewModel", "🔄 Forzando sincronización manual...")
                val resultado = reporteRepository.forzarSincronizacion()

                if (resultado.isSuccess) {
                    val sincronizados = resultado.getOrNull() ?: 0
                    android.util.Log.d("ChecklistViewModel", "✅ Sincronizados: $sincronizados reportes")
                } else {
                    android.util.Log.w("ChecklistViewModel", "⚠️ Error en sincronización: ${resultado.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChecklistViewModel", "❌ Error forzando sincronización: ${e.message}", e)
            }
        }
    }

    fun cargarPlantillaCompleta(templateId: Int, assetId: Int, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _timestampInicio.value = Instant.now()

            // Guardar contexto para auto-guardado
            currentAssetId = assetId
            currentUserId = userId
            currentTemplateId = templateId

            android.util.Log.d("ChecklistViewModel", "Iniciando inspección - Timestamp: ${_timestampInicio.value}")

            try {
                val plantilla = plantillaRepository.obtenerPlantillaCompleta(templateId)
                _plantillaCompleta.value = plantilla

                // Solo inicializar si aún no hay respuestas (para evitar el limpiado por girar pantalla)
                if (_respuestas.value.isEmpty()) {
                    plantilla?.let { initializeResponses(it) }
                }

                // Iniciar auto-guardado
                iniciarAutoGuardado()

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
            android.util.Log.w("ChecklistViewModel", "❌ Validación fallida: Preguntas sin validar: $preguntasIds")
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
        if (!todasLasPreguntasRespondidas()) {
            onError("Por favor completa todas las preguntas antes de enviar.")
            return
        }

        // Validar que las respuestas "Malo" tengan comentario O foto
        val (validacionOk, errorValidacion) = validarRespuestasMalas()
        if (!validacionOk) {
            onError(errorValidacion ?: "Error de validación")
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
                    val reporteId = resultado.getOrNull()
                    val conectado = reporteRepository.isConnected()
                    val mensaje = if (conectado) {
                        "✅ Reporte creado exitosamente (ID: $reporteId)"
                    } else {
                        "✅ Reporte guardado offline (se sincronizará cuando haya conexión)"
                    }
                    android.util.Log.d("ChecklistViewModel", mensaje)

                    // Limpiar drafts y detener auto-guardado
                    detenerAutoGuardado()
                    limpiarDraftAlCompletar()

                    _saveSuccess.value = true
                    onSuccess()
                } else {
                    val error = resultado.exceptionOrNull()?.message ?: "Error desconocido"
                    android.util.Log.e("ChecklistViewModel", "❌ Error al guardar: $error")
                    onError("Error al guardar el checklist: $error")
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

