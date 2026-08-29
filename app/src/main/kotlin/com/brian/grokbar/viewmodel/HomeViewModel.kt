package com.brian.grokbar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.grokbar.data.UsageRepository
import com.brian.grokbar.data.UsageSnapshot
import com.brian.grokbar.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val snapshot: UsageSnapshot = UsageSnapshot.demo(),
    val signedIn: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = UsageRepository.get(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.snapshot.collect { snapshot ->
                _uiState.update { it.copy(snapshot = snapshot) }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(signedIn = repo.signedIn()) }
            refresh(force = false)
        }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val snapshot = repo.refresh(force = force)
            WidgetUpdater.updateAll(getApplication())
            _uiState.update {
                it.copy(
                    snapshot = snapshot,
                    signedIn = repo.signedIn(),
                    isLoading = false,
                    message = snapshot.errorMessage
                )
            }
        }
    }

    fun onReturnedFromLogin() {
        viewModelScope.launch {
            _uiState.update { it.copy(signedIn = repo.signedIn()) }
            refresh(force = true)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            WidgetUpdater.updateAll(getApplication())
            _uiState.update {
                it.copy(
                    signedIn = false,
                    snapshot = UsageSnapshot.demo(),
                    message = "Signed out. Showing sample data."
                )
            }
        }
    }
}
