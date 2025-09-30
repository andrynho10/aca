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
        private const val CACHE_EXPIRY_MS = 60 * 1000L // ✅ 1 minuto
        private const val CACHE_DETALLE_MS = 5 * 60 * 1000L // ✅ 5 min para volver de detalle

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
     * MÉTODO PRINCIPAL OPTIMIZADO - Cache más inteligente
     */
    fun cargarDatosSupervisor(forzarRecarga: Boolean = false, volviendoDeDetalle: Boolean = false) {
        val ahora = System.currentTimeMillis()
        val cacheExpirado = esCacheExpirado()
        val tiempoDesdeUltimaActualizacion = ahora - ultimaActualizacion.get()

        // ✅ CACHE MEJORADO - Considerar si viene de detalle
        val usarCache = !forzarRecarga &&
                datosYaCargados.get() &&
                ultimaCargaExitosa.get() &&
                _uiState.value.reportes.isNotEmpty() &&
                _uiState.value.error == null &&
                !_uiState.value.isLoading &&
                ((!cacheExpirado) || (volviendoDeDetalle && tiempoDesdeUltimaActualizacion < CACHE_DETALLE_MS))

        if (usarCache) {
            val tipoCache = when {
                volviendoDeDetalle -> "volviendo de detalle"
                cacheExpirado -> "cache expirado pero válido para detalle"
                else -> "cache válido"
            }
            android.util.Log.d("SupervisorVM", "✅ Usando datos cacheados ($tipoCache) - ${tiempoDesdeUltimaActualizacion/1000}s antiguos")
            return
        }

        val tipoRecarga = when {
            forzarRecarga -> "forzada"
            cacheExpirado && !volviendoDeDetalle -> "por expiración (${tiempoDesdeUltimaActualizacion/1000}s)"
            else -> "inicial"
        }
        android.util.Log.d("SupervisorVM", "🔄 Cargando datos frescos - Razón: $tipoRecarga")

        datosYaCargados.set(true)
        ultimaCargaExitosa.set(false)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val startTime = System.currentTimeMillis()

                // Cargar activos
                val activos = activoRepository.obtenerTodosLosActivos()

                // Cargar operadores
                val usuarios = usuarioRepository.obtenerTodosLosUsuarios()
                val operadores = usuarios.filter { it.rol == "OPERADOR" }

                // ✅ CARGAR REPORTES OPTIMIZADO - Los problemas ya vienen calculados
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
                                tieneProblemas = reporte.tieneProblemas // ⭐ Ya viene calculado desde BD!
                            )
                        )
                    }
                }

                // Ordenar por fecha descendente
                val reportesOrdenados = reportesTotales.sortedByDescending {
                    it.reporte.timestampCompletado
                }

                // ✅ YA NO NECESITAMOS calcularReportesConProblemas() - Datos ya vienen listos!

                // THREAD-SAFE ASSIGNMENT
                synchronized(this@SupervisorViewModel) {
                    todosLosReportes = reportesOrdenados
                    reportesFiltrados = todosLosReportes
                }

                val estadisticas = calcularEstadisticasOptimizado(todosLosReportes)

                // APLICAR PAGINACIÓN INICIAL
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

                val endTime = System.currentTimeMillis()
                val duracion = endTime - startTime
                android.util.Log.d("SupervisorVM", "✅ Datos cargados en ${duracion}ms: ${reportesTotales.size} reportes totales, ${reportesPaginados.size} mostrados inicialmente")

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
     * ✅ AGREGAR MÉTODO NUEVO - Para cuando vuelve de ReportDetails
     */
    fun cargarDatosDesdeDetalle() {
        cargarDatosSupervisor(forzarRecarga = false, volviendoDeDetalle = true)
    }

    /**
     * ✅ REEMPLAZAR el método filtrarReportes existente
     */
    private fun filtrarReportesOptimizado(
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

            // ✅ FILTRO "Solo con problemas" SÚPER RÁPIDO
            if (filtros.soloConProblemas && !reporte.tieneProblemas) {
                return@filter false
            }

            // Filtros de fecha (sin cambios)
            reporte.timestampCompletado?.let { timestampStr ->
                try {
                    val reporteDate = DateUtils.parseTimestamp(timestampStr)

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
                }
            }

            true
        }
    }

    /**
     * ✅ REEMPLAZAR el método calcularEstadisticas existente
     */
    private suspend fun calcularEstadisticasOptimizado(reportes: List<ReporteCompleto>): EstadisticasSupervisor {
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

        // ✅ CONTAR REPORTES CON PROBLEMAS SÚPER RÁPIDO
        val reportesConProblemas = reportes.count { it.reporte.tieneProblemas }

        reportes.forEach { reporteCompleto ->
            val reporte = reporteCompleto.reporte

            // Agregar activo al set
            activosSet.add(reporte.activoId)

            // Contar reportes por fecha
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

        android.util.Log.d("SupervisorVM", "📊 Estadísticas calculadas: Total=${reportes.size}, Hoy=$reportesHoy, Semana=$reportesEstaSemana, ConProblemas=$reportesConProblemas")

        return EstadisticasSupervisor(
            totalReportes = reportes.size,
            reportesHoy = reportesHoy,
            reportesEstaSemana = reportesEstaSemana,
            activosInspeccionados = activosSet.size,
            reportesConProblemas = reportesConProblemas
        )
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
            filtrarReportesOptimizado(todosLosReportes, filtros) // ✅ Usar método optimizado
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
}