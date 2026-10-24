package com.salman.herbalencyclopedia.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import com.salman.herbalencyclopedia.data.model.AppUpdateInfo
import com.salman.herbalencyclopedia.data.update.UpdateDownloadState
import com.salman.herbalencyclopedia.ui.UpdateCheckState
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.LocalBottomBarInset
import com.salman.herbalencyclopedia.ui.theme.AppMotion
import com.salman.herbalencyclopedia.ui.theme.PerformanceMode
import com.salman.herbalencyclopedia.ui.theme.ThemePalette
import com.salman.herbalencyclopedia.ui.util.AppLanguage
import com.salman.herbalencyclopedia.ui.util.tr
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.salman.herbalencyclopedia.BuildConfig

// خُمس مراحل بدل ثلاث سابقاً (انظر FontScaleFactors في Type.kt)، بحد
// أقصى أوضح لمن يحتاج تكبيراً حقيقياً بدل الاكتفاء بفارق طفيف.
private val fontScaleLabels = listOf("عادي", "متوسط", "كبير", "أكبر", "الأكبر")
private val fontScaleSizes = listOf(14.sp, 16.sp, 18.sp, 20.sp, 23.sp)

/** طابع داخلي (غير مترجَم عمداً — تقني/محايد) لآخر جولة تحسينات هندسة واجهة
 *  ملحوظة: يظهر خافتاً جداً تحت رقم إصدار التطبيق في شاشة الإعدادات. */
