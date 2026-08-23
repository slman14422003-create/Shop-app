package com.shopmanager.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.dashboard.DashboardScreen
import com.shopmanager.app.ui.debts.DebtsScreen
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.debts.PersonDetailScreen
import com.shopmanager.app.ui.lock.LockScreen
import com.shopmanager.app.ui.materials.MaterialCatalogScreen
import com.shopmanager.app.ui.materials.MaterialsScreen
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.WebViewScreen
import com.shopmanager.app.ui.settings.SettingsScreen
import com.shopmanager.app.ui.theme.AppThemeMode
import com.shopmanager.app.ui.theme.BrandGradientStart
import com.shopmanager.app.ui.theme.SetSystemBarsColor
import com.shopmanager.app.ui.theme.ShopManagerTheme
import com.shopmanager.app.ui.theme.rememberIsDarkTheme

private const val ROUTE_DASHBOARD = "dashboard"
// Debts and Materials live together as two pages of one swipeable
// HorizontalPager (see MainPagerScreen below) instead of two separate nav
// destinations, so the person can flick right/left between them.
private const val ROUTE_MAIN_PAGER = "mainPager"
private const val PAGE_DEBTS = 0
private const val PAGE_MATERIALS = 1
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_MATERIAL_CATALOG = "materialCatalog"
private const val ROUTE_HELP = "help"
private const val ROUTE_PERSON_DETAIL = "personDetail/{personId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseModule.init(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)

        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var unlocked by remember { mutableStateOf(!settings.hasPin) }

            // Load the persisted currency symbol into the app-wide holder once,
            // so every screen (dashboard, debts, materials, notifications)
            // shows the right currency from the very first frame.
            LaunchedEffect(Unit) { AppSettingsState.setCurrency(settings.currencySymbol) }

            // Android 13+ requires explicit runtime permission to post
            // notifications (needed for the low-stock shopping list and
            // new-debt alerts).
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            ShopManagerTheme(themeMode = themeMode) {
                // Status bar (and nav bar) painted with the app's own brand
                // color instead of the bare system default.
                //
                // FIX: this used to read MaterialTheme.colorScheme.primary,
                // which in the *dark* scheme is intentionally a pale tone
                // (Indigo80) meant for text/icon contrast on dark surfaces —
                // not for painting a full status bar. That's what caused the
                // jarring bright-purple bar sitting on top of an otherwise
                // dark app. It now always uses the app's brand color, which
                // stays a deep indigo in every theme, so the status bar
                // always gets white icons and never clashes with dark mode.
                val isDark = rememberIsDarkTheme(themeMode)
                SetSystemBarsColor(
                    statusBarColor = BrandGradientStart,
                    navigationBarColor = MaterialTheme.colorScheme.surface,
                    statusBarDarkIcons = false,
                    navigationBarDarkIcons = !isDark
                )

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopManagerApp(settings: SettingsRepository, onThemeChanged: (AppThemeMode) -> Unit) {
    val navController = rememberNavController()
    // Shared across screens so everyone sees the same live data instead of
    // spinning up duplicate Firestore listeners per screen.
    val debtsViewModel: DebtsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val materialsViewModel: MaterialsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // Hoisted above the NavHost (rather than inside the pager's own
    // composable) so it survives navigating away to Settings/Help and back,
    // and so the bottom bar can read/drive the current page directly.
    val pagerState = rememberPagerState(initialPage = PAGE_DEBTS) { 2 }
    val pagerScope = rememberCoroutineScope()

    @OptIn(ExperimentalFoundationApi::class)
    fun openPager(page: Int) {
        navigateTopLevel(navController, ROUTE_MAIN_PAGER)
        pagerScope.launch { pagerState.animateScrollToPage(page) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomBarRoutes = setOf(ROUTE_DASHBOARD, ROUTE_MAIN_PAGER)
    val showBottomBar = currentRoute in bottomBarRoutes
    val onMainPager = currentRoute == ROUTE_MAIN_PAGER

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
                        selected = onMainPager && pagerState.currentPage == PAGE_DEBTS,
                        onClick = { openPager(PAGE_DEBTS) },
                        icon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        label = { Text("الديون") }
                    )
                    NavigationBarItem(
                        selected = onMainPager && pagerState.currentPage == PAGE_MATERIALS,
                        onClick = { openPager(PAGE_MATERIALS) },
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
            modifier = androidx.compose.ui.Modifier.padding(padding),
            // PERF: previously combined fadeIn+slideInHorizontally (and the
            // fade/slide-out equivalents). Layering an offset animation on
            // top of an alpha animation forces an extra graphicsLayer pass
            // every frame of every screen transition, which is a real cost
            // on lower-end devices and was part of why switching tabs/screens
            // felt laggy. A short plain crossfade reads just as intentional
            // and is noticeably lighter to composite.
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(ROUTE_DASHBOARD) {
                DashboardScreen(
                    debtsViewModel = debtsViewModel,
                    materialsViewModel = materialsViewModel,
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onNavigateToDebts = { openPager(PAGE_DEBTS) },
                    onNavigateToMaterials = { openPager(PAGE_MATERIALS) }
                )
            }
            composable(ROUTE_MAIN_PAGER) {
                // A single swipeable surface for the Debts and Materials
                // screens: flicking right or left moves between them,
                // exactly like switching the tabs below, just smoother.
                // Each row's own delete action is a tap (see
                // DeleteIconButton) rather than a horizontal swipe, so it
                // never fights this page-swipe gesture over the same axis.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset =
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                val fraction = pageOffset.coerceIn(-1f, 1f)
                                alpha = 1f - (kotlin.math.abs(fraction) * 0.35f)
                                val scale = 1f - (kotlin.math.abs(fraction) * 0.08f)
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        when (page) {
                            PAGE_DEBTS -> DebtsScreen(
                                viewModel = debtsViewModel,
                                onOpenPerson = { personId -> navController.navigate("personDetail/$personId") }
                            )
                            else -> MaterialsScreen(
                                viewModel = materialsViewModel,
                                onAddNew = { navController.navigate(ROUTE_MATERIAL_CATALOG) }
                            )
                        }
                    }
                }
            }
            composable(ROUTE_MATERIAL_CATALOG) {
                MaterialCatalogScreen(
                    viewModel = materialsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onThemeChanged = onThemeChanged,
                    debtsViewModel = debtsViewModel,
                    materialsViewModel = materialsViewModel,
                    onOpenHelp = { navController.navigate(ROUTE_HELP) }
                )
            }
            composable(ROUTE_HELP) {
                WebViewScreen(
                    url = "file:///android_asset/help.html",
                    title = "دليل الاستخدام",
                    onBack = { navController.popBackStack() }
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
