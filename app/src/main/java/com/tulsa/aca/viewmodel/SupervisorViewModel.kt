package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.Activo
import com.tulsa.aca.data.models.PlantillaChecklist
import com.tulsa.aca.data.models.ReporteConUsuario
import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.repository.ActivoRepository
import com.tulsa.aca.data.repository.PlantillaRepository
import com.tulsa.aca.data.repository.ReporteRepository
import com.tulsa.aca.data.repository.UsuarioRepository
import com.tulsa.aca.utils.CacheManager
import com.tulsa.aca.utils.Cacheable
import com.tulsa.aca.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

data class FiltrosReporte(
    val activoSeleccionado: Activo? = null,
    val operadorSeleccionado: Usuario? = null,
    val fechaDesde: Date? = null,
    val fechaHasta: Date? = null,
    val soloConProblemas: Boolean = false
)

data class EstadisticasSupervisor(
    val totalReportes: Int = 0,
    val reportesHoy: Int = 0,
    val reportesEstaSemana: Int = 0,
    val activosInspeccionados: Int = 0,
    val reportesConProblemas: Int = 0
)

data class ReporteCompleto(
    val reporte: com.tulsa.aca.data.models.ReporteInspeccion,
    val usuario: Usuario?,
    val activo: Activo?,
    val plantilla: PlantillaChecklist?,
    val tieneProblemas: Boolean = false
)

