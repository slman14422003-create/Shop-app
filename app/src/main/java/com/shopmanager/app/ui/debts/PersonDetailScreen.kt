package com.shopmanager.app.ui.debts

import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace20
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.shopmanager.app.data.debts.DebtActivityLog
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.common.AppAmountLargeSize
import com.shopmanager.app.ui.common.AppAmountRowSize
import com.shopmanager.app.ui.common.AppBorderWidth
import com.shopmanager.app.ui.common.AppCaptionLargeSize
import com.shopmanager.app.ui.common.AppCardLargeRadius
import com.shopmanager.app.ui.common.AppColorPillButton
import com.shopmanager.app.ui.common.AppOutlinedCard
import com.shopmanager.app.ui.common.appAccent
import com.shopmanager.app.ui.common.appAmountStyle
import com.shopmanager.app.ui.common.appBorderColor
import com.shopmanager.app.ui.common.appOnAccent
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.notes.NoteLinkType
import com.shopmanager.app.ui.common.ActionIconButton
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.showWithOptionalUndo
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.PhoneUtils
import com.shopmanager.app.ui.common.StatementText
import com.shopmanager.app.ui.notes.NoteEditScreen
import com.shopmanager.app.ui.notes.NotesViewModel
import com.shopmanager.app.ui.theme.LocalSemanticColors
import java.text.NumberFormat
import com.shopmanager.app.util.DateFormats
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.sp
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppGroupLargeRadius
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionGap
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import com.shopmanager.app.ui.common.ActionSpec
import com.shopmanager.app.ui.common.AppActionPill
import androidx.compose.material.icons.filled.Close
import com.shopmanager.app.data.performance.listItemEntrance

