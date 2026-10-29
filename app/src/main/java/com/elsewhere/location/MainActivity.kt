package com.elsewhere.location

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.UiEvent
import com.elsewhere.location.ui.navigation.MainShell
import com.elsewhere.location.ui.onboarding.OnboardingScreen
import com.elsewhere.location.ui.theme.ElsewhereTheme
import com.elsewhere.location.ui.theme.Paper
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: ElsewhereViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !viewModel.settingsLoaded.value }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.rgb(242, 238, 227),
                AndroidColor.rgb(242, 238, 227),
            ),
        )
        setContent {
            ElsewhereTheme {
                ElsewhereRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResume()
    }

    override fun onPause() {
        viewModel.onActivityPause()
        super.onPause()
    }
}

@Composable
private fun ElsewhereRoot(viewModel: ElsewhereViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onLocationPermissionResult(
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
        )
    }
    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }
        viewModel.onLocationPermissionResult(locationGranted)
        viewModel.onNotificationPermissionResult(notificationsGranted)
    }

    fun openSettingsIntent(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.notify("系统没有找到对应的设置页面。")
        } catch (_: SecurityException) {
            viewModel.notify("系统拒绝了打开设置页面的请求。")
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                UiEvent.RequestRuntimePermissions -> {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    runtimePermissionsLauncher.launch(permissions.toTypedArray())
                }
                UiEvent.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
                UiEvent.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                UiEvent.OpenShizukuApp -> {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(
                        com.elsewhere.location.shizuku.ShizukuManager.SHIZUKU_PACKAGE,
                    )
                    if (launchIntent != null) {
                        openSettingsIntent(launchIntent)
                    } else {
                        openSettingsIntent(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(com.elsewhere.location.shizuku.ShizukuManager.SHIZUKU_RELEASES_URL),
                            ),
                        )
                    }
                }
                UiEvent.OpenOverlaySettings -> openSettingsIntent(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
                UiEvent.OpenDeveloperOptions -> openSettingsIntent(
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                )
                UiEvent.OpenLocationSettings -> openSettingsIntent(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                )
                UiEvent.OpenNotificationSettings -> openSettingsIntent(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        context.packageName,
                    ),
                )
                UiEvent.OpenBatteryOptimizationSettings -> openSettingsIntent(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
                is UiEvent.OpenUrl -> openSettingsIntent(
                    Intent(Intent.ACTION_VIEW, Uri.parse(event.url)),
                )
                is UiEvent.ShareFile -> {
                    try {
                        val file = File(event.path)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            file,
                        )
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("ELSEWHERE JDWP kit", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "发送 JDWP 注入包"))
                    } catch (error: Exception) {
                        viewModel.notify(error.message ?: "无法分享注入包。")
                    }
                }
                UiEvent.NavigateToMap -> Unit
                is UiEvent.OpenAppDetails -> openSettingsIntent(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${event.packageName}"),
                    ),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        when {
            !settingsLoaded -> {
                // The platform splash remains visible until the first DataStore value.
                Box(Modifier.fillMaxSize().background(Paper))
            }
            !settings.onboarded -> {
                OnboardingScreen(viewModel = viewModel)
            }
            else -> {
                MainShell(viewModel = viewModel)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 96.dp),
        )
    }
}
