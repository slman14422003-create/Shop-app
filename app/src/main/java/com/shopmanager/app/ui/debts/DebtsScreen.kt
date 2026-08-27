package com.shopmanager.app.ui.debts

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.ui.common.ActionIconButton
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.DeleteIconButton
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.GlassIconButton
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.theme.SuccessGreen


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DebtsScreen(
    onOpenPerson: (String) -> Unit,
    viewModel: DebtsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var search by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Person?>(null) }
    var payTarget by remember { mutableStateOf<Person?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
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
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("الديون", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = BrandOnGradient,
                    actionIconContentColor = BrandOnGradient
                ),
                modifier = Modifier.liquidGlassSurface(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                actions = {
                    GlassIconButton(
                        icon = Icons.Default.Share,
                        contentDescription = "مشاركة",
                        onClick = {
                            val text = buildDebtsShareText(state.persons, state.totalAmount)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة كشف الديون"))
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        size = 36.dp
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("عميل جديد") }
            )
        }
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
        Column(Modifier.fillMaxSize()) {
            StatsRow(state.totalPersons, state.totalDebts, state.totalAmount)

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث عن عميل...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            val filtered = if (search.isBlank()) state.persons
            else state.persons.filter { it.name.contains(search, ignoreCase = true) }

            if (filtered.isEmpty()) {
                EmptyState(
                    icon = if (search.isBlank()) Icons.Default.People else Icons.Default.PersonSearch,
                    text = if (search.isBlank()) "لا يوجد عملاء بعد\nاضغط \"عميل جديد\" للبدء" else "لا توجد نتائج"
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { person ->
                        PersonRow(
                            person, Modifier.animateItemPlacement(MotionSpecs.reorderSpring()),
                            onClick = { onOpenPerson(person.id) },
                            onDelete = { deleteTarget = person },
                            onMarkPaid = { payTarget = person }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
        }
    }

    if (showAddDialog) {
        PersonEditDialog(
            initial = null,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showAddDialog = false },
            onSave = { name, amount, date ->
                isSaving = true
                viewModel.savePerson(null, name, amount, date) { success ->
                    isSaving = false
                    if (success) showAddDialog = false
                }
            }
        )
    }

    deleteTarget?.let { person ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${person.name}\" وكل ديونه؟") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePerson(person.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }

    payTarget?.let { person ->
        AlertDialog(
            onDismissRequest = { payTarget = null },
            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen) },
            title = { Text("تأكيد السداد") },
            text = { Text("هل \"${person.name}\" وفى ${Formatters.number(person.amount)} ${AppSettingsState.currencySymbol}؟ سيتم حذف كل ديونه من السجل وإرسال إشعار.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markPersonAsPaid(person)
                    payTarget = null
                }) { Text("تم السداد", color = SuccessGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { payTarget = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun StatsRow(persons: Int, debts: Int, amount: Double) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // ANIMATION: these three used to snap straight to the new
            // number the instant a person/debt was added or removed. Now
            // they count up/down to it (same AnimatedCounterText already
            // used for the dashboard totals), so adding a customer or a
            // debt here visibly reflects in the header instead of just
            // silently changing.
            StatItem("عملاء", persons.toDouble()) { "%.0f".format(it) }
            VerticalDivider()
            StatItem("ديون", debts.toDouble()) { "%.0f".format(it) }
            VerticalDivider()
            StatItem("الإجمالي (${AppSettingsState.currencySymbol})", amount) { Formatters.number(it) }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        Modifier
            .height(36.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun StatItem(label: String, value: Double, format: (Double) -> String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.primary
        ) {
            com.shopmanager.app.ui.common.AnimatedCounterText(
                targetValue = value,
                format = format,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PersonRow(
    person: Person,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMarkPaid: () -> Unit
) {
    val avatarColor = remember(person.name) { avatarColorFor(person.name) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "personRowScale"
    )

    // BUG FIXED (solid black bar under the row's action icons): the press
    // scale used to be applied directly on the same modifier chain as the
    // card's own elevation/shadow. On some GPUs (notably budget devices —
    // exactly the low-end phones this app already tiers for, see
    // DevicePerformance) scaling a composable that is *also* casting a
    // shadow in the same layer makes the shadow rasterize as a flat black
    // rectangle instead of a soft blur — visible as a hard black strip
    // sitting under the row, right where the check/delete/chevron icons
    // are. Moving `.scale()` onto a plain outer Box, so it never shares a
    // graphics layer with the ElevatedCard's own shadow, fixes this
    // everywhere it happens without giving up the press animation.
    Box(modifier.fillMaxWidth().scale(scale)) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(person.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // BUG FIXED: an unbounded name could wrap to 2 lines and
                // push the row taller than its avatar/action buttons,
                // breaking the row's vertical alignment for that one
                // customer only (every other row stayed single-line height).
                Text(
                    person.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                // UI: a paid-off customer (amount == 0) used to show the
                // same "٠ ل.س" as everyone else — reads as if something
                // failed to load rather than "settled". A short, muted
                // "no debt" label makes a zero balance immediately legible
                // as a good state, in the same green used for the "paid"
                // check button elsewhere on this row.
                if (person.amount > 0) {
                    Text(
                        "${Formatters.number(person.amount)} ${AppSettingsState.currencySymbol}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "لا يوجد دين حالياً",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
                }
            }
            // Mark-as-paid: same shared circular action button as every
            // other check/edit/delete affordance in the app (see
            // ActionIconButton) so it's pixel-identical to the delete "×"
            // right next to it instead of a couple dp larger with no press
            // feedback. Settles the person's whole balance in one tap and
            // fires the same "paid" notification. Only shown when there's
            // actually something to settle.
            //
            // FIX: 10dp read as touching/merged on-device once the two
            // 36dp filled circles sat next to each other — their soft
            // tinted backgrounds made the row look like one blob instead
            // of two distinct buttons. Widened to a gap that reads
            // unmistakably as two separate actions.
            if (person.amount > 0) {
                ActionIconButton(
                    icon = Icons.Default.Check,
                    tint = SuccessGreen,
                    contentDescription = "تسجيل سداد كامل الدين",
                    onClick = onMarkPaid
                )
                Spacer(Modifier.width(16.dp))
            }
            DeleteIconButton(onClick = onDelete, contentDescription = "حذف العميل")
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Default.ChevronLeft, contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
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
