package com.salman.herbalencyclopedia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.salman.herbalencyclopedia.data.repository.PreferencesRepository
import com.salman.herbalencyclopedia.data.update.UpdateNotifier
import com.salman.herbalencyclopedia.ui.AppViewModel
import com.salman.herbalencyclopedia.ui.AppViewModelFactory
import com.salman.herbalencyclopedia.ui.navigation.HerbalNavGraph
import com.salman.herbalencyclopedia.ui.theme.HerbalEncyclopediaTheme
import com.salman.herbalencyclopedia.ui.theme.LocalPerformanceMode
import com.salman.herbalencyclopedia.ui.theme.LocalRefreshRateTier
import com.salman.herbalencyclopedia.ui.theme.PerformanceMode
import com.salman.herbalencyclopedia.ui.theme.applyPreferredRefreshRate
import com.salman.herbalencyclopedia.ui.theme.currentRefreshRateHz
import com.salman.herbalencyclopedia.ui.theme.nearestRefreshRateTier
import com.salman.herbalencyclopedia.ui.theme.rememberEffectivePerformanceMode
import com.salman.herbalencyclopedia.ui.util.AppLanguage
import com.salman.herbalencyclopedia.ui.util.LocalAppLanguage
import com.salman.herbalencyclopedia.ui.util.LocaleManager
import com.salman.herbalencyclopedia.ui.util.tr

class MainActivity : ComponentActivity() {

    // *** إصلاح: الضغط على إشعار "تحديث متوفر" (راجع UpdateNotifier) كان
    // يفتح الشاشة الرئيسية فقط بلا أي انتقال فعلي لشاشة الإعدادات. ***
    // القيمة تُقرأ أولاً من نيّة بدء التشغيل (بدء بارد: التطبيق لم يكن
    // يعمل)، وتُحدَّث لاحقاً من [onNewIntent] (التطبيق كان يعمل بالفعل
    // بالخلفية وضُغط على الإشعار). mutableStateOf عادي (لا remember) لأنها
    // خاصية على الـ Activity نفسها، تعيش خارج أي إعادة تركيب لـ setContent
    // وتُقرأ داخله كحالة تفاعلية عادية — أي تحديث لها من onNewIntent يُعاد
    // رسم الواجهة به فوراً حتى لو Activity نفسه لم يُعَد إنشاؤه.
    private val pendingDeepLink = mutableStateOf<String?>(null)

    @Volatile
    private var prefsReady = false

    // يُستدعى قبل onCreate وقبل أي Compose/ViewModel: يغلّف الـ Context
    // بلغة المستخدم المحفوظة (عربي/إنجليزي) حتى تتبع كل موارد Android
    // (بما فيها اتجاه RTL/LTR الافتراضي وأي مورد @string لاحق) تلك اللغة
    // بدل لغة نظام الجهاز نفسه. راجع PreferencesRepository.getSavedLanguageCode وLocaleManager.
    override fun attachBaseContext(newBase: Context) {
        val languageCode = PreferencesRepository.getSavedLanguageCode(newBase)
        super.attachBaseContext(LocaleManager.wrapContext(newBase, languageCode))
    }

