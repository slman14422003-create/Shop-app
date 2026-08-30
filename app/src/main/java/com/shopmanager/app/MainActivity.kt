package com.shopmanager.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.backup.DailyBackupWorker
import com.shopmanager.app.data.notifications.BackgroundSyncWorker
import com.shopmanager.app.data.notifications.NotificationAction
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.performance.DevicePerformance
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.performance.resolvePerformanceTier
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.admin.AdminPanelScreen
import com.shopmanager.app.ui.dashboard.DashboardScreen
import com.shopmanager.app.ui.debts.DebtsScreen
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.debts.PersonDetailScreen
import com.shopmanager.app.ui.lock.LockScreen
import com.shopmanager.app.ui.materials.MaterialCatalogScreen
import com.shopmanager.app.ui.materials.MaterialsScreen
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.BottomNavItem
import com.shopmanager.app.ui.common.FloatingBottomNav
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.QuickAction
import com.shopmanager.app.ui.common.WebViewScreen
import com.shopmanager.app.ui.settings.SettingsScreen
import com.shopmanager.app.ui.splash.AppSplashScreen
import com.shopmanager.app.ui.theme.AppColorPalette
import com.shopmanager.app.ui.theme.AppThemeMode
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
// لوحة المسؤول السرية: not exposed through any visible nav item — reached
// only via the hidden dot on the dashboard header + the PIN dialog it
// opens (see DashboardScreen). Deliberately not shown in the bottom bar
// or Settings so a regular user has no path to it except knowing it's
// there.
private const val ROUTE_ADMIN = "adminPanel"
private const val ROUTE_MATERIAL_CATALOG = "materialCatalog"
private const val ROUTE_HELP = "help"
private const val ROUTE_PRIVACY = "privacy"
private const val ROUTE_PERSON_DETAIL = "personDetail/{personId}"

// The in-app liquid-glass splash (see AppSplashScreen) is a calm branded
// moment, not a progress readout — on a fast device real init can finish
// in well under 200ms, too quick to register as anything but a flicker.
// This is the one artificial delay in the whole startup path, just long
// enough for the splash to actually be seen before it crossfades away.
private const val SPLASH_MIN_DISPLAY_MS = 1500L

class MainActivity : ComponentActivity() {
    // Class-level (not inside setContent) so onNewIntent below - fired when
    // the app is already running and a *second* notification is tapped -
    // can update it too. A local `remember { mutableStateOf(...) }` created
    // inside setContent would only ever be initialized once, from the
    // Intent MainActivity happened to start with, and would never see a
    // later Intent onNewIntent hands us.
    private var pendingNotificationAction by mutableStateOf<NotificationAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // PERF FIX (startup jitter): installSplashScreen() must run before
        // super.onCreate(). It puts a static app icon on a flat brand-color
        // background up immediately — nothing on that screen animates or
        // recomposes, so there is nothing to jitter while the phone is
        // still busy underneath.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // "زجاجي بالكامل" (full glass, edge-to-edge): let this Activity's
        // window draw behind both system bars instead of the OS reserving
        // a solid, separately-colored strip for them. Combined with
        // transparent status/nav bar colors (see SetSystemBarsColor below),
        // this is what lets every liquid-glass header bleed all the way up
        // to the true top of the screen with no hard seam under the status
        // bar icons — previously the status bar was an opaque flat color
        // sitting directly above the gradient/glass header, and that flat
        // color meeting the header's glossy top edge was exactly the
        // visible dividing line.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val splashStartTime = System.currentTimeMillis()

        // Cold start via a notification tap (app wasn't running): the
        // Activity's very first Intent already carries the extras
        // NotificationHelper attached. Read it once, up front.
        pendingNotificationAction = NotificationAction.from(intent)

