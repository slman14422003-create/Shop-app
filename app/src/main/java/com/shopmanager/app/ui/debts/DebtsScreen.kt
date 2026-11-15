package com.shopmanager.app.ui.debts

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.shopmanager.app.ui.common.AppBorderWidth
import com.shopmanager.app.ui.common.AppCaptionLargeSize
import com.shopmanager.app.ui.common.AppCaptionSize
import com.shopmanager.app.ui.common.AppCardRadius
import com.shopmanager.app.ui.common.AppAmountRowSize
import com.shopmanager.app.ui.common.AppBodyLargeSize
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppPillChip
import com.shopmanager.app.ui.common.PhoneUtils
import com.shopmanager.app.ui.common.appAccent
import com.shopmanager.app.ui.common.appAmountStyle
import com.shopmanager.app.ui.common.appBorderColor
import com.shopmanager.app.ui.common.appOnAccent
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.settings.SettingsRepository
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.util.DateFormats
import java.util.Locale
import com.shopmanager.app.ui.common.AppSearchBar
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.showWithOptionalUndo
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.ShareFormatDialog
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.theme.LocalBrandGradientColors
import com.shopmanager.app.ui.theme.LocalSemanticColors
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppStat
import com.shopmanager.app.ui.common.AppStatsStrip
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.shopmanager.app.ui.common.ActionSpec
import com.shopmanager.app.ui.common.AppActionPill
import androidx.compose.material.icons.filled.Close
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.listItemEntrance


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DebtsScreen(
    onOpenPerson: (String) -> Unit,
    viewModel: DebtsViewModel = viewModel(),
    addPersonRequested: Boolean = false,
    onAddPersonRequestHandled: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenActivityLog: () -> Unit = {},
    onNewDebt: () -> Unit = {}
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val message = viewModel.message.collectAsStateWithLifecycle().value
    val isRefreshing = viewModel.isRefreshing.collectAsStateWithLifecycle().value
    val search = remember { mutableStateOf("") }
    // REDESIGN ("شريط البحث لازم يكون زر في الشريط العلوي يتوسع بواجهة لحالة
    // اثناء البحث"): search used to be a permanently-visible field sitting
    // under the stats row, competing for space with the actual list on
    // every visit to this tab even when nobody's searching. It's now a
    // small icon button in the top bar that expands the *whole bar* into a
    // focused search field when tapped — the field itself no longer takes
    // up permanent room on the page.
    var isSearching by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val showAddDialog = remember { mutableStateOf(false) }

    // The shared "+" beside the bottom nav pill (see MainActivity) can't
    // reach into this screen's own dialog state directly, so it just raises
    // `addPersonRequested`. Watch it here and open the same "عميل جديد"
    // dialog the in-screen button uses, then immediately tell MainActivity
    // it's been handled so the flag doesn't re-fire on recomposition.
    LaunchedEffect(addPersonRequested) {
        if (addPersonRequested) {
            showAddDialog.value = true
            onAddPersonRequestHandled()
        }
    }
    val isSaving = remember { mutableStateOf(false) }
    val deleteTarget = remember { mutableStateOf<Person?>(null) }
    val payTarget = remember { mutableStateOf<Person?>(null) }
    val showShareChoice = remember { mutableStateOf(false) }
    val showShareConfirm = remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DebtFilter.ALL) }
    var openId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brandColor = LocalBrandGradientColors.current.first().toArgb()

    // BUG FIXED ("زر عميل جديد صار تحت الشريط العائم"): the floating نav
    // bar is now an overlay drawn on top of this whole screen (see
    // MainActivity), not a Scaffold bottomBar that used to reserve its own
    // clearance automatically.

    // BUG FIXED ("زر عميل جديد" sitting too high / list not lining up under
    // it): the list's own bottom clearance only ever accounted for the
    // floating nav pill, never for the FAB-style quick-add button that used
    // to float on top of the pill — so a separate, hand-guessed
    // `Spacer(Modifier.height(72.dp))` item used to be tacked onto the end
    // of the list to make up the difference. That guess didn't track the
    // FAB's real height, so on the label-hidden/compact system font
    // settings it left too little clearance (last row peeking out from
    // under the button) and on larger font scales too much (a dead gap
    // before the button).
    // "عميل جديد" itself has since moved off this screen entirely (it's the
    // shared quick-add "+" beside the pill now — see the comment a few
    // lines below), so there's no separate FAB here to clear anymore. Kept
    // the same total clearance regardless — it now reads as this list's
    // general safety margin against the pill's transparent side margins
    // (see FloatingBottomNav.kt's own doc comment on why a screen still
    // needs a bit of its own gap on top of the pill's raw measured height)
    // rather than one specific button's height, and every list in the app
    // wants at least this much room.
    val bottomSafetyMargin = 56.dp + 24.dp
    val listBottomClearance = LocalFloatingBottomNavHeight.current + bottomSafetyMargin

    LaunchedEffect(message) {
        message?.let {
            val hasUndo = viewModel.undo.value != null
            val undone = snackbarHost.showWithOptionalUndo(it, hasUndo)
            if (undone) viewModel.performUndo() else viewModel.clearUndo()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) searchFocusRequester.requestFocus()
    }

    Scaffold(
        // BUG FIXED (black strip above the bottom nav bar, Debts tab only):
        // unlike DashboardScreen/MaterialsScreen, this Scaffold had no
        // contentWindowInsets override, so it fell back to Material3's
        // default of WindowInsets.safeDrawing (top AND bottom). The outer
        // app-level Scaffold in MainActivity already pads this screen's
        // content for the bottom nav bar/system bar once (via its own
        // `padding`); this inner Scaffold then reserved that same bottom
        // system-bar space a *second* time here, leaving an extra empty
        // gap between the list and the bottom nav bar. That gap sits on
        // this Scaffold's own background color — colorScheme.background,
        // which is deliberately a touch darker than colorScheme.surface
        // (used by the cards, the nav bar, etc.) — so the gap read as a
        // distinct dark/black bar rather than blending in. Restricting
        // this to Bottom + Horizontal only (top is already handled by the
        // TopAppBar itself, same pattern as the other two tabs) removes
        // the double-padding and the gap with it.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        floatingActionButton = {
            // مع أزرار التنقل (3 أزرار) كان الزر يرتفع زيادة: ارتفاع الشريط العائم المقاس يحوي أصلاً
            // مساحة شريط النظام السفلي، والـ Scaffold يضيفها مرة ثانية لمكان الزر. نطرحها هنا مرة واحدة.
            val navBarBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
            FloatingActionButton(
                onClick = onNewDebt,
                modifier = Modifier.padding(bottom = (LocalFloatingBottomNavHeight.current - navBarBottom).coerceAtLeast(0.dp)),
                shape = CircleShape,
                containerColor = appAccent(),
                contentColor = appOnAccent(),
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "دين جديد")
            }
        },
        topBar = {
            if (isSearching) {
                // البحث يحلّ محل الشريط كله: رجوع + حقل بحث على شكل حبّة.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScreenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "إغلاق البحث",
                        onClick = { isSearching = false; search.value = "" }
                    )
                    AppSearchBar(
                        query = search.value,
                        onQueryChange = { search.value = it },
                        onClose = { isSearching = false; search.value = "" },
                        placeholder = "بحث عن عميل...",
                        focusRequester = searchFocusRequester,
                        showBackButton = false,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                }
            } else {
                ScreenTopBar(
                    title = "الديون",
                    reservedIcons = 3,
                    navigation = {
                        ScreenIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "القائمة",
                            onClick = onOpenDrawer
                        )
                    },
                    actions = {
                        // زر سجل الديون (آخر العمليات: سداد/إضافة دين... مع التاريخ).
                        ScreenIconButton(
                            icon = Icons.Default.History,
                            contentDescription = "سجل الديون",
                            onClick = onOpenActivityLog
                        )
                        AppActionPill(
                            height = 42.dp,
                            container = MaterialTheme.colorScheme.surfaceContainerHigh,
                            actions = listOf(
                                ActionSpec(Icons.Default.Search, "بحث") { isSearching = true },
                                ActionSpec(Icons.Default.Share, "مشاركة") { showShareConfirm.value = true }
                            )
                        )
                    }
                )
            }
        },
        // The "عميل جديد" FAB used to live here on its own, but that action
        // now lives as the shared quick-add "+" beside the bottom nav pill
        // (see MainActivity's `quickAction` / `addPersonRequested`), so a
        // second FAB here would just duplicate it on screen.
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
        Column(Modifier.fillMaxSize()) {
            val overdueDays = remember { SettingsRepository(context).overdueDays }
            val byAmount = remember(state.persons) { state.persons.sortedByDescending { it.amount } }
            val searched = com.shopmanager.app.ui.common.rememberSmartSearch(byAmount, search.value) { it.name }
            // كل الحسابات الثقيلة (تصنيف/آخر نشاط/تجميع) على الخيط الخفي؛ الواجهة تستلم النتيجة جاهزة فقط.
            val statusesState = com.shopmanager.app.ui.common.rememberComputed(state.persons, state.debts, overdueDays) {
                classifyPersons(state.persons, state.debts, overdueDays, System.currentTimeMillis())
            }
            val lastActivityState = com.shopmanager.app.ui.common.rememberComputed(state.persons, state.debts) {
                val latestDebt = HashMap<String, Long>()
                for (d in state.debts) {
                    val t = maxOf(d.createdAt, 0L)
                    if (t > (latestDebt[d.personId] ?: 0L)) latestDebt[d.personId] = t
                }
                state.persons.associate { it.id to maxOf(latestDebt[it.id] ?: 0L, it.createdAt) }
            }
            val statuses = statusesState.value ?: emptyMap()
            val groupsState = com.shopmanager.app.ui.common.rememberComputed(searched, statusesState.value, filter, lastActivityState.value) {
                buildDebtGroups(searched, statusesState.value ?: emptyMap(), filter, lastActivityState.value ?: emptyMap())
            }
            val groupsReady = statusesState.value != null && lastActivityState.value != null && groupsState.value != null
            val groups = groupsState.value ?: emptyList()
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            com.shopmanager.app.data.performance.ScrollJankWatch(listState)
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (scrolling) openId = null }
            }
            val currency = AppSettingsState.currencySymbol

            val remind: (Person) -> Unit = { p ->
                if (p.phone.isBlank()) {
                    scope.launch { snackbarHost.showSnackbar("لا يوجد رقم هاتف لهذا العميل") }
                } else {
                    val st = statuses[p.id]
                    PhoneUtils.whatsApp(
                        context,
                        p.phone,
                        debtReminderText(p.name, p.amount, st?.takeIf { it.bucket == DebtBucket.OVERDUE }?.days, currency)
                    )
                }
            }

            if (state.persons.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = AppScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DebtFilter.values().forEach { option ->
                        AppPillChip(
                            label = option.label,
                            selected = filter == option,
                            onClick = { filter = option; openId = null },
                            selectedColor = appAccent(),
                            selectedContentColor = appOnAccent()
                        )
                    }
                }
            }

            if ((state.isLoading && searched.isEmpty()) || (state.persons.isNotEmpty() && !groupsReady)) {
                com.shopmanager.app.ui.common.SkeletonRowList(
                    kind = com.shopmanager.app.ui.common.SkeletonRowKind.PERSON,
                    count = 7
                )
            } else if (groups.isEmpty()) {
                Box(Modifier.padding(horizontal = AppScreenPadding)) { StatsRow(state.totalPersons, state.totalDebts, state.totalAmount) }
                EmptyState(
                    icon = if (search.value.isBlank()) Icons.Default.People else Icons.Default.PersonSearch,
                    text = when {
                        search.value.isNotBlank() -> "لا توجد نتائج"
                        filter != DebtFilter.ALL && state.persons.isNotEmpty() -> "لا يوجد عملاء ضمن هذا الفلتر"
                        else -> "لا يوجد عملاء بعد\nاضغط \"عميل جديد\" للبدء"
                    }
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = AppScreenPadding, end = AppScreenPadding, top = 4.dp,
                        bottom = listBottomClearance + 64.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "stats") { StatsRow(state.totalPersons, state.totalDebts, state.totalAmount) }
                    groups.forEach { group ->
                        stickyHeader(key = "header_${group.bucket.name}", contentType = "header") {
                            GroupHeader(group)
                        }
                        itemsIndexed(group.persons, key = { _, person -> person.id }, contentType = { _, _ -> "person" }) { index, person ->
                            val status = statuses[person.id] ?: PersonStatus(DebtBucket.LATER, -1)
                            SwipeRevealRow(
                                person = person,
                                status = status,
                                revealed = openId == person.id,
                                onRevealedChange = { open -> openId = if (open) person.id else if (openId == person.id) null else openId },
                                onClick = {
                                    if (openId != null) openId = null else onOpenPerson(person.id)
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    deleteTarget.value = person
                                },
                                onPay = { openId = null; payTarget.value = person },
                                onRemind = { openId = null; remind(person) },
                                modifier = (
                                    if (LocalPerformanceTier.current == PerformanceTier.LOW) Modifier
                                    else Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = MotionSpecs.reorderSpring(),
                                        fadeOutSpec = MotionSpecs.listItemFadeOut()
                                    )
                                    ).listItemEntrance(index)
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showAddDialog.value) {
        PersonEditDialog(
            initial = null,
            isSaving = isSaving.value,
            onDismiss = { if (!isSaving.value) showAddDialog.value = false },
            onSave = { name, amount, date, note, phone ->
                isSaving.value = true
                viewModel.savePerson(null, name, amount, date, note, phone) { success ->
                    isSaving.value = false
                    if (success) showAddDialog.value = false
                }
            }
        )
    }

    deleteTarget.value?.let { person ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget.value = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${person.name}\" وكل ديونه؟") },
            confirmButton = {
                // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
                // PersonEditDialog.kt's doc comment — RectangleShape makes
                // the press highlight fill the whole cell instead of
                // TextButton's default rounded-pill outline.
                TextButton(shape = RectangleShape, onClick = { viewModel.deletePerson(person.id); deleteTarget.value = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget.value = null }) { Text("إلغاء") } }
        )
    }

    payTarget.value?.let { person ->
        GlassAlertDialog(
            onDismissRequest = { payTarget.value = null },
            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = LocalSemanticColors.current.success) },
            title = { Text("تأكيد السداد") },
            text = { Text("هل \"${person.name}\" وفى ${Formatters.number(person.amount)} ${AppSettingsState.currencySymbol}؟ سيتم حذف كل ديونه من السجل وإرسال إشعار.") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.markPersonAsPaid(person)
                    payTarget.value = null
                }) { Text("تم السداد", color = LocalSemanticColors.current.success, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { payTarget.value = null }) { Text("إلغاء") } }
        )
    }

    if (showShareConfirm.value) {
        GlassAlertDialog(
            onDismissRequest = { showShareConfirm.value = false },
            title = { Text("مشاركة كشف الديون") },
            text = { Text("الكشف يحتوي أسماء العملاء ومبالغ ديونهم. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    showShareConfirm.value = false
                    showShareChoice.value = true
                }) { Text("متابعة") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { showShareConfirm.value = false }) { Text("إلغاء") } }
        )
    }

    if (showShareChoice.value) {
        ShareFormatDialog(
            onDismiss = { showShareChoice.value = false },
            onPickImage = {
                // PERF: Canvas drawing moved off the main thread — see the
                // matching note in MaterialsScreen's onPickImage.
                scope.launch {
                    val uri = withContext(Dispatchers.Default) {
                        DebtsReportImage.generate(context, state.persons, state.totalAmount, brandColor)
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "مشاركة كشف الديون"))
                }
            },
            onPickText = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, buildDebtsShareText(state.persons, state.totalAmount))
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة كشف الديون"))
            }
        )
    }
}

