package com.salman.herbalencyclopedia.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * عنوان شريط علوي "غني": شارة دائرية ملوّنة بأيقونة + عنوان + عنوان فرعي
 * (سطر سياق حي مثل عدد العناصر)، بدل نص مجرّد بلا وزن بصري. نفس نمط
 * شارة الهوية في [HomeScreen]، معاد استخدامه هنا لبقية الشاشات الجذرية
 * (المفضلة، كل الأعشاب...) كي تبدو منسجمة معه بدل شريط فارغ يبدو ناقصاً.
 */
@Composable
fun TopBarBrandTitle(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    large: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    // إصلاح وضوح: 0.10 مزج كانت نسبة صبغة primary ضعيفة جداً، فيبدو الشريط
    // العلوي قريباً جداً من لون خلفية الشاشة نفسها (خصوصاً بالوضع الداكن
    // وبعض لوحات الألوان قليلة التشبّع) - عملياً "بلا شريط ظاهر" رغم أن كل
    // طبقاته مرسومة فعلياً. رفعها إلى 0.24 يعطي هوية لونية واضحة تميّز
    // الشريط عن المحتوى تحته في كل الأوضاع دون تغيير أي شيء آخر بالتصميم.
    val surface = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.primary,
        0.24f
    )
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    // ظل أسود افتراضي (Modifier.shadow بلا ألوان مخصّصة) يعطي إحساساً
    // "موحلاً"/رمادياً تحت سطح زجاجي ملوّن، ويبرز بشكل خاص في الوضع
    // النهاري حيث الخلفية فاتحة والفرق مع ظل أسود صرف يصبح لافتاً بلا
    // داعٍ. تظليل بلون السطح نفسه (surfaceContainerHigh) بدل الأسود
    // المحايد يجعل الظل امتداداً طبيعياً للزجاج بدل بقعة رمادية منفصلة
    // عنه، بنفس الفكرة في [OneUiFloatingNavBar] أدناه.
    val shadowTint = surface.copy(alpha = 0.7f)

    LiquidGlassSurface(
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false, ambientColor = shadowTint, spotColor = shadowTint),
        tint = surface,
        // إصلاح وضوح (راجع تعليق `surface` أعلاه): حدّ 0.16 كان خافتاً جداً
        // ليرسم فاصلاً بصرياً واضحاً بين الشريط والمحتوى. 0.30 يعطي حافة
        // زجاجية واضحة تحدّد شكل الشريط بدل أن يذوب في الخلفية.
        borderAlpha = 0.30f,
        // إصلاح وضوح إضافي: خفض شفافية صبغة التمويه الحقيقي خلف الشريط
        // (تُستخدم فقط حين backdrop متاح وAndroid 12+) من الافتراضي
        // (0.46/0.30) إلى صبغة أعتم بوضوح، فيبقى لون الشريط المميّز (المزيج
        // أعلاه) واضحاً فوق ما يتحرك خلفه بدل أن يطغى التمويه الحقيقي عليه.
        backdropTintAlphaTop = 0.80f,
        backdropTintAlphaBottom = 0.68f,
        // فقاعتا التوهّج الأبيض/الملوّن (blurBubbles) كانتا تُنتجان بقعة
        // ساطعة غير منضبطة المكان فوق الشريط الزجاجي الملوّن حديثاً — تُقرأ
        // كخلل/فراغ أبيض غريب بدل لمعان أنيق، خصوصاً فوق تدرّج ملوّن غامق.
        // إيقافها هنا يبقي الشريط زجاجاً نظيفاً بلا أي بقعة ضوء عشوائية.
        blurBubbles = false,
        // الشريط العلوي ثابت وظاهر طول الوقت بكل الشاشات؛ لمعان لانهائي
        // يلمع ويعيد نفسه من الصفر باستمرار عليه يبدو مزعجاً بدل "حي" —
        // بعكس زر عائم صغير يظهر لثوانٍ. لذلك يُطفأ هنا.
        sheen = false,
        // خلفية زجاجية حقيقية (انظر GlassBackdrop.kt): يظهر تمويه فعلي لما
        // يمر خلف الشريط أثناء التمرير، بدل صبغة لونية ثابتة فقط.
        backdrop = LocalGlassBackdrop.current
    ) {
        // إصلاح الهيدر الضخم: LargeTopAppBar يحجز نحو 152dp (صف أيقونات علوي
        // فارغ + عنوان تحته) فيأكل ربع الشاشة ويبقى زر الرجوع/الدعم معلّقاً
        // بعيداً عن العنوان. الوضع "large" صار صفاً واحداً بارتفاع 84dp:
        // الأيقونة بجوار شارة العنوان مباشرة، ومع scrollBehavior ينسحب
        // الشريط عند التمرير للأسفل ويعود فوراً عند التمرير للأعلى.
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = if (large) 84.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            scrollBehavior = scrollBehavior
        )
    }
}

