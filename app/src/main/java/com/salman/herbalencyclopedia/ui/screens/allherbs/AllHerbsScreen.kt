package com.salman.herbalencyclopedia.ui.screens.allherbs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import com.salman.herbalencyclopedia.ui.theme.staggeredEntrance
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.LocalBottomBarInset
import com.salman.herbalencyclopedia.ui.components.TopBarBrandTitle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Herb
import androidx.compose.runtime.saveable.rememberSaveable
import com.salman.herbalencyclopedia.data.search.MatchTier
import com.salman.herbalencyclopedia.data.search.SearchEngine
import com.salman.herbalencyclopedia.data.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.salman.herbalencyclopedia.ui.components.EmptyView
import com.salman.herbalencyclopedia.ui.components.HerbCard
import com.salman.herbalencyclopedia.ui.components.LoadingView
import com.salman.herbalencyclopedia.ui.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllHerbsScreen(
    herbs: List<Herb>,
    favoriteIds: Set<String>,
    onHerbClick: (Herb) -> Unit,
    onToggleFavorite: (String) -> Unit,
    // "سحب للتحديث" (نفس مبدأ فيسبوك/إنستغرام): يفرض جولة حقيقية إلى خادم
    // Firestore (تجاوزاً للكاش المحلي) لتأكيد أن ما يُعرض هو أحدث بيانات
    // فعلاً، بدل انتظار المزامنة الحيّة التلقائية بصمت. القيمتان اختياريتان
    // (بقيمة افتراضية بلا تأثير) كي تبقى أي استدعاءات سابقة للشاشة صحيحة
    // دون تعديل، لكن HerbalNavGraph يمرّرهما فعلياً من AppViewModel (انظر
    // uiState.isLoading و[AppViewModel.refresh] هناك).
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    // *** إصلاح: أول تشغيل للتطبيق (بلا أي كاش محلي بعد) كان يعرض "لا توجد
    // نتائج" بدل مؤشر تحميل *** — هذه الشاشة لم تكن تستقبل isLoading
    // إطلاقاً، فحين تصل فارغة (herbs = []) أثناء أول جلب فعلي من Firestore
    // (راجع AppViewModel.init: كاش محلي فوراً إن وُجد، وإلا مزامنة شبكية)
    // كان `filtered.isEmpty()` يتحقق فوراً ويعرض EmptyView بنص "لا توجد
    // نتائج لـ ''" - بالضبط الرسالة المُبلَّغ عنها - قبل أن تصل البيانات
    // أصلاً، فيظن المستخدم أن الموسوعة فارغة أو التطبيق معطوب. isRefreshing
    // أعلاه غير كافٍ لهذا لأنه مُقيَّد بـ`herbs.isNotEmpty()` تحديداً (كي لا
    // يظهر مؤشر السحب-للتحديث خطأً عند أول تحميل) وهو مبرَّر لغرضه لكنه
    // يترك أول تحميل بلا أي مؤشر إطلاقاً. هذه الوسيطة الجديدة منفصلة تماماً
    // وتُستخدم فقط لإظهار LoadingView الحقيقية عند اجتماع isLoading=true مع
    // herbs فارغة - أي أول تحميل حصراً - راجع نقطة الاستخدام أدناه.
    isLoading: Boolean = false
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val hits by produceState<List<SearchHit<Herb>>?>(null, trimmed, herbs) {
        if (trimmed.isEmpty()) {
            value = null
            return@produceState
        }
        delay(150L)
        value = withContext(Dispatchers.Default) { SearchEngine.searchHerbs(trimmed, herbs) }
    }
    val currentHits = hits
    val matches = remember(currentHits) { currentHits.orEmpty().filter { it.tier == MatchTier.MATCH }.map { it.item } }
    val suggestions = remember(currentHits) { currentHits.orEmpty().filter { it.tier == MatchTier.SUGGESTION }.map { it.item } }
    val suggestionsTitle = tr("اقتراحات")
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        // انظر التعليق بنفس المكان في HomeScreen: هذه شاشة جذر أيضاً، وترك
        // الحافة السفلية الافتراضية هنا يُضاعف الفراغ فوق الشريط العائم.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            GlassTopBar(
                large = true,
                scrollBehavior = topBarScroll,
                title = {
                    TopBarBrandTitle(
                        icon = Icons.Filled.MenuBook,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = tr("كل الأعشاب"),
                        subtitle = tr("${herbs.size} عشبة في الموسوعة")
                    )
                },
                // زر تحديث صريح بجانب السحب-للتحديث: بعض المستخدمين لا
                // يكتشفون إيماءة السحب، فهذا الزر يمنحهم طريقة واضحة ومرئية
                // لجلب أحدث بيانات الأعشاب من Firestore. أثناء التحديث يدور
                // الأيقونة وتُعطَّل الضغطات المتكررة (isRefreshing) بنفس
                // منطق PullToRefreshBox أعلاه.
                actions = {
                    // ═══ إصلاح: دوران أيقونة التحديث كان يعمل بلا توقف دوماً ═══
                    // rememberInfiniteTransition + animateFloat كانا يُستدعيان هنا
                    // بلا أي شرط، أي أن هذه الحركة اللانهائية تبدأ فوراً عند أول
                    // تركيب لهذه الشاشة وتستمر طوال بقاء الشاشة في الذاكرة (حتى
                    // بعيداً عن الشاشة عبر NavHost) — بصرف النظر تماماً عن قيمة
                    // isRefreshing؛ Modifier.rotate(rotation) وحده كان يُطبَّق
                    // شرطياً، لا الحركة نفسها. أي: كل مستخدم يفتح "كل الأعشاب"
                    // كان يشغّل إطاراً إضافياً معاد رسمه ~60 مرة/ثانية للأبد، بلا
                    // أي أثر مرئي معظم الوقت (الأيقونة ثابتة فعلياً) — استهلاك
                    // معالج وبطارية بلا داعٍ إطلاقاً، وعلى الأجهزة الضعيفة تحديداً
                    // هذا فارق ملموس. الآن يُنشأ الدوران فقط أثناء isRefreshing
                    // الفعلي، فتنعدم تكلفة أي حركة حين لا يوجد تحديث جارٍ.
                    val rotation = if (isRefreshing) {
                        val infiniteTransition = rememberInfiniteTransition(label = "refreshRotation")
                        val animated by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "refreshRotationValue"
                        )
                        animated
                    } else 0f
                    GlassIconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = tr("تحديث الأعشاب"),
                            modifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            // نفس إصلاح HomeScreen: isRefreshing لا يُربَط بـisLoading مباشرة
            // (وإلا فأي تحديث صامت في الخلفية - كتغيير اللغة أو المزامنة كل
            // ٢٤ ساعة - يُشعل دائرة السحب-للتحديث فوق شريط البحث دون أي سحب
            // فعلي من المستخدم) بل فقط عندما توجد بيانات مُحمَّلة أصلاً على
            // الشاشة، تماماً بنفس منطق HomeScreen.
            isRefreshing = isRefreshing && herbs.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(tr("ابحث في الموسوعة")) },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    // خلفية ممتلئة بلون السطح وحدّ هادئ بدل الإطار الأخضر المضيء.
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (isLoading && herbs.isEmpty()) {
                    LoadingView(
                        modifier = Modifier.fillMaxSize(),
                        message = tr("جاري تحميل الأعشاب...")
                    )
                } else if (trimmed.isEmpty() && herbs.isEmpty()) {
                    EmptyView(message = tr("لا توجد أعشاب"), modifier = Modifier.fillMaxSize())
                } else if (trimmed.isNotEmpty() && currentHits == null) {
                    Box(Modifier.fillMaxSize())
                } else if (trimmed.isNotEmpty() && currentHits.isNullOrEmpty()) {
                    EmptyView(message = tr("لا توجد نتائج لـ \"$trimmed\""), modifier = Modifier.fillMaxSize())
                } else {
                    val listContentPadding = PaddingValues(
                        start = 16.dp, top = 16.dp, end = 16.dp,
                        bottom = 16.dp + LocalBottomBarInset.current
                    )
                    LazyColumn(
                        contentPadding = listContentPadding,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (trimmed.isEmpty()) {
                            itemsIndexed(herbs, key = { _, h -> h.id }) { index, herb ->
                                HerbCard(
                                    herb, herb.id in favoriteIds, { onHerbClick(herb) }, { onToggleFavorite(herb.id) },
                                    modifier = Modifier.staggeredEntrance(index)
                                )
                            }
                        } else {
                            items(matches, key = { "m-${it.id}" }) { herb ->
                                HerbCard(herb, herb.id in favoriteIds, { onHerbClick(herb) }, { onToggleFavorite(herb.id) })
                            }
                            if (suggestions.isNotEmpty()) {
                                item(key = "suggestions-header") {
                                    Text(
                                        text = suggestionsTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                    )
                                }
                                items(suggestions, key = { "s-${it.id}" }) { herb ->
                                    HerbCard(herb, herb.id in favoriteIds, { onHerbClick(herb) }, { onToggleFavorite(herb.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
