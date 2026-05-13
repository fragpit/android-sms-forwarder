package dev.local.smsforwarder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.local.smsforwarder.BuildConfig

/** Main settings activity for Telegram credentials, permissions, and forwarding state. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SettingsViewModel = viewModel()
            var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions()) }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                permissionsGranted = hasRequiredPermissions()
            }

            LaunchedEffect(Unit) {
                permissionsGranted = hasRequiredPermissions()
            }

            SmsForwarderTheme {
                SettingsScreen(
                    state = viewModel.state,
                    permissionsGranted = permissionsGranted,
                    appVersion = appVersionLabel(),
                    onTokenChange = viewModel::updateToken,
                    onChatIdChange = viewModel::updateChatId,
                    onForwardingChange = viewModel::updateForwardingEnabled,
                    onSenderFilterChange = viewModel::updateSenderFilter,
                    onRequestPermissions = {
                        permissionLauncher.launch(requiredPermissions())
                    },
                    onSave = {
                        if (viewModel.state.settings.forwardingEnabled && !permissionsGranted) {
                            permissionLauncher.launch(requiredPermissions())
                        }
                        viewModel.save()
                    },
                    onSendTest = viewModel::sendTestMessage,
                    onReset = viewModel::resetAll,
                )
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean =
        requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun appVersionLabel(): String =
        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nBuild ${BuildConfig.BUILD_ID}"
}
