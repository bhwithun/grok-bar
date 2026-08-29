package com.brian.grokbar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.grokbar.data.UsageRepository
import com.brian.grokbar.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val signedIn: Boolean = false,
    val hasBearer: Boolean = false,
    val tokenInput: String = "",
    val message: String? = null,
    val isSaving: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = UsageRepository.get(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(tokenInput = value) }
    }

    fun saveToken() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isBlank()) {
            _uiState.update { it.copy(message = "Paste a Grok Build token first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            repo.saveBearer(token)
            val snapshot = repo.refresh(force = true)
            WidgetUpdater.updateAll(getApplication())
            _uiState.update {
                it.copy(
                    isSaving = false,
                    signedIn = repo.signedIn(),
                    hasBearer = true,
                    tokenInput = "",
                    message = snapshot.errorMessage ?: "Token saved. Usage refreshed."
                )
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            val storage = com.brian.grokbar.data.AppStorage(getApplication())
            _uiState.update {
                it.copy(
                    signedIn = repo.signedIn(),
                    hasBearer = !storage.bearerTokenOnce().isNullOrBlank()
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            WidgetUpdater.updateAll(getApplication())
            _uiState.update {
                it.copy(
                    signedIn = false,
                    hasBearer = false,
                    message = "Signed out."
                )
            }
        }
    }
}
