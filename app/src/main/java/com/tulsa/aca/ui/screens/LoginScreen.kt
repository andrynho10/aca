package com.tulsa.aca.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tulsa.aca.R
import com.tulsa.aca.utils.PreferencesManager
import com.tulsa.aca.viewmodel.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberUser by remember { mutableStateOf(false) }

    // FocusRequesters para manejar el foco
    val passwordFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // CARGAR PREFERENCIAS AL INICIAR
    LaunchedEffect(Unit) {
        val shouldRemember = PreferencesManager.shouldRememberUser(context)
        if (shouldRemember) {
            val savedEmail = PreferencesManager.getSavedUserEmail(context)
            if (savedEmail.isNotEmpty()) {
                email = savedEmail
                rememberUser = true
                android.util.Log.d("LoginScreen", "Email cargado: $savedEmail")
            }
        }
    }

    // Función para ejecutar el login
    val performLogin = {
        if (email.isNotBlank() && password.isNotBlank() && !uiState.isLoading) {
            keyboardController?.hide()

            // GUARDAR/LIMPIAR PREFERENCIAS ANTES DEL LOGIN
            if (rememberUser) {
                PreferencesManager.setRememberUser(context, true)
                PreferencesManager.saveUserEmail(context, email)
                android.util.Log.d("LoginScreen", "Guardando usuario: $email")
            } else {
                PreferencesManager.clearSavedUser(context)
                android.util.Log.d("LoginScreen", "Limpiando datos guardados")
            }

            viewModel.login(email, password)
        }
    }

    // Mostrar error con Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            android.util.Log.e("LoginScreen", error)

            val userFriendlyMessage = when {
                error.contains("Invalid login credentials", ignoreCase = true) ->
                    "❌ Usuario o contraseña incorrectos"
                error.contains("Email not confirmed", ignoreCase = true) ->
                    "📧 Por favor, confirma tu email"
                error.contains("network", ignoreCase = true) || error.contains("connection", ignoreCase = true) ->
                    "🌐 Error de conexión. Verifica tu internet"
                error.contains("timeout", ignoreCase = true) ->
                    "⏱️ Tiempo de espera agotado. Intenta nuevamente"
                else -> "⚠️ Error al iniciar sesión. Intenta nuevamente"
            }

            snackbarHostState.showSnackbar(
                message = userFriendlyMessage,
                duration = SnackbarDuration.Long
            )
        }
    }

    // Navegar al éxito
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo/Título
            Image(
                painter = painterResource(id = R.drawable.logo_empresa),
                contentDescription = "Logo TULSA",
                modifier = Modifier
                    .size(140.dp)
                    .offset(x = (-6).dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "Checklist Inspección Grúas Horquilla",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Card de login
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Iniciar Sesión",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Campo email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        placeholder = { Text("usuario@tulsa.cl") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, "Email")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                passwordFocusRequester.requestFocus()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        singleLine = true
                    )

                    // Campo password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, "Contraseña")
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    "Mostrar contraseña"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                performLogin()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester),
                        enabled = !uiState.isLoading,
                        singleLine = true
                    )

                    // CHECKBOX "RECORDAR USUARIO"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = rememberUser,
                            onCheckedChange = { checked ->
                                rememberUser = checked
                                android.util.Log.d("LoginScreen", "Checkbox cambiado: $checked")
                            },
                            enabled = !uiState.isLoading
                        )
                        Text(
                            text = "Recordar usuario",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Botón login
                    Button(
                        onClick = performLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Iniciar Sesión")
                    }
                }
            }
        }
    }
}