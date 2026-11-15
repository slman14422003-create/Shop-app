package com.shopmanager.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.shopmanager.app.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.debts.DebtActivityLog
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.performance.listItemEntrance
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.sync.SyncConnectivityObserver
import com.shopmanager.app.data.sync.SyncRetry
import com.shopmanager.app.data.sync.SyncStatusStore
import com.shopmanager.app.ui.common.AppCapsuleSearchField
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionGap
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppSpace20
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.ui.common.AppTitleLargeSize
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.PhoneUtils
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.SkeletonCard
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.debts.debtReminderText
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.util.DateFormats
import com.shopmanager.app.widget.WidgetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class TimeGreeting(val text: String, val icon: ImageVector)

private fun timeBasedGreeting(): TimeGreeting {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> TimeGreeting("سهرانين لهلق؟", Icons.Filled.NightsStay)
        hour < 12 -> TimeGreeting("صباح الخير", Icons.Filled.WbSunny)
        hour < 17 -> TimeGreeting("أهلاً بك", Icons.Filled.WavingHand)
        hour < 21 -> TimeGreeting("مساء الخير", Icons.Filled.WbTwilight)
        else -> TimeGreeting("مساء النور", Icons.Filled.Bedtime)
    }
}

@Composable
private fun LazyItemScope.cardFx(index: Int): Modifier {
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    return Modifier
        .animateItem(
            fadeInSpec = if (low) null else MotionSpecs.enterSpec<Float>(),
            placementSpec = MotionSpecs.itemPlacement(),
            fadeOutSpec = if (low) null else MotionSpecs.listItemFadeOut()
        )
        .listItemEntrance(index)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    debtsViewModel: DebtsViewModel,
    materialsViewModel: MaterialsViewModel,
    onNavigateToDebts: () -> Unit = {},
    onNavigateToMaterials: () -> Unit = {},
    onOpenMissingFinder: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    notesViewModel: com.shopmanager.app.ui.notes.NotesViewModel? = null,
    onOpenPerson: (String) -> Unit = {},
    onOpenMaterial: (String) -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNewDebt: () -> Unit = {},
    onPayDebt: () -> Unit = {},
    onNewShortage: () -> Unit = onNavigateToMaterials
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) materialsViewModel.onAppEntered()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val debtsState by debtsViewModel.uiState.collectAsStateWithLifecycle()
    val materialsState by materialsViewModel.uiState.collectAsStateWithLifecycle()
    val debtsRefreshing by debtsViewModel.isRefreshing.collectAsStateWithLifecycle()
    val materialsRefreshing by materialsViewModel.isRefreshing.collectAsStateWithLifecycle()
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    val df = remember { DateFormats.get("d MMM، h:mm a", Locale("ar")) }
    val currency = AppSettingsState.currencySymbol

    val shortages = remember(materialsState.materials) { materialsState.materials }
    val isLoading = debtsState.isLoading || materialsState.isLoading

    LaunchedEffect(isLoading, debtsState.totalAmount, shortages.size, currency) {
        if (!isLoading) WidgetCache.save(context, debtsState.totalAmount, shortages.size, currency)
    }

    val overdueDays = remember { SettingsRepository(context).overdueDays }
    val personsNow = debtsState.persons
    val debtsNow = debtsState.debts
    val followUps by produceState(emptyList<FollowUpEntry>(), personsNow, debtsNow) {
        value = withContext(Dispatchers.Default) {
            computeFollowUps(personsNow, debtsNow, overdueDays, System.currentTimeMillis())
        }
    }
    val overdueAmount = remember(followUps) { followUps.filter { it.overdue }.sumOf { it.amount } }

    val activityEntries by remember { DebtActivityLog.listen(context).catch { emit(emptyList()) } }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val paidThisWeek by produceState(0.0, activityEntries) {
        value = withContext(Dispatchers.Default) {
            computeWeekTrend(activityEntries, System.currentTimeMillis()).paid.sum()
        }
    }

    val online by remember { SyncConnectivityObserver.observe(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val debtsSyncError by debtsViewModel.hasSyncError.collectAsStateWithLifecycle()
    val materialsSyncError by materialsViewModel.hasSyncError.collectAsStateWithLifecycle()
    val syncProblem = !online || debtsSyncError || materialsSyncError
    val syncLabel = remember(online, debtsSyncError, materialsSyncError) {
        when {
            !online -> "لا يوجد اتصال بالإنترنت"
            debtsSyncError || materialsSyncError -> "تعذّرت المزامنة"
            else -> SyncStatusStore.lastSyncedAt(context)?.let { "آخر مزامنة: ${df.format(Date(it))}" } ?: "لم تتم أي مزامنة بعد"
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = debtsRefreshing || materialsRefreshing,
            onRefresh = { debtsViewModel.refresh(); materialsViewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp + LocalFloatingBottomNavHeight.current),
                verticalArrangement = Arrangement.spacedBy(AppSectionGap)
            ) {
                item(key = "header", contentType = "header") {
                    HomeHeader(
                        syncLabel = syncLabel,
                        syncProblem = syncProblem,
                        onOpenDrawer = onOpenDrawer,
                        onSyncClick = { if (syncProblem) scope.launch { SyncRetry.forceReconnect() } }
                    )
                }

                item(key = "hero", contentType = "hero") {
                    HomeHeroCard(
                        totalAmount = debtsState.totalAmount,
                        totalPersons = debtsState.totalPersons,
                        overdueAmount = overdueAmount,
                        paidThisWeek = paidThisWeek,
                        loading = debtsState.isLoading,
                        nf = nf,
                        onClick = onNavigateToDebts,
                        modifier = cardFx(0)
                    )
                }

                item(key = "actions", contentType = "actions") {
                    HomeActionRow(
                        onNewDebt = onNewDebt,
                        onPay = onPayDebt,
                        onNewShortage = onNewShortage,
                        modifier = cardFx(1)
                    )
                }

                if (debtsState.isLoading) {
                    item(key = "skeleton_debts", contentType = "skeleton") {
                        SkeletonCard(lines = 2, modifier = Modifier.padding(horizontal = AppScreenPadding))
                    }
                } else {
                    item(key = "follow_up", contentType = "section") {
                        FollowUpSection(
                            entries = followUps,
                            nf = nf,
                            onOpenPerson = onOpenPerson,
                            onRemind = { e ->
                                PhoneUtils.whatsApp(
                                    context,
                                    e.phone,
                                    debtReminderText(e.name, e.amount, if (e.overdue) e.days else null, currency)
                                )
                            },
                            onShowAll = onNavigateToDebts,
                            modifier = cardFx(2)
                        )
                    }
                }

                item(key = "shortages", contentType = "section") {
                    ShortageStrip(
                        count = shortages.size,
                        names = shortages.take(3).joinToString("، ") { it.name },
                        loading = materialsState.isLoading,
                        onClick = onNavigateToMaterials,
                        modifier = cardFx(3)
                    )
                }

                if (personsNow.isEmpty() && shortages.isEmpty() && !debtsState.isLoading && !materialsState.isLoading) {
                    item(key = "empty_state", contentType = "empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 24.dp, start = AppSpaceLg, end = AppSpaceLg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Storefront, contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(Modifier.height(AppSpaceMd))
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
}

@Composable
private fun HomeHeader(
    syncLabel: String,
    syncProblem: Boolean,
    onOpenDrawer: () -> Unit,
    onSyncClick: () -> Unit
) {
    var greeting by remember { mutableStateOf(timeBasedGreeting()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            greeting = timeBasedGreeting()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = AppSpaceXs, end = AppScreenPadding, top = AppSpace6),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(AppMinTouchTarget)) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "القائمة",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(AppSpace6))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    greeting.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(AppSpace6))
                Text(
                    greeting.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                "إدارة المحل",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = AppTitleLargeSize, lineHeight = 30.sp),
                fontWeight = FontWeight.Bold
            )
        }
        HomeSyncChip(label = syncLabel, problem = syncProblem, onClick = onSyncClick)
    }
}