private fun today(): String = DateFormats.get("yyyy-MM-dd", Locale.US).format(Date())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person,
    onBack: () -> Unit,
    viewModel: DebtsViewModel = viewModel(),
    // "ترابط بين الديون والملاحظات": نفس نسخة NotesViewModel المشتركة اللي
    // يستخدمها تبويب "ملاحظات هامة" - راجع MainActivity، اللي يمررها هون
    // بدل ما يترك القيمة الافتراضية تنشئ نسخة جديدة منفصلة (تصير عندها
    // مستمع Firestore خاص فيها وتخرج عن مزامنة مع باقي الشاشة).
    notesViewModel: NotesViewModel = viewModel(),
    onQuickAdd: (QuickAddMode) -> Unit = {}
) {
    // PERF FIX: debtsForPerson() builds a brand-new callbackFlow on every call.
    // Calling it straight inside collectAsState meant a NEW flow instance on
    // every recomposition (typing in a field, opening a dialog, ...), which
    // tore down and re-registered this customer's Firestore listener each time -
    // flicker, extra reads, wasted battery. Cached per customer id now.
    val debtsFlow = remember(person.id) { viewModel.debtsForPerson(person.id) }
    val debts by debtsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var editingDebt by remember { mutableStateOf<Debt?>(null) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var note by remember { mutableStateOf("") }
    var showDeletePersonConfirm by remember { mutableStateOf(false) }
    var deleteDebtTarget by remember { mutableStateOf<String?>(null) }
    var payDebtTarget by remember { mutableStateOf<Debt?>(null) }
    // "ملاحظات مرتبطة": كل الملاحظات اللي نوعها PERSON ومربوطة بهذا العميل
    // بالذات - نفس منطق الفلترة اللي NotesScreen يعرضه بتبويب "الديون".
    val allPersons = viewModel.uiState.collectAsStateWithLifecycle().value.persons
    val notesState = notesViewModel.uiState.collectAsStateWithLifecycle().value
    val linkedNotes = remember(notesState.notes, person.id) {
        notesState.notes.filter { it.linkType == NoteLinkType.PERSON && it.linkedId == person.id }
    }
    var editingLinkedNote by remember { mutableStateOf<ImportantNote?>(null) }
    var showAddLinkedNote by remember { mutableStateOf(false) }
    var isSavingLinkedNote by remember { mutableStateOf(false) }
    var deleteNoteTarget by remember { mutableStateOf<ImportantNote?>(null) }
    // FEATURE ADDED ("تعديل اسم الشخص من واجهة تفاصيل الديون"): the
    // update-person write path (DebtsViewModel.savePerson with a non-null
    // existingId -> DebtsRepository.updatePerson) already existed, but
    // nothing in the UI ever called it with an id — DebtsScreen only ever
    // calls savePerson(null, ...) to create a *new* person, so renaming an
    // existing customer had no entry point anywhere in the app. This adds
    // one here, right where it's needed most: while already looking at
    // that specific customer's page.
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }
    var editPhoneText by remember { mutableStateOf("") }
    var editPhoneError by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSavingName by remember { mutableStateOf(false) }
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    val avatarColor = remember(person.name) { avatarColorFor(person.name) }

    LaunchedEffect(message) {
        message?.let {
            val hasUndo = viewModel.undo.value != null
            val undone = snackbarHost.showWithOptionalUndo(it, hasUndo)
            if (undone) viewModel.performUndo() else viewModel.clearUndo()
            viewModel.clearMessage()
        }
    }

    val total = debts.sumOf { it.amount }
    val scope = rememberCoroutineScope()
    val currency = AppSettingsState.currencySymbol
    val overdueDays = remember { SettingsRepository(context).overdueDays }
    val status = remember(person, total, debts, overdueDays) {
        classifyPersons(listOf(person.copy(amount = total)), debts, overdueDays, System.currentTimeMillis())[person.id]
            ?: PersonStatus(DebtBucket.LATER, -1)
    }
    val activityEntries by remember { DebtActivityLog.listen(context).catch { emit(emptyList()) } }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val payments = remember(activityEntries, person.name) {
        activityEntries.filter {
            (it.kind == DebtActivityLog.Kind.PAID || it.kind == DebtActivityLog.Kind.PAID_ALL) && it.person == person.name
        }
    }
    val paidTotal = remember(payments) { payments.sumOf { abs(it.amount) } }
    val timeline = remember(debts, payments) {
        val parser = DateFormats.get("yyyy-MM-dd", Locale.US, lenient = false)
        val debtRows = debts.map { d ->
            val t = if (d.createdAt > 0L) d.createdAt
            else runCatching { parser.parse(d.date.trim())?.time }.getOrNull() ?: 0L
            TimelineEntry("d_${d.id}", d.amount, t, d.date, d.note, d)
        }
        val printer = DateFormats.get("yyyy-MM-dd", Locale.US)
        val payRows = payments.map { e ->
            TimelineEntry("p_${e.id}", -abs(e.amount), e.time, printer.format(Date(e.time)), "", null)
        }
        (debtRows + payRows).sortedByDescending { it.time }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var debtMenuKey by remember { mutableStateOf<String?>(null) }
    val remind: () -> Unit = {
        if (person.phone.isBlank()) {
            scope.launch { snackbarHost.showSnackbar("لا يوجد رقم هاتف لهذا العميل") }
        } else {
            PhoneUtils.whatsApp(
                context,
                person.phone,
                debtReminderText(person.name, total, status.takeIf { it.bucket == DebtBucket.OVERDUE }?.days, currency)
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            ScreenTopBar(
                title = person.name,
                reservedIcons = 1,
                navigation = {
                    ScreenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack
                    )
                },
                actions = {
                    Box {
                        ScreenIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "المزيد",
                            onClick = { menuOpen = true }
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("تعديل بيانات العميل") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    editNameText = person.name
                                    editPhoneText = person.phone
                                    editPhoneError = false
                                    showEditNameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف العميل", color = LocalSemanticColors.current.danger) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = LocalSemanticColors.current.danger)
                                },
                                onClick = {
                                    menuOpen = false
                                    showDeletePersonConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Box(Modifier.fillMaxWidth().height(AppBorderWidth).background(appBorderColor()))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppScreenPadding, vertical = AppSpace10),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace10),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppColorPillButton(
                        label = "تسديد",
                        onClick = { onQuickAdd(QuickAddMode.PAY) },
                        containerColor = LocalSemanticColors.current.success,
                        contentColor = appOnAccent(),
                        enabled = total > 0,
                        height = 52.dp,
                        modifier = Modifier.weight(1f)
                    )
                    AppColorPillButton(
                        label = "دين",
                        onClick = { onQuickAdd(QuickAddMode.DEBT) },
                        containerColor = appAccent(),
                        contentColor = appOnAccent(),
                        outlined = true,
                        height = 52.dp,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = remind,
                        enabled = total > 0,
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(AppBorderWidth * 1.5f, appBorderColor()),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "تذكير",
                                tint = if (total > 0) appAccent() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace6, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(AppGroupGap)
        ) {
            item {
                BalanceCard(
                    name = person.name,
                    avatarColor = avatarColor,
                    total = total,
                    status = status,
                    paidTotal = paidTotal,
                    nf = nf,
                    phone = person.phone,
                    onCall = { PhoneUtils.call(context, person.phone) },
                    onWhatsApp = { PhoneUtils.whatsApp(context, person.phone) },
                    onSms = { PhoneUtils.sms(context, person.phone) }
                )
            }

            if (editingDebt != null) {
                item { Spacer(Modifier.height(AppSectionGap - AppGroupGap)) }
                item {
                    AddDebtCard(
                        isEditing = true,
                        amount = amount,
                        date = date,
                        note = note,
                        onAmountChange = { amount = it },
                        onDateChange = { date = it },
                        onNoteChange = { note = it },
                        onCancelEdit = { editingDebt = null; amount = ""; date = today(); note = "" },
                        onSubmit = {
                            val a = amount.trim().toDoubleOrNull()
                            if (a != null && a > 0 && date.isNotBlank()) {
                                viewModel.addOrUpdateDebt(editingDebt?.id, person.id, a, date, note.trim())
                                amount = ""
                                date = today()
                                note = ""
                                editingDebt = null
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(AppSectionGap - AppGroupGap)) }

            item {
                PersonScheduleCard(
                    person = person,
                    totalOwed = total,
                    currency = currency,
                    onSetDueDate = { viewModel.saveSchedule(person, it, person.installments) },
                    onAddInstallment = { viewModel.saveSchedule(person, person.dueDate, person.installments + it) },
                    onDeleteInstallment = { removed ->
                        viewModel.saveSchedule(person, person.dueDate, person.installments.filter { it.id != removed.id })
                    },
                    onPayInstallment = { viewModel.payInstallment(person, it) }
                )
            }

            item { Spacer(Modifier.height(AppSectionGap - AppGroupGap)) }

            item {
                AppSectionTitle(
                    text = "سجل الديون",
                    trailing = {
                        if (person.phone.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    PhoneUtils.whatsApp(
                                        context,
                                        person.phone,
                                        StatementText.build(person.name, debts, total, currency)
                                    )
                                },
                                modifier = Modifier.size(AppMinTouchTarget)
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = "إرسال كشف الحساب عبر واتساب",
                                    tint = LocalSemanticColors.current.success
                                )
                            }
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(AppSpaceXs)) }

            if (timeline.isEmpty()) {
                item {
                    AppRowSurface(shape = RoundedCornerShape(AppGroupLargeRadius)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = AppSpaceLg, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PriceCheck, contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(AppSpaceLg))
                            Text(
                                "لا يوجد ديون مسجلة",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(timeline, key = { _, e -> e.key }, contentType = { _, _ -> "timeline" }) { index, entry ->
                    TimelineRow(
                        entry = entry,
                        nf = nf,
                        currency = currency,
                        shape = groupedRowShape(index, timeline.lastIndex),
                        menuOpen = debtMenuKey == entry.key,
                        onOpenMenu = { debtMenuKey = entry.key },
                        onDismissMenu = { debtMenuKey = null },
                        onEdit = {
                            entry.debt?.let { debt ->
                                editingDebt = debt
                                amount = debt.amount.toString()
                                date = debt.date
                                note = debt.note
                            }
                        },
                        onDelete = { entry.debt?.let { deleteDebtTarget = it.id } },
                        onMarkPaid = { entry.debt?.let { payDebtTarget = it } },
                        modifier = Modifier.listItemEntrance(index)
                    )
                }
            }

            item { Spacer(Modifier.height(AppSectionGap - AppGroupGap)) }

            item {
                AppSectionTitle(
                    text = "ملاحظات مرتبطة",
                    trailing = {
                        ActionIconButton(
                            icon = Icons.Default.Add,
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "إضافة ملاحظة لهذا العميل",
                            onClick = { showAddLinkedNote = true }
                        )
                    }
                )
            }

            item { Spacer(Modifier.height(AppSpaceXs)) }

            if (linkedNotes.isEmpty()) {
                item {
                    AppFootnote("لا توجد ملاحظات مرتبطة بهذا العميل بعد")
                }
            } else {
                itemsIndexed(linkedNotes, key = { _, n -> "linkedNote_${n.id}" }, contentType = { _, _ -> "linkedNote" }) { index, linkedNote ->
                    LinkedNoteRow(
                        note = linkedNote,
                        shape = groupedRowShape(index, linkedNotes.lastIndex),
                        onToggleDone = { notesViewModel.setDone(linkedNote, !linkedNote.isDone) },
                        onEdit = { editingLinkedNote = linkedNote },
                        onDelete = { deleteNoteTarget = linkedNote }
                    )
                }
            }
        }
    }

    if (showDeletePersonConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showDeletePersonConfirm = false },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${person.name}\" وكل ديونه؟") },
            confirmButton = {
                // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
                // PersonEditDialog.kt's doc comment.
                TextButton(shape = RectangleShape, onClick = {
                    viewModel.deletePerson(person.id)
                    showDeletePersonConfirm = false
                    onBack()
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { showDeletePersonConfirm = false }) { Text("إلغاء") } }
        )
    }

    deleteDebtTarget?.let { id ->
        GlassAlertDialog(
            onDismissRequest = { deleteDebtTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذا الدين؟") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    viewModel.deleteDebt(id)
                    deleteDebtTarget = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteDebtTarget = null }) { Text("إلغاء") } }
        )
    }

    payDebtTarget?.let { debt ->
        GlassAlertDialog(
            onDismissRequest = { payDebtTarget = null },
            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = LocalSemanticColors.current.success) },
            title = { Text("تأكيد السداد") },
            text = { Text("هل \"${person.name}\" وفى ${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}؟ سيتم حذف هذا الدين من السجل وإرسال إشعار.") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    viewModel.markDebtAsPaid(debt, person.name)
                    payDebtTarget = null
                }) { Text("تم السداد", color = LocalSemanticColors.current.success, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { payDebtTarget = null }) { Text("إلغاء") } }
        )
    }
    if (showEditNameDialog) {
        GlassAlertDialog(
            onDismissRequest = { if (!isSavingName) showEditNameDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = LocalSemanticColors.current.info) },
            title = { Text("تعديل بيانات العميل") },
            text = {
                Column {
                    Column(verticalArrangement = Arrangement.spacedBy(AppGroupGap)) {
                        AppTextField(
                            value = editNameText,
                            onValueChange = { editNameText = it },
                            label = "اسم العميل",
                            enabled = !isSavingName,
                            shape = groupedRowShape(0, 1)
                        )
                        AppTextField(
                            value = editPhoneText,
                            onValueChange = { editPhoneText = it; editPhoneError = false },
                            label = "رقم الهاتف (اختياري)",
                            placeholder = "09xxxxxxxx",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                            enabled = !isSavingName,
                            isError = editPhoneError,
                            shape = groupedRowShape(1, 1)
                        )
                    }
                    if (editPhoneError) {
                        Text(
                            "رقم الهاتف غير صحيح",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = AppSpace10, start = AppSpaceSm)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editNameText.isNotBlank() && !isSavingName,
                    shape = RectangleShape,
                    onClick = {
                        val newName = editNameText.trim()
                        val newPhone = editPhoneText.trim()
                        if (!PhoneUtils.isValidOrBlank(newPhone)) {
                            editPhoneError = true
                        } else if (newName.isNotEmpty() && (newName != person.name || newPhone != person.phone)) {
                            isSavingName = true
                            // Only the name/phone change here — amount/date are
                            // passed through unchanged (updatePerson writes
                            // them together), so this can't silently
                            // clobber the customer's existing balance/date.
                            viewModel.savePerson(person.id, newName, person.amount, person.date, phone = newPhone) { success ->
                                isSavingName = false
                                if (success) showEditNameDialog = false
                            }
                        } else {
                            showEditNameDialog = false
                        }
                    }
                ) {
                    if (isSavingName) {
                        com.shopmanager.app.ui.common.AppSpinner(size = 16.dp, color = androidx.compose.material3.LocalContentColor.current)
                    } else {
                        Text("حفظ")
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isSavingName, shape = RectangleShape, onClick = { showEditNameDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // "ترابط بين الديون والملاحظات": إضافة ملاحظة جديدة مربوطة تلقائيًا
    // بهذا العميل - initial = null (فالعنوان يضل "ملاحظة جديدة" فعليًا،
    // مو "تعديل") مع defaultLinkType/defaultLinkedId/defaultLinkedName
    // لتعبئة الربط مسبقًا - راجع NoteEditDialog.kt للتفصيل.
    if (showAddLinkedNote) {
        NoteEditScreen(
            initial = null,
            persons = allPersons,
            materials = emptyList(),
            isSaving = isSavingLinkedNote,
            defaultLinkType = NoteLinkType.PERSON,
            defaultLinkedId = person.id,
            defaultLinkedName = person.name,
            onDismiss = { if (!isSavingLinkedNote) showAddLinkedNote = false },
            onSave = { title, content, linkType, linkedId, linkedName, reminderAt ->
                isSavingLinkedNote = true
                notesViewModel.addNote(title, content, linkType, linkedId, linkedName, reminderAt) { success ->
                    isSavingLinkedNote = false
                    if (success) showAddLinkedNote = false
                }
            }
        )
    }

    editingLinkedNote?.let { current ->
        NoteEditScreen(
            initial = current,
            persons = allPersons,
            materials = emptyList(),
            isSaving = isSavingLinkedNote,
            onDismiss = { if (!isSavingLinkedNote) editingLinkedNote = null },
            onSave = { title, content, linkType, linkedId, linkedName, reminderAt ->
                isSavingLinkedNote = true
                notesViewModel.updateNote(current, title, content, linkType, linkedId, linkedName, reminderAt) { success ->
                    isSavingLinkedNote = false
                    if (success) editingLinkedNote = null
                }
            }
        )
    }

    deleteNoteTarget?.let { targetNote ->
        GlassAlertDialog(
            onDismissRequest = { deleteNoteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف الملاحظة \"${targetNote.title}\"؟") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { notesViewModel.deleteNote(targetNote); deleteNoteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteNoteTarget = null }) { Text("إلغاء") } }
        )
    }
}

private class TimelineEntry(
    val key: String,
    val amount: Double,
    val time: Long,
    val dateLabel: String,
    val note: String,
    val debt: Debt?
)

@Composable
private fun BalanceCard(
    name: String,
    avatarColor: Color,
    total: Double,
    status: PersonStatus,
    paidTotal: Double,
    nf: NumberFormat,
    phone: String = "",
    onCall: () -> Unit = {},
    onWhatsApp: () -> Unit = {},
    onSms: () -> Unit = {}
) {
    val semantic = LocalSemanticColors.current
    val cs = MaterialTheme.colorScheme
    val currency = AppSettingsState.currencySymbol
    val chipColor = when (status.bucket) {
        DebtBucket.OVERDUE -> semantic.danger
        DebtBucket.THIS_WEEK -> semantic.warning
        DebtBucket.PAID -> semantic.success
        DebtBucket.LATER -> cs.onSurfaceVariant
    }
    AppOutlinedCard(shape = RoundedCornerShape(AppCardLargeRadius)) {
        Column(Modifier.padding(AppSpace20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconCircle(color = avatarColor, size = 48.dp) {
                    Text(
                        name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.width(AppSpaceMd))
                Text(
                    "إجمالي الديون",
                    modifier = Modifier.weight(1f),
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
                )
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(chipColor.copy(alpha = 0.14f))
                        .padding(horizontal = AppSpaceMd, vertical = 6.dp)
                ) {
                    Text(
                        statusLine(status),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = AppCaptionLargeSize),
                        fontWeight = FontWeight.Bold,
                        color = chipColor,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(AppSpaceMd))
            Text(
                "${nf.format(total)} $currency",
                style = appAmountStyle(AppAmountLargeSize),
                color = cs.onSurface,
                maxLines = 1
            )
            if (paidTotal > 0.0) {
                val fraction = (paidTotal / (paidTotal + total.coerceAtLeast(0.0))).toFloat().coerceIn(0f, 1f)
                Spacer(Modifier.height(AppSpaceMd))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(cs.onSurface.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(semantic.success)
                    )
                }
                Spacer(Modifier.height(AppSpace6))
                Text(
                    "سُدّد ${nf.format(paidTotal)} من ${nf.format(paidTotal + total.coerceAtLeast(0.0))} $currency",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = AppCaptionLargeSize),
                    color = cs.onSurfaceVariant
                )
            }
            if (phone.isNotBlank()) {
                Spacer(Modifier.height(AppSpaceMd))
                Text(
                    "\u200E$phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(AppSpace10))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpaceSm)) {
                    ContactActionButton(Modifier.weight(1f), Icons.Default.Call, "اتصال", cs.onSurface, onCall)
                    ContactActionButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.Chat, "واتساب", semantic.success, onWhatsApp)
                    ContactActionButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.Send, "رسالة", semantic.info, onSms)
                }
            }
        }
    }
}

