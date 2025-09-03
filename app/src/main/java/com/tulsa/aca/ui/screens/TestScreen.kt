package com.tulsa.aca.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.viewmodel.ActivoViewModel

@Composable
fun TestScreen(
    modifier: Modifier = Modifier,  // <- Agregar este parámetro
    viewModel: ActivoViewModel = viewModel()
) {
    val activos by viewModel.activos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = modifier  // <- Usar el parámetro modifier aquí
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Prueba de Conexión Supabase",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.cargarActivos() },
            enabled = !isLoading
        ) {
            Text("Cargar Activos")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        }

        LazyColumn {
            items(activos) { activo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = activo.nombre,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Modelo: ${activo.modelo}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Tipo: ${activo.tipo}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "QR: ${activo.codigoQr}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}