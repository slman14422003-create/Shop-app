package com.shopmanager.app.ui.common

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.shopmanager.app.ui.common.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.LocalSemanticColors
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.collectDelayedPressedAsState
import com.shopmanager.app.data.performance.glowBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The floating pill's actual on-screen height (including its own top/
 * bottom margins), in dp — 0.dp when it isn't showing at all.
 *
 * WHY THIS EXISTS: once [FloatingBottomNav] became a true overlay drawn on
 * top of the page (see MainActivity) instead of a Scaffold `bottomBar`
 * slot, screens underneath stopped automatically getting bottom clearance
 * for it — Scaffold used to hand that clearance out for free via its
 * `padding`. Anything a screen anchors to its own bottom edge (a
 * FloatingActionButton, a list's last row) now needs to know how tall the
 * pill floating on top of it actually is, so it can pad itself clear of it
 * instead of being covered.
 *
 * Deliberately measured at runtime (via `Modifier.onSizeChanged` on the
 * real composable in MainActivity) and threaded down through this
 * CompositionLocal, rather than hard-coded as a fixed dp guess: the pill's
 * true height depends on the device's gesture/navigation-bar inset (which
 * varies by device/OS) plus its own content padding — a guessed constant
 * would drift out of sync the moment either changes and quietly reopen
 * this exact bug. Screens that read it should still add their own small
 * extra gap on top (see DebtsScreen/MaterialsScreen) so content doesn't
 * sit flush against the pill.
 */
val LocalFloatingBottomNavHeight = compositionLocalOf { 0.dp }

/**
 * BUG FIXED ("رسالة تم الحذف/السداد تظهر تحت الشريط السفلي"): every
 * screen's own `Scaffold(snackbarHost = { SnackbarHost(snackbarHost) })`
 * places that host at the true bottom edge of the screen's content — the
 * same edge [FloatingBottomNav] floats over. Since the nav pill is drawn
 * *after* (see MainActivity's Box: NavHost first, [FloatingBottomNav]
 * layered on top of it), it visually sits in front of a plain
 * `SnackbarHost`, hiding the exact "تم الحذف"/"تم السداد" confirmation
 * the person needs to see right after deleting/settling something. Every
 * screen with a snackbar (Debts/PersonDetail/Materials/MaterialCatalog/
 * Notes) should call this instead of `SnackbarHost` directly: it pads the
 * host up by [LocalFloatingBottomNavHeight] (plus a small extra gap) so
 * it always lands above the floating pill — and by exactly 0.dp extra
 * when the pill isn't showing, so nothing shifts on screens/states where
 * there's no pill to clear.
 *
 * REDESIGN ("مربع حوار تم حذف المادة او الدين بكل التطبيق بدو اعادة تنسيق
 * بشكل كامل"): every one of those "تم حذف المادة"/"تم حذف الدين" (and every
 * other "تم ..."/"خطأ ...") confirmation across the app renders through
 * this single shared host, so restyling it here is what restyles all of
 * them at once. It used to fall through to the plain default Material
 * `Snackbar` — a dark, low-contrast bar that didn't match the rest of the
 * app's own flat "glass" card language (see GlassAlertDialog, GlassCard).
 * Now it renders as [GlassSnackbar]: the same opaque, rounded, bordered
 * surface used everywhere else in the app, with a status icon (a green
 * check for a "تم ..." success message, a red outline for a "خطأ ..."
 * failure one) so the result of a delete/edit/add reads at a glance
 * instead of just as an unstyled line of text.
 */
