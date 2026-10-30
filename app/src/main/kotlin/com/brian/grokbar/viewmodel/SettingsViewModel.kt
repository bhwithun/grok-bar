package com.brian.grokbar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.grokbar.BuildConfig
import com.brian.grokbar.data.UsageRepository
import com.brian.grokbar.update.AppUpdater
import com.brian.grokbar.update.GithubReleases
import com.brian.grokbar.update.decideUpdate
import com.brian.grokbar.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val signedIn: Boolean = false,
    val hasBearer: Boolean = false,
    val tokenInput: String = "",
    val message: String? = null,
    val isSaving: Boolean = false,
    val installedVersion: String = "",
    val updateMessage: String? = null,
    val updatePageUrl: String? = null,
    val updateApkUrl: String? = null,
    val isCheckingUpdate: Boolean = false,
    val isInstallingUpdate: Boolean = false
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
                    hasBearer = !storage.bearerTokenOnce().isNullOrBlank(),
                    installedVersion = BuildConfig.VERSION_NAME
                )
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateMessage = null,
                    updatePageUrl = null,
                    updateApkUrl = null
                )
            }
            val offer = withContext(Dispatchers.IO) {
                runCatching { decideUpdate(BuildConfig.VERSION_NAME, GithubReleases.latest()) }
            }
            _uiState.update {
                offer.fold(
                    onSuccess = { result ->
                        it.copy(
                            isCheckingUpdate = false,
                            updateMessage = result.message,
                            updatePageUrl = result.pageUrl,
                            updateApkUrl = result.apkUrl
                        )
                    },
                    onFailure = { error ->
                        it.copy(
                            isCheckingUpdate = false,
                            updateMessage = error.message ?: "Couldn't check for updates."
                        )
                    }
                )
            }
        }
    }

    fun installUpdate() {
        val apkUrl = _uiState.value.updateApkUrl ?: return
        val updater = AppUpdater(getApplication())
        if (!updater.canInstallPackages()) {
            _uiState.update {
                it.copy(updateMessage = "Allow Grok Bar to install updates, then try again.")
            }
            updater.openInstallPermissionSettings()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isInstallingUpdate = true) }
            val apk = withContext(Dispatchers.IO) { runCatching { updater.download(apkUrl) } }
            apk.fold(
                onSuccess = { file ->
                    _uiState.update { it.copy(isInstallingUpdate = false) }
                    updater.promptInstall(file)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isInstallingUpdate = false,
                            updateMessage = error.message ?: "Couldn't download the update."
                        )
                    }
                }
            )
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