data class SupervisorUiState(
    val reportes: List<ReporteCompleto> = emptyList(),
    val activos: List<Activo> = emptyList(),
    val operadores: List<Usuario> = emptyList(),
    val estadisticas: EstadisticasSupervisor = EstadisticasSupervisor(),
    val filtros: FiltrosReporte = FiltrosReporte(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // CAMPOS PARA PAGINACIÓN
    val reportesMostrados: Int = 15, // Cuántos se están mostrando
    val totalReportesDisponibles: Int = 0, // Total después de filtros
    val puedeCargarMas: Boolean = false, // Si hay más para cargar
    val isLoadingMore: Boolean = false // Loading para "Cargar más"
)

class SupervisorViewModel : ViewModel(), Cacheable {

    private val reporteRepository = ReporteRepository()
    private val activoRepository = ActivoRepository()
    private val usuarioRepository = UsuarioRepository()
    private val plantillaRepository = PlantillaRepository()

    private val _uiState = MutableStateFlow(SupervisorUiState())
    val uiState: StateFlow<SupervisorUiState> = _uiState.asStateFlow()

    // THREAD-SAFE: AtomicLong y AtomicBoolean para thread safety
    private val ultimaActualizacion = AtomicLong(0L)
    private val datosYaCargados = AtomicBoolean(false)
    private val ultimaCargaExitosa = AtomicBoolean(false)

    // Datos por instancia
    @Volatile
    private var todosLosReportes: List<ReporteCompleto> = emptyList()
    @Volatile
    private var reportesFiltrados: List<ReporteCompleto> = emptyList()

    // CONSTANTES DE PAGINACIÓN
    companion object {
        private const val REPORTES_POR_PAGINA = 15
        private const val CACHE_EXPIRY_MS = 3 * 60 * 1000L // 3 minutos

        // MÉTODOS LEGACY PARA COMPATIBILIDAD HACIA ATRÁS
        @Deprecated("Usar CacheManager.limpiarTodosLosCaches()", ReplaceWith("CacheManager.limpiarTodosLosCaches()"))
        fun limpiarCacheTodasLasInstancias() {
            android.util.Log.d("SupervisorVM", "Método legacy llamado - redirigiendo a CacheManager")
            CacheManager.limpiarTodosLosCaches()
        }

        @Deprecated("Usar CacheManager.obtenerInfoCaches()", ReplaceWith("CacheManager.obtenerInfoCaches()"))
        fun obtenerInfoCache(): String {
            return "Migrado a CacheManager - usar CacheManager.obtenerInfoCaches()"
        }

        fun invalidarCacheTemporal() {
            android.util.Log.d("SupervisorVM", "⚠invalidarCacheTemporal() es deprecated - usar instancias individuales")
            // Para compatibilidad, limpiar todos los cachés
            CacheManager.limpiarTodosLosCaches()
        }
    }

    init {
        // REGISTRAR EN CACHE MANAGER MEJORADO
        CacheManager.registrar(this)
        android.util.Log.d("SupervisorVM", "SupervisorViewModel creado y registrado en CacheManager")
    }

    // IMPLEMENTACIÓN DE CACHEABLE INTERFACE
    override fun limpiarCache() {
        android.util.Log.d("SupervisorVM", "Limpiando caché interno de SupervisorViewModel")

        // Limpiar listas de datos de forma thread-safe
        synchronized(this) {
            todosLosReportes = emptyList()
            reportesFiltrados = emptyList()
        }

        // THREAD-SAFE: Usar atomic operations
        datosYaCargados.set(false)
        ultimaCargaExitosa.set(false)
        ultimaActualizacion.set(0L)

        // Resetear estado UI
        _uiState.value = SupervisorUiState()

        android.util.Log.d("SupervisorVM", "Caché interno limpiado - Estado reseteado")
    }

    override fun obtenerInfoCache(): String {
        return "Reportes: ${todosLosReportes.size}, Filtrados: ${reportesFiltrados.size}, " +
                "Última actualización: ${(System.currentTimeMillis() - ultimaActualizacion.get()) / 1000}s atrás"
    }

    override fun esCacheExpirado(): Boolean {
        return (System.currentTimeMillis() - ultimaActualizacion.get()) > CACHE_EXPIRY_MS
    }

    // CLEANUP AL DESTRUIR
    override fun onCleared() {
        super.onCleared()
        CacheManager.desregistrar(this)
        android.util.Log.d("SupervisorVM", "🗑️ SupervisorViewModel destruido y desregistrado del CacheManager")
    }

    /**
     * MÉTODO PRINCIPAL OPTIMIZADO - Carga datos con caché inteligente
     */
    fun cargarDatosSupervisor(forzarRecarga: Boolean = false) {
        val ahora = System.currentTimeMillis()
        val cacheExpirado = esCacheExpirado()

        // USAR CACHÉ SI ESTÁ FRESCO Y VÁLIDO
        if (!forzarRecarga &&
            datosYaCargados.get() &&
            ultimaCargaExitosa.get() &&
            !cacheExpirado &&
            _uiState.value.reportes.isNotEmpty() &&
            _uiState.value.error == null &&
            !_uiState.value.isLoading) {

            android.util.Log.d("SupervisorVM", "Usando datos cacheados (${(ahora - ultimaActualizacion.get())/1000}s antiguos)")
            return
        }

        val tipoRecarga = when {
            forzarRecarga -> "forzada"
            cacheExpirado -> "por expiración (${(ahora - ultimaActualizacion.get())/1000}s)"
            else -> "inicial"
        }
        android.util.Log.d("SupervisorVM", "📡 Cargando datos frescos - Razón: $tipoRecarga")

        datosYaCargados.set(true)
        ultimaCargaExitosa.set(false)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Cargar activos
                val activos = activoRepository.obtenerTodosLosActivos()

                // Cargar operadores
                val usuarios = usuarioRepository.obtenerTodosLosUsuarios()
                val operadores = usuarios.filter { it.rol == "OPERADOR" }

                // Cargar todos los reportes CON INFORMACIÓN COMPLETA
                val reportesTotales = mutableListOf<ReporteCompleto>()

                activos.forEach { activo ->
                    val reportesActivo = reporteRepository.obtenerHistorialPorActivo(activo.id ?: 0)
                    reportesActivo.forEach { reporte ->
                        val usuario = usuarioRepository.obtenerUsuarioPorId(reporte.usuarioId)
                        val plantilla = try {
                            plantillaRepository.obtenerPlantillaCompleta(reporte.plantillaId)
                        } catch (e: Exception) {
                            null
                        }

                        reportesTotales.add(
                            ReporteCompleto(
                                reporte = reporte,
                                usuario = usuario,
                                activo = activo,
                                plantilla = plantilla,
                                tieneProblemas = false // Se calcula después
                            )
                        )
                    }
                }

                // Ordenar por fecha descendente
                val reportesOrdenados = reportesTotales.sortedByDescending {
                    it.reporte.timestampCompletado
                }

                // CALCULAR CUÁLES TIENEN PROBLEMAS
                val reportesConProblemas = calcularReportesConProblemas(reportesOrdenados)

                // THREAD-SAFE ASSIGNMENT
                synchronized(this@SupervisorViewModel) {
                    todosLosReportes = reportesConProblemas
                    reportesFiltrados = todosLosReportes // Inicialmente sin filtros
                }

                val estadisticas = calcularEstadisticas(todosLosReportes)

                // APLICAR PAGINACIÓN INICIAL (primeros 15)
                val reportesPaginados = reportesFiltrados.take(REPORTES_POR_PAGINA)

                _uiState.value = _uiState.value.copy(
                    reportes = reportesPaginados,
                    activos = activos,
                    operadores = operadores,
                    estadisticas = estadisticas,
                    totalReportesDisponibles = reportesFiltrados.size,
                    reportesMostrados = reportesPaginados.size,
                    puedeCargarMas = reportesFiltrados.size > REPORTES_POR_PAGINA,
                    isLoading = false
                )

                ultimaActualizacion.set(ahora)
                ultimaCargaExitosa.set(true)

                android.util.Log.d("SupervisorVM", "Datos cargados: ${reportesTotales.size} reportes totales, ${reportesPaginados.size} mostrados inicialmente")

            } catch (e: Exception) {
                android.util.Log.e("SupervisorVM", "Error cargando datos: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar datos: ${e.message}"
                )
                ultimaCargaExitosa.set(false)
            }
        }
    }

    /**
     * PAGINACIÓN - Cargar más reportes
     */
    fun cargarMasReportes() {
        val estadoActual = _uiState.value

        if (estadoActual.isLoadingMore || !estadoActual.puedeCargarMas) {
            return // Ya está cargando o no hay más
        }

        android.util.Log.d("SupervisorVM", "Cargando más reportes...")

        _uiState.value = _uiState.value.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val reportesMostradosActual = estadoActual.reportesMostrados
                val siguientesCantidad = REPORTES_POR_PAGINA
                val nuevoTotal = reportesMostradosActual + siguientesCantidad

                // Tomar más reportes de los filtrados (thread-safe read)
                val reportesAmpliados = synchronized(this@SupervisorViewModel) {
                    reportesFiltrados.take(nuevoTotal)
                }

                _uiState.value = _uiState.value.copy(
                    reportes = reportesAmpliados,
                    reportesMostrados = reportesAmpliados.size,
                    puedeCargarMas = reportesAmpliados.size < reportesFiltrados.size,
                    isLoadingMore = false
                )

                android.util.Log.d("SupervisorVM", "Cargados ${reportesAmpliados.size - reportesMostradosActual} reportes adicionales")

            } catch (e: Exception) {
                android.util.Log.e("SupervisorVM", "Error cargando más reportes: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "Error al cargar más reportes: ${e.message}"
                )
            }
        }
    }

    /**
     * FILTROS - Aplicar filtros con paginación reset
     */
    fun aplicarFiltros(filtros: FiltrosReporte) {
        android.util.Log.d("SupervisorVM", "Aplicando filtros: soloConProblemas=${filtros.soloConProblemas}")

        // Thread-safe filtering
        val nuevosReportesFiltrados = synchronized(this) {
            filtrarReportes(todosLosReportes, filtros)
        }

        reportesFiltrados = nuevosReportesFiltrados

        // Resetear paginación a los primeros 15
        val reportesPaginados = reportesFiltrados.take(REPORTES_POR_PAGINA)

        _uiState.value = _uiState.value.copy(
            reportes = reportesPaginados,
            filtros = filtros,
            totalReportesDisponibles = reportesFiltrados.size,
            reportesMostrados = reportesPaginados.size,
            puedeCargarMas = reportesFiltrados.size > REPORTES_POR_PAGINA
        )

        android.util.Log.d("SupervisorVM", "Filtros aplicados: ${reportesFiltrados.size} reportes encontrados, ${reportesPaginados.size} mostrados")
    }

    fun limpiarFiltros() {
        synchronized(this) {
            reportesFiltrados = todosLosReportes
        }
        val reportesPaginados = reportesFiltrados.take(REPORTES_POR_PAGINA)

        _uiState.value = _uiState.value.copy(
            reportes = reportesPaginados,
            filtros = FiltrosReporte(),
            totalReportesDisponibles = reportesFiltrados.size,
            reportesMostrados = reportesPaginados.size,
            puedeCargarMas = reportesFiltrados.size > REPORTES_POR_PAGINA
        )
    }

    /**
     * Method para pull-to-refresh
     */
    fun forzarRecarga() {
        cargarDatosSupervisor(forzarRecarga = true)
    }

    /**
     * Invalidar caché cuando llega un nuevo reporte
     */
    fun invalidarCachePorNuevoReporte() {
        ultimaActualizacion.set(0L) // Fuerza expiración inmediata
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * FUNCIÓN PÚBLICA PARA OBTENER INFO DE ESTA INSTANCIA (COMPATIBILIDAD)
     */
    fun obtenerInfoCacheInstancia(): String {
        return obtenerInfoCache()
    }

    // ===============================
    // MÉTODOS PRIVADOS AUXILIARES
    // ===============================

    /**
     * FUNCIÓN DE FILTROS - Thread-safe
     */
    private fun filtrarReportes(
        reportes: List<ReporteCompleto>,
        filtros: FiltrosReporte
    ): List<ReporteCompleto> {
        return reportes.filter { reporteCompleto ->
            val reporte = reporteCompleto.reporte

            // Filtro por activo
            if (filtros.activoSeleccionado != null &&
                reporte.activoId != filtros.activoSeleccionado.id) {
                return@filter false
            }

            // Filtro por operador
            if (filtros.operadorSeleccionado != null &&
                reporte.usuarioId != filtros.operadorSeleccionado.id) {
                return@filter false
            }

            // Filtro "Solo con problemas"
            if (filtros.soloConProblemas && !reporteCompleto.tieneProblemas) {
                return@filter false
            }

            // Filtros de fecha
            reporte.timestampCompletado?.let { timestampStr ->
                try {
                    // Parsear la fecha del reporte
                    val reporteDate = DateUtils.parseTimestamp(timestampStr)

                    // Filtro fecha desde
                    filtros.fechaDesde?.let { fechaDesde ->
                        val fechaDesdeStartOfDay = Calendar.getInstance().apply {
                            time = fechaDesde
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.time

                        if (reporteDate.before(fechaDesdeStartOfDay)) {
                            return@filter false
                        }
                    }

                    // Filtro fecha hasta
                    filtros.fechaHasta?.let { fechaHasta ->
                        val fechaHastaEndOfDay = Calendar.getInstance().apply {
                            time = fechaHasta
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.time

                        if (reporteDate.after(fechaHastaEndOfDay)) {
                            return@filter false
                        }
                    }

                } catch (e: Exception) {
                    android.util.Log.e("SupervisorVM", "Error parseando fecha: ${e.message}")
                    // Si hay error parseando, incluir el reporte (no filtrar por fecha)
                }
            }

            true
        }
    }

    /**
     * FUNCIÓN PARA CALCULAR REPORTES CON PROBLEMAS
     */
    private suspend fun calcularReportesConProblemas(reportes: List<ReporteCompleto>): List<ReporteCompleto> {
        if (reportes.isEmpty()) return reportes

        try {
            // Obtener IDs de todos los reportes
            val reporteIds = reportes.mapNotNull { it.reporte.id }

            // UNA SOLA LLAMADA para verificar todos los reportes con problemas
            val reportesConProblemasMap = reporteRepository.verificarReportesConProblemas(reporteIds)

            // Actualizar cada reporte con su estado de problemas
            return reportes.map { reporteCompleto ->
                val tieneProblemas = reportesConProblemasMap[reporteCompleto.reporte.id] ?: false
                reporteCompleto.copy(tieneProblemas = tieneProblemas)
            }
        } catch (e: Exception) {
            android.util.Log.e("SupervisorVM", "Error calculando reportes con problemas: ${e.message}", e)
            return reportes // Devolver sin modificar si hay error
        }
    }

    private suspend fun calcularEstadisticas(reportes: List<ReporteCompleto>): EstadisticasSupervisor {
        val hoy = Calendar.getInstance()
        val inicioSemana = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var reportesHoy = 0
        var reportesEstaSemana = 0
        val activosSet = mutableSetOf<Int>()

        // Contar reportes con problemas usando el campo calculado
        val reportesConProblemas = reportes.count { it.tieneProblemas }

        reportes.forEach { reporteCompleto ->
            val reporte = reporteCompleto.reporte

            // Agregar activo al set
            activosSet.add(reporte.activoId)

            // Contar reportes por fecha (implementación básica mejorable)
            reporte.timestampCompletado?.let { timestamp ->
                try {
                    val reporteDate = DateUtils.parseTimestamp(timestamp)

                    // Verificar si es de hoy
                    val esDeHoy = Calendar.getInstance().apply {
                        time = reporteDate
                    }.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR) &&
                            Calendar.getInstance().apply {
                                time = reporteDate
                            }.get(Calendar.YEAR) == hoy.get(Calendar.YEAR)

                    if (esDeHoy) {
                        reportesHoy++
                    }

                    // Verificar si es de esta semana
                    if (reporteDate.after(inicioSemana.time) || reporteDate.equals(inicioSemana.time)) {
                        reportesEstaSemana++
                    }

                } catch (e: Exception) {
                    android.util.Log.w("SupervisorVM", "Error parseando fecha para estadísticas: ${e.message}")
                }
            }
        }

        android.util.Log.d("SupervisorVM", "Estadísticas: Total=${reportes.size}, Hoy=$reportesHoy, Semana=$reportesEstaSemana, ConProblemas=$reportesConProblemas")

        return EstadisticasSupervisor(
            totalReportes = reportes.size,
            reportesHoy = reportesHoy,
            reportesEstaSemana = reportesEstaSemana,
            activosInspeccionados = activosSet.size,
            reportesConProblemas = reportesConProblemas
        )
    }
}