@Composable
private fun StatsRow(persons: Int, debts: Int, amount: Double) {
    AppStatsStrip(
        stats = listOf(
            AppStat("عملاء", persons.toDouble()) { "%.0f".format(it) },
            AppStat("ديون", debts.toDouble()) { "%.0f".format(it) },
            AppStat("الإجمالي (${AppSettingsState.currencySymbol})", amount) { Formatters.number(it) }
        ),
        compact = true,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    AppEmptyState(icon = icon, text = text)
}

private class DebtGroup(val bucket: DebtBucket, val title: String, val persons: List<Person>, val subtotal: Double)

private fun buildDebtGroups(persons: List<Person>, statuses: Map<String, PersonStatus>, filter: DebtFilter, lastActivity: Map<String, Long>): List<DebtGroup> {
    fun pick(bucket: DebtBucket) = persons.filter { statuses[it.id]?.bucket == bucket }
    val order = when (filter) {
        DebtFilter.ALL -> listOf(DebtBucket.OVERDUE, DebtBucket.THIS_WEEK, DebtBucket.LATER, DebtBucket.PAID)
        DebtFilter.OVERDUE -> listOf(DebtBucket.OVERDUE)
        DebtFilter.THIS_WEEK -> listOf(DebtBucket.THIS_WEEK)
        DebtFilter.PAID -> listOf(DebtBucket.PAID)
    }
    return order.mapNotNull { bucket ->
        val list = pick(bucket).let { l ->
            when (bucket) {
                DebtBucket.OVERDUE -> l.sortedWith(compareByDescending<Person> { statuses[it.id]?.days ?: 0 }.thenByDescending { it.amount })
                DebtBucket.THIS_WEEK -> l.sortedWith(compareBy<Person> { statuses[it.id]?.days ?: 0 }.thenByDescending { it.amount })
                // الأحدث أولاً داخل «لاحقاً» و«مسدّد» (المجموعات الأهم تبقى أعلى القائمة دائماً).
                else -> l.sortedWith(compareByDescending<Person> { lastActivity[it.id] ?: 0L }.thenByDescending { it.amount })
            }
        }
        if (list.isEmpty()) null else DebtGroup(
            bucket,
            when (bucket) {
                DebtBucket.OVERDUE -> "متأخر"
                DebtBucket.THIS_WEEK -> "هالأسبوع"
                DebtBucket.LATER -> "لاحقاً"
                DebtBucket.PAID -> "مسدّد"
            },
            list,
            list.sumOf { it.amount }
        )
    }
}

@Composable
private fun bucketColor(bucket: DebtBucket): Color {
    val semantic = LocalSemanticColors.current
    return when (bucket) {
        DebtBucket.OVERDUE -> semantic.danger
        DebtBucket.THIS_WEEK -> semantic.warning
        DebtBucket.LATER -> MaterialTheme.colorScheme.outline
        DebtBucket.PAID -> semantic.success
    }
}

@Composable
private fun GroupHeader(group: DebtGroup) {
    val color = bucketColor(group.bucket)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            group.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(
            Formatters.number(group.persons.size.toDouble()),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = AppCaptionLargeSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (group.bucket != DebtBucket.PAID) {
            Text(
                "${Formatters.number(group.subtotal)} ${AppSettingsState.currencySymbol}",
                style = appAmountStyle(15.sp),
                color = color
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeRevealRow(
    person: Person,
    status: PersonStatus,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPay: () -> Unit,
    onRemind: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppCardRadius)
    val canSwipe = person.amount > 0
    val actionWidth = 184.dp
    val density = LocalDensity.current
    val maxPx = with(density) { actionWidth.toPx() }
    val overshootPx = with(density) { 28.dp.toPx() }
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // موضع البطاقة حالة عادية تُقرأ في مرحلة الرسم فقط (graphicsLayer) — لا إعادة تركيب أثناء السحب
    // ولا coroutine لكل حركة إصبع.
    var pos by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var crossed by remember { mutableStateOf(false) }

    fun settle(target: Float, velocity: Float = 0f) {
        settleJob?.cancel()
        if (low) { pos = target; return }
        settleJob = scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = pos,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow * 1.5f
                )
            ) { v, _ -> pos = v }
        }
    }

    LaunchedEffect(revealed, canSwipe) { settle(if (revealed && canSwipe) maxPx else 0f) }

    val dragState = rememberDraggableState { delta ->
        settleJob?.cancel()
        val raw = pos + delta
        // مقاومة مطاطية بعد أقصى فتح بدل التوقف الحاد.
        pos = when {
            raw < 0f -> 0f
            raw > maxPx -> (maxPx + (raw - maxPx) * 0.35f).coerceAtMost(maxPx + overshootPx)
            else -> raw
        }
        val nowCrossed = pos > maxPx * 0.5f
        if (nowCrossed != crossed) {
            crossed = nowCrossed
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val semantic = LocalSemanticColors.current
    val cs = MaterialTheme.colorScheme

    Box(modifier.fillMaxWidth()) {
        if (canSwipe) {
            Row(
                Modifier
                    .matchParentSize()
                    .wrapContentWidth(AbsoluteAlignment.Left)
                    .width(actionWidth)
                    .absolutePadding(right = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RevealAction(Modifier.weight(1f), Icons.Default.Check, "تسديد", semantic.success, 0f, low, { pos / maxPx }, onPay)
                RevealAction(Modifier.weight(1f), Icons.Default.NotificationsActive, "تذكير", appAccent(), 0.25f, low, { pos / maxPx }, onRemind)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = pos }
                .then(
                    if (canSwipe) Modifier.draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity ->
                            val open = pos > maxPx * 0.45f || velocity > 900f
                            val close = velocity < -900f
                            val openFinal = open && !close
                            crossed = false
                            onRevealedChange(openFinal)
                            settle(if (openFinal) maxPx else 0f, velocity)
                        }
                    ) else Modifier
                )
                .background(cs.surfaceContainerHigh, shape)
                .border(AppBorderWidth, appBorderColor(), shape)
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            PersonRowContent(person, status)
        }
    }
}

@Composable
private fun RevealAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    delay: Float,
    low: Boolean,
    progress: () -> Float,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AppCardRadius)
    Column(
        modifier
            .fillMaxHeight()
            .then(
                if (low) Modifier else Modifier.graphicsLayer {
                    val p = ((progress() - delay) / (1f - delay)).coerceIn(0f, 1f)
                    alpha = p
                    val sc = 0.8f + 0.2f * p
                    scaleX = sc
                    scaleY = sc
                }
            )
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .heightIn(min = AppMinTouchTarget),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = AppCaptionLargeSize),
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun PersonRowContent(person: Person, status: PersonStatus) {
    val avatarColor = remember(person.name) { avatarColorFor(person.name) }
    val rail = bucketColor(status.bucket)
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .drawBehind {
                val w = 5.dp.toPx()
                val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - w else 0f
                drawRect(rail, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(17.dp))
        AppIconCircle(color = avatarColor, size = 44.dp) {
            Text(
                person.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                person.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = AppBodyLargeSize),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                statusLine(status),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = AppCaptionLargeSize),
                color = if (status.bucket == DebtBucket.LATER) cs.onSurfaceVariant else rail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (person.amount > 0) {
            Text(
                "${Formatters.number(person.amount)} ${AppSettingsState.currencySymbol}",
                style = appAmountStyle(AppAmountRowSize),
                color = if (status.bucket == DebtBucket.OVERDUE) rail else cs.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.width(14.dp))
    }
}

private fun buildDebtsShareText(persons: List<Person>, totalAmount: Double): String {
    val currency = AppSettingsState.currencySymbol
    val sb = StringBuilder("💰 كشف الديون\n\n")
    persons.sortedByDescending { it.amount }.forEach { p ->
        sb.append("• ${p.name}: ${Formatters.number(p.amount)} $currency\n")
    }
    sb.append("\nالإجمالي: ${Formatters.number(totalAmount)} $currency")
    return sb.toString()
}

enum class DebtFilter(val label: String) {
    ALL("الكل"),
    OVERDUE("متأخر"),
    THIS_WEEK("هالأسبوع"),
    PAID("مسدّد")
}

@Composable
private fun OverdueTag() {
    val tier = LocalPerformanceTier.current
    val warning = LocalSemanticColors.current.warning
    val pulse = if (tier == PerformanceTier.STANDARD) {
        val transition = rememberInfiniteTransition(label = "overduePulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(MotionSpecs.breatheMs), RepeatMode.Reverse),
            label = "overduePulseAlpha"
        ).value
    } else 1f
    Box(
        Modifier
            .alpha(pulse)
            .clip(RoundedCornerShape(50))
            .background(warning.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text("متأخر", style = MaterialTheme.typography.labelSmall, color = warning, fontWeight = FontWeight.SemiBold)
    }
}
