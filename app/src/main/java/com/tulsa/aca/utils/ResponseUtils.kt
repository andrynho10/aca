package com.tulsa.aca.utils

// Extensiones para convertir Boolean a texto de display
fun Boolean.toDisplayText(): String = if (this) "BUENO" else "MALO"

fun Boolean.toStatusText(): String = if (this) "Bueno" else "Malo"

fun Boolean.toStatusColor(): androidx.compose.ui.graphics.Color {
    return if (this) {
        androidx.compose.ui.graphics.Color(0xFF4CAF50) // Verde para BUENO
    } else {
        androidx.compose.ui.graphics.Color(0xFFF44336) // Rojo para MALO
    }
}

// Para casos donde se necesite convertir de string a boolean (si es necesario en el futuro)
fun String.toBooleanFromDisplay(): Boolean = this.uppercase() == "BUENO"