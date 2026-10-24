package com.salman.herbalencyclopedia.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.salman.herbalencyclopedia.ui.util.tr
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.ui.components.*
import com.salman.herbalencyclopedia.ui.theme.entranceFade
import com.salman.herbalencyclopedia.ui.theme.staggeredEntrance
import java.util.Calendar

/**
 * تحية الشريط العلوي — كانت فترتين فقط (صباح/مساء) بجملة واحدة ثابتة لكل
 * فترة، فتتكرر حرفياً بلا أي تنوّع طوال نصف اليوم. الآن أربع فترات يومية
 * (صباح، ظهر/عصر، مساء، ليل) وبعدة صيغ لكل فترة تُختار عشوائياً — تُحسَب
 * مرة واحدة فقط لكل دخول للشاشة الرئيسية (انظر `remember` عند نقطة
 * الاستخدام بالأسفل) كي لا تتغيّر الجملة أثناء إعادة تركيب الواجهة نفسها
 * (كتبديل الثيم مثلاً) وتبدو وكأنها تُعاد عشوائياً بلا سبب. تحل هذه الدالة
 * محل الجملة التعريفية الثابتة القديمة ("معرفة موثوقة • تجربة هادئة •
 * تصميم حديث") التي كانت لا تتغيّر أبداً.
 */