@Composable
fun GlassSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    // مدة قصيرة موحّدة لكل رسائل التطبيق (حذف/إضافة/تعديل…): الرسالة العادية ثانية ونصف،
    // ومع زر "تراجع" ثانيتان ونصف (تكفي للضغط)، والخطأ ثلاث ثوانٍ. تُغلق قبل مهلة Material الطويلة
    // (4s/10s) فلا تبقى فوق القائمة وتزعج.
    val current = hostState.currentSnackbarData
    LaunchedEffect(current) {
        current?.let { d ->
            val ms = when {
                d.visuals.message.startsWith("خطأ") -> 3000L
                d.visuals.actionLabel != null -> 2500L
                else -> 1500L
            }
            kotlinx.coroutines.delay(ms)
            d.dismiss()
        }
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = LocalFloatingBottomNavHeight.current + 8.dp)
    ) { data -> GlassSnackbar(data) }
}

/**
 * نافذة نتيجة مصغّرة: كبسولة بعرض المحتوى فقط (لا تمتد على عرض الشاشة)، ارتفاع ~40dp، في منتصف
 * الأسفل فوق الشريط السفلي — أقرب موضع للإبهام وأبعد عن عناصر القائمة وحقل البحث. الضغط عليها يغلقها فوراً.
 */
