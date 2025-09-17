package com.tulsa.aca.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tulsa.aca.data.models.Usuario
import com.tulsa.aca.data.repository.AuthRepository
import com.tulsa.aca.data.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: Usuario? = null,
    val error: String? = null
)

class LoginViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isLoggedIn() && UserSession.isLoggedIn()
            _uiState.value = _uiState.value.copy(isLoggedIn = isLoggedIn)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = authRepository.login(email, password)

            result.fold(
                onSuccess = { usuario ->
                    // Actualizar UserSession para compatibilidad
                    UserSession.login(usuario)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        currentUser = usuario
                    )

                    android.util.Log.d("LoginViewModel", "Login exitoso: ${usuario.nombreCompleto}")
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Error: ${error.message}"
                    )

                    android.util.Log.e("LoginViewModel", "Login fallido: ${error.message}")
                }
            )
        }
    }

    // Función para limpiar el estado (para cuando se hace logout desde otra pantalla)
    fun clearLoginState() {
        _uiState.value = LoginUiState()
    }
}