package com.tulsa.aca.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

// Función para descargar imagen
private fun downloadImage(
    context: android.content.Context,
    imageUrl: String,
    fileName: String,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    scope.launch {
        try {
            withContext(Dispatchers.IO) {
                val imageLoader = ImageLoader.Builder(context).build()
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .build()

                val result = imageLoader.execute(request)
                val drawable = result.drawable

                // Crear directorio Downloads si no existe
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ACA_App")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                // Guardar imagen
                val file = File(downloadsDir, fileName)
                val outputStream = FileOutputStream(file)
                drawable?.let {
                    val bitmap = (it as android.graphics.drawable.BitmapDrawable).bitmap
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                outputStream.close()

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Error al descargar la imagen: ${e.message}")
            }
        }
    }
}

// Función para compartir imagen
private fun shareImage(
    context: android.content.Context,
    imageUrl: String,
    scope: CoroutineScope,
    onError: (String) -> Unit
) {
    scope.launch {
        try {
            withContext(Dispatchers.IO) {
                val imageLoader = ImageLoader.Builder(context).build()
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .build()

                val result = imageLoader.execute(request)
                val drawable = result.drawable

                // Crear archivo temporal para compartir
                val cacheDir = File(context.cacheDir, "images")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                val file = File(cacheDir, "shared_image_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                drawable?.let {
                    val bitmap = (it as android.graphics.drawable.BitmapDrawable).bitmap
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                outputStream.close()

                withContext(Dispatchers.Main) {
                    // Crear intent para compartir
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    context.startActivity(Intent.createChooser(shareIntent, "Compartir imagen"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Error al compartir la imagen: ${e.message}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photos: List<String>,
    initialIndex: Int = 0,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )

    // Estados para manejar mensajes
    var showDownloadMessage by remember { mutableStateOf<String?>(null) }
    var showShareMessage by remember { mutableStateOf<String?>(null) }

    // Estado para Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar mensajes de descarga
    LaunchedEffect(showDownloadMessage) {
        showDownloadMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                showDownloadMessage = null
            }
        }
    }

    // Mostrar mensajes de compartir
    LaunchedEffect(showShareMessage) {
        showShareMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                showShareMessage = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Pager para navegar entre fotos
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val zoomState = rememberZoomState()

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photos[index])
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto ${index + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomable(zoomState),
                    contentScale = ContentScale.Fit
                )

                // Overlay con loading si está cargando
                if (false) { // Aquí se puede agregar lógica de loading si es necesario
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        // Top bar con botón de volver y contador
        TopAppBar(
            title = {
                Text(
                    text = "${pagerState.currentPage + 1} de ${photos.size}",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // Botón de descarga
                IconButton(
                    onClick = {
                        val currentPhotoUrl = photos[pagerState.currentPage]
                        val fileName = "foto_${pagerState.currentPage + 1}_${System.currentTimeMillis()}.jpg"

                        downloadImage(
                            context = context,
                            imageUrl = currentPhotoUrl,
                            fileName = fileName,
                            scope = scope,
                            onSuccess = {
                                showDownloadMessage = "Imagen descargada exitosamente"
                            },
                            onError = { error ->
                                showDownloadMessage = error
                            }
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Descargar",
                        tint = Color.White
                    )
                }

                // Botón de compartir
                IconButton(
                    onClick = {
                        val currentPhotoUrl = photos[pagerState.currentPage]

                        shareImage(
                            context = context,
                            imageUrl = currentPhotoUrl,
                            scope = scope,
                            onError = { error ->
                                showShareMessage = error
                            }
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        )

        // Indicadores de página (puntos) si hay múltiples fotos
        if (photos.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(photos.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (index == pagerState.currentPage)
                                    Color.White
                                else
                                    Color.White.copy(alpha = 0.5f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
        }

        // Snackbar para mostrar mensajes
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }
}