@Composable
private fun GlassSnackbar(data: SnackbarData) {
    val isError = data.visuals.message.startsWith("خطأ")
    val semantic = LocalSemanticColors.current
    val accent = if (isError) semantic.danger else semantic.success

    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = { data.dismiss() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 0.dp
        ) {
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .padding(start = 14.dp, end = if (data.visuals.actionLabel != null) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isError) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                } else {
                    AnimatedCheckmark(size = 18.dp, color = accent)
                }
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                data.visuals.actionLabel?.let { label ->
                    TextButton(
                        onClick = { data.performAction() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Describes a floating circular action (quick-add, save). Kept as data so the bar can render every
 * one of them with the same glass-circle look.
 */
data class QuickAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

// ───────────────────────── مساعدات الشريط السفلي ─────────────────────────

/**
 * إخفاء الشريط عند التمرير لأسفل وإظهاره عند التمرير لأعلى (HIGH فقط، يُفرض في [AppBottomBar]).
 * يُمرَّر [connection] إلى `Modifier.nestedScroll` على حاوية الـ NavHost. الإخفاء transform فقط،
 * فلا يتغير [LocalFloatingBottomNavHeight] ولا تقفز التخطيطات.
 */
@Stable
class BottomBarScrollState {
    var hidden by mutableStateOf(false)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < -SCROLL_THRESHOLD_PX) hidden = true else if (dy > SCROLL_THRESHOLD_PX) hidden = false
            return Offset.Zero
        }
    }

    private companion object {
        const val SCROLL_THRESHOLD_PX = 6f
    }
}

@Composable
fun rememberBottomBarScrollState(): BottomBarScrollState = remember { BottomBarScrollState() }

/** سجل "اضغط التبويب المحدد للعودة لأعلى القائمة": كل صفحة تسجّل دالتها عبر [RegisterScrollToTop]. */
@Stable
class TabScrollToTop {
    private val handlers = mutableMapOf<Int, () -> Unit>()
    fun register(page: Int, action: () -> Unit) { handlers[page] = action }
    fun unregister(page: Int) { handlers.remove(page) }
    fun scroll(page: Int) { handlers[page]?.invoke() }
}

val LocalTabScrollToTop = staticCompositionLocalOf<TabScrollToTop?> { null }

@Composable
fun RegisterScrollToTop(page: Int, state: LazyListState) {
    val registry = LocalTabScrollToTop.current ?: return
    val scope = rememberCoroutineScope()
    DisposableEffect(registry, page, state) {
        registry.register(page) { scope.launch { state.animateScrollToItem(0) } }
        onDispose { registry.unregister(page) }
    }
}

// ───────────────────────── الشريط السفلي بأسلوب iPhone ─────────────────────────

data class BottomNavTab(
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
    val page: Int
)

private val BottomNavTabs = listOf(
    BottomNavTab(Icons.Outlined.Home, Icons.Filled.Home, "الرئيسية", 0),
    BottomNavTab(Icons.Outlined.AttachMoney, Icons.Filled.AttachMoney, "الديون", 1),
    BottomNavTab(Icons.Outlined.Inventory2, Icons.Filled.Inventory2, "المواد", 2),
    BottomNavTab(Icons.Outlined.Forum, Icons.Filled.Forum, "الدردشة", 3)
)

private val BarHeight = 64.dp
private val BarHairline = 0.5.dp
private val BadgeRed = Color(0xFFFF3B30)

/**
 * شريط تنقل بأسلوب iOS:
 * - تبويبات متساوية العرض، أيقونة فوق تسمية 10sp؛ Outlined عند عدم التحديد وFilled عند التحديد.
 * - "عدسة" زجاجية منزلقة خلف التبويب المحدد (graphicsLayer translationX، تراعي RTL): نابض في HIGH
 *   وtween 150ms في LOW. السحب الأفقي على الشريط يبدّل التبويب والعدسة تتبع الإصبع.
 * - خلفية ضبابية عبر [BlurBackdrop] حين يكون [blurEnabled]، وإلا surfaceContainerHigh معتم؛ حد 0.5dp بلا ظل.
 * - أيقونة تتصغر 0.88 عند الضغط وتكبر 1.06 عند التحديد (HIGH فقط)، واهتزاز عند تغيّر التبويب.
 * - شارات حمراء ([badges]: رقم الصفحة ← العدد).
 * - الضغط على التبويب المحدد ينادي [onReselect] (للعودة لأعلى القائمة).
 * - [quickAction] زر زجاجي دائري بجانب الكبسولة وبنفس ارتفاعها.
 * - [hidden]: ينزلق الشريط خارج الشاشة (HIGH فقط) دون تغيير حجمه في التخطيط.
 */
@Composable
fun AppBottomBar(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Int, Int> = emptyMap(),
    quickAction: QuickAction? = null,
    onReselect: (Int) -> Unit = {},
    hidden: Boolean = false
) {
    val tier = LocalPerformanceTier.current
    val high = tier == PerformanceTier.HIGH
    val hide = animateFloatAsState(
        targetValue = if (hidden && high) 1f else 0f,
        animationSpec = MotionSpecs.contentTween(),
        label = "bottomBarHide"
    )
    var lastQuick by remember { mutableStateOf<QuickAction?>(null) }
    LaunchedEffect(quickAction) { if (quickAction != null) lastQuick = quickAction }

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                // يُقرأ في مرحلة الرسم فقط؛ يزيح الشريط بحجمه + هامش النظام السفلي.
                translationY = (size.height + 24.dp.toPx()) * hide.value
                alpha = 1f - 0.4f * hide.value
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BottomCapsule(
            selectedPage = selectedPage,
            onSelectPage = onSelectPage,
            onReselect = onReselect,
            badges = badges,
            modifier = Modifier.weight(1f)
        )
        AnimatedVisibility(
            visible = quickAction != null,
            enter = MotionSpecs.popEnter(),
            exit = MotionSpecs.popExit()
        ) {
            lastQuick?.let { GlassCircleButton(it) }
        }
    }
}

@Composable
private fun GlassBackground(shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.clip(shape)) {
        if (blurEnabled()) {
            BlurBackdrop(Modifier.matchParentSize(), tint = cs.surfaceContainerHigh.copy(alpha = 0.55f))
        } else {
            Box(Modifier.matchParentSize().background(cs.surfaceContainerHigh))
        }
    }
}

