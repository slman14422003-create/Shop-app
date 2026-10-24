package com.salman.herbalencyclopedia.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.salman.herbalencyclopedia.data.ai.AiConfig
import com.salman.herbalencyclopedia.data.repository.PreferencesRepository
import com.salman.herbalencyclopedia.ui.AppViewModel
import com.salman.herbalencyclopedia.ui.components.GlassBackdropState
import com.salman.herbalencyclopedia.ui.components.LocalBottomBarInset
import com.salman.herbalencyclopedia.ui.components.LocalGlassBackdrop
import com.salman.herbalencyclopedia.ui.components.OneUiFloatingNavBar
import com.salman.herbalencyclopedia.ui.components.OneUiNavItem
import com.salman.herbalencyclopedia.ui.components.OneUiNavigationRail
import com.salman.herbalencyclopedia.ui.components.glassBackdropSource
import com.salman.herbalencyclopedia.ui.util.rememberWindowSizeInfo
import com.salman.herbalencyclopedia.ui.screens.admin.*
import com.salman.herbalencyclopedia.ui.screens.allherbs.AllHerbsScreen
import com.salman.herbalencyclopedia.ui.screens.auth.LoginScreen
import com.salman.herbalencyclopedia.ui.screens.blends.BlendDetailScreen
import com.salman.herbalencyclopedia.ui.screens.blends.BlendsScreen
import com.salman.herbalencyclopedia.ui.screens.category.CategoryHerbsScreen
import com.salman.herbalencyclopedia.ui.screens.semo.SemoAssistantScreen
import com.salman.herbalencyclopedia.ui.screens.favorites.FavoritesScreen
import com.salman.herbalencyclopedia.ui.screens.feedback.SendFeedbackScreen
import com.salman.herbalencyclopedia.ui.screens.help.HelpScreen
import com.salman.herbalencyclopedia.ui.screens.herbdetail.HerbDetailScreen
import com.salman.herbalencyclopedia.ui.screens.home.HomeScreen
import com.salman.herbalencyclopedia.ui.screens.onboarding.WelcomeScreen
import com.salman.herbalencyclopedia.ui.screens.search.SearchScreen
import com.salman.herbalencyclopedia.ui.screens.settings.SettingsScreen
import com.salman.herbalencyclopedia.ui.screens.splash.SplashScreen
import com.salman.herbalencyclopedia.ui.screens.terms.TermsScreen
import com.salman.herbalencyclopedia.ui.screens.tools.AdminToolsScreen
import com.salman.herbalencyclopedia.ui.theme.rememberSmoothMotionAllowed
import kotlinx.coroutines.launch

