package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ZtoeTheme
import com.example.ui.viewmodel.MainViewModel

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            ZtoeTheme(themeMode = uiState.settings.themeMode) {
                ZtoeAppShell(
                    viewModel = viewModel,
                    uiState = uiState,
                )
            }
        }
    }
}

@Composable
private fun ZtoeAppShell(
    viewModel: MainViewModel,
    uiState: com.example.ui.viewmodel.MainUiState,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                val isDashboardSelected = currentDestination?.hierarchy?.any { it.route == ROUTE_DASHBOARD } == true
                val isSettingsSelected = currentDestination?.hierarchy?.any { it.route == ROUTE_SETTINGS } == true

                NavigationBarItem(
                    selected = isDashboardSelected,
                    onClick = {
                        navController.navigate(ROUTE_DASHBOARD) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isDashboardSelected) Icons.Rounded.Bolt else Icons.Outlined.Bolt,
                            contentDescription = "График",
                        )
                    },
                    label = { Text("График") },
                    modifier = Modifier.testTag("nav_schedule"),
                )

                NavigationBarItem(
                    selected = isSettingsSelected,
                    onClick = {
                        navController.navigate(ROUTE_SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isSettingsSelected) Icons.Rounded.Settings else Icons.Outlined.Settings,
                            contentDescription = "Настройки",
                        )
                    },
                    label = { Text("Настройки") },
                    modifier = Modifier.testTag("nav_settings"),
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_DASHBOARD,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(ROUTE_DASHBOARD) {
                ScheduleScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refreshSchedule,
                    onSelectQueue = viewModel::selectQueue,
                    onSelectDayIndex = viewModel::selectDayIndex,
                    onDismissError = viewModel::clearError,
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    uiState = uiState,
                    onSelectQueue = viewModel::selectQueue,
                    onToggleNotifications = viewModel::setNotificationsEnabled,
                    onSelectLeadTime = viewModel::setLeadTimeMinutes,
                    onUpdateAlertMessage = viewModel::setCustomAlertMessage,
                    onToggleScheduleChangeAlert = viewModel::setNotifyOnScheduleChange,
                    onSelectRefreshInterval = viewModel::setRefreshIntervalMinutes,
                    onToggleKyivTimezone = viewModel::setUseKyivTimezone,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onSendTestNotification = viewModel::sendTestNotification,
                )
            }
        }
    }
}
