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
    object Checklist : Screen("checklist/{assetId}/{templateId}") {
        fun createRoute(assetId: Int, templateId: Int) = "checklist/$assetId/$templateId"
    }
}