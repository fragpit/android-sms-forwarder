package dev.local.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Compose settings screen for forwarding configuration and test delivery. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    permissionsGranted: Boolean,
    onTokenChange: (String) -> Unit,
    onChatIdChange: (String) -> Unit,
    onForwardingChange: (Boolean) -> Unit,
    onSenderFilterChange: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onSave: () -> Unit,
    onSendTest: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.status) {
        val status = state.status ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(status)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "SMS Forwarder",
                style = MaterialTheme.typography.headlineMedium,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.botToken,
                onValueChange = onTokenChange,
                label = { Text("Telegram Bot Token") },
                singleLine = true,
                visualTransformation = if (state.settings.botToken.isBlank()) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.settings.chatId,
                onValueChange = onChatIdChange,
                label = { Text("Telegram Chat ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                value = state.settings.senderFilter,
                onValueChange = onSenderFilterChange,
                label = { Text("Sender filter, optional") },
                supportingText = { Text("Comma or newline separated numbers. Empty means all.") },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Forward SMS",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (state.settings.forwardingEnabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.settings.forwardingEnabled,
                    onCheckedChange = onForwardingChange,
                )
            }

            if (!permissionsGranted) {
                Text(
                    text = "SMS and notification permissions are not granted.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRequestPermissions) {
                    Text("Grant permissions")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                ) {
                    Text("Save settings")
                }
                ElevatedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSendingTest,
                    onClick = onSendTest,
                ) {
                    Text(if (state.isSendingTest) "Sending..." else "Send test")
                }
            }
        }
    }
}
