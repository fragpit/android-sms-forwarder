package dev.local.smsforwarder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.local.smsforwarder.BuildConfig
import dev.local.smsforwarder.calls.MissedCallMonitor
import dev.local.smsforwarder.calls.MissedCallScheduler
import dev.local.smsforwarder.data.AppSettings

/** Main settings activity for Telegram credentials, permissions, and forwarding state. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SettingsViewModel = viewModel()
            var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions()) }
            var notificationPermissionGranted by remember {
                mutableStateOf(hasRequiredPermissions(includeSms = false))
            }
            var callPermissionsGranted by remember {
                mutableStateOf(MissedCallMonitor.hasPermissions(this))
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                permissionsGranted = hasRequiredPermissions()
                notificationPermissionGranted = hasRequiredPermissions(includeSms = false)
                callPermissionsGranted = MissedCallMonitor.hasPermissions(this)
                MissedCallScheduler.enqueue(this)
            }

            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        permissionsGranted = hasRequiredPermissions()
                        notificationPermissionGranted = hasRequiredPermissions(includeSms = false)
                        callPermissionsGranted = MissedCallMonitor.hasPermissions(this@MainActivity)
                        MissedCallScheduler.enqueue(this@MainActivity)
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            SmsForwarderTheme {
                SettingsScreen(
                    state = viewModel.state,
                    permissionsGranted = if (viewModel.state.settings.forwardingEnabled ||
                        !viewModel.state.settings.missedCallForwardingEnabled
                    ) permissionsGranted else notificationPermissionGranted,
                    callPermissionsGranted = callPermissionsGranted,
                    appVersion = appVersionLabel(),
                    onTokenChange = viewModel::updateToken,
                    onChatIdChange = viewModel::updateChatId,
                    onForwardingChange = viewModel::updateForwardingEnabled,
                    onMissedCallForwardingChange = viewModel::updateMissedCallForwardingEnabled,
                    onSenderFilterChange = viewModel::updateSenderFilter,
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            requestedPermissions(viewModel.state.settings),
                        )
                    },
                    onSave = {
                        val settings = viewModel.state.settings
                        if (
                            (settings.forwardingEnabled && !permissionsGranted) ||
                            (settings.missedCallForwardingEnabled &&
                                (!callPermissionsGranted || !notificationPermissionGranted))
                        ) {
                            permissionLauncher.launch(
                                requestedPermissions(settings),
                            )
                        }
                        viewModel.save()
                    },
                    onSendTest = viewModel::sendTestMessage,
                    onReset = viewModel::resetAll,
                )
            }
        }
    }

    private fun hasRequiredPermissions(includeSms: Boolean = true): Boolean =
        requiredPermissions(includeSms = includeSms).all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestedPermissions(settings: AppSettings): Array<String> = requiredPermissions(
        includeCalls = settings.missedCallForwardingEnabled,
        includeSms = settings.forwardingEnabled || !settings.missedCallForwardingEnabled,
    )

    private fun requiredPermissions(
        includeCalls: Boolean = false,
        includeSms: Boolean = true,
    ): Array<String> = buildList {
        if (includeSms) {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
        }
        if (includeCalls) {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun appVersionLabel(): String =
        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nBuild ${BuildConfig.BUILD_ID}"
}
