package com.pushgate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.apps.AppsScreen
import com.pushgate.app.ui.home.HomeScreen
import com.pushgate.app.ui.onboarding.OnboardingScreen
import com.pushgate.app.ui.settings.SettingsScreen
import com.pushgate.app.ui.stats.StatsScreen
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.InkRaised
import com.pushgate.app.ui.theme.Mist
import com.pushgate.app.ui.theme.PushGateTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PushGateTheme(forceDark = true) {
                val settings by viewModel.settings.collectAsStateWithLifecycle()

                Box(Modifier.fillMaxSize().background(Ink)) {
                    if (!settings.onboardingComplete) {
                        OnboardingScreen(viewModel = viewModel)
                    } else {
                        MainShell(viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission screens live outside the app, so state is re-read every time we come back.
        viewModel.refreshProtection()
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Today", Icons.Default.Home),
    APPS("apps", "Apps", Icons.Default.Apps),
    STATS("stats", "Stats", Icons.Default.BarChart),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

@Composable
private fun MainShell(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = InkRaised, tonalElevation = 0.dp) {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink,
                            selectedTextColor = Emerald,
                            indicatorColor = Emerald,
                            unselectedIconColor = Mist,
                            unselectedTextColor = Mist
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Tab.HOME.route) { HomeScreen(viewModel) }
            composable(Tab.APPS.route) { AppsScreen(viewModel) }
            composable(Tab.STATS.route) { StatsScreen(viewModel) }
            composable(Tab.SETTINGS.route) { SettingsScreen(viewModel) }
        }
    }
}