@Composable
fun HerbalNavGraph(
    appViewModel: AppViewModel,
    preferencesRepository: PreferencesRepository,
    navController: NavHostController = rememberNavController(),
    /** راجع MainActivity.pendingDeepLink — حالياً القيمة الوحيدة المدعومة هي [UpdateNotifier.OPEN_SCREEN_SETTINGS] ("settings")، قادمة من الضغط على إشعار تحديث متوفر. */
    pendingDeepLink: String? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    // قدرة جديدة: انتقالات التنقّل بين الشاشات (أدناه في NavHost) كانت
    // الحركة الوحيدة بالتطبيق التي تتجاهل وضع الأداء ومعدّل التحديث الفعلي
    // تماماً — بعكس كل حركة أخرى بالتطبيق (staggeredEntrance، entranceFade...
    // راجع Animations.kt) التي تحترم [rememberSmoothMotionAllowed] بدقة. هذا
    // بالضبط ما يجعل الحركة تبدو "متقطعة وغير متناسقة بكل الواجهات": حركات
    // ظهور العناصر داخل الشاشة تتوقف بذكاء على جهاز ضعيف/وضع اقتصادي بينما
    // الانتقال *بين* الشاشات نفسها يستمر يشغّل fade+slide كاملة فتتصادم
    // الحركتان بإحساسين مختلفين تماماً في نفس اللحظة. الآن تُستخدم نفس
    // الإشارة هنا أيضاً: تنتقل الشاشات فوراً بلا أي حركة على جهاز ضعيف أو
    // معدّل تحديث منخفض حالياً، وتبقى نفس حركة fade+slide السلسة كما هي
    // على الأجهزة/الحالات التي تتحمّلها فعلاً.
    val smoothMotionAllowed = rememberSmoothMotionAllowed()
    val uiState by appViewModel.uiState.collectAsState()
    val favoriteIds by appViewModel.favoriteIds.collectAsState()
    val webHerbsState by appViewModel.webHerbs.collectAsState()
    val darkMode by preferencesRepository.darkMode.collectAsState(initial = null)
    // null = لم تُقرأ بعد من DataStore. نتعامل معها كـ "لم يوافق بعد" (نفس
    // معاملة false) لتفادي أي وميض للشاشة الرئيسية قبل عرض شاشة الترحيب.
    val termsAccepted by preferencesRepository.termsAccepted.collectAsState(initial = null)
    // شاشة ترحيب/شروط سيمو المنفصلة (انظر SemoIntroScreen) — initial = null
    // (لا true ولا false) حتى لا تُخطَف بلمحة سريعة من شاشة سيمو الحقيقية
    // قبل اكتمال قراءة القيمة الفعلية من DataStore عند أول رسم للشاشة.
    val semoIntroSeen by preferencesRepository.semoIntroSeen.collectAsState(initial = null)
    val dynamicColor by preferencesRepository.dynamicColor.collectAsState(initial = true)
    val fontScale by preferencesRepository.fontScale.collectAsState(initial = 0)
    val themePalette by preferencesRepository.themePalette.collectAsState(
        initial = com.salman.herbalencyclopedia.ui.theme.ThemePalette.LEAF
    )
    val performanceMode by preferencesRepository.performanceMode.collectAsState(
        initial = com.salman.herbalencyclopedia.ui.theme.PerformanceMode.HIGH_QUALITY
    )
    val appLanguage by preferencesRepository.appLanguage.collectAsState(
        initial = com.salman.herbalencyclopedia.ui.util.AppLanguage.ARABIC
    )
    val updateState by appViewModel.updateState.collectAsState()
    val downloadState by appViewModel.downloadState.collectAsState()
    val updateConfig by appViewModel.updateConfigState.collectAsState()
    // إعدادات "سيمو المساعد" — تُحمَّل من DataStore وتُطبَّق حياً على
    // AiConfig (الكائن الذي يقرأ منه HerbAssistant مباشرة)، بحيث أي تعديل
    // من أدوات المطور ينعكس فوراً على شاشة سيمو دون إعادة تشغيل التطبيق.
    val aiSimilarityThreshold by preferencesRepository.aiSimilarityThreshold.collectAsState(
        initial = AiConfig.defaultSimilarityThreshold.toFloat()
    )
    val aiSearchThreshold by preferencesRepository.aiSearchThreshold.collectAsState(
        initial = AiConfig.defaultSearchThreshold.toFloat()
    )
    val aiExtraStopWords by preferencesRepository.aiExtraStopWords.collectAsState(initial = emptySet())
    val aiSynonyms by preferencesRepository.aiSynonyms.collectAsState(initial = emptyMap())
    val aiTrainedExamples by preferencesRepository.aiTrainedExamples.collectAsState(initial = emptyList())
    val aiTrainedThreshold by preferencesRepository.aiTrainedThreshold.collectAsState(
        initial = AiConfig.defaultTrainedThreshold.toFloat()
    )
    // "تعلّم سيمو الذاتي" — حالات يولّدها التطبيق تلقائياً من تقييمات
    // المستخدمين (👍/👎) على إجابات البحث الحر في شاشة الدردشة، منفصلة عن
    // تدريب المطوّر اليدوي أعلاه، ومحفوظة/مُطبَّقة حياً بنفس الآلية.
    val aiAutoLearnedExamples by preferencesRepository.aiAutoLearnedExamples.collectAsState(initial = emptyList())
    val aiAutoLearnEnabled by preferencesRepository.aiAutoLearnEnabled.collectAsState(
        initial = AiConfig.defaultAutoLearnEnabled
    )
    // الوضع الذكي عبر الإنترنت (Gemini المجاني) — نفس آلية العيش/التطبيق
    // الفوري أعلاه، راجع توثيق AiConfig.onlineEnabled في HerbAssistant.kt.
    val aiOnlineEnabled by preferencesRepository.aiOnlineEnabled.collectAsState(
        initial = AiConfig.defaultOnlineEnabled
    )
    val aiOnlineApiKey by preferencesRepository.aiOnlineApiKey.collectAsState(initial = "")
    val aiOnlineModel by preferencesRepository.aiOnlineModel.collectAsState(
        initial = AiConfig.defaultOnlineModel
    )
    val aiOnlineBaseUrl by preferencesRepository.aiOnlineBaseUrl.collectAsState(
        initial = AiConfig.defaultOnlineBaseUrl
    )
    LaunchedEffect(
        aiSimilarityThreshold, aiSearchThreshold, aiExtraStopWords, aiSynonyms,
        aiTrainedExamples, aiTrainedThreshold, aiAutoLearnedExamples, aiAutoLearnEnabled,
        aiOnlineEnabled, aiOnlineApiKey, aiOnlineModel, aiOnlineBaseUrl
    ) {
        AiConfig.similarityThreshold = aiSimilarityThreshold.toDouble()
        AiConfig.searchThreshold = aiSearchThreshold.toDouble()
        AiConfig.extraStopWords = aiExtraStopWords
        AiConfig.synonyms = aiSynonyms
        AiConfig.trainedExamples = aiTrainedExamples
        AiConfig.trainedMatchThreshold = aiTrainedThreshold.toDouble()
        AiConfig.autoLearnedExamples = aiAutoLearnedExamples
        AiConfig.autoLearnEnabled = aiAutoLearnEnabled
        AiConfig.onlineEnabled = aiOnlineEnabled
        AiConfig.onlineApiKey = aiOnlineApiKey
        AiConfig.onlineModel = aiOnlineModel
        AiConfig.onlineBaseUrl = aiOnlineBaseUrl
    }
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // ═══ ملاحظة: منع لقطة الشاشة أصبح على مستوى التطبيق كاملاً ═══
    // كان هنا سابقاً SideEffect يُفعّل FLAG_SECURE فقط أثناء شاشات الإدارة
    // (المسارات التي تبدأ بـ"admin") ويُزيله في أي شاشة أخرى، تفادياً لوميض
    // أسود عند العودة من مبدّل التطبيقات (راجع الشرح الكامل والتاريخ في
    // MainActivity.onCreate). بطلب صريح من المستخدم بتعميم منع لقطة الشاشة
    // على التطبيق بأكمله، أصبح FLAG_SECURE يُضبط مرة واحدة هناك طوال حياة
    // النشاط. أُزيل هذا المنطق من هنا لأنه كان سيتعارض معه مباشرة: لو بقي
    // موجوداً لكان يُلغي (clearFlags) العلَم العام في كل مرة يغادر فيها
    // المستخدم شاشات الإدارة، فتنكشف بقية شاشات التطبيق من جديد رغم الطلب
    // الصريح بحمايتها كلها.
    // *** إصلاح: الضغط على إشعار "تحديث متوفر" كان يفتح الشاشة الرئيسية فقط
    // بلا أي انتقال فعلي لشاشة الإعدادات. *** يُفعَّل فقط بعد تجاوز شاشتي
    // البداية (Splash/Welcome) — أي بعد قبول الشروط فعلياً وبدء التنقّل
    // الطبيعي — حتى لا يتصادم مع منطق توجيه أول تشغيل (شاشة الترحيب/الشروط
    // لمستخدم جديد لم يوافق عليها بعد)، ثم يتنقّل لشاشة الإعدادات من أي
    // شاشة كان المستخدم عليها لحظة الضغط على الإشعار.
    LaunchedEffect(pendingDeepLink, termsAccepted, current) {
        if (pendingDeepLink == com.salman.herbalencyclopedia.data.update.UpdateNotifier.OPEN_SCREEN_SETTINGS &&
            termsAccepted == true &&
            current != null && current != Screen.Splash.route
        ) {
            navController.navigate(Screen.Settings.route)
            onDeepLinkHandled()
        }
    }
    val topRoutes = setOf(Screen.Home.route, Screen.AllHerbs.route, Screen.Favorites.route, Screen.Settings.route)
    val scope = rememberCoroutineScope()
    val bottomNavHomeLabel = com.salman.herbalencyclopedia.ui.util.tr("الرئيسية")
    val bottomNavHerbsLabel = com.salman.herbalencyclopedia.ui.util.tr("الأعشاب")
    val bottomNavFavoritesLabel = com.salman.herbalencyclopedia.ui.util.tr("المفضلة")
    val bottomNavSettingsLabel = com.salman.herbalencyclopedia.ui.util.tr("الإعدادات")
    val bottomNavItems = remember(bottomNavHomeLabel, bottomNavHerbsLabel, bottomNavFavoritesLabel, bottomNavSettingsLabel) {
        listOf(
            OneUiNavItem(bottomNavHomeLabel, Icons.Filled.Home, Screen.Home.route),
            OneUiNavItem(bottomNavHerbsLabel, Icons.Filled.MenuBook, Screen.AllHerbs.route),
            OneUiNavItem(bottomNavFavoritesLabel, Icons.Filled.Favorite, Screen.Favorites.route),
            OneUiNavItem(bottomNavSettingsLabel, Icons.Filled.Settings, Screen.Settings.route)
        )
    }
    // محرك اكتشاف الشاشة يقرر نمط التنقّل: شريط عائم سفلي بالإبهام على
    // جوال (COMPACT)، وشريط جانبي ثابت على تابلت/نافذة عريضة
    // (MEDIUM/EXPANDED) بدل تمديد نفس الشريط السفلي بعرض الشاشة كاملاً —
    // وهو ما كان يحدث سابقاً بلا أي تمييز بين الحجمين.
    val windowInfo = rememberWindowSizeInfo()
    val onNavItemClick: (OneUiNavItem) -> Unit = { item ->
        navController.navigate(item.route) {
            popUpTo(Screen.Home.route)
            launchSingleTop = true
        }
    }
    val showNav = current in topRoutes
    // حالة "الزجاج الحقيقي" (انظر GlassBackdrop.kt): مشتركة بين خلفية
    // الشاشة (AmbientBackground أدناه، المصدر) والشريطين العائمين العلوي/
    // السفلي (المستهلكان، عبر LocalGlassBackdrop.current داخل OneUiBars.kt)
    // كي يريا فعلياً نفس المحتوى الحيّ خلفهما بدل تدرّج تقريبي ثابت.
    val glassBackdrop = remember { GlassBackdropState() }
    // ارتفاع الشريط العائم السفلي الفعلي (مقاساً بعد الرسم، وليس ثابتاً
    // مفترَضاً) — يُستخدم كهامش سفلي لحاوية NavHost فقط، بينما تبقى خلفية
    // AmbientBackground ممتدة كامل الشاشة خلف الشريط نفسه. هذا بالضبط ما
    // يسمح للشريط بأن يطفو فوق امتداد حقيقي لخلفية التطبيق بدل التوقّف
    // عندها (وهو ما كان يحدث سابقاً عندما كان الشريط في فتحة bottomBar
    // الخاصة بـScaffold، والتي تحجز مساحتها الخاصة فتفصل الخلفية عنه تماماً).
    var navBarHeightPx by remember { mutableStateOf(0) }

    Scaffold(
        // كل شاشة تدير حواف نظامها بنفسها: GlassTopBar/TopAppBar يتكفّل بشريط
        // الحالة العلوي. الشريط السفلي لم يعد يُمرَّر عبر فتحة bottomBar هنا
        // (انظر التعليق أعلاه)؛ يُرسم بدلاً من ذلك كطبقة عائمة فوق نفس صندوق
        // المحتوى أدناه. لو ترك Scaffold الخارجي هنا القيمة الافتراضية
        // (safeDrawing) لحجز مساحة إضافية لنفس الحواف، تظهر فجوة مزدوجة
        // أعلى/أسفل كل شاشة — وعلى شاشة البداية تحديداً كانت تقطع التدرّج
        // اللوني قبل أن يصل لحواف الشاشة فعلياً.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { inner ->
        Row(Modifier.padding(inner).fillMaxSize()) {
            // على تابلت/نافذة عريضة: شريط جانبي ثابت بجوار المحتوى بدل
            // شريط سفلي عائم يمتد بعرض الشاشة كاملاً بعيداً عن متناول
            // الإبهام في تلك الحالة.
            if (showNav && windowInfo.useNavigationRail) {
                OneUiNavigationRail(
                    items = bottomNavItems,
                    currentRoute = current,
                    onItemClick = onNavItemClick
                )
            }
        Box(Modifier.weight(1f).fillMaxSize()) {
          val floatingBarActive = showNav && !windowInfo.useNavigationRail
          val navBarHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) { navBarHeightPx.toDp() }
          // مزوَّدة هنا (فوق AmbientBackground والشريط العائم معاً) كي يقرأ
          // كلا الشريطين (العلوي داخل شاشات NavHost، والسفلي أدناه) نفس
          // حالة الخلفية الحيّة الواحدة.
          CompositionLocalProvider(
              LocalGlassBackdrop provides glassBackdrop,
              // القيمة الحقيقية (لا صفر) تُزوَّد فقط هنا؛ أي شاشة تحتها
              // تقرأها لتحجز نفس المساحة كهامش سفلي *داخل* قائمتها القابلة
              // للتمرير (contentPadding) بدل هامش خارجي على NavHost كاملاً
              // كما كان سابقاً — الفرق الجوهري: NavHost نفسه صار يمتد كامل
              // الارتفاع خلف الشريط فعلياً (انظر أدناه)، فتظهر نهاية أي
              // قائمة فعلياً خلف الزجاج أثناء التمرير نفسه بدل توقّفها قبل
              // تلك المنطقة بمسافة ثابتة وكأن خلف الشريط صورة خلفية منفصلة.
              LocalBottomBarInset provides if (floatingBarActive) navBarHeightDp else 0.dp
          ) {
            com.salman.herbalencyclopedia.ui.components.AmbientBackground(
                modifier = Modifier.glassBackdropSource(glassBackdrop)
            )
            // بلا أي هامش سفلي هنا الآن (قارن بالتعليق أعلاه): NavHost يملأ
            // كامل الصندوق حتى خلف الشريط العائم تماماً، وكل شاشة تتكفّل هي
            // بحجز نفس [navBarHeightDp] كـcontentPadding داخل قائمتها.
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                enterTransition = {
                    if (!smoothMotionAllowed) androidx.compose.animation.EnterTransition.None else
                    androidx.compose.animation.fadeIn(com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth()) +
                        androidx.compose.animation.slideInHorizontally(
                            com.salman.herbalencyclopedia.ui.theme.AppMotion.silky()
                        ) { it / 6 }
                },
                exitTransition = {
                    if (!smoothMotionAllowed) androidx.compose.animation.ExitTransition.None else
                    androidx.compose.animation.fadeOut(
                        com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth(
                            com.salman.herbalencyclopedia.ui.theme.AppMotion.Quick
                        )
                    )
                },
                popEnterTransition = {
                    if (!smoothMotionAllowed) androidx.compose.animation.EnterTransition.None else
                    androidx.compose.animation.fadeIn(com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth())
                },
                popExitTransition = {
                    if (!smoothMotionAllowed) androidx.compose.animation.ExitTransition.None else
                    androidx.compose.animation.fadeOut(com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth()) +
                        androidx.compose.animation.slideOutHorizontally(
                            com.salman.herbalencyclopedia.ui.theme.AppMotion.silky()
                        ) { it / 6 }
                }
            ) {
                composable(Screen.Splash.route) {
                    val syncProgress by appViewModel.syncProgress.collectAsState()
                    SplashScreen(
                        isDataReady = !uiState.isLoading,
                        hasData = uiState.herbs.isNotEmpty(),
                        syncProgress = syncProgress,
                        onRetry = appViewModel::refresh,
                        onFinished = {
                            val destination = if (termsAccepted == true) Screen.Home.route else Screen.Welcome.route
                            navController.navigate(destination) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Welcome.route) {
                    WelcomeScreen(
                        currentLanguage = appLanguage,
                        onLanguageChange = { language -> scope.launch { preferencesRepository.setAppLanguage(language) } },
                        onViewFullPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                        onAgree = {
                            scope.launch {
                                preferencesRepository.setTermsAccepted(true)
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }
                composable(Screen.Home.route) { HomeScreen(uiState.categories, uiState.herbs, uiState.isLoading, uiState.error, appViewModel.isAdmin, appViewModel::refresh, { c -> navController.navigate(Screen.CategoryHerbs.createRoute(c.id,c.name)) }, { navController.navigate(Screen.Search.route) }, { navController.navigate(Screen.Favorites.route) }, { navController.navigate(Screen.Settings.route) }, { navController.navigate(Screen.Admin.route) }, { navController.navigate(Screen.SemoAssistant.route) }, { navController.navigate(Screen.Blends.route) }, { navController.navigate(Screen.Support.route) }) }
                composable(Screen.AllHerbs.route) { var autoRefreshTried by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }; LaunchedEffect(uiState.isLoading, uiState.herbs.isEmpty()) { if (!autoRefreshTried && uiState.herbs.isEmpty() && !uiState.isLoading) { autoRefreshTried = true; appViewModel.refresh() } }; AllHerbsScreen(uiState.herbs, favoriteIds, { h -> navController.navigate(Screen.HerbDetail.createRoute(h.id)) }, appViewModel::toggleFavorite, uiState.isLoading, appViewModel::refresh, isLoading = uiState.isLoading) }
                composable(Screen.Favorites.route) { FavoritesScreen(uiState.herbs.filter { it.id in favoriteIds }, { h -> navController.navigate(Screen.HerbDetail.createRoute(h.id)) }, appViewModel::toggleFavorite, { navController.navigate(Screen.AllHerbs.route) { popUpTo(Screen.Home.route); launchSingleTop = true } }) }
                composable(Screen.Settings.route) { SettingsScreen(appViewModel.isLoggedIn, appViewModel.isAdmin, darkMode, dynamicColor, fontScale, themePalette, performanceMode, appLanguage, updateState, downloadState, { navController.popBackStack() }, { scope.launch { preferencesRepository.setDarkMode(it) } }, { scope.launch { preferencesRepository.setDynamicColor(it) } }, { scope.launch { preferencesRepository.setFontScale(it) } }, { scope.launch { preferencesRepository.setThemePalette(it) } }, { scope.launch { preferencesRepository.setPerformanceMode(it) } }, { preferencesRepository.setAppLanguage(it) }, { navController.navigate(Screen.Login.route) }, { appViewModel.logout() }, { navController.navigate(Screen.Help.route) }, { navController.navigate(Screen.Support.route) }, { navController.navigate(Screen.PrivacyPolicy.route) }, { navController.navigate(Screen.Terms.route) }, { if (appViewModel.isAdmin) navController.navigate(Screen.AdminTools.route) }, { if (appViewModel.isAdmin) navController.navigate(Screen.AdminFeedback.route) }, { ctx -> appViewModel.checkForUpdate(ctx) }, { ctx, info -> appViewModel.downloadUpdate(ctx, info) }, { ctx -> appViewModel.installUpdate(ctx) }, { ctx -> appViewModel.cancelDownload(ctx) }, herbsForEval = uiState.herbs) }
                composable(Screen.Search.route) {
                    SearchScreen(
                        herbs = uiState.herbs,
                        blends = uiState.blends,
                        categories = uiState.categories,
                        favoriteIds = favoriteIds,
                        onBack = { navController.popBackStack() },
                        onHerbClick = { h -> navController.navigate(Screen.HerbDetail.createRoute(h.id)) },
                        onBlendClick = { b -> navController.navigate(Screen.BlendDetail.createRoute(b.id)) },
                        onCategoryClick = { c -> navController.navigate(Screen.CategoryHerbs.createRoute(c.id, c.name)) },
                        onToggleFavorite = appViewModel::toggleFavorite
                    )
                }
                composable(Screen.SemoAssistant.route) {
                    // أول فتح لسيمو فقط: تُعرض شاشة الترحيب/الشروط الخاصة به
                    // قبل أي محادثة فعلية (انظر SemoIntroScreen وتوثيق
                    // semoIntroSeen أعلاه). null = لم تُقرأ القيمة بعد، فلا
                    // نعرض أي شيء لحظياً تفادياً لومضة شاشة خاطئة.
                    if (semoIntroSeen == false) {
                        com.salman.herbalencyclopedia.ui.screens.semo.SemoIntroScreen(
                            onAccept = { scope.launch { preferencesRepository.setSemoIntroSeen(true) } }
                        )
                    } else if (semoIntroSeen == true) {
                        SemoAssistantScreen(
                            herbs = uiState.herbs,
                            blends = uiState.blends,
                            onBack = { navController.popBackStack() },
                            onAutoLearnedExamplesChange = { list -> scope.launch { preferencesRepository.setAiAutoLearnedExamples(list) } },
                            // مزامنة بين الأجهزة: يرفع نفس الحالة المحفوظة محلياً
                            // للتو إلى Firestore (👍 = صوت إيجابي/إنشاء، 👎 = صوت
                            // سلبي) عبر AppViewModel — انظر توثيق SemoAssistantScreen
                            // وAppViewModel.contributeSemoLearning/demoteSemoLearning.
                            onFeedbackRecorded = { question, answer, helpful ->
                                if (helpful) appViewModel.contributeSemoLearning(question, answer)
                                else appViewModel.demoteSemoLearning(question)
                            },
                            // معرفة الإنترنت: أعشاب تعلّمها سيمو + إشعارات تعلّم/سؤال بلا جواب.
                            webHerbs = webHerbsState,
                            onWebHerbLearned = { appViewModel.contributeWebHerb(it) },
                            onUnanswered = { appViewModel.logUnansweredQuestion(it) },
                            onOpenHerb = { id -> navController.navigate(Screen.HerbDetail.createRoute(id)) }
                            // ملاحظة: لا حاجة لتمرير إعدادات الوضع الذكي عبر
                            // الإنترنت (aiOnlineEnabled/aiOnlineApiKey) هنا
                            // صراحة — SemoAssistantScreen يقرأها مباشرة من
                            // AiConfig الحي (نفس الكائن المُحدَّث أعلاه في
                            // LaunchedEffect)، تماماً كبقية إعدادات AiConfig
                            // الأخرى التي لا تُمرَّر كمعاملات صريحة (العتبات
                            // المحلية، المرادفات...).
                        )
                    }
                }
                composable(Screen.Help.route) { HelpScreen { navController.popBackStack() } }
                composable(Screen.Support.route) {
                    com.salman.herbalencyclopedia.ui.screens.support.SupportScreen { navController.popBackStack() }
                }
                composable(Screen.PrivacyPolicy.route) {
                    com.salman.herbalencyclopedia.ui.screens.privacy.PrivacyPolicyScreen { navController.popBackStack() }
                }
                composable(Screen.Terms.route) {
                    TermsScreen { navController.popBackStack() }
                }
                composable(Screen.CategoryHerbs.route, arguments=listOf(navArgument("categoryId"){type=NavType.StringType},navArgument("categoryName"){type=NavType.StringType})) { e -> val id=e.arguments?.getString("categoryId") ?: ""; val name=e.arguments?.getString("categoryName") ?: ""; CategoryHerbsScreen(name, uiState.herbs.filter { it.categoryId==id }, favoriteIds, {navController.popBackStack()}, {h->navController.navigate(Screen.HerbDetail.createRoute(h.id))}, appViewModel::toggleFavorite) }
                composable(
                    Screen.HerbDetail.route,
                    arguments = listOf(navArgument("herbId") { type = NavType.StringType }),
                    // دخول/خروج مميّز لتفاصيل العشبة: تكبير من المنتصف + تلاشي
                    // بدل الانزلاق الأفقي العام، لإحساس "فتح البطاقة" بدل تنقّل عادي.
                    enterTransition = {
                        androidx.compose.animation.fadeIn(com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth()) +
                            androidx.compose.animation.scaleIn(
                                com.salman.herbalencyclopedia.ui.theme.AppMotion.silky(),
                                initialScale = 0.92f
                            )
                    },
                    exitTransition = {
                        androidx.compose.animation.fadeOut(
                            com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth(
                                com.salman.herbalencyclopedia.ui.theme.AppMotion.Quick
                            )
                        )
                    },
                    popEnterTransition = {
                        androidx.compose.animation.fadeIn(com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth())
                    },
                    popExitTransition = {
                        androidx.compose.animation.fadeOut(com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth()) +
                            androidx.compose.animation.scaleOut(
                                com.salman.herbalencyclopedia.ui.theme.AppMotion.silky(),
                                targetScale = 0.92f
                            )
                    }
                ) { e ->
                    uiState.herbs.firstOrNull { it.id == e.arguments?.getString("herbId") }?.let { h ->
                        HerbDetailScreen(h, h.id in favoriteIds, { navController.popBackStack() }, { appViewModel.toggleFavorite(h.id) }, { navController.navigate(Screen.SendFeedback.createRoute("herb", h.id, h.name)) })
                    }
                }
                composable(Screen.Login.route) { LoginScreen({ navController.popBackStack() }, appViewModel::login) { navController.popBackStack() } }
                composable(Screen.Admin.route) { if (appViewModel.isAdmin) AdminListScreen(uiState.herbs, {navController.popBackStack()}, {navController.navigate(Screen.AdminEdit.createRoute(Screen.AdminEdit.NEW))}, {h->navController.navigate(Screen.AdminEdit.createRoute(h.id))}, {h->appViewModel.deleteHerb(h.id) { _, _ -> }}) { navController.navigate(Screen.AdminTools.route) } }
                composable(Screen.AdminTools.route) {
                    if (appViewModel.isAdmin) AdminToolsScreen(
                        categories = uiState.categories,
                        herbs = uiState.herbs,
                        blends = uiState.blends,
                        onMigrateImages = { cb -> appViewModel.migrateInlineImages(cb) },
                        onBack = { navController.popBackStack() },
                        onRefresh = appViewModel::refresh,
                        onAddCategory = { n, cb -> appViewModel.addCategory(n, cb) },
                        onUpdateCategory = { id, n, cb -> appViewModel.updateCategory(id, n, cb) },
                        onDeleteCategory = { id, cb -> appViewModel.deleteCategory(id, cb) },
                        onDeleteAllHerbs = { cb -> appViewModel.deleteAllHerbs(cb) },
                        onDeleteAllData = { cb -> appViewModel.deleteAllData(cb) },
                        onTestConnection = { cb -> appViewModel.testConnection(cb) },
                        onClearFavorites = { appViewModel.clearFavorites() },
                        onRestoreBackup = { json, cb -> appViewModel.restoreBackup(json, cb) },
                        onUpdateSettingsClick = { navController.navigate(Screen.AdminUpdate.route) },
                        aiSimilarityThreshold = aiSimilarityThreshold,
                        aiSearchThreshold = aiSearchThreshold,
                        aiExtraStopWords = aiExtraStopWords,
                        onSetAiSimilarityThreshold = { v -> scope.launch { preferencesRepository.setAiSimilarityThreshold(v) } },
                        onSetAiSearchThreshold = { v -> scope.launch { preferencesRepository.setAiSearchThreshold(v) } },
                        onSetAiExtraStopWords = { w -> scope.launch { preferencesRepository.setAiExtraStopWords(w) } },
                        onResetAiSettings = { scope.launch { preferencesRepository.resetAiSettings() } },
                        onOpenTrainedCases = { navController.navigate(Screen.AdminTrainedCases.route) },
                        onOpenSemoLearning = { navController.navigate(Screen.AdminSemoLearning.route) },
                        onOpenSemoWeb = { navController.navigate(Screen.AdminSemoWeb.route) },
                        aiOnlineEnabled = aiOnlineEnabled,
                        aiOnlineApiKey = aiOnlineApiKey,
                        aiOnlineModel = aiOnlineModel,
                        aiOnlineBaseUrl = aiOnlineBaseUrl,
                        onSetAiOnlineEnabled = { v -> scope.launch { preferencesRepository.setAiOnlineEnabled(v) } },
                        onSetAiOnlineApiKey = { v -> scope.launch { preferencesRepository.setAiOnlineApiKey(v) } },
                        onSetAiOnlineModel = { v -> scope.launch { preferencesRepository.setAiOnlineModel(v) } },
                        onSetAiOnlineBaseUrl = { v -> scope.launch { preferencesRepository.setAiOnlineBaseUrl(v) } },
                        onResetAiOnlineSettings = { scope.launch { preferencesRepository.resetAiOnlineSettings() } },
                        deviceStatsLoading = appViewModel.deviceStatsLoading,
                        deviceStatsTotal = appViewModel.deviceStatsTotal,
                        deviceStatsActive7d = appViewModel.deviceStatsActive7d,
                        deviceStatsError = appViewModel.deviceStatsError,
                        onRefreshDeviceStats = { appViewModel.refreshDeviceStats() },
                        deviceGrowthLoading = appViewModel.deviceGrowthLoading,
                        deviceGrowth = appViewModel.deviceGrowth,
                        deviceGrowthError = appViewModel.deviceGrowthError,
                        onRefreshDeviceGrowth = { appViewModel.refreshDeviceGrowth() },
                        crashLogsLoading = appViewModel.crashLogsLoading,
                        crashLogs = appViewModel.crashLogs,
                        crashLogsError = appViewModel.crashLogsError,
                        onRefreshCrashLogs = { appViewModel.refreshCrashLogs() },
                        onClearCrashLogs = { cb -> appViewModel.clearCrashLogs(cb) },
                        maintenanceEnabled = appViewModel.maintenanceAdminStatusEnabled,
                        maintenanceMessage = appViewModel.maintenanceAdminStatusMessage,
                        onSaveMaintenanceConfig = { enabled, message, cb -> appViewModel.saveMaintenanceConfig(enabled, message, cb) }
                    )
                }
                composable(Screen.AdminUpdate.route) {
                    if (appViewModel.isAdmin) {
                        LaunchedEffect(Unit) { appViewModel.loadUpdateConfig() }
                        val adminTestState by appViewModel.adminUpdateTestState.collectAsState()
                        com.salman.herbalencyclopedia.ui.screens.admin.AdminUpdateScreen(
                            config = updateConfig,
                            testState = adminTestState,
                            onBack = { navController.popBackStack() },
                            onSave = { config, cb -> appViewModel.saveUpdateConfig(config, cb) },
                            onTestNow = { ctx, config -> appViewModel.testUpdateConfig(ctx, config) }
                        )
                    }
                }
                // ── شاشتان مستقلتان لـ"الحالات المدرَّبة" و"تعلّم سيمو الذاتي"،
                // كانتا مدمجتين ضمن AdminTools أعلاه (راجع توثيق onOpenTrainedCases/
                // onOpenSemoLearning في AdminToolsScreen.kt). ──
                composable(Screen.AdminTrainedCases.route) {
                    if (appViewModel.isAdmin) {
                        com.salman.herbalencyclopedia.ui.screens.tools.SemoTrainedCasesScreen(
                            onBack = { navController.popBackStack() },
                            synonyms = aiSynonyms,
                            trainedExamples = aiTrainedExamples,
                            trainedThreshold = aiTrainedThreshold,
                            onSynonymsChange = { m -> scope.launch { preferencesRepository.setAiSynonyms(m) } },
                            onTrainedExamplesChange = { list ->
                                scope.launch { preferencesRepository.setAiTrainedExamples(list) }
                                appViewModel.publishTrainedExamples(list)
                            },
                            onTrainedThresholdChange = { v -> scope.launch { preferencesRepository.setAiTrainedThreshold(v) } }
                        )
                    }
                }
                composable(Screen.AdminSemoWeb.route) {
                    if (appViewModel.isAdmin) {
                        val unansweredList by appViewModel.unansweredQuestions.collectAsState()
                        com.salman.herbalencyclopedia.ui.screens.tools.SemoWebKnowledgeScreen(
                            onBack = { navController.popBackStack() },
                            webHerbs = webHerbsState,
                            unanswered = unansweredList,
                            onPromote = { w -> appViewModel.promoteWebHerb(w) { _, _ -> } },
                            onApprove = { id -> appViewModel.approveWebHerb(id) },
                            onDeleteWebHerb = { id -> appViewModel.deleteWebHerb(id) },
                            onDeleteUnanswered = { id -> appViewModel.deleteUnanswered(id) }
                        )
                    }
                }
                composable(Screen.AdminSemoLearning.route) {
                    if (appViewModel.isAdmin) {
                        com.salman.herbalencyclopedia.ui.screens.tools.SemoSelfLearningScreen(
                            onBack = { navController.popBackStack() },
                            autoLearnedExamples = aiAutoLearnedExamples,
                            autoLearnEnabled = aiAutoLearnEnabled,
                            trainedExamples = aiTrainedExamples,
                            onAutoLearnedExamplesChange = { list -> scope.launch { preferencesRepository.setAiAutoLearnedExamples(list) } },
                            onAutoLearnEnabledChange = { v -> scope.launch { preferencesRepository.setAiAutoLearnEnabled(v) } },
                            onDemoteLearnedExample = { question -> appViewModel.demoteSemoLearning(question) },
                            onPromoteToTrained = { example ->
                                val updatedTrained = aiTrainedExamples + example
                                scope.launch { preferencesRepository.setAiTrainedExamples(updatedTrained) }
                                appViewModel.publishTrainedExamples(updatedTrained)
                                scope.launch { preferencesRepository.setAiAutoLearnedExamples(aiAutoLearnedExamples - example) }
                                appViewModel.demoteSemoLearning(example.pattern)
                            }
                        )
                    }
                }
                composable(Screen.AdminEdit.route, arguments=listOf(navArgument("herbId"){type=NavType.StringType})) { e -> if (appViewModel.isAdmin) { val id=e.arguments?.getString("herbId"); val existing=uiState.herbs.firstOrNull {it.id==id}; AdminEditHerbScreen(existing, uiState.categories, {navController.popBackStack()}, {h,cb->if(existing==null) appViewModel.addHerb(h,cb) else appViewModel.updateHerb(h,cb)}) } }

                // ── الخلطات (Blends) ──────────────────────────────────
                composable(Screen.Blends.route) {
                    BlendsScreen(
                        blends = uiState.blends,
                        isAdmin = appViewModel.isAdmin,
                        onBack = { navController.popBackStack() },
                        onBlendClick = { b -> navController.navigate(Screen.BlendDetail.createRoute(b.id)) },
                        onAddNew = { navController.navigate(Screen.AdminEditBlend.createRoute(Screen.AdminEditBlend.NEW)) }
                    )
                }
                composable(Screen.BlendDetail.route, arguments = listOf(navArgument("blendId") { type = NavType.StringType })) { e ->
                    val blend = uiState.blends.firstOrNull { it.id == e.arguments?.getString("blendId") }
                    blend?.let { b ->
                        BlendDetailScreen(
                            blend = b,
                            ingredientHerbs = uiState.herbs.filter { it.id in b.herbIds },
                            isAdmin = appViewModel.isAdmin,
                            onBack = { navController.popBackStack() },
                            onIngredientClick = { h -> navController.navigate(Screen.HerbDetail.createRoute(h.id)) },
                            onEdit = { navController.navigate(Screen.AdminEditBlend.createRoute(b.id)) },
                            onDelete = { appViewModel.deleteBlend(b.id) { success, _ -> if (success) navController.popBackStack() } },
                            onReportIssue = { navController.navigate(Screen.SendFeedback.createRoute("blend", b.id, b.name)) }
                        )
                    }
                }
                composable(Screen.AdminEditBlend.route, arguments = listOf(navArgument("blendId") { type = NavType.StringType })) { e ->
                    if (appViewModel.isAdmin) {
                        val id = e.arguments?.getString("blendId")
                        val existing = uiState.blends.firstOrNull { it.id == id }
                        AdminEditBlendScreen(existing, uiState.herbs, { navController.popBackStack() }, { b, cb -> if (existing == null) appViewModel.addBlend(b, cb) else appViewModel.updateBlend(b, cb) })
                    }
                }

                // ── ملاحظات المستخدمين (Feedback) ─────────────────────
                composable(
                    Screen.SendFeedback.route,
                    arguments = listOf(
                        navArgument("targetType") { type = NavType.StringType },
                        navArgument("targetId") { type = NavType.StringType },
                        navArgument("targetName") { type = NavType.StringType }
                    )
                ) { e ->
                    val targetType = e.arguments?.getString("targetType") ?: "herb"
                    val targetId = e.arguments?.getString("targetId") ?: ""
                    val targetName = e.arguments?.getString("targetName") ?: ""
                    SendFeedbackScreen(
                        targetName = targetName,
                        onBack = { navController.popBackStack() },
                        onSubmit = { senderName, message, cb ->
                            appViewModel.submitFeedback(targetType, targetId, targetName, message, senderName, cb)
                        }
                    )
                }
                composable(Screen.AdminFeedback.route) {
                    if (appViewModel.isAdmin) {
                        LaunchedEffect(Unit) {
                            appViewModel.loadFeedback()
                            appViewModel.loadBlockedUsers()
                        }
                        val feedbackList by appViewModel.feedbackList.collectAsState()
                        val feedbackLoading by appViewModel.feedbackLoading.collectAsState()
                        val feedbackError by appViewModel.feedbackError.collectAsState()
                        val blockedUsers by appViewModel.blockedUsers.collectAsState()
                        AdminFeedbackScreen(
                            feedback = feedbackList,
                            isLoading = feedbackLoading,
                            error = feedbackError,
                            onBack = { navController.popBackStack() },
                            onDelete = { f -> appViewModel.deleteFeedback(f.id) },
                            blockedUsers = blockedUsers,
                            onBlock = { uid, name -> appViewModel.blockUser(uid, name) },
                            onUnblock = { uid -> appViewModel.unblockUser(uid) }
                        )
                    }
                }
            }
            // الشريط العائم السفلي: يُرسم هنا فوق نفس صندوق AmbientBackground/
            // NavHost أعلاه (لا في فتحة bottomBar الخاصة بـScaffold) كي يطفو
            // فوق امتداد حقيقي لخلفية التطبيق قابل للتمويه الفعلي خلفه (انظر
            // GlassBackdrop.kt)، وليقيس ارتفاعه الفعلي عبر onSizeChanged بدل
            // ثابت مفترَض.
            if (floatingBarActive) {
                OneUiFloatingNavBar(
                    items = bottomNavItems,
                    currentRoute = current,
                    onItemClick = onNavItemClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { navBarHeightPx = it.height }
                )
            }
          }
        }
        }
    }
}