@Composable
private fun ContactActionButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AppMinTouchTarget).padding(vertical = AppSpace10),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpace6))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDebtCard(
    isEditing: Boolean,
    amount: String,
    date: String,
    note: String,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSubmit: () -> Unit
) {
    // طلب "دمج نمط الـ Glassmorphism": نفس البطاقة، بستايل الزجاج الموحّد
    // (GlassCard) بدل الـ Surface المسطحة - راجع الشرح الكامل بـ GlassCard.kt.
    AppCard {
        Column(Modifier.padding(AppSpaceLg)) {
            Text(
                if (isEditing) "تعديل الدين" else "إضافة دين جديد",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(AppSpace10))
            Row(verticalAlignment = Alignment.Top) {
                AppTextField(
                    value = amount, onValueChange = onAmountChange,
                    label = "المبلغ (${AppSettingsState.currencySymbol})",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(AppSpaceSm))
                AppTextField(
                    value = date, onValueChange = onDateChange,
                    label = "التاريخ",
                    leadingIcon = Icons.Default.CalendarMonth,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(AppSpaceSm))
            AppTextField(
                value = note, onValueChange = onNoteChange,
                label = "ملاحظة (اختياري)",
                leadingIcon = Icons.Default.Notes,
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(AppSectionGap))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpaceSm)) {
                AppPillButton(
                    label = if (isEditing) "حفظ التعديل" else "إضافة الدين",
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f)
                )
                if (isEditing) {
                    AppPillButton(label = "إلغاء", onClick = onCancelEdit, tonal = true)
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    entry: TimelineEntry,
    nf: NumberFormat,
    currency: String,
    shape: Shape,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkPaid: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val isDebt = entry.amount > 0
    val tint = if (isDebt) semantic.danger else semantic.success
    val sign = if (isDebt) "+" else "\u2212"
    AppRowSurface(shape = shape, modifier = modifier, onClick = if (entry.debt != null) onOpenMenu else null) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = AppScreenPadding, vertical = AppSpace10),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
                Spacer(Modifier.width(AppSpaceMd))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.dateLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (entry.note.isNotBlank()) {
                        Row(
                            Modifier.padding(top = AppSpace2),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Notes, contentDescription = null,
                                modifier = Modifier.size(14.dp).padding(top = AppSpace2),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(AppSpaceXs))
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = AppCaptionLargeSize),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
                Spacer(Modifier.width(AppSpaceSm))
                Text(
                    "\u2066$sign${nf.format(abs(entry.amount))} $currency\u2069",
                    style = appAmountStyle(AppAmountRowSize),
                    color = tint,
                    maxLines = 1,
                    softWrap = false
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text("تسجيل السداد") },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = semantic.success) },
                    onClick = { onDismissMenu(); onMarkPaid() }
                )
                DropdownMenuItem(
                    text = { Text("تعديل") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { onDismissMenu(); onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("حذف الدين", color = semantic.danger) },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = semantic.danger) },
                    onClick = { onDismissMenu(); onDelete() }
                )
            }
        }
    }
}

