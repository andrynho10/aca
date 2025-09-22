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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FiltrosReporte(
    val activoSeleccionado: Activo? = null,
    val operadorSeleccionado: Usuario? = null,
    val fechaDesde: String? = null,
    val fechaHasta: String? = null,
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

class SupervisorViewModel : ViewModel() {
    private val reporteRepository = ReporteRepository()
    private val activoRepository = ActivoRepository()
    private val usuarioRepository = UsuarioRepository()
    private val plantillaRepository = PlantillaRepository()

    private val _uiState = MutableStateFlow(SupervisorUiState())
    val uiState: StateFlow<SupervisorUiState> = _uiState.asStateFlow()

    // CONSTANTES DE PAGINACIÓN
    companion object {
        private const val REPORTES_POR_PAGINA = 15
    }

    private var todosLosReportes: List<ReporteCompleto> = emptyList()
    private var reportesFiltrados: List<ReporteCompleto> = emptyList()

    // Caché para evitar recargas innecesarias
    private var datosYaCargados = false
    private var ultimaCargaExitosa = false
    private var ultimaActualizacion = 0L
    private val cacheExpiry = 3 * 60 * 1000L // 3 minutos

    fun cargarDatosSupervisor(forzarRecarga: Boolean = false) {
        val ahora = System.currentTimeMillis()
        val cacheExpirado = (ahora - ultimaActualizacion) > cacheExpiry

        // Usar caché si está fresco y no se fuerza recarga
        if (!forzarRecarga &&
            datosYaCargados &&
            ultimaCargaExitosa &&
            !cacheExpirado &&
            _uiState.value.reportes.isNotEmpty() &&
            _uiState.value.error == null &&
            !_uiState.value.isLoading) {

            android.util.Log.d("SupervisorVM", "Usando datos cacheados (${(ahora - ultimaActualizacion)/1000}s antiguos)")
            return
        }

        val tipoRecarga = when {
            forzarRecarga -> "forzada"
            cacheExpirado -> "por expiración (${(ahora - ultimaActualizacion)/1000}s)"
            else -> "inicial"
        }
        android.util.Log.d("SupervisorVM", "Cargando datos frescos - Razón: $tipoRecarga")

        datosYaCargados = true
        ultimaCargaExitosa = false

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

                todosLosReportes = reportesConProblemas
                reportesFiltrados = todosLosReportes // Inicialmente sin filtros

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

                ultimaActualizacion = ahora
                ultimaCargaExitosa = true

                android.util.Log.d("SupervisorVM", "Datos cargados: ${reportesTotales.size} reportes totales, ${reportesPaginados.size} mostrados inicialmente")

            } catch (e: Exception) {
                android.util.Log.e("SupervisorVM", "Error cargando datos: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar datos: ${e.message}"
                )
            }
        }
    }

    // NUEVA FUNCIÓN PARA CARGAR MÁS REPORTES
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

                // Tomar más reportes de los filtrados
                val reportesAmpliados = reportesFiltrados.take(nuevoTotal)

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

    // ACTUALIZAR FUNCIÓN DE FILTROS
    fun aplicarFiltros(filtros: FiltrosReporte) {
        android.util.Log.d("SupervisorVM", "Aplicando filtros: soloConProblemas=${filtros.soloConProblemas}")

        reportesFiltrados = filtrarReportes(todosLosReportes, filtros)

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
        reportesFiltrados = todosLosReportes
        val reportesPaginados = reportesFiltrados.take(REPORTES_POR_PAGINA)

        _uiState.value = _uiState.value.copy(
            reportes = reportesPaginados,
            filtros = FiltrosReporte(),
            totalReportesDisponibles = reportesFiltrados.size,
            reportesMostrados = reportesPaginados.size,
            puedeCargarMas = reportesFiltrados.size > REPORTES_POR_PAGINA
        )
    }

    // Method para pull-to-refresh
    fun forzarRecarga() {
        cargarDatosSupervisor(forzarRecarga = true)
    }

    // Invalidar caché cuando llega un nuevo reporte (para uso futuro)
    fun invalidarCachePorNuevoReporte() {
        ultimaActualizacion = 0L // Fuerza expiración inmediata
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ACTUALIZAR FUNCIÓN DE FILTROS
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

            // 🟢 FILTRO "SOLO CON PROBLEMAS"
            if (filtros.soloConProblemas && !reporteCompleto.tieneProblemas) {
                return@filter false
            }

            // Filtros de fecha (implementación básica)
            // TODO: Implementar filtros de fecha si es necesario

            true
        }
    }

    // NUEVA FUNCIÓN PARA CALCULAR REPORTES CON PROBLEMAS
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

            // Contar reportes por fecha (implementación básica)
            reporte.timestampCompletado?.let { timestamp ->
                try {
                    reportesEstaSemana++
                    if (reportes.indexOf(reporteCompleto) < 3) {
                        reportesHoy++
                    }
                } catch (e: Exception) {
                    // Ignorar errores
                }
            }
        }

        android.util.Log.d("SupervisorVM", "Estadísticas: Total=${reportes.size}, ConProblemas=$reportesConProblemas")

        return EstadisticasSupervisor(
            totalReportes = reportes.size,
            reportesHoy = reportesHoy,
            reportesEstaSemana = reportesEstaSemana,
            activosInspeccionados = activosSet.size,
            reportesConProblemas = reportesConProblemas
        )
    }
}