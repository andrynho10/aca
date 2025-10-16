package com.tulsa.aca.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tulsa.aca.utils.NetworkMonitor
import kotlinx.coroutines.delay

/**
 * Componente compacto para mostrar el estado de conectividad en el TopAppBar
 * Muestra un icono con color indicativo del estado de red
 */
@Composable
fun NetworkStatusBadge(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isConnected by networkMonitor.isConnected.collectAsState(initial = true)

    // Color animado del badge
    val badgeColor by animateColorAsState(
        targetValue = if (isConnected) {
            Color(0xFF4CAF50) // Verde para online
        } else {
            Color(0xFFF44336) // Rojo para offline
        },
        animationSpec = tween(durationMillis = 300),
        label = "Badge Color"
    )

    // Icono según estado
    val icon = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff
    val description = if (isConnected) "En línea" else "Sin conexión"

    Badge(
        modifier = modifier,
        containerColor = badgeColor,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Chip compacto que muestra el estado de conectividad
 * Para usar en TopAppBar actions
 */
@Composable
fun NetworkStatusChip(
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isConnected by networkMonitor.isConnected.collectAsState(initial = true)

    // Color animado
    val chipColor by animateColorAsState(
        targetValue = if (isConnected) {
            Color(0xFF4CAF50) // Verde
        } else {
            Color(0xFFFF9800) // Naranja para offline
        },
        animationSpec = tween(durationMillis = 300),
        label = "Chip Color"
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = chipColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = if (isConnected) "En línea" else "Sin conexión",
                tint = chipColor,
                modifier = Modifier.size(16.dp)
            )

            if (showLabel) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isConnected) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = chipColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Banner persistente que aparece en la parte superior cuando no hay conexión
 * Se muestra automáticamente y se oculta cuando se recupera la conexión
 */
@Composable
fun NetworkStatusBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isConnected by networkMonitor.isConnected.collectAsState(initial = true)

    // Mostrar banner solo cuando está offline
    AnimatedVisibility(
        visible = !isConnected,
        enter = expandVertically(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Sin conexión",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sin conexión a Internet - Trabajando en modo offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Snackbar que muestra notificaciones temporales cuando cambia el estado de red
 * Se muestra automáticamente cuando se pierde o recupera la conexión
 */
@Composable
fun NetworkStatusSnackbar(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isConnected by networkMonitor.isConnected.collectAsState(initial = true)

    // Estado previo para detectar cambios
    var previousState by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isConnected) {
        // Solo mostrar si hay un cambio de estado (no la primera vez)
        if (previousState != null && previousState != isConnected) {
            val message = if (isConnected) {
                "Conexión restablecida"
            } else {
                "Sin conexión - Trabajando en modo offline"
            }

            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
        previousState = isConnected
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Indicador con pulsación para estado offline (más prominente)
 * Útil para llamar la atención cuando está offline
 */
@Composable
fun PulsingOfflineIndicator(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isConnected by networkMonitor.isConnected.collectAsState(initial = true)

    if (!isConnected) {
        // Animación de pulsación
        var animationIteration by remember { mutableStateOf(0) }
        val alpha by animateFloatAsState(
            targetValue = if (animationIteration % 2 == 0) 1f else 0.3f,
            animationSpec = tween(durationMillis = 1000),
            finishedListener = {
                animationIteration++
            },
            label = "Pulse Animation"
        )

        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                animationIteration++
            }
        }

        Surface(
            modifier = modifier.alpha(alpha),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Sin conexión",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