    /**
     * يُستدعى فقط حين يكون التطبيق يعمل بالفعل (بالخلفية أو بالمقدّمة) ويصله
     * Intent جديد — بفضل android:launchMode="singleTask" بالمانيفست، لا
     * يُنشئ أندرويد نسخة ثانية من MainActivity فوق الأولى عند الضغط على
     * الإشعار بينما التطبيق يعمل، بل يعيد استخدام نفس النسخة ويمرّ عبر هنا.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent.getStringExtra(UpdateNotifier.EXTRA_OPEN_SCREEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        systemSplash.setKeepOnScreenCondition { !prefsReady }
        super.onCreate(savedInstanceState)
        // ═══ طلب المستخدم: منع لقطة الشاشة والتسجيل — نسخة public فقط ═══
        // سابقاً كان FLAG_SECURE يُفعَّل فقط أثناء شاشات الإدارة (المسارات
        // التي تبدأ بـ"admin" — راجع الإصدار السابق من هذا التعليق وHerbalNavGraph)
        // تفادياً لأثر جانبي معروف: أندرويد يمنع أخذ صورة مصغّرة حقيقية لأي
        // نافذة تحمل هذا العلَم في مبدّل التطبيقات (Recents)، فيستبدلها ببطاقة
        // سوداء، ويعيد إرفاق سطح الرسم من الصفر عند العودة — ما يسبب وميضاً
        // أسود لحظياً عند كل "تبديل ثم عودة". ثم أُزيل هذا التنقّل الجزئي
        // حسب الشاشة بطلب صريح من المستخدم بتعميم المنع على التطبيق كاملاً.
        //
        // بطلب لاحق صريح أيضاً: نسخة full (نسخة المطوّر/dev harb) يجب أن
        // تسمح بلقطة الشاشة (لأخذ صور للتوثيق، تصوير شاشات الإدارة، إلخ)،
        // بينما تبقى نسخة public محمية بالكامل كما كانت. فالعلَم الآن
        // يُضبط فقط عندما تكون النكهة الحالية ليست "full"، مرة واحدة طوال
        // حياة النشاط، ولم يعد يُزال أو يُعاد ضبطه من HerbalNavGraph حسب
        // المسار. الأثر الجانبي أعلاه (فقدان الصورة المصغّرة في Recents)
        // يبقى قائماً على كل شاشات نسخة public تحديداً — وهذا مقصود؛
        // نسخة full لا تتأثر به إطلاقاً بما أن العلَم لا يُضبط لها أصلاً.
        if (BuildConfig.FLAVOR != "full") {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // بدء بارد: نيّة إطلاق التطبيق نفسها قد تحمل نفس علامة الانتقال
        // (لو المستخدم ضغط على الإشعار والتطبيق مغلق تماماً) — راجع
        // HerbalNavGraph لكيفية استهلاكها فعلياً بعد جهوزية التنقّل.
        pendingDeepLink.value = intent?.getStringExtra(UpdateNotifier.EXTRA_OPEN_SCREEN)

        val container = (application as HerbalApp).container

        lifecycleScope.launch {
            withTimeoutOrNull(1200L) { runCatching { container.preferencesRepository.darkMode.first() } }
            prefsReady = true
        }

        setContent {
            val appViewModel: AppViewModel = viewModel(factory = AppViewModelFactory(container))
            val darkModePref by container.preferencesRepository.darkMode.collectAsState(initial = null)
            val dynamicColorPref by container.preferencesRepository.dynamicColor.collectAsState(initial = false)
            val fontScale by container.preferencesRepository.fontScale.collectAsState(initial = 0)
            val themePalette by container.preferencesRepository.themePalette.collectAsState(
                initial = com.salman.herbalencyclopedia.ui.theme.ThemePalette.LEAF
            )
            val performanceMode by container.preferencesRepository.performanceMode.collectAsState(
                initial = PerformanceMode.HIGH_QUALITY
            )
            val appLanguage by container.preferencesRepository.appLanguage.collectAsState(
                initial = AppLanguage.ARABIC
            )
            val useDark = darkModePref ?: isSystemInDarkTheme()
            val deepLink by pendingDeepLink

            // النسخة "الفعلية" من وضع الأداء: تطابق اختيار المستخدم عادةً،
            // لكنها تتراجع تلقائياً ومؤقتاً لوضع "اقتصادي" إن فعّل النظام
            // توفير الطاقة أو دخل الجهاز حالة تحكّم حراري — راجع
            // RuntimePerformanceSignals.kt للتفاصيل. اختيار المستخدم
            // الخام (performanceMode) يبقى كما هو في الإعدادات ولا يتأثر.
            val effectivePerformanceMode = rememberEffectivePerformanceMode(performanceMode)

            // معدل تحديث الشاشة الفعلي الحالي (مُقرَّب لأقرب طبقة من
            // 20/40/60/90/120 — راجع RefreshRate.kt)، يُعاد حسابه في كل
            // مرة يتغيّر فيها وضع الأداء *الفعلي* (تبديله من الإعدادات، أو
            // تراجعه التلقائي بسبب توفير الطاقة/الحرارة، يُعيد تشغيل
            // SideEffect التالي، الذي يطلب من النظام وضع عرض جديد أولاً ثم
            // يقرأ المعدل الفعلي الناتج) — فتخفيض معدل التحديث نفسه يصبح
            // جزءاً من نفس التراجع التلقائي، لا الحركات داخل Compose فقط.
            var refreshRateTier by remember { mutableIntStateOf(60) }
            SideEffect {
                applyPreferredRefreshRate(this@MainActivity, effectivePerformanceMode)
                refreshRateTier = nearestRefreshRateTier(currentRefreshRateHz())
            }

            SideEffect {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }

            // اتجاه التخطيط يتبع الآن لغة التطبيق المختارة من الإعدادات
            // (عربي = RTL، إنجليزي = LTR) بدل فرض RTL دائماً كما كان
            // سابقاً حين كان التطبيق عربياً فقط بلا أي لغة أخرى. راجع
            // AppLanguage وLocalAppLanguage وSettingsScreen لآلية التبديل
            // الكاملة (بما فيها إعادة إنشاء النشاط عبر attachBaseContext
            // أعلاه لتطبيق اللغة على موارد Android أيضاً وليس فقط اتجاه Compose).
            CompositionLocalProvider(
                LocalAppLanguage provides appLanguage,
                LocalLayoutDirection provides if (appLanguage == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                HerbalEncyclopediaTheme(
                    darkTheme = useDark,
                    dynamicColor = dynamicColorPref,
                    palette = themePalette,
                    fontScale = fontScale
                ) {
                    // بدون هذا، اختيار "اقتصادي" من الإعدادات كان يُحفظ في
                    // DataStore فقط دون أي أثر فعلي: LocalPerformanceMode لم
                    // يكن يُزوَّد (provide) بالقيمة الحقيقية في أي مكان بالتطبيق،
                    // فكانت كل مكوّنات الزجاج السائل (LiquidGlassSurface وغيرها)
                    // تقرأ دائماً القيمة الافتراضية HIGH_QUALITY بغض النظر عن
                    // اختيار المستخدم — هذا هو إصلاح "الزر الاقتصادي".
                    CompositionLocalProvider(
                        LocalPerformanceMode provides effectivePerformanceMode,
                        LocalRefreshRateTier provides refreshRateTier
                    ) {
                        // ═══ وضع الصيانة — ميزة إدمن جديدة ═══
                        // maintenanceEnabled لا يُضبط بـtrue إطلاقاً إلا لنسخة
                        // public (راجع AppViewModel.init وMaintenanceRepository)،
                        // فنسخة full (الأدمن) تتجاوز هذا الفحص دوماً وتصل للتطبيق
                        // كاملاً بصرف النظر عن قيمة العلَم، لتستطيع تعطيل
                        // الصيانة لاحقاً من نفس الجهاز.
                        if (appViewModel.maintenanceEnabled) {
                            MaintenanceScreen(message = appViewModel.maintenanceMessage)
                        } else {
                            HerbalNavGraph(
                                appViewModel = appViewModel,
                                preferencesRepository = container.preferencesRepository,
                                pendingDeepLink = deepLink,
                                onDeepLinkHandled = { pendingDeepLink.value = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * شاشة "وضع الصيانة" — تحجب كامل نسخة public عند تفعيلها من لوحة الإدمن
 * (راجع AdminToolsScreen وMaintenanceRepository)، بدل توزيع تحديث كامل فقط
 * لإيقاف التطبيق مؤقتاً أثناء صيانة السيرفر أو ترحيل بيانات كبير.
 */
@Composable
private fun MaintenanceScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Text(
                tr("التطبيق قيد الصيانة حالياً"),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                message.ifBlank { tr("نعمل على تحسين التطبيق حالياً، عودوا خلال وقت قصير.") },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
