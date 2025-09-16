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
    val operarioSeleccionado: Usuario? = null,
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
    val plantilla: PlantillaChecklist?
)

data class SupervisorUiState(
    val reportes: List<ReporteCompleto> = emptyList(),
    val activos: List<Activo> = emptyList(),
    val operarios: List<Usuario> = emptyList(),
    val estadisticas: EstadisticasSupervisor = EstadisticasSupervisor(),
    val filtros: FiltrosReporte = FiltrosReporte(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SupervisorViewModel : ViewModel() {
    private val reporteRepository = ReporteRepository()
    private val activoRepository = ActivoRepository()
    private val usuarioRepository = UsuarioRepository()
    private val plantillaRepository = PlantillaRepository()

    private val _uiState = MutableStateFlow(SupervisorUiState())
    val uiState: StateFlow<SupervisorUiState> = _uiState.asStateFlow()

    private var todosLosReportes: List<ReporteCompleto> = emptyList()

    // Caché para evitar recargas innecesarias
    private var datosYaCargados = false
    private var ultimaCargaExitosa = false
    private var ultimaActualizacion = 0L
    private val CACHE_EXPIRY = 3 * 60 * 1000L // 3 minutos

    fun cargarDatosSupervisor(forzarRecarga: Boolean = false) {
        val ahora = System.currentTimeMillis()
        val cacheExpirado = (ahora - ultimaActualizacion) > CACHE_EXPIRY

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

                // Cargar operarios
                val usuarios = usuarioRepository.obtenerTodosLosUsuarios()
                val operarios = usuarios.filter { it.rol == "OPERARIO" }

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
                                plantilla = plantilla
                            )
                        )
                    }
                }

                todosLosReportes = reportesTotales.sortedByDescending {
                    it.reporte.timestampCompletado
                }

                val estadisticas = calcularEstadisticas(todosLosReportes)

                _uiState.value = _uiState.value.copy(
                    reportes = todosLosReportes,
                    activos = activos,
                    operarios = operarios,
                    estadisticas = estadisticas,
                    isLoading = false
                )

                ultimaActualizacion = ahora
                ultimaCargaExitosa = true

            } catch (e: Exception) {
                android.util.Log.e("SupervisorVM", "Error cargando datos: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar datos: ${e.message}"
                )
            }
        }
    }

    // Method para pull-to-refresh
    fun forzarRecarga() {
        cargarDatosSupervisor(forzarRecarga = true)
    }
    // Invalidar caché cuando llega un nuevo reporte (para uso futuro)
    fun invalidarCachePorNuevoReporte() {
        ultimaActualizacion = 0L // Fuerza expiración inmediata
    }
    fun aplicarFiltros(filtros: FiltrosReporte) {
        val reportesFiltrados = filtrarReportes(todosLosReportes, filtros)
        _uiState.value = _uiState.value.copy(
            reportes = reportesFiltrados,
            filtros = filtros
        )
    }

    fun limpiarFiltros() {
        _uiState.value = _uiState.value.copy(
            reportes = todosLosReportes,
            filtros = FiltrosReporte()
        )
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

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

            // Filtro por operario
            if (filtros.operarioSeleccionado != null &&
                reporte.usuarioId != filtros.operarioSeleccionado.id) {
                return@filter false
            }

            // Filtros de fecha (implementación básica)
            // En producción, usar librería java.time

            // TODO: Implementar filtro de "solo con problemas"
            // Requeriría cargar las respuestas de cada reporte

            true
        }
    }

    private fun calcularEstadisticas(reportes: List<ReporteCompleto>): EstadisticasSupervisor {
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

        reportes.forEach { reporteCompleto ->
            val reporte = reporteCompleto.reporte

            // Agregar activo al set
            activosSet.add(reporte.activoId)

            // Contar reportes por fecha (implementación básica)
            reporte.timestampCompletado?.let { timestamp ->
                try {
                    // Aquí se podría implementar lógica más precisa de fechas
                    // Por ahora, contamos todos como de esta semana para demo
                    reportesEstaSemana++

                    // Y algunos como de hoy (simplificado)
                    if (reportes.indexOf(reporteCompleto) < 3) {
                        reportesHoy++
                    }
                } catch (e: Exception) {
                    // Ignorar errores de parsing de fecha
                }
            }
        }

        return EstadisticasSupervisor(
            totalReportes = reportes.size,
            reportesHoy = reportesHoy,
            reportesEstaSemana = reportesEstaSemana,
            activosInspeccionados = activosSet.size,
            reportesConProblemas = 0 // TODO: Implementar después
        )
    }
}