package com.shopmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.dashboard.DashboardScreen
import com.shopmanager.app.ui.debts.DebtsScreen
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.debts.PersonDetailScreen
import com.shopmanager.app.ui.lock.LockScreen
import com.shopmanager.app.ui.materials.MaterialsScreen
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.settings.SettingsScreen
import com.shopmanager.app.ui.theme.AppThemeMode
import com.shopmanager.app.ui.theme.ShopManagerTheme

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_DEBTS = "debts"
private const val ROUTE_MATERIALS = "materials"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PERSON_DETAIL = "personDetail/{personId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseModule.init(applicationContext)

        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var unlocked by remember { mutableStateOf(!settings.hasPin) }

            ShopManagerTheme(themeMode = themeMode) {
                Surface {
                    if (!unlocked) {
                        LockScreen(settings = settings, onUnlocked = { unlocked = true })
                    } else {
                        ShopManagerApp(
                            settings = settings,
                            onThemeChanged = { themeMode = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopManagerApp(settings: SettingsRepository, onThemeChanged: (AppThemeMode) -> Unit) {
    val navController = rememberNavController()
    // Shared across screens so everyone sees the same live data instead of
    // spinning up duplicate Firestore listeners per screen.
    val debtsViewModel: DebtsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val materialsViewModel: MaterialsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomBarRoutes = setOf(ROUTE_DASHBOARD, ROUTE_DEBTS, ROUTE_MATERIALS)
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_DASHBOARD,
                        onClick = { navigateTopLevel(navController, ROUTE_DASHBOARD) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("الرئيسية") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_DEBTS,
                        onClick = { navigateTopLevel(navController, ROUTE_DEBTS) },
                        icon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        label = { Text("الديون") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_MATERIALS,
                        onClick = { navigateTopLevel(navController, ROUTE_MATERIALS) },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        label = { Text("المواد والأسعار") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_DASHBOARD,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(ROUTE_DASHBOARD) {
                DashboardScreen(
                    debtsViewModel = debtsViewModel,
                    materialsViewModel = materialsViewModel,
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                )
            }
            composable(ROUTE_DEBTS) {
                DebtsScreen(
                    viewModel = debtsViewModel,
                    onOpenPerson = { personId -> navController.navigate("personDetail/$personId") }
                )
            }
            composable(ROUTE_MATERIALS) {
                MaterialsScreen(viewModel = materialsViewModel)
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onThemeChanged = onThemeChanged
                )
            }
            composable(
                ROUTE_PERSON_DETAIL,
                arguments = listOf(navArgument("personId") { type = NavType.StringType })
            ) { entry ->
                val personId = entry.arguments?.getString("personId")
                val person = debtsViewModel.uiState.collectAsState().value.persons
                    .find { it.id == personId }
                if (person != null) {
                    PersonDetailScreen(
                        person = person,
                        viewModel = debtsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private fun navigateTopLevel(navController: androidx.navigation.NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
