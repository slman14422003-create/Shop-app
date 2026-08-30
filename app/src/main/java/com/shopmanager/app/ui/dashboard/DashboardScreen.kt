package com.shopmanager.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.security.PinAttemptThrottle
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.GradientIconButton
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.WarningAmber as WarningAmberColor
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ActivityRow(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val timestamp: Long
)

/** لوحة المسؤول السرية: fixed PIN gate for the tiny hidden admin button on
 * this screen's header (see DashboardHeader/AdminAccessDot below). Not
 * related to the app-lock PIN in Settings — that one is user-chosen and
 * protects the whole app; this one is a fixed developer password that
 * only unlocks the developer/admin panel. Change this constant if the
 * password ever needs to rotate — it's the single place it's defined. */
private const val ADMIN_PANEL_PASSWORD = "1442"

private fun timeBasedGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "سهرانين لهلق؟ 🌙"
        hour < 12 -> "صباح الخير ☀️"
        hour < 17 -> "أهلاً بك 👋"
        hour < 21 -> "مساء الخير 🌇"
        else -> "مساء النور 🌙"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    debtsViewModel: DebtsViewModel,
    materialsViewModel: MaterialsViewModel,
    onOpenSettings: () -> Unit,
    onNavigateToDebts: () -> Unit = {},
    onNavigateToMaterials: () -> Unit = {},
    onOpenAdmin: () -> Unit = {}
) {
    var showAdminPinDialog by remember { mutableStateOf(false) }
    // SECURITY FIX: "1442" is a fixed 4-digit password with no attempt
    // limit at all previously — trivially brute-forceable (10,000 tries
    // max, no delay) directly from this dialog's keypad. Same escalating
    // lockout as the main app-lock PIN now, via the shared throttle — see
    // PinAttemptThrottle and AdminPinDialog below.
    val context = LocalContext.current
    val adminThrottle = remember { PinAttemptThrottle(context, "shop_manager_admin_throttle") }
    val debtsState by debtsViewModel.uiState.collectAsState()
    val materialsState by materialsViewModel.uiState.collectAsState()
    val debtsRefreshing by debtsViewModel.isRefreshing.collectAsState()
    val materialsRefreshing by materialsViewModel.isRefreshing.collectAsState()
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    // 12-hour clock (was HH:mm/24h) — "a" renders as ص/م in Arabic locale.
    val df = remember { SimpleDateFormat("d MMM، h:mm a", Locale("ar")) }

    // Every material in the list is, by definition, a shortage the shop
    // needs to buy - it's a live shopping list, not a stock count.
    val shortages = remember(materialsState.materials) { materialsState.materials }
    val topDebtors = remember(debtsState.persons) {
        debtsState.persons.sortedByDescending { it.amount }.take(5)
    }
    val isLoading = debtsState.isLoading || materialsState.isLoading

    val recentActivity = remember(debtsState.debts, debtsState.persons, materialsState.materials) {
        val personsById = debtsState.persons.associateBy { it.id }
        val debtRows = debtsState.debts.map { debt ->
            val personName = personsById[debt.personId]?.name ?: "عميل"
            ActivityRow(
                icon = Icons.Default.AttachMoney,
                color = avatarColorFor(personName),
                title = personName,
                subtitle = "دين جديد: ${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}",
                timestamp = debt.createdAt
            )
        }
        val materialRows = materialsState.materials.map { m ->
            ActivityRow(
                icon = Icons.Default.Spa,
                color = avatarColorFor(m.name),
                title = m.name,
                subtitle = "نقص مضاف: ${m.quantityLabel()}",
                timestamp = m.updatedAt
            )
        }
        (debtRows + materialRows).sortedByDescending { it.timestamp }.take(6)
    }

    Scaffold(
        // Edge-to-edge: the status bar is transparent (see
        // SetSystemBarsColor/MainActivity) and DashboardHeader below draws
        // its own liquid-glass panel all the way up to the true top of the
        // window, padding its *content* down manually — so this Scaffold
        // must not reserve top space itself, or the header would be pushed
        // down a second time, leaving a plain gap above it instead of one
        // continuous glass surface. Bottom/horizontal safe-area insets
        // (gesture nav bar, cutouts) are kept as-is.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = debtsRefreshing || materialsRefreshing,
            onRefresh = { debtsViewModel.refresh(); materialsViewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
        // BUG FIXED: same as the other two pager tabs — the floating nav
        // pill floats over this screen too, so its last card needs to
        // clear the pill's real measured height, not just a flat 16dp.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp + LocalFloatingBottomNavHeight.current),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DashboardHeader(
                    onOpenSettings = onOpenSettings,
                    onAdminTap = { showAdminPinDialog = true }
                )
            }

            item {
                QuickActionsRow(
                    onAddPerson = onNavigateToDebts,
                    onAddMaterial = onNavigateToMaterials
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AttachMoney,
                        accentColor = MaterialTheme.colorScheme.primary,
                        title = "إجمالي الديون",
                        valueContent = {
                            AnimatedCounterText(
                                targetValue = debtsState.totalAmount,
                                format = { "${nf.format(it)} ${AppSettingsState.currencySymbol}" },
                                animate = !debtsState.isLoading
                            )
                        },
                        subtitle = "${debtsState.totalPersons} عميل"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Inventory2,
                        accentColor = if (shortages.isNotEmpty()) WarningAmberColor else MaterialTheme.colorScheme.secondary,
                        title = "قائمة النواقص",
                        valueContent = {
                            AnimatedCounterText(
                                targetValue = shortages.size.toDouble(),
                                format = { it.toInt().toString() },
                                animate = !materialsState.isLoading
                            )
                        },
                        subtitle = if (shortages.isNotEmpty()) "بانتظار الشراء" else "لا يوجد نواقص"
                    )
                }
            }

            if (isLoading && debtsState.persons.isEmpty() && materialsState.materials.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            }

            if (shortages.isNotEmpty()) {
                item {
                    SectionCard(title = "🛒 قائمة مشتريات السوق", color = WarningAmberColor) {
                        shortages.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m.name)
                                Text(m.quantityLabel(), color = WarningAmberColor, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (recentActivity.isNotEmpty()) {
                item {
                    SectionCard(title = "🕓 آخر النشاطات", icon = Icons.Default.History) {
                        recentActivity.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(MaterialTheme.shapes.small).background(row.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(row.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        row.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                // BUG FIXED: this timestamp had no fixed
                                // width and no line/overflow limit, so a
                                // long title next to it (see row.title
                                // above) used to squeeze it and could wrap
                                // the time/date onto two lines, breaking
                                // this row's height versus every other row.
                                if (row.timestamp > 0) {
                                    Text(
                                        df.format(Date(row.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (topDebtors.isNotEmpty()) {
                item {
                    SectionCard(title = "أكبر الديون") {
                        topDebtors.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(28.dp).clip(MaterialTheme.shapes.small).background(avatarColorFor(p.name)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        p.name.firstOrNull()?.uppercase() ?: "?",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    p.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    "${nf.format(p.amount)} ${AppSettingsState.currencySymbol}",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (debtsState.persons.isEmpty() && materialsState.materials.isEmpty() && !debtsState.isLoading && !materialsState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Storefront, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "ابدأ بإضافة عملاء أو مواد من التبويبات بالأسفل",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showAdminPinDialog) {
        AdminPinDialog(
            throttle = adminThrottle,
            onDismiss = { showAdminPinDialog = false },
            onSubmit = { entered ->
                if (adminThrottle.isLocked()) {
                    false
                } else if (entered == ADMIN_PANEL_PASSWORD) {
                    adminThrottle.registerSuccess()
                    showAdminPinDialog = false
                    onOpenAdmin()
                    true
                } else {
                    adminThrottle.registerFailure()
                    false
                }
            }
        )
    }
}

/**
 * Large-title header, iOS-style: a bold oversized title with a small
 * secondary greeting above it, sitting on a flat brand-gradient panel with
 * softly rounded bottom corners instead of a hard-edged bar. The settings
 * affordance is a solid opaque circular button ([GradientIconButton]) — no
 * translucency/blur — which is what was reading as dated before (a plain
 * unstyled gear glyph floating directly on the gradient with no shape of
 * its own).
 */
@Composable
private fun DashboardHeader(onOpenSettings: () -> Unit, onAdminTap: () -> Unit = {}) {
    val greeting = remember { timeBasedGreeting() }
    Box(
        Modifier
            .fillMaxWidth()
            // "زجاج سائل بشكل مذهل": this is the single most-seen surface
            // in the app (the very first thing drawn every time it opens),
            // so it's the one header that opts into the extra animated
            // sheen sweep on top of the shared drift highlight every glass
            // panel already has — see liquidGlassSurface's `sheen` param.
            .liquidGlassSurface(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            // The glass panel (background/border above) already fills this
            // Box's full bounds, which now extend up behind the
            // transparent status bar; this only pushes the *content*
            // (greeting/title/settings button) down far enough to clear
            // the status bar icons, so it reads as one continuous glass
            // surface from the true top of the screen instead of a seam
            // between the system bar and the header.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    color = BrandOnGradient.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "إدارة المحل",
                    color = BrandOnGradient,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp, lineHeight = 33.sp),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            GradientIconButton(icon = Icons.Rounded.Settings, contentDescription = "الإعدادات", onClick = onOpenSettings)
            // BUG FIXED: this was only 10.dp, which reads as glued/touching
            // once IconButton's own minimum-touch-target sizing is taken
            // into account — the two glass circles visually met with no
            // gap. Widened to a clearly organized gap between the two
            // header buttons.
            Spacer(Modifier.width(18.dp))
            // زر لوحة المسؤول: بزر زجاجي حقيقي وواضح بجانب زر الإعدادات
            // بمسافة كافية بينهما — مو نقطة مخفية بزاوية الهيدر متل قبل.
            // نفس منطق فتح صندوق رمز الدخول (onAdminTap) ما تغيّر، بس صار
            // الزر يشوفه أي مستخدم عادي.
            GradientIconButton(
                icon = Icons.Rounded.AdminPanelSettings,
                contentDescription = "لوحة المسؤول",
                onClick = onAdminTap
            )
        }
    }
}

@Composable
private fun AdminPinDialog(
    throttle: com.shopmanager.app.data.security.PinAttemptThrottle,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockRemaining by remember { mutableLongStateOf(throttle.lockRemainingSeconds()) }
    val isLocked = lockRemaining > 0

    LaunchedEffect(isLocked) {
        while (lockRemaining > 0) {
            kotlinx.coroutines.delay(1000)
            lockRemaining = throttle.lockRemainingSeconds()
        }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دخول لوحة المطوّر") },
        text = {
            Column {
                AppTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = false },
                    label = "كلمة المرور",
                    singleLine = true,
                    enabled = !isLocked,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error
                )
                if (isLocked) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "محاولات كثيرة خاطئة — حاول بعد $lockRemaining ثانية",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "كلمة المرور غير صحيحة",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isLocked, onClick = {
                if (!onSubmit(pin)) {
                    error = true
                    lockRemaining = throttle.lockRemainingSeconds()
                }
            }) { Text("دخول") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun QuickActionsRow(onAddPerson: () -> Unit, onAddMaterial: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PersonAdd,
            label = "عميل جديد",
            onClick = onAddPerson
        )
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Inventory2,
            label = "مادة جديدة",
            onClick = onAddMaterial
        )
    }
}

// REDESIGN ("جمال + أداء"): QuickActionButton used to be a flat
// OutlinedButton — same 1dp border regardless of accent, icon and label
// packed tight with no breathing room, and no depth of its own (it only
// registered as "a button" from its border). Rebuilt as a self-contained
// tonal card with its own soft accent-colored icon badge (mirrors
// StatCard's badge language below, so the two feel like one family) and a
// two-line layout so the label gets its own row instead of squeezing next
// to the icon. Still a single `background()` + `border()` — no extra
// graphicsLayer/blur — so this costs nothing extra on LOW tier versus the
// old OutlinedButton.
@Composable
private fun QuickActionButton(modifier: Modifier = Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "quickActionScale"
    )
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, accent.copy(alpha = 0.16f), MaterialTheme.shapes.large)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// REDESIGN: StatCard's icon badge and value used to share the same visual
// weight as everything else on the card (a small tinted square, then plain
// text) — the number itself, which is the whole point of the card, didn't
// stand out from its own label/subtitle. The badge is now a circle (matches
// QuickActionButton's badge language above) and sits beside a slim
// accent-colored vertical rule instead of stacked above the text, so the
// eye reads accent → number in one line instead of scanning top-to-bottom
// through three same-weight rows. No new animated/blurred layers, so this
// is exactly as cheap on LOW tier as the previous version.
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accentColor: Color,
    title: String,
    valueContent: @Composable () -> Unit,
    subtitle: String
) {
    // iOS 26 REDESIGN: flat card — a plain surface with a hairline border
    // instead of a drop-shadowed ElevatedCard. Real iOS cards read as
    // "grouped" via a subtle 1dp separator, not a floating shadow.
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(accentColor.copy(alpha = 0.55f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(10.dp))
                valueContent()
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
