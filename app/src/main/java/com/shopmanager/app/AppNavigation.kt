package com.shopmanager.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier

/**
 * STABILITY FIX: a fast double-tap on a back arrow (or back arrow + system
 * back together) used to call popBackStack() twice. The second call popped
 * the start destination (the main pager) off the stack and left the NavHost
 * empty - a blank screen with no way out but killing the app. This only pops
 * while there is still a screen below the current one.
 */
internal fun androidx.navigation.NavController.safePopBackStack(force: Boolean = false) {
    if (previousBackStackEntry == null) return
    // FIX: ضغطتان سريعتان على سهم الرجوع أثناء انتقال الرجوع نفسه كانتا تسحبان شاشتين معاً في مكدس
    // أعمق من اثنتين (إعدادات ← حول ← الخصوصية) وتقطعان الحركة. الآن لا نسحب إلا والشاشة الحالية
    // مستقرة (RESUMED). force=true فقط للخروج التلقائي من شاشة فارغة (عميل محذوف) قد لا تكون استقرت بعد.
    if (!force) {
        val state = currentBackStackEntry?.lifecycle?.currentState
        if (state != null && state != androidx.lifecycle.Lifecycle.State.RESUMED) return
    }
    popBackStack()
}

// حاجز التنقل المزدوج (مأخوذ من تطبيق الملاحظات: SmoothActivity.isDuplicateNavigation): ضغطتان
// سريعتان على نفس الصف/الزر كانتا تدفعان الشاشة مرتين فتتراكم نسختان وينقطع الانتقال. الآن تُتجاهل
// الثانية إن جاءت خلال 550ms لنفس الوجهة أو أثناء تشغيل انتقال سابق (المدخل الحالي ليس RESUMED).
internal var lastNavRoute = ""

internal var lastNavAt = 0L

internal fun androidx.navigation.NavController.safeNavigate(route: String) {
    val now = android.os.SystemClock.uptimeMillis()
    if (route == lastNavRoute && now - lastNavAt < 550L) return
    val state = currentBackStackEntry?.lifecycle?.currentState
    if (state != null && state != androidx.lifecycle.Lifecycle.State.RESUMED) return
    lastNavRoute = route
    lastNavAt = now
    navigate(route)
}

internal fun navigateTopLevel(navController: androidx.navigation.NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * بديل لـ composable(): نفس السلوك، لكن تُرسم فوق المحتوى طبقة تعتيم خفيفة ([NavCoverScrim]) حين
 * تُغطّى هذه الشاشة بشاشة أخرى. هكذا تحصل الشاشة المتراجعة على عمق بصري أوضح (إضافة إلى
 * الانزلاق والتصغير) بلا alpha على الشجرة كلها.
 */
internal fun NavGraphBuilder.screen(
    navController: NavController,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(route, arguments = arguments) { entry ->
        Box(Modifier.fillMaxSize()) {
            content(entry)
            NavCoverScrim(entry, navController)
        }
    }
}

/**
 * تعتيم يصل إلى [MotionSpecs.navScrimAlpha] حين تكون هذه الشاشة ليست العليا في المكدس.
 * الدخول بمدة الدفع والخروج بمدة الرجوع، والقيمة تُقرأ داخل drawBehind فقط (بلا إعادة تركيب).
 * HIGH فقط؛ في LOW لا طبقة ولا حركة. لا يستهلك اللمس (الرسم فقط).
 */
@Composable
internal fun NavCoverScrim(entry: NavBackStackEntry, navController: NavController) {
    if (LocalPerformanceTier.current != PerformanceTier.HIGH || MotionSpecs.reduceMotion()) return
    val top by navController.currentBackStackEntryAsState()
    val covered = top != null && top?.id != entry.id
    val pushMs = MotionSpecs.navPushMillis()
    val popMs = MotionSpecs.navPopMillis()
    val scrim = MaterialTheme.colorScheme.scrim
    val alpha = animateFloatAsState(
        targetValue = if (covered) MotionSpecs.navScrimAlpha else 0f,
        animationSpec = tween(if (covered) pushMs else popMs, easing = MotionSpecs.navEasing),
        label = "navCoverScrim"
    )
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val a = alpha.value
                if (a > 0f) drawRect(scrim.copy(alpha = a))
            }
    )
}
