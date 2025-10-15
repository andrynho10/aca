package com.tulsa.aca.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onQRCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear Código QR") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        when {
            cameraPermissionState.status.isGranted -> {
                QRScannerView(
                    onQRCodeScanned = onQRCodeScanned,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            else -> {
                PermissionDeniedContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun QRScannerView(
    onQRCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    var scannerView by remember { mutableStateOf<BarcodeView?>(null) }
    var hasScanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                scannerView?.pause()
            } catch (e: Exception) {
                Log.e("QRScanner", "Error al pausar escáner: ${e.message}")
            }
        }
    }

    Box(modifier = modifier) {
        // Vista de cámara (solo BarcodeView, sin decoraciones)
        AndroidView(
            factory = { ctx ->
                try {
                    BarcodeView(ctx).apply {
                        // Configurar formatos - SOLO QR por defecto
                        val formats = listOf(BarcodeFormat.QR_CODE)
                        decoderFactory = DefaultDecoderFactory(formats)

                        // Callback cuando se escanea un código
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult?) {
                                result?.text?.let { code ->
                                    if (!hasScanned) {
                                        hasScanned = true
                                        Log.d("QRScanner", "Código escaneado: $code")
                                        onQRCodeScanned(code)
                                    }
                                }
                            }
                        })

                        // Iniciar escaneo
                        resume()

                        scannerView = this
                    }
                } catch (e: Exception) {
                    Log.e("QRScanner", "Error al crear vista de escáner: ${e.message}", e)
                    throw e
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay personalizado con marco de escaneo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Tamaño del área de escaneo (cuadrado)
            val scanSize = canvasWidth * 0.7f
            val left = (canvasWidth - scanSize) / 2
            val top = (canvasHeight - scanSize) / 2

            // Dibujar 4 rectángulos oscuros alrededor del área de escaneo (dejando el centro transparente)

            // Rectángulo superior
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, top)
            )

            // Rectángulo inferior
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, top + scanSize),
                size = Size(canvasWidth, canvasHeight - (top + scanSize))
            )

            // Rectángulo izquierdo
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, top),
                size = Size(left, scanSize)
            )

            // Rectángulo derecho
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(left + scanSize, top),
                size = Size(canvasWidth - (left + scanSize), scanSize)
            )

            // Borde blanco del área de escaneo
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(scanSize, scanSize),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 4f)
            )

            // Esquinas decorativas verdes
            val cornerLength = 40f
            val cornerWidth = 6f

            // Esquina superior izquierda
            drawLine(
                color = Color.Green,
                start = Offset(left, top + cornerLength),
                end = Offset(left, top),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.Green,
                start = Offset(left, top),
                end = Offset(left + cornerLength, top),
                strokeWidth = cornerWidth
            )

            // Esquina superior derecha
            drawLine(
                color = Color.Green,
                start = Offset(left + scanSize, top + cornerLength),
                end = Offset(left + scanSize, top),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.Green,
                start = Offset(left + scanSize, top),
                end = Offset(left + scanSize - cornerLength, top),
                strokeWidth = cornerWidth
            )

            // Esquina inferior izquierda
            drawLine(
                color = Color.Green,
                start = Offset(left, top + scanSize - cornerLength),
                end = Offset(left, top + scanSize),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.Green,
                start = Offset(left, top + scanSize),
                end = Offset(left + cornerLength, top + scanSize),
                strokeWidth = cornerWidth
            )

            // Esquina inferior derecha
            drawLine(
                color = Color.Green,
                start = Offset(left + scanSize, top + scanSize - cornerLength),
                end = Offset(left + scanSize, top + scanSize),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.Green,
                start = Offset(left + scanSize, top + scanSize),
                end = Offset(left + scanSize - cornerLength, top + scanSize),
                strokeWidth = cornerWidth
            )
        }

        // Controles e instrucciones
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Instrucciones arriba
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Apunta al código QR",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Botón de linterna abajo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingActionButton(
                    onClick = {
                        try {
                            torchOn = !torchOn
                            scannerView?.setTorch(torchOn)
                        } catch (e: Exception) {
                            Log.e("QRScanner", "Error al cambiar linterna: ${e.message}")
                        }
                    },
                    containerColor = if (torchOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (torchOn) "Apagar linterna" else "Encender linterna",
                        tint = if (torchOn) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (torchOn) "Linterna encendida" else "Toca para encender linterna",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Permiso de cámara requerido",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Para escanear códigos QR, necesitamos acceso a tu cámara",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRequestPermission) {
            Text("Conceder permiso")
        }
    }
}
