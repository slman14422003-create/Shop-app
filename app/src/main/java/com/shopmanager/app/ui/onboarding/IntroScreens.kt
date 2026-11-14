package com.shopmanager.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.onboarding.IntroFeature
import com.shopmanager.app.data.onboarding.IntroPage
import com.shopmanager.app.data.onboarding.ReleaseNotes
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.theme.LocalBrandGradientColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val Ease = FastOutSlowInEasing

/** يظهر عنصره بتلاشٍ وانزلاق خفيف بعد [delayMs]، ويتكيّف مع الأجهزة الضعيفة. */
@Composable
private fun Modifier.staggerIn(key: Any, delayMs: Int): Modifier {
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    val p = remember(key) { Animatable(if (low) 1f else 0f) }
    LaunchedEffect(key) {
        if (!low) {
            delay(delayMs.toLong())
            p.animateTo(1f, tween(520, easing = Ease))
        }
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 28.dp.toPx()
    }
}

@Composable
private fun brandPair(): Pair<Color, Color> {
    val colors = LocalBrandGradientColors.current
    return colors.first() to colors.last()
}

@Composable
private fun IntroBackground(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (a, b) = brandPair()
    Box(
        Modifier
            .fillMaxSize()
            .background(cs.surface)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(a.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.minDimension * 1.0f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(b.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height),
                        radius = size.minDimension * 0.9f
                    )
                )
            }
    ) { content() }
}

/** الأيقونة الكبيرة: هالة متدرجة + نبض خفيف مستمر (متوقف على الأجهزة الضعيفة). */
@Composable
private fun HeroIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, pageKey: Any, size: androidx.compose.ui.unit.Dp = 148.dp) {
    val cs = MaterialTheme.colorScheme
    val (a, b) = brandPair()
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    val pop = remember(pageKey) { Animatable(if (low) 1f else 0.6f) }
    LaunchedEffect(pageKey) {
        if (!low) pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
    }
    val pulse = if (low) null else rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseValue"
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f)
            }
            .drawBehind {
                pulse?.let {
                    val t = it.value
                    drawCircle(a.copy(alpha = 0.22f * (1f - t)), radius = this.size.minDimension / 2f * (1f + 0.45f * t))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(a.copy(alpha = 0.20f), b.copy(alpha = 0.06f))))
                .border(1.dp, a.copy(alpha = 0.25f), CircleShape)
        )
        Box(
            Modifier
                .size(size * 0.68f)
                .shadow(16.dp, RoundedCornerShape(size * 0.2f), ambientColor = a, spotColor = a)
                .clip(RoundedCornerShape(size * 0.2f))
                .background(Brush.linearGradient(listOf(a, b))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.34f))
        }
    }
}

/**
 * شرح التطبيق لأول تثبيت: صفحات تُسحب أفقياً، كل صفحة أيقونة + عنوان + شرح + نقاط
 * تظهر بالتتابع. زر "تخطي" في الأعلى، ومؤشر صفحات متحرك، وزر "التالي / ابدأ".
 */
@Composable
fun WelcomeIntroScreen(
    pages: List<IntroPage>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (a, b) = brandPair()
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    IntroBackground {
        Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${pager.currentPage + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant
                )
                if (!last) {
                    Text(
                        "تخطي",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onFinish)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant
                    )
                } else Spacer(Modifier.height(36.dp))
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - offset.coerceIn(0f, 1f) * 0.6f
                            translationX = 0f
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.height(12.dp))
                    HeroIcon(page.icon, pageKey = index)
                    Spacer(Modifier.height(36.dp))
                    Text(
                        page.title,
                        modifier = Modifier.staggerIn(index to "t", 120),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp),
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        page.body,
                        modifier = Modifier.staggerIn(index to "b", 260),
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    page.points.forEachIndexed { i, point ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .staggerIn(index to "p$i", 420 + i * 140)
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(cs.surfaceContainerHigh)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(a.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = a, modifier = Modifier.size(15.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                point,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = cs.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageDots(count = pages.size, current = pager.currentPage)
                Spacer(Modifier.height(18.dp))
                PrimaryPill(
                    text = if (last) "ابدأ الآن" else "التالي",
                    onClick = {
                        if (last) onFinish()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                )
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val cs = MaterialTheme.colorScheme
    val (a, _) = brandPair()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 26.dp else 8.dp, tween(300, easing = Ease), label = "dotW")
            Box(
                Modifier
                    .height(8.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(if (i == current) a else cs.onSurface.copy(alpha = 0.15f))
            )
        }
    }
}

@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit) {
    val (a, b) = brandPair()
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(10.dp, CircleShape, ambientColor = a, spotColor = a)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(b, a)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * ملاحظات التحديث: رأس بشارة "جديد"، ثم بطاقات الميزات تدخل واحدة تلو الأخرى
 * مع شرح لكل ميزة، وزر "حسناً" في الأسفل. عند وجود أكثر من إصدار لم يُشاهد
 * تُجمع ميزاتها كلها بالترتيب من الأحدث.
 */
@Composable
fun WhatsNewScreen(
    releases: List<ReleaseNotes>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (a, b) = brandPair()
    val sorted = remember(releases) { releases.sortedByDescending { it.id } }
    val features: List<IntroFeature> = remember(sorted) { sorted.flatMap { it.features } }
    val head = sorted.firstOrNull()

    IntroBackground {
        Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(36.dp))
                HeroIcon(Icons.Default.AutoAwesome, pageKey = "new", size = 120.dp)
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = CircleShape,
                    color = a.copy(alpha = 0.16f),
                    modifier = Modifier.staggerIn("badge", 100)
                ) {
                    Text(
                        "جديد",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = a
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    head?.title ?: "ما الجديد",
                    modifier = Modifier.staggerIn("title", 200),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp),
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center
                )
                if (!head?.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        head!!.subtitle,
                        modifier = Modifier.staggerIn("sub", 300),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(26.dp))
                features.forEachIndexed { i, f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .staggerIn("f$i", 450 + i * 170)
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(a, b))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(f.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                f.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).staggerIn("btn", 450 + features.size * 170 + 100)) {
                PrimaryPill(text = "حسناً، فهمت", onClick = onDone)
            }
        }
    }
}
