package com.salman.herbalencyclopedia.ui.screens.splash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import com.salman.herbalencyclopedia.ui.theme.AppMotion
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import com.salman.herbalencyclopedia.ui.SyncProgress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salman.herbalencyclopedia.ui.util.tr
import com.salman.herbalencyclopedia.ui.theme.rememberSmoothMotionAllowed
import kotlinx.coroutines.delay

/** الحد الأدنى لعرض المرحلة الثانية (النص + النقاط) قبل التحقق من جهوزية البيانات. */
private const val SPLASH_MIN_STAGE2_MS = 900L

/**
 * حد أقصى إضافي (بعد الحد الأدنى) لانتظار بيانات الموسوعة الفعلية —
 * تحوّطاً من تعليق شاشة البداية للأبد لو تأخرت الشبكة أو انعدم الاتصال؛
 * بعد هذه المهلة يُكمَل المستخدم إلى الشاشة الرئيسية بغض النظر، والتي
 * تملك أصلاً مؤشر تحميل/خطأ خاصاً بها.
 */
private const val SPLASH_MAX_EXTRA_WAIT_MS = 3500L
private const val SPLASH_AUTO_RETRY_MS = 6000L
// سقف صلب لانتظار أول تشغيل: بعده ننتقل للرئيسية (المزامنة تكمل بالخلفية وتظهر
// البيانات تلقائياً) بدل التعليق للأبد عند 100% أو "جارٍ الاتصال".
private const val SPLASH_FIRST_RUN_MAX_WAIT_MS = 120_000L
private const val SPLASH_FIRST_RUN_STALL_MS = 25_000L
private const val SPLASH_COMPLETE_HOLD_MS = 500L
private const val SPLASH_FIRST_RUN_MIN_SHOW_MS = 9000L