private const val UI_ENGINEERING_BUILD_TAG = "UI engineering pass • 2026-09"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isLoggedIn: Boolean,
    isAdmin: Boolean,
    darkMode: Boolean?,
    dynamicColor: Boolean,
    fontScale: Int,
    themePalette: com.salman.herbalencyclopedia.ui.theme.ThemePalette,
    performanceMode: PerformanceMode,
    appLanguage: AppLanguage,
    updateState: UpdateCheckState,
    downloadState: UpdateDownloadState,
    onBack: () -> Unit,
    onDarkModeChange: (Boolean?) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onFontScaleChange: (Int) -> Unit,
    onThemePaletteChange: (com.salman.herbalencyclopedia.ui.theme.ThemePalette) -> Unit,
    onPerformanceModeChange: (PerformanceMode) -> Unit,
    onAppLanguageChange: suspend (AppLanguage) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSupportClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onAdminToolsClick: () -> Unit,
    onAdminFeedbackClick: () -> Unit,
    onCheckForUpdate: (android.content.Context) -> Unit,
    onDownloadUpdate: (android.content.Context, AppUpdateInfo) -> Unit,
    onInstallUpdate: (android.content.Context) -> Unit,
    onCancelDownload: (android.content.Context) -> Unit = {},
    herbsForEval: List<com.salman.herbalencyclopedia.data.model.Herb> = emptyList()
) {
    val context = LocalContext.current
    val currentVersionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }
    // إصلاح مهم: كانت onAppLanguageChange تُستدعى هنا كدالة "أطلق ولا تنتظر"
    // (تبدأ كتابة DataStore في الخلفية ثم تعود فوراً)، بينما كان recreate()
    // يُستدعى مباشرة بعدها بلا انتظار — أي أن إعادة إنشاء النشاط (وبالتالي
    // قراءة اللغة المحفوظة في attachBaseContext) كانت غالباً تسبق اكتمال
    // الكتابة الفعلية على القرص. هذا بالضبط ما كان يسبب الحاجة للضغط عدة
    // مرات (أحياناً تكتمل الكتابة قبل إعادة الإنشاء بالصدفة، وأحياناً لا)،
    // وأحياناً "تعليق" ملحوظ للواجهة أثناء إعادة الإنشاء والترجمة معاً.
    // الحل: onAppLanguageChange أصبحت الآن دالة suspend حقيقية (راجع
    // HerbalNavGraph)، فننتظرها بالكامل هنا (عبر rememberCoroutineScope)
    // قبل استدعاء recreate()، فيُضمَن أن اللغة محفوظة فعلياً قبل أي إعادة إنشاء.
    val scope = rememberCoroutineScope()
    var isChangingLanguage by remember { mutableStateOf(false) }
    val handleLanguageChange: (AppLanguage) -> Unit = { language ->
        if (!isChangingLanguage) {
            isChangingLanguage = true
            scope.launch {
                onAppLanguageChange(language)
                (context as? android.app.Activity)?.recreate()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (updateState == UpdateCheckState.Idle) onCheckForUpdate(context)
    }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        // انظر التعليق بنفس المكان في HomeScreen: هذه شاشة جذر أيضاً، وترك
        // الحافة السفلية الافتراضية هنا يُضاعف الفراغ فوق الشريط العائم.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            GlassTopBar(
                title = { Text(tr("الإعدادات")) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        }
    ) { padding ->
        com.salman.herbalencyclopedia.ui.util.ResponsiveScreenContent(
            windowInfo = com.salman.herbalencyclopedia.ui.util.rememberWindowSizeInfo(),
            modifier = Modifier.padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, top = 16.dp, end = 16.dp,
                bottom = 16.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsSection(title = tr("المظهر")) {
                    ThemeModeSelector(darkMode = darkMode, onDarkModeChange = onDarkModeChange)
                    // زر "ألوان ديناميكية" (Material You) كان يظهر دوماً حتى على
                    // أجهزة أقدم من أندرويد 12 (API 31) لا تدعم هذه الميزة على
                    // مستوى النظام إطلاقاً — Theme.kt نفسه يتحقق من هذا الشرط
                    // قبل تطبيق الألوان فعلياً (راجع HerbalEncyclopediaTheme)،
                    // فتفعيل المستخدم للمفتاح على تلك الأجهزة لا يُغيّر شيئاً
                    // مرئياً إطلاقاً — مفتاح يبدو معطوباً/بلا أثر بدل أن يكون
                    // مخفياً بذكاء عن الأجهزة التي لا تعنيها الميزة أصلاً.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsDivider()
                        SwitchRow(
                            icon = Icons.Filled.Palette,
                            iconTint = Color(0xFF7C4DFF),
                            title = tr("ألوان ديناميكية"),
                            subtitle = tr("استخدام ألوان الخلفية (Material You)"),
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                    SettingsDivider()
                    // على الأجهزة غير الداعمة (أقل من أندرويد 12)، المفتاح
                    // مخفي أعلاه فلا يستطيع المستخدم أصلاً تغيير dynamicColor
                    // من الواجهة — لكن لو وصلت قيمة true محفوظة من مصدر آخر
                    // (نسخة احتياطية من جهاز يدعمها مثلاً)، هذا الشرط يمنع أن
                    // تبقى لوحة الألوان اليدوية معطّلة بلا أي طريقة لإعادة
                    // تفعيلها على هذا الجهاز تحديداً.
                    val dynamicColorEffective = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    PaletteRow(
                        enabled = !dynamicColorEffective,
                        selected = themePalette,
                        onSelect = onThemePaletteChange
                    )
                    SettingsDivider()
                    FontScaleRow(fontScale = fontScale, onFontScaleChange = onFontScaleChange)
                }
            }

            item {
                SettingsSection(title = tr("اللغة")) {
                    LanguageSelector(selected = appLanguage, isChanging = isChangingLanguage, onSelect = handleLanguageChange)
                }
            }

            item {
                SettingsSection(title = tr("الأداء")) {
                    PerformanceModeSelector(
                        selected = performanceMode,
                        onSelect = onPerformanceModeChange
                    )
                }
            }

            item {
                LocalAiSettingsSection(performanceMode = performanceMode, isAdmin = isAdmin, herbs = herbsForEval)
            }

            item {
                SettingsSection(title = tr("التحديثات")) {
                    UpdateRow(
                        currentVersionName = currentVersionName,
                        updateState = updateState,
                        downloadState = downloadState,
                        onCheckForUpdate = { onCheckForUpdate(context) },
                        onDownloadUpdate = { info -> onDownloadUpdate(context, info) },
                        onInstallUpdate = { onInstallUpdate(context) },
                        onCancelDownload = { onCancelDownload(context) }
                    )
                }
            }

            item {
                SettingsSection(title = tr("الحساب")) {
                    ActionRow(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        iconTint = Color(0xFF2E7D32),
                        title = tr("المساعدة"),
                        subtitle = tr("الأسئلة الشائعة وطريقة الاستخدام"),
                        onClick = onHelpClick
                    )
                    SettingsDivider()
                    ActionRow(
                        icon = Icons.Filled.SupportAgent,
                        iconTint = Color(0xFF25D366),
                        title = tr("الدعم الفني"),
                        subtitle = tr("تواصل مباشر معنا عبر واتساب"),
                        onClick = onSupportClick
                    )
                    SettingsDivider()
                    ActionRow(
                        icon = Icons.Filled.PrivacyTip,
                        iconTint = Color(0xFF6A1B9A),
                        title = tr("سياسة الخصوصية"),
                        subtitle = tr("كيف نتعامل مع بياناتك، وإخلاء المسؤولية الطبية"),
                        onClick = onPrivacyPolicyClick
                    )
                    SettingsDivider()
                    ActionRow(
                        icon = Icons.Filled.Gavel,
                        iconTint = Color(0xFF8D6E63),
                        title = tr("الشروط والأحكام"),
                        subtitle = tr("شروط استخدام التطبيق ومحتوى الموسوعة"),
                        onClick = onTermsClick
                    )
                    // نسخة "public" (BuildConfig.HAS_ADMIN == false): لا تسجيل دخول
                    // ولا أي زر إداري إطلاقاً — راجع flavorDimensions/productFlavors
                    // في app/build.gradle.kts وملفات app/src/public/... للتفاصيل.
                    if (BuildConfig.HAS_ADMIN) {
                        if (isAdmin) {
                            SettingsDivider()
                            ActionRow(
                                icon = Icons.Filled.AdminPanelSettings,
                                iconTint = Color(0xFF1565C0),
                                title = tr("أدوات الإدارة"),
                                subtitle = tr("إدارة الأعشاب والتصنيفات والبيانات"),
                                onClick = onAdminToolsClick
                            )
                            SettingsDivider()
                            ActionRow(
                                icon = Icons.Filled.Inbox,
                                iconTint = Color(0xFF00838F),
                                title = tr("ملاحظات المستخدمين"),
                                subtitle = tr("الأخطاء والملاحظات المرسلة من المستخدمين"),
                                onClick = onAdminFeedbackClick
                            )
                        }
                        SettingsDivider()
                        if (isLoggedIn) {
                            ActionRow(
                                icon = Icons.AutoMirrored.Filled.Logout,
                                iconTint = Color(0xFFC62828),
                                title = tr("تسجيل الخروج"),
                                subtitle = tr("إنهاء الجلسة الحالية"),
                                onClick = onLogoutClick
                            )
                        } else {
                            ActionRow(
                                icon = Icons.Filled.Login,
                                iconTint = Color(0xFF00695C),
                                title = tr("تسجيل الدخول"),
                                subtitle = tr("لإدارة المحتوى وحفظ التفضيلات"),
                                onClick = onLoginClick
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    tr("موسوعة الأعشاب الطبية"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    tr("© جميع الحقوق محفوظة — تطوير المعالج الفيزيائي سلمان"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
        )
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
internal fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ThemeModeSelector(darkMode: Boolean?, onDarkModeChange: (Boolean?) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Filled.Brightness6, tint = Color(0xFFFF8F00))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(tr("وضع العرض"), fontWeight = FontWeight.SemiBold)
                Text(
                    when (darkMode) { null -> tr("يتبع النظام"); true -> tr("داكن"); false -> tr("فاتح") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOptionChip(
                selected = darkMode == null,
                icon = Icons.Filled.Contrast,
                label = tr("النظام"),
                modifier = Modifier.weight(1f)
            ) { onDarkModeChange(null) }
            ThemeOptionChip(
                selected = darkMode == false,
                icon = Icons.Filled.LightMode,
                label = tr("فاتح"),
                modifier = Modifier.weight(1f)
            ) { onDarkModeChange(false) }
            ThemeOptionChip(
                selected = darkMode == true,
                icon = Icons.Filled.DarkMode,
                label = tr("داكن"),
                modifier = Modifier.weight(1f)
            ) { onDarkModeChange(true) }
        }
    }
}

@Composable
private fun LanguageSelector(selected: AppLanguage, isChanging: Boolean, onSelect: (AppLanguage) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Filled.Translate, tint = Color(0xFF00897B))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(tr("لغة التطبيق"), fontWeight = FontWeight.SemiBold)
                Text(
                    // توضيح صريح لسبب طلب المستخدم — "الزر مو واضح إنه يترجم
                    // عبر جوجل": هذا النص يذكر ذلك حرفياً بدل الاكتفاء باسم
                    // اللغة الحالية فقط.
                    tr("يترجم محتوى التطبيق تلقائياً عبر ترجمة جوجل"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LanguageOptionCard(
                selected = selected == AppLanguage.ARABIC,
                loading = isChanging && selected != AppLanguage.ARABIC,
                label = AppLanguage.ARABIC.nativeName,
                modifier = Modifier.weight(1f),
                enabled = !isChanging
            ) { onSelect(AppLanguage.ARABIC) }
            LanguageOptionCard(
                selected = selected == AppLanguage.ENGLISH,
                loading = isChanging && selected != AppLanguage.ENGLISH,
                label = AppLanguage.ENGLISH.nativeName,
                modifier = Modifier.weight(1f),
                enabled = !isChanging
            ) { onSelect(AppLanguage.ENGLISH) }
        }
    }
}

/**
 * بطاقة اختيار لغة واضحة (بدل شريحة FilterChip صغيرة كانت تستخدم نفس
 * الأيقونة لكلا الخيارين، ما جعلها — بحسب ملاحظة مستخدم فعلية — "غير
 * واضحة إطلاقاً"): حدّ ولون خلفية مميّزان جداً للخيار المُفعَّل، وعلامة ✓
 * صريحة بدل الاعتماد على تباين لوني خفيف فقط، مع مؤشر تحميل صغير أثناء
 * التبديل الفعلي (إعادة إنشاء الشاشة + الترجمة) بدل بقاء الزر بلا أي رد
 * فعل مرئي على الضغطة.
 */
@Composable
private fun LanguageOptionCard(
    selected: Boolean,
    loading: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ThemeOptionChip(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = modifier
    )
}

@Composable
internal fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontScaleRow(fontScale: Int, onFontScaleChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Filled.FormatSize, tint = Color(0xFF00838F))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("حجم النص"), fontWeight = FontWeight.SemiBold)
                Text(tr(fontScaleLabels[fontScale]), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                tr("أبج"),
                fontSize = fontScaleSizes[fontScale],
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(10.dp))
        val sliderInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        // مقبض دائري واضح بدل الخط العمودي الافتراضي الذي كان يلتصق
        // بحافة المسار، ومسار بنقاط توقّف متناسقة مع المقبض.
        Slider(
            value = fontScale.toFloat(),
            onValueChange = { onFontScaleChange(it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3,
            interactionSource = sliderInteraction,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = sliderInteraction,
                    thumbSize = androidx.compose.ui.unit.DpSize(26.dp, 26.dp),
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(10.dp),
                    thumbTrackGapSize = 4.dp,
                    trackInsideCornerSize = 2.dp,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteRow(
    enabled: Boolean,
    selected: ThemePalette,
    onSelect: (ThemePalette) -> Unit
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Filled.Palette, tint = Color(0xFFAD1457))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("لوحة الألوان"), fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) tr("اختر لون الهوية اليدوي") else tr("متاحة عند إيقاف الألوان الديناميكية"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // شبكة مرتّبة تلتفّ على أكثر من سطر بدل صفّ واحد مقصوص من الجانب:
        // كل الألوان ظاهرة دفعة واحدة بلا حاجة لاكتشاف التمرير الأفقي.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ThemePalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = enabled && palette == selected,
                    enabled = enabled,
                    onClick = { onSelect(palette) }
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // خيار "بدون تلوين" هو إيقاف/تشغيل التلوين نفسه، فمن المهم أن يكون
    // مميّزاً بصرياً عن باقي الدوائر الملوّنة (وإلا بدا مجرّد لون رمادي
    // إضافي بينها) — لذلك يحمل أيقونة "ممنوع" ثابتة بدل الاكتفاء بلون
    // خلفيته، بغضّ النظر عن حالة الاختيار.
    val isNone = palette == ThemePalette.NONE
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (isNone) Color(0xFFE0E0E0) else palette.swatch)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected -> Icon(
                Icons.Filled.Check,
                contentDescription = tr(palette.label),
                tint = if (isNone) Color(0xFF616161) else Color.White,
                modifier = Modifier.size(18.dp)
            )
            isNone -> Icon(
                Icons.Filled.NotInterested,
                contentDescription = tr(palette.label),
                tint = Color(0xFF757575),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PerformanceModeSelector(
    selected: PerformanceMode,
    onSelect: (PerformanceMode) -> Unit
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Filled.Speed, tint = Color(0xFF00897B))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(tr("وضع الأداء"), fontWeight = FontWeight.SemiBold)
                Text(
                    selected.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PerformanceModeCard(
                mode = PerformanceMode.HIGH_QUALITY,
                icon = Icons.Filled.AutoAwesome,
                selected = selected == PerformanceMode.HIGH_QUALITY,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(PerformanceMode.HIGH_QUALITY) }
            )
            PerformanceModeCard(
                mode = PerformanceMode.ECO,
                icon = Icons.Filled.BatteryChargingFull,
                selected = selected == PerformanceMode.ECO,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(PerformanceMode.ECO) }
            )
        }
    }
}

@Composable
private fun PerformanceModeCard(
    mode: PerformanceMode,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(tr(mode.label), fontWeight = FontWeight.SemiBold, color = content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Filled.ChevronLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun UpdateRow(
    currentVersionName: String,
    updateState: UpdateCheckState,
    downloadState: UpdateDownloadState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: (AppUpdateInfo) -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit = {}
) {
    // أندرويد 13+ يتطلّب إذناً صريحاً قبل إظهار أي إشعار — بما فيها إشعار
    // تقدّم تحميل التحديث بالخلفية. التحميل نفسه يعمل حتى بدون هذا الإذن،
    // فقط الإشعار لن يظهر، لذا لا نمنع التحميل إن رُفض — فقط نطلبه مرة
    // بشكل استباقي عند أول ضغطة على "تحميل التحديث".
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* النتيجة لا تغيّر أي شيء هنا — راجع التعليق أعلاه. */ }
    val context = LocalContext.current
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("تحديث التطبيق"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("الإصدار الحالي: $currentVersionName"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // طابع داخلي يوثّق آخر جولة تحسينات هندسة واجهة (ترجمة أدوات
                // الإدارة/الخصوصية، معالج إعداد اللغة الأول، وتحسينات أداء
                // الزجاج السائل) — يظهر بخط صغير خافت تحت رقم الإصدار كي لا
                // يُشوّش على المستخدم العادي لكنه موثَّق ومرئي عند الحاجة.
                Text(
                    UI_ENGINEERING_BUILD_TAG,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = updateState,
            // إصلاح: كان بلا easing صريح (منحنى Compose الافتراضي)، بخلاف
            // منحنى [AppMotion.Smooth] الموحّد المستخدم بكل حركات التطبيق
            // الأخرى — الآن موحّدة معها.
            transitionSpec = { fadeIn(tween(220, easing = AppMotion.Smooth)) togetherWith fadeOut(tween(150, easing = AppMotion.Smooth)) },
            label = "updateCheckState"
        ) { state ->
            when (state) {
                is UpdateCheckState.Idle -> {
                    OutlinedButton(
                        onClick = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("التحقق من التحديثات"))
                    }
                }
                is UpdateCheckState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("جارٍ التحقق من وجود تحديث..."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UpdateCheckState.UpToDate -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("التطبيق محدّث لأحدث إصدار"), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCheckForUpdate) { Text(tr("إعادة التحقق")) }
                    }
                }
                is UpdateCheckState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onCheckForUpdate) { Text(tr("إعادة المحاولة")) }
                    }
                }
                is UpdateCheckState.Available -> {
                    val info = state.info
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "v${info.versionName}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr("يتوفر تحديث جديد"),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (info.mandatory) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        tr("إجباري"),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (info.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        AnimatedContent(
                            targetState = downloadState,
                            // نفس التوحيد أعلاه: منحنى AppMotion.Smooth بدل الافتراضي.
                            transitionSpec = { fadeIn(tween(220, easing = AppMotion.Smooth)) togetherWith fadeOut(tween(150, easing = AppMotion.Smooth)) },
                            label = "updateDownloadState"
                        ) { dState ->
                            when (dState) {
                                is UpdateDownloadState.Idle -> {
                                    Button(
                                        onClick = {
                                            requestNotificationPermissionIfNeeded()
                                            onDownloadUpdate(info)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(tr("تحميل التحديث"))
                                    }
                                }
                                is UpdateDownloadState.Downloading -> {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(
                                                    progress = { dState.progress / 100f },
                                                    modifier = Modifier.size(36.dp),
                                                    strokeWidth = 3.dp
                                                )
                                                Text(
                                                    "${dState.progress}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    tr("جارٍ التحميل بالخلفية..."),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    tr("يمكنك إغلاق هذه الشاشة — سيصلك إشعار عند الانتهاء"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(onClick = onCancelDownload) { Text(tr("إلغاء")) }
                                        }
                                    }
                                }
                                is UpdateDownloadState.ReadyToInstall -> {
                                    Column {
                                        Button(
                                            onClick = onInstallUpdate,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                        ) {
                                            Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("تثبيت الآن"))
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            tr("التحديث جاهز — يمكنك أيضاً الضغط على الإشعار للتثبيت"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                is UpdateDownloadState.Failed -> {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                dState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { onDownloadUpdate(info) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(tr("إعادة المحاولة"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
