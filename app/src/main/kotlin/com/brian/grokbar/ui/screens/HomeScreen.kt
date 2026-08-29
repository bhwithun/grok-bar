package com.brian.grokbar.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.brian.grokbar.ui.components.UsageGaugeCard
import com.brian.grokbar.util.Formatters
import com.brian.grokbar.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReturnedFromLogin()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grok Bar") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
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
            UsageGaugeCard(snapshot = state.snapshot)
            Spacer(Modifier.height(16.dp))
            Text(
                text = Formatters.lastUpdated(state.snapshot.fetchedAtEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.snapshot.isDemo && !state.signedIn) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sample data until you sign in.\n\nTap Sign in → log in with X → wait until Grok appears → tap Done in the top-right.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.signedIn && state.snapshot.isDemo) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You're signed in, but usage hasn't loaded yet. Tap Refresh. If that fails, sign in again and tap Done once Grok is on screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!state.message.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.signedIn) {
                    Button(
                        onClick = {
                            loginLauncher.launch(Intent(context, LoginActivity::class.java))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Sign in") }
                }
                OutlinedButton(
                    onClick = { viewModel.refresh(force = true) },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.isLoading) "Refreshing…" else "Refresh")
                }
            }
            if (state.signedIn) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.signOut() }) { Text("Sign out") }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Add the Grok usage widget from your home screen: long-press the wallpaper → Widgets → Grok Bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
