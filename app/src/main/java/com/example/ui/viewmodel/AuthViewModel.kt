package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.ChatDepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isRegisterMode: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val repository: ChatDepRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setRegisterMode(isRegister: Boolean) {
        _uiState.update { it.copy(isRegisterMode = isRegister, errorMessage = null) }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = if (state.isRegisterMode) {
                    repository.registerUser(state.name, state.email, state.password)
                } else {
                    repository.loginUser(state.email, state.password)
                }
                result.onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Error al iniciar sesión.")
                    }
                }.onSuccess {
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Ocurrió un error inesperado.")
                }
            }
        }
    }

    fun continueAsGuestProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.continueWithQuickProfile()
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "No se pudo abrir el perfil local.")
                        }
                    }
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Error al abrir perfil local.")
                }
            }
        }
    }

    companion object {
        fun provideFactory(repository: ChatDepRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(repository) }
        }
    }
}
