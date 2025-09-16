package com.tulsa.aca.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AssetList : Screen("asset_list")
    object QRScanner : Screen("qr_scanner")
    object ChecklistSelection : Screen("checklist_selection/{assetId}") {
        fun createRoute(assetId: Int) = "checklist_selection/$assetId"
    }
    object AssetHistory : Screen("asset_history/{assetId}") {
        fun createRoute(assetId: Int) = "asset_history/$assetId"
    }
    object SupervisorPanel : Screen("supervisor_panel")
    object ReportDetails : Screen("report_details/{reportId}") {
        fun createRoute(reportId: String) = "report_details/$reportId"
    }
    object ActivosCrud : Screen("activos_crud")
    object PlantillasCrud : Screen("plantillas_crud")
    object PlantillaEditor : Screen("plantilla_editor/{plantillaId}") { // NUEVO
        fun createRoute(plantillaId: Int) = "plantilla_editor/$plantillaId"
    }
    object Checklist : Screen("checklist/{assetId}/{templateId}") {
        fun createRoute(assetId: Int, templateId: Int) = "checklist/$assetId/$templateId"
    }
    object PhotoViewer : Screen("photo_viewer/{photos}/{initialIndex}") {
        fun createRoute(photos: List<String>, initialIndex: Int = 0): String {
            // Codificar las URLs de las fotos para pasarlas como parámetro
            val photosEncoded = photos.joinToString(",") {
                java.net.URLEncoder.encode(it, "UTF-8")
            }
            return "photo_viewer/$photosEncoded/$initialIndex"
        }
    }
}