        var isReady by mutableStateOf(false)
        var detectedTier by mutableStateOf(PerformanceTier.STANDARD)
        // Tracks only whether Compose has produced its first frame — NOT
        // whether init is done. The static system splash now only needs to
        // bridge the gap until Compose can draw *something*; from that
        // first frame on, AppSplashScreen (rendered below, in Compose) is
        // what actually covers the screen and shows real init progress, so
        // there's no reason to keep the frozen system icon up any longer
        // than that. (Approximated via a LaunchedEffect(Unit) in setContent
        // — not pixel-exact to the true first draw, but close enough that
        // the handoff is invisible in practice.)
        var composeSplashAttached by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !composeSplashAttached }

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
        // splash screen (system, then in-app — see above) covers the UI.
        lifecycleScope.launch(Dispatchers.Default) {
            FirebaseModule.init(applicationContext)
            NotificationHelper.ensureChannels(applicationContext)

            // "وضع لكل هاتف": classified once (cached after that), then
            // used below to switch off the heavier visual effects on
            // entry-level hardware — see DevicePerformance for the
            // detection signals.
            val tier = DevicePerformance.detectTier(applicationContext)

            BackgroundSyncWorker.schedule(applicationContext)
            // Silent, fully local daily backup — no notification, ever
            // (see DailyBackupWorker/BackupManager). Scheduled here, off
            // the main thread, same as BackgroundSyncWorker above.
            DailyBackupWorker.schedule(applicationContext)

            val elapsed = System.currentTimeMillis() - splashStartTime
            if (elapsed < SPLASH_MIN_DISPLAY_MS) delay(SPLASH_MIN_DISPLAY_MS - elapsed)

            withContext(Dispatchers.Main) {
                detectedTier = tier
                isReady = true
            }

        }

        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var colorPalette by remember { mutableStateOf(settings.colorPalette) }
            var unlocked by remember { mutableStateOf(!settings.hasPin) }

            // "تفضيل الأداء": loaded once here (not re-read from disk on
            // every recomposition), then kept in sync live when changed in
            // Settings via onPerformancePreferenceChanged below — same
            // pattern as themeMode/onThemeChanged just above.
            var performancePreference by remember { mutableStateOf(settings.performanceMode) }
            val performanceTier by remember {
                derivedStateOf { resolvePerformanceTier(detectedTier, performancePreference) }
            }

            // Dismiss the static system splash screen as soon as Compose has
            // a frame ready to draw — see the composeSplashAttached comment
            // above. From here on AppSplashScreen below is what the person
            // actually sees while the background init finishes.
            LaunchedEffect(Unit) { composeSplashAttached = true }

            ShopManagerTheme(themeMode = themeMode, colorPalette = colorPalette) {
                // "زجاجي بالكامل" (fully glass): the status bar is now
                // always fully transparent (see the edge-to-edge window
                // setup in onCreate above and SetSystemBarsColor below) —
                // there's no separate OS-painted strip to keep color-synced
                // with the app any more, so whatever is actually behind it
                // (the splash gradient, or a screen's own liquid-glass
                // header) just shows straight through with no seam between
                // "system bar" and "app content". Only the icon *color*
                // still needs picking per screen: the splash and every
                // glass header (once unlocked) are dark enough for white
                // icons; the plain PIN lock screen instead follows the
                // ordinary light/dark surface color like any other screen.
                val isDark = rememberIsDarkTheme(themeMode)
                SetSystemBarsColor(
                    // "الشريط السفلي العائم بدون الخلفية السوداء": this used
                    // to switch to a solid MaterialTheme.colorScheme.background
                    // once the splash finished, which on the dark theme
                    // (near-black DarkBackground) painted a flat black strip
                    // across the whole gesture-bar area — sitting directly
                    // under FloatingBottomNav's transparent side margins, so
                    // the floating pill visibly had a black rectangle behind
                    // it instead of the page just continuing through. The nav
                    // bar now stays fully transparent always, exactly like
                    // the status bar already does, so FloatingBottomNav's
                    // margins show the real page content/background instead
                    // of a separately-painted solid color.
                    navigationBarColor = Color.Transparent,
                    statusBarDarkIcons = isReady && !unlocked && !isDark,
                    navigationBarDarkIcons = if (isReady) !isDark else false
                )

                Surface {
                    // A one-time crossfade from the in-app splash into the
                    // real UI once isReady flips true — smoother than the
                    // hard cut a plain `if` would give, and it only ever
                    // runs once per cold start so it's not worth gating
                    // behind the LOW-tier "skip animations" convention used
                    // for the Pager/route transitions elsewhere in this file.
                    Crossfade(targetState = isReady, label = "splashToApp") { ready ->
                        if (!ready) {
                            AppSplashScreen()
                        } else {
                            CompositionLocalProvider(LocalPerformanceTier provides performanceTier) {
                                // Load the persisted currency symbol into the
                                // app-wide holder once, so every screen
                                // (dashboard, debts, materials, notifications)
                                // shows the right currency from the very
                                // first frame of the real app.
                                LaunchedEffect(Unit) { AppSettingsState.setCurrency(settings.currencySymbol) }

                                // Android 13+ requires explicit runtime
                                // permission to post notifications (needed
                                // for the low-stock shopping list and
                                // new-debt alerts). Requested once the real
                                // UI is up, not while the splash is showing.
                                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.RequestPermission()
                                ) { }
                                LaunchedEffect(Unit) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }

                                if (!unlocked) {
                                    LockScreen(settings = settings, onUnlocked = { unlocked = true })
                                } else {
                                    ShopManagerApp(
                                        settings = settings,
                                        onThemeChanged = { themeMode = it },
                                        onColorPaletteChanged = { colorPalette = it },
                                        onPerformancePreferenceChanged = { performancePreference = it },
                                        pendingNotificationAction = pendingNotificationAction,
                                        onConsumeNotificationAction = { pendingNotificationAction = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Warm start: the app is already running (this Activity instance is
     * still alive) and a notification is tapped. `android:launchMode=
     * "singleTop"` on MainActivity (see AndroidManifest.xml) is what
     * routes the tap here instead of spinning up a whole new Activity
     * instance - without it, this override would simply never fire and
     * the tap would silently do nothing while the app was open/backgrounded.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationAction = NotificationAction.from(intent)
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
private fun ShopManagerApp(
    settings: SettingsRepository,
    onThemeChanged: (AppThemeMode) -> Unit,
    onColorPaletteChanged: (AppColorPalette) -> Unit,
    onPerformancePreferenceChanged: (PerformanceMode) -> Unit,
    pendingNotificationAction: NotificationAction? = null,
    onConsumeNotificationAction: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Shared across screens so everyone sees the same live data instead of
    // spinning up duplicate Firestore listeners per screen.
    val debtsViewModel: DebtsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val materialsViewModel: MaterialsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // Hoisted above the NavHost (rather than inside the pager's own
    // composable) so it survives navigating away to Settings/Help and back,
    // and so the bottom bar can read/drive the current page directly.
    val pagerState = rememberPagerState(initialPage = PAGE_DASHBOARD) { 3 }
    // REDESIGN ("زر عميل جديد بجانب الشريط السفلي"): the "+" that used to
    // be each tab's own FloatingActionButton is now a single shared button
    // rendered as part of FloatingBottomNav itself (see its `quickAction`
    // param) so it always sits attached to the pill instead of floating
    // separately over the list. It can't reach into DebtsScreen's own
    // dialog state directly, so tapping it just raises this flag; DebtsScreen
    // watches it and opens its "عميل جديد" dialog when it turns true (see
    // DebtsScreen's `addPersonRequested` parameter).
    var addPersonRequested by remember { mutableStateOf(false) }
    val pagerScope = rememberCoroutineScope()

    @OptIn(ExperimentalFoundationApi::class)
    fun openPager(page: Int) {
        navigateTopLevel(navController, ROUTE_MAIN_PAGER)
        pagerScope.launch { pagerState.animateScrollToPage(page) }
    }

    // Tapping a notification should land on the screen it's about, not just
    // pop a dialog over whatever tab happened to be open. Debts stay on the
    // الديون tab, the shopping-list one jumps to المواد والأسعار.
    LaunchedEffect(pendingNotificationAction) {
        when (pendingNotificationAction) {
            is NotificationAction.DebtPaid, is NotificationAction.NewDebt -> openPager(PAGE_DEBTS)
            is NotificationAction.ShoppingList -> openPager(PAGE_MATERIALS)
            null -> {}
        }
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

    // ROOT FIX ("الشريط العائم خلفيته لسه بيضاء/سوداء"): a Scaffold(bottomBar
    // = ...) was the actual root cause, not a styling detail inside
    // FloatingBottomNav. Two things about Scaffold made a flat white/black
    // rectangle unavoidable no matter what color/transparency was tried on
    // the bar itself:
    //  1) Scaffold paints its own `containerColor` (default
    //     colorScheme.background) behind whatever is in the bottomBar slot.
    //  2) Scaffold shrinks NavHost's content to stop short of that slot
    //     (via the `padding` it hands down), so there was never any real
    //     page content behind the bar's transparent side margins either —
    //     just empty space over that flat Scaffold color.
    // Both of those were true regardless of FloatingBottomNav's own
    // background, so nothing changed there could ever fix it. This now
    // uses a plain Box instead of Scaffold: NavHost fills the *entire*
    // screen (see the pager tabs' own contentWindowInsets below — Dashboard/
    // Debts/Materials already reserve their own bottom safe-area space, so
    // no double-padding), and FloatingBottomNav is layered on top as a true
    // overlay via Modifier.align(Alignment.BottomCenter). The margins
    // around the pill are now genuinely transparent over the live page
    // (list rows, cards) scrolling underneath — never a separately-painted
    // rectangle.
    // BUG FIXED ("زر عميل جديد/مادة جديدة صار تحت الشريط"): a direct side
    // effect of the overlay fix above. Previously the outer Scaffold's
    // `padding` shrank NavHost so screens never even extended into the
    // pill's area — a screen's own FloatingActionButton (bottom-end of ITS
    // OWN Scaffold) naturally landed above the pill with no extra work.
    // Now that NavHost fills the true screen height, each tab's FAB anchors
    // to the *real* bottom edge same as the pill does — same strip, so the
    // pill (drawn after, on top) covers most of it, leaving only the
    // sliver that peeks out past the pill's own width.
    // FIX: measure the pill's actual rendered height right where it's
    // drawn (`onSizeChanged` below — real layout size, not a guessed
    // constant) and thread it down via [LocalFloatingBottomNavHeight] so
    // DebtsScreen/MaterialsScreen (the two tabs with a FAB) can pad their
    // FAB, and every tab's list, clear of it by that exact amount.
    val density = LocalDensity.current
    var floatingNavHeight by remember { mutableStateOf(0.dp) }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalFloatingBottomNavHeight provides if (showBottomBar) floatingNavHeight else 0.dp
        ) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_MAIN_PAGER,
            // Horizontal safe-area (cutouts/rounded corners) always
            // applies. Bottom safe-area only applies here when there's no
            // floating nav on screen (Settings, person detail, etc.) —
            // when the pager IS showing, Dashboard/Debts/Materials each
            // already reserve their own bottom inset internally (see their
            // own `contentWindowInsets`), and reserving it a second time
            // here is exactly what used to create the empty flat-colored
            // strip behind the bar in the first place.
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .then(
                    if (!showBottomBar) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    } else {
                        Modifier
                    }
                ),
            // PERF/FEEL: LOW tier keeps this at zero cost (no transition at
            // all — the fastest a screen change can be). STANDARD/HIGH pairs
            // a fade with a short, subtle horizontal slide instead of the
            // previous plain crossfade: a one-shot ~200ms transition (not a
            // continuous per-frame cost like the Pager's own drag below) is
            // cheap enough to afford the extra graphicsLayer pass, and it's
            // what actually reads as a "smooth, designed" transition instead
            // of a flat fade that can feel like something's missing.
            // FastOutSlowInEasing (Material's standard curve) so it eases
            // out at the end instead of stopping abruptly.
            enterTransition = {
                if (isLowTier) EnterTransition.None
                else fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 8 }
            },
            exitTransition = { if (isLowTier) ExitTransition.None else fadeOut(tween(160, easing = FastOutSlowInEasing)) },
            popEnterTransition = {
                if (isLowTier) EnterTransition.None
                else fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 8 }
            },
            popExitTransition = { if (isLowTier) ExitTransition.None else fadeOut(tween(160, easing = FastOutSlowInEasing)) }
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
                            onNavigateToMaterials = { openPager(PAGE_MATERIALS) },
                            onOpenAdmin = { navController.navigate(ROUTE_ADMIN) }
                        )
                        PAGE_DEBTS -> DebtsScreen(
                            viewModel = debtsViewModel,
                            onOpenPerson = { personId -> navController.navigate("personDetail/$personId") },
                            addPersonRequested = addPersonRequested,
                            onAddPersonRequestHandled = { addPersonRequested = false }
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
                    onColorPaletteChanged = onColorPaletteChanged,
                    onPerformancePreferenceChanged = onPerformancePreferenceChanged,
                    debtsViewModel = debtsViewModel,
                    materialsViewModel = materialsViewModel,
                    onOpenHelp = { navController.navigate(ROUTE_HELP) },
                    onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY) }
                )
            }
            composable(ROUTE_ADMIN) {
                AdminPanelScreen(
                    onBack = { navController.popBackStack() },
                    debtsViewModel = debtsViewModel,
                    materialsViewModel = materialsViewModel
                )
            }
            composable(ROUTE_HELP) {
                WebViewScreen(
                    url = "file:///android_asset/help.html",
                    title = "دليل الاستخدام",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_PRIVACY) {
                WebViewScreen(
                    url = "file:///android_asset/privacy.html",
                    title = "سياسة الخصوصية",
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
        } // CompositionLocalProvider

        // Drawn AFTER (so visually on top of) NavHost above — a real
        // overlay, not a layout slot with its own painted background. This
        // is the piece that actually fixes the white/black rectangle: the
        // pill now sits directly over whichever tab is currently showing,
        // so its transparent margins reveal that tab's real content.
        if (showBottomBar) {
            // REDESIGN: which quick-add action (if any) shows beside the
            // pill follows the same `pagerState.currentPage` the pill's own
            // `selectedIndex` below already tracks — one page, one source
            // of truth for "what tab is this". Home has nothing to add, so
            // it's null there and the button fades out entirely (see
            // FloatingBottomNav's own AnimatedVisibility around it).
            val quickAction = when (pagerState.currentPage) {
                PAGE_DEBTS -> QuickAction(
                    icon = Icons.Default.Add,
                    contentDescription = "عميل جديد",
                    onClick = { addPersonRequested = true }
                )
                PAGE_MATERIALS -> QuickAction(
                    icon = Icons.Default.Add,
                    contentDescription = "مادة جديدة",
                    onClick = { navController.navigate(ROUTE_MATERIAL_CATALOG) }
                )
                else -> null
            }
            FloatingBottomNav(
                items = listOf(
                    BottomNavItem(Icons.Default.Home, "الرئيسية"),
                    BottomNavItem(Icons.Default.AttachMoney, "الديون"),
                    BottomNavItem(Icons.Default.Inventory2, "المواد والأسعار")
                ),
                selectedIndex = pagerState.currentPage,
                onSelect = { page -> openPager(page) },
                quickAction = quickAction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Real measured layout height (margins included, since
                    // this size is taken at FloatingBottomNav's own root —
                    // see its Box in FloatingBottomNav.kt), converted from
                    // px to dp with the current screen density. This is
                    // what LocalFloatingBottomNavHeight above actually
                    // reflects.
                    .onSizeChanged { size ->
                        floatingNavHeight = with(density) { size.height.toDp() }
                    }
            )
        }
    }

    // The confirmation dialog a tapped notification opens the app to show
    // ("تم تسديد الدين", "عميل جديد", "قائمة المشتريات"). Rendered as a
    // sibling of the Box above (an AlertDialog is its own system window,
    // not part of that layout) so it appears on top regardless of which
    // tab openPager() just switched to.
    pendingNotificationAction?.let { action ->
        when (action) {
            is NotificationAction.DebtPaid -> AlertDialog(
                onDismissRequest = onConsumeNotificationAction,
                title = { Text("✅ تم سداد دين") },
                text = { Text("${action.personName} وفى ${action.amount} ${action.currency}") },
                confirmButton = { TextButton(onClick = onConsumeNotificationAction) { Text("موافق") } }
            )
            is NotificationAction.NewDebt -> AlertDialog(
                onDismissRequest = onConsumeNotificationAction,
                title = { Text("💰 عميل جديد بالديون") },
                text = { Text("${action.personName} — ${action.amount} ${action.currency}") },
                confirmButton = { TextButton(onClick = onConsumeNotificationAction) { Text("موافق") } }
            )
            is NotificationAction.ShoppingList -> AlertDialog(
                onDismissRequest = onConsumeNotificationAction,
                title = { Text("🛒 قائمة المشتريات") },
                text = {
                    Text(
                        if (action.materialNames.isEmpty()) "لا توجد مواد ناقصة حالياً"
                        else action.materialNames.joinToString("، ")
                    )
                },
                confirmButton = { TextButton(onClick = onConsumeNotificationAction) { Text("موافق") } }
            )
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