private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val options = when (hour) {
        in 4..9 -> listOf(
            "أهلاً بك، صباح الخير",
            "صباح الخير، يومك مليء بالنشاط",
            "صباح النور، أهلاً بعودتك",
            "أهلاً بك، بداية موفّقة ليومك"
        )
        in 10..16 -> listOf(
            "أهلاً بك، نهارك سعيد",
            "طاب نهارك، أهلاً بعودتك",
            "أهلاً بك، وقت رائع لاستكشاف الأعشاب",
            "نهارك مليء بالصحة والعافية"
        )
        in 17..20 -> listOf(
            "أهلاً بك، مساء الخير",
            "مساء النور، أهلاً بعودتك",
            "أهلاً بك، أمسية طيبة",
            "مساء الخير، وقت هادئ لتصفّح الموسوعة"
        )
        else -> listOf(
            "أهلاً بك، ليلة سعيدة",
            "طابت ليلتك، أهلاً بعودتك",
            "أهلاً بك، سهرة هادئة معك",
            "ليلتك طيبة، أهلاً بك من جديد"
        )
    }
    return options.random()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    categories: List<Category>,
    herbs: List<Herb>,
    isLoading: Boolean,
    error: String?,
    isAdmin: Boolean,
    onRetry: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onSemoClick: () -> Unit,
    onBlendsClick: () -> Unit,
    onSupportClick: () -> Unit = {}
) {
    // مرة واحدة فقط لكل دخول للشاشة — راجع تعليق greetingForNow أعلاه.
    val greeting = remember { greetingForNow() }
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        // هذه الشاشة تظهر دائماً فوق OneUiFloatingNavBar (شاشة جذر ضمن
        // topRoutes)، والـBox الخارجي في HerbalNavGraph يكون قد حجز أصلاً
        // كامل ارتفاع الشريط العائم (شاملاً حاجز نظام التنقّل السفلي، لأن
        // الشريط نفسه يطبّق windowInsetsPadding(navigationBars) داخلياً).
        // لو تُرك Scaffold هنا على قيمته الافتراضية (safeDrawing) سيحجز
        // إضافياً نفس ارتفاع شريط النظام السفلي من جديد، فتظهر فجوة
        // فارغة مضاعفة (سماكة/مسافة) بين آخر عنصر بالمحتوى وبين الشريط
        // العائم نفسه. لذلك نُبقي فقط حجز الحافة العلوية (شريط الحالة)
        // اللازمة لـGlassTopBar، ونُصفّر الحافة السفلية هنا.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            GlassTopBar(
                large = true,
                scrollBehavior = topBarScroll,
                // التطبيق بواجهة عربية (RTL)، وMaterial يعكس ترتيب الشريط
                // العلوي تلقائياً في هذه الحالة: عنصر navigationIcon يظهر
                // عند الحافة "القائدة" لاتجاه القراءة، وهي أقصى يمين الشاشة
                // في RTL — بعكس actions التي تظهر عند الحافة المقابلة
                // (أقصى يسار الشاشة). لذلك أيقونة الدعم المطلوبة "أعلى
                // الشاشة يمين" توضع هنا في navigationIcon وليس actions، كي
                // تظهر فعلياً بالزاوية العلوية اليمنى بدل اليسرى.
                navigationIcon = {
                    GlassIconButton(onClick = onSupportClick) {
                        Icon(Icons.Filled.SupportAgent, contentDescription = tr("الدعم الفني"))
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // شارة دائرية بهوية التطبيق (نفس أيقونة شاشة البداية)
                        // بدل عنوان نصي مجرّد، لإحساس علامة تجارية أوضح.
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Spa,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                com.salman.herbalencyclopedia.ui.util.tr("موسوعة الأعشاب الطبية"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                com.salman.herbalencyclopedia.ui.util.tr(greeting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    if (isAdmin) {
                        GlassIconButton(onClick = onAdminClick, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = com.salman.herbalencyclopedia.ui.util.tr("لوحة التحكم"))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .entranceFade(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Search,
                    label = tr("بحث"),
                    onClick = onSearchClick
                )
                QuickAction(
                    modifier = Modifier.weight(1.35f),
                    icon = Icons.Filled.AutoAwesome,
                    label = tr("سيمو"),
                    onClick = onSemoClick,
                    highlighted = true
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Blender,
                    label = tr("الخلطات"),
                    onClick = onBlendsClick
                )
            }

            // "سحب للتحديث" (نفس مبدأ فيسبوك/إنستغرام): يفرض جولة حقيقية إلى
            // خادم Firestore بدل انتظار المزامنة الحيّة الصامتة. onRetry
            // كان مربوطاً سابقاً فقط بزر "إعادة المحاولة" الذي يظهر عند
            // خطأ صريح؛ الآن يُستخدم أيضاً كمصدر تحديث يدوي عبر السحب لأسفل
            // في أي وقت، لا عند الخطأ فقط.
            PullToRefreshBox(
                isRefreshing = isLoading && categories.isNotEmpty(),
                onRefresh = onRetry,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    // كان الشرط هنا `isLoading` وحدها: أي تحديث يدوي (حتى
                    // سحب-للتحديث فوق بيانات مُحمَّلة أصلاً) يُخفي الشبكة
                    // كاملة خلف مؤشّر تحميل عام لحظياً، فيختفي كل المحتوى
                    // المعروض فعلاً ثم يظهر من جديد — وهذا يُبطل الغرض من
                    // "سحب للتحديث" على طريقة فيسبوك أصلاً (يبقى المحتوى
                    // القديم ظاهراً أثناء التحديث، ومؤشر السحب وحده يدل على
                    // التقدّم). الآن يظهر مؤشر التحميل الكامل فقط في التحميل
                    // الأول الحقيقي (لا بيانات معروضة أصلاً بعد).
                    isLoading && categories.isEmpty() -> LoadingView(
                        Modifier.fillMaxSize(),
                        message = tr("جاري تحميل بيانات الموسوعة...")
                    )
                    // بنفس المبدأ: خطأ أثناء تحديث بيانات مُحمَّلة أصلاً لا
                    // يجب أن يُخفي تلك البيانات — تماماً كما يبقي معالج
                    // المزامنة الحيّة في AppViewModel.init البيانات القديمة
                    // ظاهرة ويُسجّل الخطأ فقط بدل مسحها بالكامل.
                    error != null && categories.isEmpty() -> ErrorView(error, onRetry, Modifier.fillMaxSize())
                    categories.isEmpty() -> EmptyView(tr("لا توجد تصنيفات بعد"), Modifier.fillMaxSize())
                    // كانت هذه شبكة (LazyVerticalGrid) تعرض عدة أعمدة، فتظهر
                    // بطاقات التصنيفات جنباً إلى جنب. الآن قائمة عمودية واحدة
                    // (LazyColumn) تعرض كل بطاقة بعرض كامل تحت التي قبلها،
                    // مثل باقي قوائم التطبيق (الأعشاب/المفضلة/الخلطات).
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            bottom = 20.dp + LocalBottomBarInset.current
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // عنوان قسم بسيط أعلى القائمة يمنحها تنظيماً بصرياً
                        // أوضح بدل انتقال البطاقات مباشرة تحت أزرار الإجراءات
                        // السريعة بلا أي فاصل أو تسمية.
                        item(key = "categories_header") {
                            Text(
                                tr("التصنيفات"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
                            CategoryCard(
                                category = category,
                                herbCount = herbs.count { it.categoryId == category.id },
                                onClick = { onCategoryClick(category) },
                                // بدل ظهور كل البطاقات دفعة واحدة، كل بطاقة تتلاشى
                                // وتنزلق للأعلى بعد اللي قبلها بفارق بسيط — مرة
                                // واحدة عند تحميل الشاشة، بلا أي تكرار لانهائي.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .staggeredEntrance(index)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    // نفس نابض الضغط الموحّد المستخدم بأزرار الزجاج (GlassButton) — تصغير
    // خفيف فوري عند الضغط ثم عودة نابضة، بتكلفة منخفضة جداً (قيمة واحدة
    // متحركة فقط عند تغيّر حالة الضغط الفعلية، وليست حركة مستمرة)، تُبقي
    // هذه البطاقات متّسقة الإحساس مع بقية عناصر التطبيق القابلة للنقر.
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressScale by com.salman.herbalencyclopedia.ui.theme.rememberPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .height(54.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        shape = RoundedCornerShape(18.dp),
        // سيمو هو الإجراء المميّز بالصفحة: تدرّج بلوني الهوية ونص فوقه
        // بدل ثلاث بطاقات متطابقة تماماً لا يُعرف أيّها الأهم.
        color = if (highlighted) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (highlighted) 0.dp else 2.dp
    ) {
        Row(
            // كان padding أفقي 16.dp مع Spacer عرضه 8.dp يترك مساحة ضيقة
            // جداً أمام النص داخل عنصر بعرض ١/٣ الشاشة فقط (ثلاث بطاقات
            // متجاورة بوزن متساوٍ) — تكفي لكلمة قصيرة مثل "بحث" أو "سيمو"
            // لكن ليس لكلمة أطول مثل "الخلطات"، فكانت تلتف لسطر ثانٍ ثم
            // تُقصّ داخل الارتفاع الثابت 54.dp فيظهر السطر الثاني (الحرف
            // الأخير) وحده مقطوعاً. تقليل الحشو والفراغ بينهما يمنح النص
            // مساحة إضافية كافية، و maxLines/overflow يبقيان كشبكة أمان
            // نهائية تمنع تكرار المشكلة مع أي تسمية أطول مستقبلاً.
            Modifier
                .fillMaxSize()
                .then(
                    if (highlighted) Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.22f)
                            )
                        )
                    ) else Modifier
                )
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary else Color.Unspecified,
                fontWeight = if (highlighted) FontWeight.Bold else null,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