/**
 * شاشة البداية المخصصة داخل Compose (تظهر بعد شاشة النظام
 * Theme.HerbalEncyclopedia.Splash القصيرة، والتي أصبحت الآن متحركة فعلياً
 * أيضاً — راجع avd_splash_icon.xml وthemes.xml). أُعيد تصميمها لتعطي طابعاً
 * نباتياً هادئاً وفخماً: خلفية متدرّجة، وميض عضوي خلف الأيقونة، أوراق
 * زخرفية خافتة في الزوايا، وظهور متتابع للعناصر بدل ظهورها دفعة واحدة.
 *
 * ═══ إصلاح: الألوان لم تكن "حسب ثيم التطبيق" فعلياً ═══
 * كانت كل ألوان هذه الشاشة (الخلفية، توهّج الشعار، لون الأيقونة) أخضراً
 * ثابتاً مكتوباً يدوياً بصرف النظر تماماً عن لوحة الألوان التي يختارها
 * المستخدم فعلياً من الإعدادات (9 لوحات مختلفة في [ThemePalette] — أزرق،
 * بنفسجي، برتقالي...) أو عن تفعيله "الألوان الديناميكية" (Material You)،
 * رغم أن هذه الشاشة تقع أصلاً *داخل* HerbalEncyclopediaTheme (راجع
 * HerbalNavGraph/MainActivity) وتملك وصولاً كاملاً لـMaterialTheme.colorScheme
 * الصحيح فعلياً — أي أن أول شيء يراه المستخدم عند كل فتح للتطبيق كان
 * يخالف هويته اللونية التي اختارها بنفسه. الآن كل الألوان مُشتقة من
 * [MaterialTheme.colorScheme] (primary/tertiary) فتتبدّل تلقائياً مع أي
 * لوحة، مع الوضع الليلي/النهاري، ومع الألوان الديناميكية أيضاً. التدرّج
 * يُعتّم تدريجياً نحو الأسود (بدل التلاشي نحو سطح فاتح كما كان سابقاً عبر
 * colorScheme.surface، الذي يصبح شبه أبيض في الوضع النهاري ويُضعف تباين
 * النص الأبيض عليه) لضمان تباين كافٍ للنص في كل لوحة ووضع معاً دون استثناء.
 *
 * ═══ إضافة: حركة مستمرة بدل ثبات بعد الظهور ═══
 * سابقاً، بعد انتهاء حركة الدخول (تكبّر + تلاشي)، كانت شارة الشعار والأوراق
 * الزخرفية تجمد تماماً بلا أي حركة إضافية طوال بقية عرض الشاشة (فقط توهّج
 * الشفافية والنقاط الثلاث كانتا متحركتين). الآن تطفو الشارة بهدوء لأعلى
 * وأسفل ([badgeBob]) وتتمايل الأوراق الزخرفية بلطف ([leafSway])، بنفس مبدأ
 * إيقاف الحركات اللانهائية في الوضع الاقتصادي المتّبع أصلاً في بقية الشاشة.
 *
 * ═══ إصلاح: الحركات اللانهائية هنا لا تراعي معدّل التحديث الفعلي ═══
 * التوهّج (glowAlpha) وطفوّ الشارة (badgeBob) وتمايل الأوراق (leafSway)
 * والنقاط الثلاث (LoadingDots) كانت كلها تُشغَّل بشرط واحد فقط: اختيار
 * المستخدم "أداء عالٍ" (highQuality)، بخلاف بقية حركات التطبيق المتصلة
 * (staggeredEntrance/entranceFade في Animations.kt، فقاعة "سيمو يكتب…"،
 * انتقالات التنقّل بين الشاشات) التي تحترم أيضاً [rememberSmoothMotionAllowed]
 * — أي تتوقف تلقائياً حين يكون معدّل التحديث الفعلي الحالي أقل من 60Hz، سواء
 * لأن الجهاز ضعيف أصلاً أو لأن توفير الطاقة/التحكّم الحراري أرجعا الوضع
 * الفعلي لاقتصادي مؤقتاً. وبما أن شاشة البداية هي أول ما يراه كل مستخدم عند
 * كل فتح للتطبيق، كانت هذا الاستثناء يعني أربع حركات لانهائية بلا فائدة
 * بصرية تُذكر تحديداً على الأجهزة التي تحتاج أقل حِمل ممكن. الآن تستخدم نفس
 * الإشارة الموحّدة [smoothMotionAllowed] بدل [highQuality] وحدها لهذا القرار
 * تحديداً (يبقى [highQuality] Boolean قديم لكن لم يعد يُستخدم في هذا الملف).
 * كما تُوحَّد كل حركات fadeIn/scaleIn/slideIn هنا على منحنى [AppMotion.Smooth]
 * بدل ترك بعضها بمنحنى Compose الافتراضي (FastOutSlowInEasing) الذي كان
 * يخلق إحساساً بعدم التناسق داخل نفس تتابع الظهور.
 *
 * [isDataReady] تحسين لتجربة أول تشغيل بعد التثبيت تحديداً: سابقاً كانت
 * الشاشة تنتقل دوماً بعد مهلة ثابتة (١٫٧٥ ثانية) بغض النظر عن وصول بيانات
 * الموسوعة من Firestore أم لا، فكان أول تثبيت (بلا أي كاش محلي بعد) يهبط
 * غالباً على شاشة رئيسية فارغة يتبعها مؤشر تحميل منفصل هناك — انتقال أقل
 * سلاسة من الاستمرار بحركة شاشة البداية نفسها لبضع لحظات إضافية فقط. الآن
 * تنتظر الشاشة (بعد حدها الأدنى الثابت [SPLASH_MIN_STAGE2_MS] الذي يضمن
 * ظهور الحركة كاملة دوماً) حتى [isDataReady] تصبح `true`، بحد أقصى
 * [SPLASH_MAX_EXTRA_WAIT_MS] كي لا تتعلّق الشاشة بلا نهاية عند انعدام
 * الاتصال. القيمة الافتراضية `true` تُبقي الاستخدام القديم (بلا تمرير هذه
 * الوسيطة) يعمل تماماً كما كان.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    isDataReady: Boolean = true,
    hasData: Boolean = true,
    syncProgress: SyncProgress = SyncProgress(),
    onRetry: () -> Unit = {}
) {
    var stage by remember { mutableStateOf(0) }
    // يصبح true فقط إذا انتهى الحد الأدنى الثابت لعرض الحركة
    // ([SPLASH_MIN_STAGE2_MS]) والبيانات لا تزال غير جاهزة - أي أن الشاشة
    // تنتظر فعلياً استجابة الشبكة الآن (أبرز ما يحدث في أول تشغيل بعد
    // التثبيت مباشرة، بلا أي كاش محلي بعد)، لا مجرّد المدة الجمالية الثابتة
    // لحركة الدخول. يُستخدم أدناه لعرض مؤشر ونص "جاري تحميل البيانات"
    // صريحين بدل النقاط الثلاث العامة، فيعرف المستخدم فعلياً أن التطبيق
    // يجلب بيانات الموسوعة لا أنه معلّق بلا سبب.
    var waitingForData by remember { mutableStateOf(false) }
    // يجمع اختيار المستخدم (وضع الأداء) مع معدّل التحديث الفعلي الحالي —
    // راجع التوثيق أعلاه. هذا ما يقرر تشغيل الحركات اللانهائية الأربع أدناه
    // بدل [PerformanceMode.isHighQuality] وحدها.
    val smoothMotionAllowed = rememberSmoothMotionAllowed()
    val dataReadyState = androidx.compose.runtime.rememberUpdatedState(isDataReady && (hasData || !syncProgress.firstRun) && !syncProgress.failed)
    val progressState = androidx.compose.runtime.rememberUpdatedState(syncProgress)
    val retryState = androidx.compose.runtime.rememberUpdatedState(onRetry)

    val iconScale by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0.6f,
        animationSpec = tween(700, easing = AppMotion.Smooth),
        label = "logoScale"
    )

    // التوهّج العضوي المتنفس حركة لانهائية — تُستبعد حين لا يُسمح بحركة
    // متصلة سلسة (وضع اقتصادي، أو معدّل تحديث فعلي أقل من 60Hz) فتبقى شاشة
    // البداية خفيفة تماماً على الأجهزة الضعيفة.
    val glowAlpha = if (smoothMotionAllowed) {
        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(tween(1400, easing = AppMotion.Smooth), RepeatMode.Reverse),
            label = "glowAlpha"
        )
        animated
        // ملاحظة: نُبقي القيمة كـ State مقروءة أدناه بنفس الاسم.
    } else 0.4f

    // طفوّ خفيف مستمر للشارة (شعار + الزجاج المحيط به معاً) — يمنحها إحساس
    // "حيّة" بدل التجمّد التام بعد حركة الدخول. سعة الحركة صغيرة عمداً (±5dp)
    // كي تبقى أنيقة وهادئة لا مشتّتة. تُستبعد كلياً حين لا يُسمح بحركة متصلة
    // سلسة (نفس شرط [glowAlpha] أعلاه).
    val badgeBob = if (smoothMotionAllowed) {
        val bobTransition = rememberInfiniteTransition(label = "badgeBob")
        val animated by bobTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(1900, easing = AppMotion.Smooth), RepeatMode.Reverse),
            label = "badgeBobOffset"
        )
        animated
    } else 0f

    // تمايل بسيط جداً لأوراق الزخرفة في الزوايا (±3 درجات حول زاويتها
    // الأصلية) — نفس شرط التوقف أعلاه.
    val leafSway = if (smoothMotionAllowed) {
        val swayTransition = rememberInfiniteTransition(label = "leafSway")
        val animated by swayTransition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(5000, easing = AppMotion.Smooth), RepeatMode.Reverse),
            label = "leafSwayAngle"
        )
        animated
    } else 0f

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        var sawFirstRun = false
        stage = 1
        delay(250)
        stage = 2
        delay(SPLASH_MIN_STAGE2_MS)
        if (!dataReadyState.value) {
            waitingForData = true
        }
        val deadline = System.currentTimeMillis() + SPLASH_MAX_EXTRA_WAIT_MS
        val firstRunDeadline = System.currentTimeMillis() + SPLASH_FIRST_RUN_MAX_WAIT_MS
        var failedSince = 0L
        var lastFraction = -1f
        var lastProgressAt = System.currentTimeMillis()
        while (!dataReadyState.value) {
            val progress = progressState.value
            if (progress.firstRun) sawFirstRun = true
            val nowMs = System.currentTimeMillis()
            if (progress.fraction != lastFraction) {
                lastFraction = progress.fraction
                lastProgressAt = nowMs
            }
            if (progress.firstRun && nowMs - lastProgressAt >= SPLASH_FIRST_RUN_STALL_MS) break
            if (!progress.firstRun && System.currentTimeMillis() >= deadline) break
            if (progress.firstRun && System.currentTimeMillis() >= firstRunDeadline) break
            if (progress.failed) {
                if (failedSince == 0L) failedSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - failedSince >= SPLASH_AUTO_RETRY_MS) {
                    failedSince = 0L
                    retryState.value()
                }
            } else {
                failedSince = 0L
            }
            delay(100)
        }
        if (progressState.value.firstRun || sawFirstRun) {
            delay(SPLASH_COMPLETE_HOLD_MS)
            val remaining = SPLASH_FIRST_RUN_MIN_SHOW_MS - (System.currentTimeMillis() - startedAt)
            if (remaining > 0L) delay(remaining)
        }
        onFinished()
    }

    // ألوان الشاشة كاملة مُشتقّة الآن من ثيم التطبيق الفعلي (راجع التوثيق
    // أعلاه) بدل أخضر ثابت — فتطابق أي لوحة يختارها المستخدم أو الألوان
    // الديناميكية تلقائياً، في الوضعين الليلي والنهاري معاً.
    //
    // إصلاح: كان heroTop = scheme.primary مباشرة بلا أي مزج نحو الأسود، فإذا
    // كان primary فاتحاً (كثير الحدوث تحديداً مع الألوان الديناميكية على
    // خلفية شاشة رئيسية فاتحة، أو مع بعض اللوحات في الوضع النهاري) كانت
    // الشاشة كلها تبدو فاتحة بصرف النظر عن هوية أغمق — عكس طابع "شاشة
    // بداية داكنة" الذي يفترضه التصميم (نص أبيض، أوراق شبه شفافة بيضاء).
    // الآن الأعلى نفسه يُمزج نحو الأسود بنسبة ثابتة، فتبقى الشاشة داكنة
    // دوماً (أسود أساساً) مع لون التطبيق كصبغة واضحة فوقه، بدل الاعتماد على
    // كون primary غامقاً بالصدفة.
    val scheme = MaterialTheme.colorScheme
    val heroTop = lerp(scheme.primary, Color.Black, 0.35f)
    val heroMid = lerp(lerp(scheme.primary, scheme.tertiary, 0.5f), Color.Black, 0.55f)
    val heroBottom = Color.Black
    val compact = syncProgress.firstRun

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(heroTop, heroMid, heroBottom))),
        contentAlignment = Alignment.Center
    ) {
        // زخرفة أوراق خافتة في الزوايا لإعطاء عمق نباتي بلا إلهاء عن المحتوى.
        Icon(
            imageVector = Icons.Filled.Spa,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-28).dp, y = 36.dp)
                .size(150.dp)
                .rotate(-18f + leafSway)
        )
        Icon(
            imageVector = Icons.Filled.Spa,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.07f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = (-24).dp)
                .size(190.dp)
                .rotate(154f - leafSway)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer { translationY = badgeBob.dp.toPx() }
            ) {
                // وميض عضوي متنفس خلف الشعار، بلون ثانوي/ثالث من الثيم
                // (inversePrimary مصمَّم أصلاً ليبرز فوق أسطح بلون Primary).
                Box(
                    modifier = Modifier
                        .size(if (compact) 96.dp else 150.dp)
                        .scale(iconScale)
                        .background(scheme.inversePrimary.copy(alpha = glowAlpha * 0.35f), CircleShape)
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = stage >= 1,
                    enter = fadeIn(tween(550, easing = AppMotion.Smooth)) + scaleIn(tween(700, easing = AppMotion.Smooth), initialScale = 0.6f)
                ) {
                    com.salman.herbalencyclopedia.ui.components.LiquidGlassSurface(
                        shape = CircleShape,
                        modifier = Modifier.size(if (compact) 72.dp else 108.dp),
                        tint = Color.White,
                        glowColor = scheme.tertiary,
                        borderAlpha = 0.4f
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Spa,
                                contentDescription = null,
                                modifier = Modifier.size(if (compact) 36.dp else 54.dp),
                                tint = scheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 12.dp else 30.dp))

            AnimatedVisibility(
                visible = stage >= 2,
                enter = fadeIn(tween(600, easing = AppMotion.Smooth)) + slideInVertically(tween(600, easing = AppMotion.Smooth)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tr("موسوعة الأعشاب"),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!compact) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = tr("دليلك الطبيعي للمعرفة بالأعشاب"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 14.dp else 34.dp))

            if (compact) {
                AnimatedVisibility(
                    visible = stage >= 2,
                    enter = fadeIn(tween(600, delayMillis = 150, easing = AppMotion.Smooth)) +
                        slideInVertically(tween(600, delayMillis = 150, easing = AppMotion.Smooth)) { it / 5 }
                ) {
                    SetupShowcase(
                        smoothMotionAllowed = smoothMotionAllowed,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            AnimatedVisibility(
                visible = stage >= 2,
                enter = fadeIn(tween(600, delayMillis = 200, easing = AppMotion.Smooth))
            ) {
                // أثناء الانتظار الفعلي لبيانات الشبكة (waitingForData)
                // نستبدل النقاط الثلاث العامة بدائرة تحميل + نص صريح "جاري
                // تحميل البيانات"، حتى لا يبدو التطبيق معلّقاً بلا سبب واضح
                // في أول تشغيل بعد التثبيت تحديداً (حيث لا كاش محلي بعد
                // والانتظار الفعلي للشبكة أطول من بقية الحالات). بمجرد وصول
                // البيانات (أو التوقف عن الانتظار) تعود الشاشة لتنتقل
                // للرئيسية عبر onFinished في LaunchedEffect أعلاه كالمعتاد.
                if (syncProgress.firstRun) {
                    FirstRunProgress(
                        progress = syncProgress,
                        smoothMotionAllowed = smoothMotionAllowed,
                        onRetry = onRetry
                    )
                } else AnimatedContent(
                    targetState = waitingForData,
                    transitionSpec = { fadeIn(tween(300, easing = AppMotion.Smooth)) togetherWith fadeOut(tween(150, easing = AppMotion.Smooth)) },
                    label = "splashLoadingIndicator"
                ) { waiting ->
                    if (waiting) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = tr("جاري تحميل البيانات..."),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    } else {
                        LoadingDots(smoothMotionAllowed = smoothMotionAllowed)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = stage >= 2,
            enter = fadeIn(tween(700, delayMillis = 250, easing = AppMotion.Smooth)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        ) {
            Text(
                text = "HERBAL ENCYCLOPEDIA",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.2.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

/**
 * نبضات النقاط الثلاث تتوقف الآن تحت نفس شرط بقية الحركات اللانهائية في
 * هذا الملف: [smoothMotionAllowed] (وضع الأداء + معدّل التحديث الفعلي معاً)،
 * لا اختيار وضع الأداء وحده. في الحالتين تظهر النقاط بسطوع ثابت متدرّج بدل
 * النبض المتحرك — نفس الشكل تقريباً بلا أي تكلفة حركة إضافية.
 */
