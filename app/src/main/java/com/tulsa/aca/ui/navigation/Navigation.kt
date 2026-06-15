package com.tulsa.aca.ui.navigation

/**
 * Define todas las rutas de navegación de la app con Jetpack Navigation Compose
 * Cada objeto anida createRoute() para construir rutas con parámetros de forma segura
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object AssetList : Screen("asset_list")
    object QRScanner : Screen("qr_scanner")
    object ChecklistSelection : Screen("checklist_selection/{assetId}") {
        fun createRoute(assetId: Int) = "checklist_selection/$assetId"
    }
    object AssetHistory : Screen("asset_history/{assetId}") {
        fun createRoute(assetId: Int) = "asset_history/$assetId"
    }
    // Solo accesible para usuarios con rol Supervisor
    object SupervisorPanel : Screen("supervisor_panel")
    object ReportDetails : Screen("report_details/{reportId}") {
        fun createRoute(reportId: String) = "report_details/$reportId"
    }
    object ActivosCrud : Screen("activos_crud")
    object PlantillasCrud : Screen("plantillas_crud")
    object PlantillaEditor : Screen("plantilla_editor/{plantillaId}") {
        fun createRoute(plantillaId: Int) = "plantilla_editor/$plantillaId"
    }
    object Checklist : Screen("checklist/{assetId}/{templateId}") {
        fun createRoute(assetId: Int, templateId: Int) = "checklist/$assetId/$templateId"
    }
    object PhotoViewer : Screen("photo_viewer/{photos}/{initialIndex}") {
        fun createRoute(photos: List<String>, initialIndex: Int = 0): String {
            // Las URLs se codifican para que las barras y caracteres especiales no rompan la ruta
            val photosEncoded = photos.joinToString(",") {
                java.net.URLEncoder.encode(it, "UTF-8")
            }
            return "photo_viewer/$photosEncoded/$initialIndex"
        }
    }
    // Pantallas del flujo de cierre de horómetro (operador registra horómetro final)
    object HorometrosPendientes : Screen("horometros_pendientes")
    object CerrarHorometro : Screen("cerrar_horometro/{reporteId}") {
        fun createRoute(reporteId: String) = "cerrar_horometro/$reporteId"
    }
}