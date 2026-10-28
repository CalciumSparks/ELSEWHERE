package com.elsewhere.location.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.UiEvent
import com.elsewhere.location.ui.apps.AppsScreen
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.environment.EnvironmentScreen
import com.elsewhere.location.ui.map.MapScreen
import com.elsewhere.location.ui.motion.MotionScreen
import com.elsewhere.location.ui.settings.SettingsScreen
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.PaperLow
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SystemSans
import kotlinx.coroutines.flow.collectLatest

enum class TopDestination(
    val route: String,
    val title: String,
    val english: String,
    val icon: ImageVector,
) {
    Map("map", "地图", "MAP", EIcon.Map),
    Motion("motion", "运动", "MOTION", EIcon.Motion),
    Environment("environment", "环境", "ENV", EIcon.Environment),
    Apps("apps", "应用", "APPS", EIcon.Apps),
}

private const val SETTINGS_ROUTE = "settings"

@Composable
fun MainShell(viewModel: ElsewhereViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != SETTINGS_ROUTE

    LaunchedEffect(navController) {
        viewModel.events.collectLatest { event ->
            if (event == UiEvent.NavigateToMap) {
                navController.navigate(TopDestination.Map.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    Scaffold(
        containerColor = com.elsewhere.location.ui.theme.Paper,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                MainNavigationBar(
                    current = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Map.route,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(TopDestination.Map.route) {
                MapScreen(
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(TopDestination.Motion.route) {
                MotionScreen(
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onPlanRoute = {
                        viewModel.beginRouteDraft()
                        navController.navigate(TopDestination.Map.route)
                    },
                )
            }
            composable(TopDestination.Environment.route) {
                EnvironmentScreen(
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(TopDestination.Apps.route) {
                AppsScreen(
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun MainNavigationBar(
    current: String?,
    onSelect: (TopDestination) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PaperHigh)
            .border(width = 1.dp, color = Line)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TopDestination.entries.forEach { destination ->
                val selected = current == destination.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { onSelect(destination) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 22.dp, height = 2.dp)
                            .background(if (selected) Signal else androidx.compose.ui.graphics.Color.Transparent),
                    )
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.title,
                        tint = if (selected) Ink else Faint,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(19.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            destination.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SystemSans,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp,
                            ),
                            color = if (selected) Ink else Faint,
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                        Text(
                            destination.english,
                            style = MonoTiny.copy(fontSize = 7.sp, letterSpacing = 0.4.sp),
                            color = if (selected) Signal else PaperLow,
                        )
                    }
                }
            }
        }
    }
}