@Composable
private fun LoadingDots(smoothMotionAllowed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (smoothMotionAllowed) {
            val transition = rememberInfiniteTransition(label = "dots")
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 160, easing = AppMotion.Smooth),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotAlpha$index"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White.copy(alpha = alpha), CircleShape)
                )
            }
        } else {
            val staticAlphas = listOf(0.45f, 0.7f, 1f)
            staticAlphas.forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White.copy(alpha = alpha), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun FirstRunProgress(
    progress: SyncProgress,
    smoothMotionAllowed: Boolean,
    onRetry: () -> Unit
) {
    val animated by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        animationSpec = if (smoothMotionAllowed) tween(650, easing = AppMotion.Smooth) else tween(0),
        label = "syncFraction"
    )
    val shimmer = if (smoothMotionAllowed) {
        val transition = rememberInfiniteTransition(label = "syncShimmer")
        val value by transition.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(1300, easing = AppMotion.Smooth), RepeatMode.Restart),
            label = "syncShimmerOffset"
        )
        value
    } else -1f
    val status = when {
        progress.failed -> tr("تعذّر تحميل البيانات")
        animated < 0.15f -> tr("جاري الاتصال بالخادم...")
        animated < 0.8f -> tr("جاري تحميل الأعشاب والتصنيفات...")
        animated < 0.98f -> tr("جاري حفظ البيانات...")
        else -> tr("اكتمل التحميل")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(260.dp)) {
        Text(
            text = "${(animated * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            val barWidth = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(barWidth * animated)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (progress.failed) 0.45f else 0.95f))
            )
            if (!progress.failed && animated > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(barWidth * 0.28f)
                        .offset(x = barWidth * shimmer)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)
                            )
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
        if (progress.failed) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr("تحقق من الإنترنت، وسنعيد المحاولة تلقائيًا"),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(tr("إعادة المحاولة"))
            }
        }
    }
}