/**
 * "ترابط بين الديون والملاحظات": صف مختصر لملاحظة مربوطة بهذا العميل -
 * نفس تخطيط [DebtRow] فوق (Surface بحدّ رفيع بدل ظل، وزرّي تعديل/حذف
 * دائريين بنفس الحجم) عشان يبين كإكمال طبيعي لنفس القائمة، بدل قسم بستايل
 * مختلف. التبديل/التعديل/الحذف الفعلي بيصير عبر NotesViewModel مباشرة -
 * نفس الكائن المشترك اللي تبويب "ملاحظات هامة" يستخدمه.
 */
@Composable
private fun LinkedNoteRow(
    note: ImportantNote,
    shape: Shape,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppRowSurface(shape = shape) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = AppScreenPadding, vertical = AppSpace10),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIconButton(
                icon = if (note.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                tint = if (note.isDone) LocalSemanticColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = "تم",
                onClick = onToggleDone
            )
            Spacer(Modifier.width(AppSpace10))
            Column(Modifier.weight(1f)) {
                Text(
                    note.title,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                    color = if (note.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (note.content.isNotBlank()) {
                    Text(
                        note.content,
                        style = MaterialTheme.typography.labelSmall,
                        // LIGHT-MODE CONTRAST FIX: see the matching note on
                        // DebtRow's debt.note text above — `outline` fails
                        // text contrast in light mode, onSurfaceVariant is
                        // this app's established secondary-text color.
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (note.reminderAt > 0) {
                    Row(Modifier.padding(top = AppSpace2), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule, contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            remember(note.reminderAt) {
                                DateFormats.get("yyyy-MM-dd HH:mm", Locale.US).format(Date(note.reminderAt))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.width(AppSpaceSm))
            AppActionPill(
                actions = listOf(
                    ActionSpec(Icons.Default.Edit, "تعديل الملاحظة", null, onEdit),
                    ActionSpec(Icons.Default.Close, "حذف الملاحظة", LocalSemanticColors.current.danger, onDelete)
                )
            )
        }
    }
}
