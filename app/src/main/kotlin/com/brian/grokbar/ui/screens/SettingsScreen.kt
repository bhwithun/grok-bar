package com.brian.grokbar.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.grokbar.LoginActivity
import com.brian.grokbar.data.GrokUsageApi
import com.brian.grokbar.util.IntentUtils
import com.brian.grokbar.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.reload()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    state.signedIn && state.hasBearer -> "Signed in with Grok session and Build token"
                    state.signedIn -> "Signed in with grok.com session"
                    state.hasBearer -> "Using a Grok Build token"
                    else -> "Not signed in"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { loginLauncher.launch(Intent(context, LoginActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.signedIn) "Re-sign in on grok.com" else "Sign in on grok.com")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { IntentUtils.openUrl(context, GrokUsageApi.USAGE_PAGE) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Grok Usage in browser")
            }
            if (state.signedIn || state.hasBearer) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.signOut() }) { Text("Sign out") }
            }

            Spacer(Modifier.height(28.dp))
            Text("Grok Build token", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Optional. Paste the access token from ~/.grok/auth.json after running grok login. This is the most reliable way to refresh the widget in the background.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.tokenInput,
                onValueChange = viewModel::onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bearer token") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.saveToken() },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "Saving…" else "Save token and refresh")
            }
            if (!state.message.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(state.message.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
