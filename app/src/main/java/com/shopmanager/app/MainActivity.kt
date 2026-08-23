package com.shopmanager.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.notifications.BackgroundSyncWorker
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.performance.DevicePerformance
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
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

// FIX: Home used to be its own NavHost destination, separate from the
// Debts/Materials HorizontalPager, so swiping only ever worked between
// those two and a swipe from Debts back to Home did nothing (you had to
// tap the bottom tab instead). All three main tabs now live as pages of
// the same HorizontalPager, so a flick right/left moves between Home,
// Debts, and Materials exactly like tapping the tabs below, and the
// gesture is consistent everywhere instead of only covering two of the
// three screens.
private const val ROUTE_MAIN_PAGER = "mainPager"
private const val PAGE_DASHBOARD = 0
private const val PAGE_DEBTS = 1
private const val PAGE_MATERIALS = 2
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_MATERIAL_CATALOG = "materialCatalog"
private const val ROUTE_HELP = "help"
private const val ROUTE_PERSON_DETAIL = "personDetail/{personId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // PERF FIX (startup jitter): installSplashScreen() must run before
        // super.onCreate(). It puts a static app icon on a flat brand-color
        // background up immediately — nothing on that screen animates or
        // recomposes, so there is nothing to jitter while the phone is
        // still busy underneath.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var isReady by mutableStateOf(false)
        var performanceTier by mutableStateOf(PerformanceTier.STANDARD)
        // Keeps the splash icon up — instead of showing a half-initialized
        // screen — until performanceTier is known AND Firebase/notification
        // channels/background-sync scheduling have finished below. Once
        // this flips true, Compose already has everything it needs for a
        // single correct first frame; nothing has to change color or
        // re-layout right after appearing.
        splashScreen.setKeepOnScreenCondition { !isReady }

        requestSmoothestRefreshRate()

        // PERF FIX (startup jitter): all of Firebase init, notification
        // channel setup, device-tier detection (disk read), and scheduling
        // the background sync worker (which — see AndroidManifest.xml and
        // ShopManagerApplication.kt — is also where WorkManager's Room
        // database actually gets built the first time) used to run
        // synchronously on the main thread in onCreate, before Compose
        // ever got a chance to draw. That's real, measurable main-thread
        // work stacked right at cold start, which is what showed up as a
        // few seconds of visible jank/"shaking". None of it needs the main
        // thread, so it now all runs on a background dispatcher while the
        // splash screen (above) covers the UI.
        lifecycleScope.launch(Dispatchers.Default) {
            FirebaseModule.init(applicationContext)
            NotificationHelper.ensureChannels(applicationContext)
            // "وضع لكل هاتف": classified once (cached after that), then
            // used below to switch off the heavier visual effects on
            // entry-level hardware — see DevicePerformance for the
            // detection signals.
            val tier = DevicePerformance.detectTier(applicationContext)
            BackgroundSyncWorker.schedule(applicationContext)
            withContext(Dispatchers.Main) {
                performanceTier = tier
                isReady = true
            }
        }

        setContent {
            // Nothing to compose until the background init above finishes —
            // the splash screen is still covering the activity at this
            // point, so this is invisible to the user, not a blank flash.
            if (!isReady) return@setContent

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

            CompositionLocalProvider(LocalPerformanceTier provides performanceTier) {
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

    /**
     * "معدل تحديث الشاشة" fix: without this, Android is free to run the
     * activity's window at a lower refresh rate than the display actually
     * supports (commonly defaulting to 60Hz even on a 90/120Hz phone for
     * apps that never state a preference), which makes swipes/animations
     * look less smooth than the hardware is capable of. This asks for the
     * highest refresh rate the *current* display reports. On a display
     * that only supports 60Hz (most entry-level phones, including the
     * Redmi A10), every mode has the same refresh rate, so this is a
     * harmless no-op there — it only changes anything on hardware that
     * actually has a faster mode to give.
     */
    private fun requestSmoothestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return
        val bestMode = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = bestMode.modeId
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
    val pagerState = rememberPagerState(initialPage = PAGE_DASHBOARD) { 3 }
    val pagerScope = rememberCoroutineScope()

    @OptIn(ExperimentalFoundationApi::class)
    fun openPager(page: Int) {
        navigateTopLevel(navController, ROUTE_MAIN_PAGER)
        pagerScope.launch { pagerState.animateScrollToPage(page) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == ROUTE_MAIN_PAGER

    // PERF (low-end tier): a fade still allocates a graphicsLayer and runs
    // a compositor pass every frame of the transition. That's cheap on a
    // Samsung A16-class device but is exactly the kind of per-frame cost
    // that shows up as stutter on a 2GB/quad-core phone like the Redmi
    // A10. On LOW tier screens simply swap with no transition at all.
    val performanceTier = LocalPerformanceTier.current
    val isLowTier = performanceTier == PerformanceTier.LOW

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = pagerState.currentPage == PAGE_DASHBOARD,
                        onClick = { openPager(PAGE_DASHBOARD) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("الرئيسية") }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == PAGE_DEBTS,
                        onClick = { openPager(PAGE_DEBTS) },
                        icon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        label = { Text("الديون") }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == PAGE_MATERIALS,
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
            startDestination = ROUTE_MAIN_PAGER,
            modifier = androidx.compose.ui.Modifier.padding(padding),
            // PERF: previously combined fadeIn+slideInHorizontally (and the
            // fade/slide-out equivalents). Layering an offset animation on
            // top of an alpha animation forces an extra graphicsLayer pass
            // every frame of every screen transition, which is a real cost
            // on lower-end devices and was part of why switching tabs/screens
            // felt laggy. A short plain crossfade reads just as intentional
            // and is noticeably lighter to composite.
            enterTransition = { if (isLowTier) EnterTransition.None else fadeIn(tween(150)) },
            exitTransition = { if (isLowTier) ExitTransition.None else fadeOut(tween(120)) },
            popEnterTransition = { if (isLowTier) EnterTransition.None else fadeIn(tween(150)) },
            popExitTransition = { if (isLowTier) ExitTransition.None else fadeOut(tween(120)) }
        ) {
            composable(ROUTE_MAIN_PAGER) {
                // A single swipeable surface for Home, Debts, and Materials:
                // flicking right or left moves between all three, exactly
                // like switching the tabs below, just smoother. Each row's
                // own delete action is a tap (see DeleteIconButton) rather
                // than a horizontal swipe, so it never fights this
                // page-swipe gesture over the same axis.
                //
                // PERF: this used to wrap every page in a per-frame
                // graphicsLayer that scaled and faded it while dragging.
                // With list-heavy screens full of ElevatedCards (each one
                // already its own shadow-casting layer), animating a
                // scale/alpha transform across the whole subtree on every
                // scroll frame was expensive and was the main reason
                // swiping between tabs felt heavy on real devices. The
                // pager still swipes and snaps the same way without it —
                // it just no longer pays that compositing cost.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        PAGE_DASHBOARD -> DashboardScreen(
                            debtsViewModel = debtsViewModel,
                            materialsViewModel = materialsViewModel,
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                            onNavigateToDebts = { openPager(PAGE_DEBTS) },
                            onNavigateToMaterials = { openPager(PAGE_MATERIALS) }
                        )
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