data class OneUiNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun OneUiFloatingNavBar(
    items: List<OneUiNavItem>,
    currentRoute: String?,
    onItemClick: (OneUiNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // surfaceContainerHigh وحدها لون شبه محايد (تشبّع منخفض جداً)، فيبدو
    // الشريط كأنه عنصر نظام عام رمادي بلا هوية بصرية — بالضبط الإحساس
    // بـ"مربع/كبسولة غير مندمجة مع التطبيق" الذي لوحظ على الأجهزة الأقدم
    // (بلا تمويه حقيقي متاح، فالتلوين هنا هو الفارق الوحيد المرئي). مزجه
    // بقليل من primary اللوحة المختارة يمنحه هوية لونية واضحة تتبدّل
    // تلقائياً مع أي ثيم بدل رمادي عام واحد لكل الثيمات.
    // إصلاح وضوح (نفس سبب GlassTopBar أعلاه بالضبط): 0.14 مزج ضعيف جداً
    // يجعل كبسولة التنقّل السفلية قريبة جداً من لون خلفية الشاشة، فتبدو
    // بلا حضور بصري واضح فوق المحتوى المتحرّك خلفها. 0.26 يعطيها هوية
    // لونية أوضح تُميّزها بوصفها شريط تنقّل عائم، لا امتداداً شفافاً للخلفية.
    val container = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.primary,
        0.26f
    )
    val shape = RoundedCornerShape(30.dp)
    // على تنقّل الإيماءات الحاجز السفلي (navigationBars) رفيع جداً (عادة
    // أقل من 32dp)، بينما على أزرار التنقل التقليدية الثلاثة يكون أثخن
    // بوضوح. windowInsetsPadding(navigationBars) وحده يتكفّل بعدم تداخل
    // الشريط مع نظام التنقل في الحالتين، لكن بلا أي هامش إضافي كان الشريط
    // العائم يلتصق بحافة منطقة الإيماءات مباشرة على أجهزة الإيماءات —
    // فرق بسيط لكنه يجعل الشريط "معلّقاً" بدل مستقر. هامش سفلي إضافي صغير
    // فقط في وضع الإيماءات يعيد له نفس الإحساس بالاستقرار الذي يملكه
    // تلقائياً فوق أزرار التنقل الأثخن.
    val extraBottomPadding = if (com.salman.herbalencyclopedia.ui.util.isGestureNavigation()) 8.dp else 0.dp

    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .padding(bottom = extraBottomPadding),
        contentAlignment = Alignment.Center
    ) {
        // الظل تحت الشريط أُزيل بالكامل بناءً على طلب المستخدم — بلا أي
        // هالة/تظليل مرسومة أسفل الشريط، فقط سطح الزجاج نفسه أدناه.

        // طبقة صلبة شبه معتمة تحت الزجاج: بعض مسارات الرسم (بلا backdrop أو
        // بوضع أداء أدنى) تترك 15-26% شفافية فتظهر نصوص البطاقات خلف الشريط.
        Box(Modifier.background(container.copy(alpha = 0.97f), shape)) {
        LiquidGlassSurface(
            shape = shape,
            tint = container,
            glowColor = MaterialTheme.colorScheme.tertiary,
            // إصلاح وضوح: نفس رفع حدّ الشريط العلوي، لحافة زجاجية أوضح تحدّد
            // شكل الكبسولة العائمة بدل أن تذوب حافتها في المحتوى خلفها.
            borderAlpha = 0.28f,
            // نفس السبب أعلاه في GlassTopBar: بلا فقاعات توهّج على الشريط
            // السفلي، فلا تظهر بقعة بيضاء/فراغ بصري غريب فوق تلوينه الجديد.
            blurBubbles = false,
            // نفس خلفية الزجاج الحقيقي أعلاه: هذا الشريط يطفو مباشرة فوق
            // محتوى الشاشة (المفضلة/الأعشاب/الإعدادات...)، فالتمويه الحقيقي
            // هنا هو ما يجعله يبدو امتداداً لواجهة التطبيق تحته بدل كبسولة
            // رمادية مقحمة فوق خلفية لا علاقة لها بها.
            backdrop = LocalGlassBackdrop.current,
            // القيم الافتراضية (0.46/0.30) في LiquidGlassSurface صُمِّمت
            // للشريط العلوي الثابت الذي يظهر دائماً فوق نفس خلفية الصفحة.
            // الشريط السفلي العائم يطفو فوق محتوى متغيّر الألوان (شبكات/
            // قوائم بطاقات ملوّنة تتحرك خلفه أثناء التمرير)، وبنفس القيم
            // المنخفضة كان التمويه الحقيقي خلفه يعبر بوضوح شديد لدرجة يصعب
            // معها تمييز أيقونات/نص التنقّل فوقه. رفع الشفافية هنا يبقي
            // إحساس "الزجاج الحقيقي" (لا يزال يُرى تحرّك الألوان خلفه) لكن
            // بصبغة أقوى تحمي وضوح خيارات التنقّل.
            // إصلاح تداخل المحتوى: 0.74/0.60 كانت تُظهر نصوص البطاقات خلف
            // الشريط وتتداخل مع التسميات. 0.93/0.88 يبقي لمسة الزجاج لكن
            // بخلفية شبه معتمدة تحمي وضوح أيقونات التنقّل.
            backdropTintAlphaTop = 0.93f,
            backdropTintAlphaBottom = 0.88f
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    OneUiFloatingNavItem(
                        item = item,
                        selected = item.route == currentRoute,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun OneUiFloatingNavItem(
    item: OneUiNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    // كل خصائص التبديل هنا (اللون، حجم الأيقونة، اتساع الكبسولة، ظهور
    // النص) كانت تُقسَّم سابقاً بين نوعين مختلفين من الحركة: الألوان
    // بتوين ثابت المدة 140ms، بينما الحجم/الاتساع بنابض snappy() الذي لا
    // يملك "مدة" ثابتة أصلاً بل يستقر تقريبياً حول 150-200ms. الفارق
    // البسيط في طريقة الاستقرار بين النوعين كان يخلق إحساساً بعدم
    // التزامن (اللون يصل لوضعه النهائي بتوقيت مختلف قليلاً عن الحجم/
    // النص) — وهذا تحديداً ما يجعل الحركة "مو ظابطة". الحل: توحيد كل
    // الخصائص هنا على نفس نوع النابض وثوابته تماماً، فتصل كلها لوضعها
    // النهائي معاً كحركة واحدة متماسكة بدل أربع حركات منفصلة قليلاً.
    // (كل استدعاء snappy() أدناه منفصل عمداً — كل واحد يُستدل نوعه من
    // موضع استخدامه: Color، IntSize، Float... لكنها كلها تحمل نفس ثابتي
    // النابض [dampingRatio/stiffness] فتستقر معاً بنفس الإيقاع تماماً.)
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy(),
        label = "navBackground"
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy(),
        label = "navContent"
    )
    // نبضة خفيفة على الأيقونة عند الاختيار بدل التبديل المفاجئ.
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy(),
        label = "navIconScale"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(background, CircleShape)
            // العرض يتوسّع/يتقلّص بسلاسة عند ظهور/اختفاء التسمية بدل القفز
            // المباشر بين حالتي "أيقونة فقط" و"أيقونة + نص"، بنفس النابض
            // الموحّد أعلاه كي يتزامن مع تغيّر اللون والحجم تماماً.
            .animateContentSize(animationSpec = com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy())
            .padding(horizontal = if (selected) 16.dp else 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = content,
            modifier = Modifier
                .size(22.dp)
                .scale(iconScale)
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = androidx.compose.animation.fadeIn(com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy()) +
                androidx.compose.animation.expandHorizontally(com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy()),
            exit = androidx.compose.animation.fadeOut(com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy()) +
                androidx.compose.animation.shrinkHorizontally(com.salman.herbalencyclopedia.ui.theme.AppMotion.snappy())
        ) {
            Text(item.label, color = content, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * شريط تنقّل جانبي لتابلت/نافذة عريضة (Medium/Expanded)، بديل عن
 * [OneUiFloatingNavBar] الذي صُمّم لعرض جوال ضيق. قبل هذا المكوّن كان
 * الشريط العائم السفلي نفسه يُستخدم على كل الأحجام بلا استثناء: على
 * تابلت بوضع أفقي هذا يعني شريطاً عريضاً يمتد كامل عرض الشاشة أسفلها
 * بعيداً عن الإبهام في تلك الحالة، ويسرق ارتفاعاً كان يمكن استغلاله
 * لعرض محتوى أكثر. شريط جانبي ثابت العرض على الحافة (يمين الشاشة بلغة
 * RTL) هو النمط المعتاد لتطبيقات أندرويد على الشاشات الواسعة.
 */
@Composable
fun OneUiNavigationRail(
    items: List<OneUiNavItem>,
    currentRoute: String?,
    onItemClick: (OneUiNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Spacer(Modifier.height(12.dp))
        items.forEach { item ->
            val selected = item.route == currentRoute
            NavigationRailItem(
                selected = selected,
                onClick = { onItemClick(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontWeight = FontWeight.SemiBold) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
