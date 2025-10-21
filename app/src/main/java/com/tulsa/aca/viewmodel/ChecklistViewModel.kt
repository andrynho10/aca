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
        // 🔍 LOGGING DETALLADO - RECUPERACIÓN DE DRAFT
        android.util.Log.d("ChecklistViewModel", "🔄 === RECUPERANDO DRAFT ===")
        android.util.Log.d("ChecklistViewModel", "   - Draft ID: ${draft.draftId}")
        android.util.Log.d("ChecklistViewModel", "   - Respuestas en draft: ${draft.respuestas.size}")
        android.util.Log.d("ChecklistViewModel", "   - Respuestas actuales: ${_respuestas.value.size}")
        android.util.Log.d("ChecklistViewModel", "   - Timestamp draft: ${draft.timestampInicio}")
        android.util.Log.d("ChecklistViewModel", "   - Último guardado: ${draft.ultimoGuardado}")

        // 🔍 VALIDACIÓN CRÍTICA ANTES DE RESTAURAR
        if (draft.respuestas.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "❌ ERROR: El draft no tiene respuestas")
            android.util.Log.e("ChecklistViewModel", "❌ Esto podría indicar corrupción de datos")
            _draftRecuperado.value = null
            return
        }

        // Verificar integridad de respuestas en draft
        var validasEnDraft = 0
        var nulasEnDraft = 0
        draft.respuestas.forEach { (preguntaId, respuesta) ->
            if (respuesta.respuesta != null) {
                validasEnDraft++
            } else {
                nulasEnDraft++
            }
            android.util.Log.d("ChecklistViewModel", "   - Draft Pregunta $preguntaId: ${respuesta.respuesta}")
        }

        android.util.Log.d("ChecklistViewModel", "📊 Análisis del draft:")
        android.util.Log.d("ChecklistViewModel", "   - Válidas: $validasEnDraft")
        android.util.Log.d("ChecklistViewModel", "   - Nulas: $nulasEnDraft")

        // 🔍 PREVENIR SOBRESCRITURA DE RESPUESTAS VÁLIDAS
        val currentSize = _respuestas.value.size
        if (currentSize > 0) {
            android.util.Log.w("ChecklistViewModel", "⚠️ ADVERTENCIA: Ya existen $currentSize respuestas actuales")
            android.util.Log.w("ChecklistViewModel", "⚠️ Comparando con draft para decidir si restaurar...")

            // Comparar progreso
            var currentValidas = 0
            _respuestas.value.values.forEach { respuesta ->
                if (respuesta.respuesta != null) currentValidas++
            }

            android.util.Log.w("ChecklistViewModel", "   - Respuestas válidas actuales: $currentValidas")
            android.util.Log.w("ChecklistViewModel", "   - Respuestas válidas en draft: $validasEnDraft")

            if (currentValidas >= validasEnDraft) {
                android.util.Log.w("ChecklistViewModel", "🛡️ NO se restaura draft: progreso actual >= draft")
                android.util.Log.w("ChecklistViewModel", "   - Actual: $currentValidas/$currentSize")
                android.util.Log.w("ChecklistViewModel", "   - Draft: $validasEnDraft/${draft.respuestas.size}")
                _draftRecuperado.value = null
                return
            } else {
                android.util.Log.w("ChecklistViewModel", "🔄 Se restaura draft: tiene más progreso")
                android.util.Log.w("ChecklistViewModel", "   - Actual: $currentValidas/$currentSize")
                android.util.Log.w("ChecklistViewModel", "   - Draft: $validasEnDraft/${draft.respuestas.size}")
            }
        }

        // Restaurar respuestas con logging
        android.util.Log.d("ChecklistViewModel", "🔄 Restaurando ${draft.respuestas.size} respuestas del draft...")
        draftId = draft.draftId
        _respuestas.value = draft.respuestas
        _timestampInicio.value = Instant.parse(draft.timestampInicio)
        _horometroInicial.value = draft.horometroInicial
        _turnoActual.value = draft.turno

        // Verificación post-restauración
        android.util.Log.d("ChecklistViewModel", "✅ === DRAFT RESTAURADO ===")
        android.util.Log.d("ChecklistViewModel", "   - Respuestas restauradas: ${_respuestas.value.size}")
        android.util.Log.d("ChecklistViewModel", "   - Timestamp: ${_timestampInicio.value}")
        android.util.Log.d("ChecklistViewModel", "   - Horómetro: ${_horometroInicial.value}")
        android.util.Log.d("ChecklistViewModel", "   - Turno: ${_turnoActual.value}")

        // Validar que la restauración fue exitosa
        if (_respuestas.value.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "❌ ERROR CRÍTICO: Las respuestas están vacías después de restaurar")
            android.util.Log.e("ChecklistViewModel", "❌ Esto indica un problema grave en la recuperación")
        }

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
            // 🔍 LOGGING DETALLADO - INICIO DE CARGA
            android.util.Log.d("ChecklistViewModel", "🚀 === INICIANDO CARGA DE PLANTILLA COMPLETA ===")
            android.util.Log.d("ChecklistViewModel", "   Template ID: $templateId")
            android.util.Log.d("ChecklistViewModel", "   Asset ID: $assetId")
            android.util.Log.d("ChecklistViewModel", "   User ID: $userId")
            android.util.Log.d("ChecklistViewModel", "   Respuestas actuales: ${_respuestas.value.size}")

            _isLoading.value = true
            _timestampInicio.value = Instant.now()

            // Guardar contexto para auto-guardado
            currentAssetId = assetId
            currentUserId = userId
            currentTemplateId = templateId

            android.util.Log.d("ChecklistViewModel", "🕐 Timestamp inicio: ${_timestampInicio.value}")

            try {
                android.util.Log.d("ChecklistViewModel", "📥 Obteniendo plantilla del repository...")
                val plantilla = plantillaRepository.obtenerPlantillaCompleta(templateId)

                if (plantilla == null) {
                    android.util.Log.e("ChecklistViewModel", "❌ ERROR CRÍTICO: Plantilla nula desde repository")
                    android.util.Log.e("ChecklistViewModel", "❌ Template ID: $templateId")
                    _isLoading.value = false
                    return@launch
                }

                android.util.Log.d("ChecklistViewModel", "✅ Plantilla obtenida exitosamente:")
                android.util.Log.d("ChecklistViewModel", "   - ID: ${plantilla.id}")
                android.util.Log.d("ChecklistViewModel", "   - Nombre: ${plantilla.nombre}")
                android.util.Log.d("ChecklistViewModel", "   - Tipo Activo: ${plantilla.tipoActivo}")
                android.util.Log.d("ChecklistViewModel", "   - Categorías: ${plantilla.categorias.size}")

                val totalPreguntas = plantilla.categorias.sumOf { it.preguntas.size }
                android.util.Log.d("ChecklistViewModel", "   - Total preguntas: $totalPreguntas")

                _plantillaCompleta.value = plantilla

                // 🔍 VALIDACIÓN CRÍTICA ANTES DE INICIALIZAR
                if (_respuestas.value.isNotEmpty()) {
                    android.util.Log.w("ChecklistViewModel", "⚠️ El mapa de respuestas ya contiene datos:")
                    android.util.Log.w("ChecklistViewModel", "   - Respuestas existentes: ${_respuestas.value.size}")
                    android.util.Log.w("ChecklistViewModel", "   - NO se inicializará para evitar sobreescribir")

                    // Verificar integridad de respuestas existentes
                    val validas = _respuestas.value.values.count { it.respuesta != null }
                    android.util.Log.w("ChecklistViewModel", "   - Respuestas válidas: $validas/${_respuestas.value.size}")
                } else {
                    android.util.Log.d("ChecklistViewModel", "🎯 Mapa vacío, inicializando respuestas...")
                    plantilla.let {
                        initializeResponses(it)

                        // Verificar que la inicialización funcionó
                        android.util.Log.d("ChecklistViewModel", "🔍 Post-inicialización:")
                        android.util.Log.d("ChecklistViewModel", "   - Respuestas en mapa: ${_respuestas.value.size}")
                        android.util.Log.d("ChecklistViewModel", "   - Esperadas: $totalPreguntas")

                        if (_respuestas.value.size != totalPreguntas) {
                            android.util.Log.e("ChecklistViewModel", "❌ ERROR: Mismatch en inicialización!")
                            android.util.Log.e("ChecklistViewModel", "   - Esperadas: $totalPreguntas")
                            android.util.Log.e("ChecklistViewModel", "   - Inicializadas: ${_respuestas.value.size}")
                        }
                    }
                }

                // Iniciar auto-guardado
                android.util.Log.d("ChecklistViewModel", "🔄 Iniciando auto-guardado...")
                iniciarAutoGuardado()

            } catch (e: Exception) {
                android.util.Log.e("ChecklistViewModel", "❌ ERROR CRÍTICO cargando plantilla:", e)
                android.util.Log.e("ChecklistViewModel", "   - Error: ${e.message}")
                android.util.Log.e("ChecklistViewModel", "   - Template ID: $templateId")
                android.util.Log.e("ChecklistViewModel", "   - Asset ID: $assetId")
                android.util.Log.e("ChecklistViewModel", "   - User ID: $userId")
            } finally {
                android.util.Log.d("ChecklistViewModel", "🏁 === CARGA DE PLANTILLA COMPLETADA ===")
                android.util.Log.d("ChecklistViewModel", "📊 Estado final:")
                android.util.Log.d("ChecklistViewModel", "   - Loading: ${_isLoading.value}")
                android.util.Log.d("ChecklistViewModel", "   - Plantilla: ${_plantillaCompleta.value?.nombre}")
                android.util.Log.d("ChecklistViewModel", "   - Respuestas: ${_respuestas.value.size}")
                _isLoading.value = false
            }
        }
    }



    private fun initializeResponses(plantilla: PlantillaChecklist) {
        // 🔍 LOGGING DETALLADO - INICIALIZACIÓN
        android.util.Log.d("ChecklistViewModel", "🎯 === INICIALIZANDO RESPUESTAS ===")
        android.util.Log.d("ChecklistViewModel", "   Plantilla ID: ${plantilla.id}")
        android.util.Log.d("ChecklistViewModel", "   Plantilla nombre: ${plantilla.nombre}")
        android.util.Log.d("ChecklistViewModel", "   Categorías: ${plantilla.categorias.size}")

        val respuestasIniciales = mutableMapOf<Int, RespuestaChecklistItem>()
        var totalPreguntas = 0

        plantilla.categorias.forEach { categoria ->
            android.util.Log.d("ChecklistViewModel", "   Categoría: ${categoria.nombre} (${categoria.preguntas.size} preguntas)")

            categoria.preguntas.forEach { pregunta ->
                totalPreguntas++
                android.util.Log.d("ChecklistViewModel", "     - Pregunta ${pregunta.id}: ${pregunta.texto.take(50)}...")

                respuestasIniciales[pregunta.id] = RespuestaChecklistItem(
                    preguntaId = pregunta.id,
                    respuesta = true
                )
            }
        }

        android.util.Log.d("ChecklistViewModel", "📊 RESUMEN DE INICIALIZACIÓN:")
        android.util.Log.d("ChecklistViewModel", "   - Total preguntas encontradas: $totalPreguntas")
        android.util.Log.d("ChecklistViewModel", "   - Respuestas inicializadas: ${respuestasIniciales.size}")
        android.util.Log.d("ChecklistViewModel", "   - Mapa actual tiene: ${_respuestas.value.size} respuestas")

        // Verificación crítica antes de asignar
        if (respuestasIniciales.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "❌ ERROR CRÍTICO: No se encontraron preguntas en la plantilla")
            android.util.Log.e("ChecklistViewModel", "❌ Plantilla: ${plantilla.nombre} (${plantilla.id})")
            android.util.Log.e("ChecklistViewModel", "❌ Categorías encontradas: ${plantilla.categorias.size}")
            plantilla.categorias.forEach { categoria ->
                android.util.Log.e("ChecklistViewModel", "   - Categoría '${categoria.nombre}': ${categoria.preguntas.size} preguntas")
            }
        }

        // Solo asignar si está vacío para evitar sobreescribir (previene race conditions)
        val currentSize = _respuestas.value.size
        if (currentSize > 0) {
            android.util.Log.w("ChecklistViewModel", "⚠️ El mapa ya tiene $currentSize respuestas. NO se sobrecribe.")
            android.util.Log.w("ChecklistViewModel", "⚠️ Esto puede indicar una race condition o inicialización múltiple")
        } else {
            android.util.Log.d("ChecklistViewModel", "✅ Asignando ${respuestasIniciales.size} respuestas iniciales")
            _respuestas.value = respuestasIniciales
        }

        android.util.Log.d("ChecklistViewModel", "🏁 === INICIALIZACIÓN COMPLETADA ===")
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
        // 🔍 LOGGING DETALLADO - INICIO DE GUARDADO
        android.util.Log.d("ChecklistViewModel", "🚀 === INICIANDO GUARDADO DE CHECKLIST ===")
        android.util.Log.d("ChecklistViewModel", "📊 Estado actual de respuestas:")
        android.util.Log.d("ChecklistViewModel", "   - Total preguntas en mapa: ${_respuestas.value.size}")
        android.util.Log.d("ChecklistViewModel", "   - Respuestas con valor: ${_respuestas.value.values.count { it.respuesta != null }}")
        android.util.Log.d("ChecklistViewModel", "   - Respuestas nulas: ${_respuestas.value.values.count { it.respuesta == null }}")
        android.util.Log.d("ChecklistViewModel", "   - AssetID: $assetId, UserID: $userId, TemplateID: $templateId")

        // Validación mejorada: verificar que tenemos respuestas
        if (_respuestas.value.isEmpty()) {
            android.util.Log.e("ChecklistViewModel", "❌ ERROR CRÍTICO: El mapa de respuestas está completamente vacío")
            android.util.Log.e("ChecklistViewModel", "❌ Posibles causas:")
            android.util.Log.e("ChecklistViewModel", "   - initializeResponses() no se ejecutó")
            android.util.Log.e("ChecklistViewModel", "   - El mapa fue limpiado accidentalmente")
            android.util.Log.e("ChecklistViewModel", "   - Race condition con auto-guardado")
            onError("Error interno: No hay respuestas registradas. Por favor, reinicie la inspección.")
            return
        }

        if (!todasLasPreguntasRespondidas()) {
            val sinResponder = _respuestas.value.values.count { it.respuesta == null }
            android.util.Log.w("ChecklistViewModel", "⚠️ Hay $sinResponder preguntas sin responder")
            onError("Por favor completa todas las preguntas antes de enviar.")
            return
        }

        // Validar que las respuestas "Malo" tengan comentario O foto
        val (validacionOk, errorValidacion) = validarRespuestasMalas()
        if (!validacionOk) {
            android.util.Log.w("ChecklistViewModel", "⚠️ Validación de respuestas malas fallida: $errorValidacion")
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

                // 🔍 DEBUG DETALLADO DE RESPUESTAS
                android.util.Log.d("ChecklistViewModel", "📋 === ANÁLISIS DETALLADO DE RESPUESTAS ===")
                android.util.Log.d("ChecklistViewModel", "   Total en mapa: ${_respuestas.value.size}")

                var buenas = 0
                var malas = 0
                var nulas = 0
                var conComentarios = 0
                var conFotos = 0

                _respuestas.value.forEach { (preguntaId, item) ->
                    val estado = if (item.respuesta == true) {
                        buenas++
                        "✅ BUENO"
                    } else if (item.respuesta == false) {
                        malas++
                        "❌ MALO"
                    } else {
                        nulas++
                        "❓ NULL"
                    }

                    if (item.comentario.isNotBlank()) conComentarios++
                    if (item.fotos.isNotEmpty()) conFotos++

                    android.util.Log.d("ChecklistViewModel", "   - Pregunta $preguntaId: $estado")
                    android.util.Log.d("ChecklistViewModel", "     • Respuesta cruda: ${item.respuesta}")
                    android.util.Log.d("ChecklistViewModel", "     • Comentario length: ${item.comentario.length}")
                    android.util.Log.d("ChecklistViewModel", "     • Fotos count: ${item.fotos.size}")

                    if (item.respuesta == false) {
                        android.util.Log.d("ChecklistViewModel", "     • Comentario: '${item.comentario}'")
                        item.fotos.forEachIndexed { index, uri ->
                            android.util.Log.d("ChecklistViewModel", "       Foto $index: $uri")
                        }
                    }
                }

                android.util.Log.d("ChecklistViewModel", "📊 RESUMEN DE RESPUESTAS:")
                android.util.Log.d("ChecklistViewModel", "   - Buenas: $buenas")
                android.util.Log.d("ChecklistViewModel", "   - Malas: $malas")
                android.util.Log.d("ChecklistViewModel", "   - Nulas: $nulas")
                android.util.Log.d("ChecklistViewModel", "   - Con comentarios: $conComentarios")
                android.util.Log.d("ChecklistViewModel", "   - Con fotos: $conFotos")

                // 🔍 VALIDACIÓN CRÍTICA ANTES DE CONVERTIR
                if (_respuestas.value.isEmpty()) {
                    android.util.Log.e("ChecklistViewModel", "❌ ERROR CRÍTICO: Mapa de respuestas vacío ANTES de convertir")
                    throw IllegalStateException("El mapa de respuestas está vacío. No se puede continuar.")
                }

                // 🔍 VALIDACIÓN NUEVA: Comparar con preguntas esperadas
                val plantilla = _plantillaCompleta.value
                if (plantilla != null) {
                    val totalPreguntasEsperadas = plantilla.categorias.sumOf { it.preguntas.size }
                    val totalRespuestasActuales = _respuestas.value.size

                    android.util.Log.d("ChecklistViewModel", "🔍 VALIDACIÓN DE CANTIDAD:")
                    android.util.Log.d("ChecklistViewModel", "   - Preguntas esperadas: $totalPreguntasEsperadas")
                    android.util.Log.d("ChecklistViewModel", "   - Respuestas actuales: $totalRespuestasActuales")

                    if (totalRespuestasActuales != totalPreguntasEsperadas) {
                        android.util.Log.e("ChecklistViewModel", "❌ ERROR CRÍTICO: Mismatch en cantidad de respuestas")
                        android.util.Log.e("ChecklistViewModel", "❌ Preguntas esperadas: $totalPreguntasEsperadas")
                        android.util.Log.e("ChecklistViewModel", "❌ Respuestas actuales: $totalRespuestasActuales")
                        android.util.Log.e("ChecklistViewModel", "❌ Diferencia: ${totalPreguntasEsperadas - totalRespuestasActuales}")

                        // Listar preguntas faltantes
                        val idsEsperados = plantilla.categorias.flatMap { it.preguntas }.map { it.id }.toSet()
                        val idsActuales = _respuestas.value.keys
                        val faltantes = idsEsperados - idsActuales
                        val sobrantes = idsActuales - idsEsperados

                        if (faltantes.isNotEmpty()) {
                            android.util.Log.e("ChecklistViewModel", "❌ Preguntas faltantes: $faltantes")
                        }
                        if (sobrantes.isNotEmpty()) {
                            android.util.Log.w("ChecklistViewModel", "⚠️ Preguntas sobrantes: $sobrantes")
                        }

                        throw IllegalStateException(
                            "Error crítico: Hay $totalRespuestasActuales respuestas pero se esperaban $totalPreguntasEsperadas. " +
                            "Faltantes: ${faltantes.size}, Sobrantes: ${sobrantes.size}"
                        )
                    }
                } else {
                    android.util.Log.w("ChecklistViewModel", "⚠️ Plantilla es null, no se puede validar cantidad de respuestas")
                }

                if (nulas > 0) {
                    android.util.Log.w("ChecklistViewModel", "⚠️ ADVERTENCIA: Hay $nulas respuestas nulas en el mapa")
                }

                // Convertir respuestas con logging detallado
                android.util.Log.d("ChecklistViewModel", "🔄 === INICIANDO CONVERSIÓN A RespuestaConFotos ===")
                val respuestasConFotos = _respuestas.value.values.mapIndexed { index, item ->
                    android.util.Log.d("ChecklistViewModel", "   Convirtiendo respuesta $index:")
                    android.util.Log.d("ChecklistViewModel", "     - preguntaId: ${item.preguntaId}")
                    android.util.Log.d("ChecklistViewModel", "     - respuesta: ${item.respuesta}")
                    android.util.Log.d("ChecklistViewModel", "     - comentario: '${item.comentario.take(50)}...'")
                    android.util.Log.d("ChecklistViewModel", "     - fotos: ${item.fotos.size}")

                    RespuestaConFotos(
                        respuesta = RespuestaReporte(
                            preguntaId = item.preguntaId,
                            reporteId = "",
                            respuesta = item.respuesta ?: true, // Default a true para seguridad
                            comentario = item.comentario.takeIf { it.isNotBlank() }
                        ),
                        fotos = item.fotos
                    )
                }

                android.util.Log.d("ChecklistViewModel", "✅ Conversión completada. Total convertidas: ${respuestasConFotos.size}")

                if (respuestasConFotos.size != _respuestas.value.size) {
                    android.util.Log.e("ChecklistViewModel", "❌ ERROR: Mismatch en conversion!")
                    android.util.Log.e("ChecklistViewModel", "   - Original: ${_respuestas.value.size}")
                    android.util.Log.e("ChecklistViewModel", "   - Convertidas: ${respuestasConFotos.size}")
                }

                android.util.Log.d("ChecklistViewModel", "📤 === ENVIANDO AL REPOSITORY ===")
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