@Composable
private fun BottomCapsule(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    onReselect: (Int) -> Unit,
    badges: Map<Int, Int>,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val accent = appAccent()
    val high = LocalPerformanceTier.current == PerformanceTier.HIGH
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dirSign = if (rtl) -1f else 1f
    val count = BottomNavTabs.size
    val shape = RoundedCornerShape(BarHeight / 2)
    val selectedIndex = BottomNavTabs.indexOfFirst { it.page == selectedPage }.coerceAtLeast(0)

    // موضع العدسة كفهرس كسري (0..count-1): يتبع الإصبع أثناء السحب، وينزلق للتبويب المحدد بنابض/tween.
    val lens = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val lensSpec = MotionSpecs.lensSpring()
    LaunchedEffect(selectedIndex, dragging) {
        if (!dragging) lens.animateTo(selectedIndex.toFloat(), lensSpec)
    }

    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelectPage)

    BoxWithConstraints(
        modifier
            .height(BarHeight)
            .border(BarHairline, cs.outlineVariant, shape)
    ) {
        val tabWidth = maxWidth / count
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val tabWidthPx = widthPx / count

        GlassBackground(shape, Modifier.matchParentSize())

        // العدسة الزجاجية خلف التبويب المحدد.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .width(tabWidth - 8.dp)
                .fillMaxHeight()
                .graphicsLayer { translationX = lens.value * tabWidthPx * dirSign }
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.20f), accent.copy(alpha = 0.16f))
                    )
                )
                .border(BarHairline, accent.copy(alpha = 0.35f), CircleShape)
        )

        Row(
            Modifier
                .matchParentSize()
                .pointerInput(count, tabWidthPx, rtl) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val target = lens.value.roundToInt().coerceIn(0, count - 1)
                            dragging = false
                            if (target != currentSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(BottomNavTabs[target].page)
                            }
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val x = change.position.x.coerceIn(0f, size.width.toFloat())
                            val fromStart = if (rtl) size.width - x else x
                            val fraction = (fromStart / tabWidthPx - 0.5f).coerceIn(0f, (count - 1).toFloat())
                            val before = lens.value.roundToInt()
                            scope.launch { lens.snapTo(fraction) }
                            if (fraction.roundToInt() != before) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                },
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTabs.forEach { tab ->
                val selected = selectedPage == tab.page
                BottomNavItem(
                    tab = tab,
                    selected = selected,
                    badge = badges[tab.page] ?: 0,
                    onClick = {
                        if (selected) {
                            onReselect(tab.page)
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectPage(tab.page)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: BottomNavTab,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val high = LocalPerformanceTier.current == PerformanceTier.HIGH
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconScale = animateFloatAsState(
        targetValue = when {
            !high -> 1f
            pressed -> 0.88f
            selected -> 1.06f
            else -> 1f
        },
        animationSpec = MotionSpecs.quickSpring(),
        label = "bottomNavIconScale"
    )
    val tint = if (selected) appAccent() else cs.onSurfaceVariant

    Box(
        modifier
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = if (badge > 0) "${tab.label}، $badge" else tab.label
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value }) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                if (badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (if (rtl) -9 else 9).dp, y = (-6).dp)
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .clip(CircleShape)
                            .background(BadgeRed)
                            .border(1.dp, cs.surfaceContainerHigh, CircleShape)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (badge > 99) "99+" else badge.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                tab.label,
                color = tint,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** زر الإضافة السريع: دائرة زجاجية بجانب الكبسولة وبنفس ارتفاعها، يحلّ محل الزر العائم في الزاوية. */
@Composable
private fun GlassCircleButton(action: QuickAction) {
    val cs = MaterialTheme.colorScheme
    val high = LocalPerformanceTier.current == PerformanceTier.HIGH
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed && high) 0.9f else 1f,
        animationSpec = MotionSpecs.quickSpring(),
        label = "bottomQuickScale"
    )
    Box(
        Modifier
            .size(BarHeight)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .border(BarHairline, cs.outlineVariant, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = action.contentDescription,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    action.onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassBackground(CircleShape, Modifier.matchParentSize())
        Icon(
            action.icon,
            contentDescription = action.contentDescription,
            tint = appAccent(),
            modifier = Modifier.size(26.dp)
        )
    }
}
