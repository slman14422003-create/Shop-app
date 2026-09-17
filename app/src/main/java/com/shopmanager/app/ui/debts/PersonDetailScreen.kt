package com.shopmanager.app.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.shopmanager.app.ui.common.GlassIconButton
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.common.listItemEntrance
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.DeleteIconButton
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.GlassCard
import com.shopmanager.app.ui.notes.NoteEditScreen
import com.shopmanager.app.ui.notes.NotesViewModel
import com.shopmanager.app.ui.theme.InfoBlue
import com.shopmanager.app.ui.theme.LocalBrandGradientColors
import com.shopmanager.app.ui.theme.SuccessGreen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

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
    notesViewModel: NotesViewModel = viewModel()
) {
    val debts by viewModel.debtsForPerson(person.id).collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()
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
    val allPersons = viewModel.uiState.collectAsState().value.persons
    val notesState = notesViewModel.uiState.collectAsState().value
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
    var isSavingName by remember { mutableStateOf(false) }
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    val avatarColor = remember(person.name) { avatarColorFor(person.name) }

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    val total = debts.sumOf { it.amount }
    // BUG FIXED ("اللونان بالشريط العلوي منفصلان بخط، لازم يكونوا نفس اللون
    // متصل"): the TopAppBar and PersonHeader right below it are two
    // *independent* liquidGlassSurface panels (see `topFlush`'s own doc on
    // that function — merging them into one panel isn't possible here since
    // one lives in Scaffold's `topBar` slot and the other is the first
    // LazyColumn item). `topFlush` already killed the shadow/topEdge-glare/
    // rim-border that used to draw an extra line at their seam, but each
    // panel's default `baseBrush` (`BrandGradient.brush()`) still built its
    // OWN full gradientStart→gradientEnd sweep across *its own* local
    // height — a `Brush.verticalGradient` with no explicit end resolves
    // against whatever height it's actually drawn into. TopAppBar (~64dp +
    // status bar) finished its sweep all the way at gradientEnd right at its
    // own bottom edge, while PersonHeader started its *own* sweep fresh at
    // gradientStart one pixel below — two different colors meeting head-on,
    // which is exactly the visible line in the screenshot. Giving the
    // TopAppBar a flat `gradientStart` fill instead (no sweep of its own)
    // means its bottom edge is now the *same* color PersonHeader's gradient
    // begins at, so the seam disappears and all the fading into
    // `gradientEnd` happens across PersonHeader's own, taller panel instead.
    val brandGradientColors = LocalBrandGradientColors.current
    val topBarBrush = remember(brandGradientColors) {
        SolidColor(brandGradientColors.first())
    }

    Scaffold(
        // Off-pager screen (no bottom nav bar of its own) — the outer app
        // Scaffold already reserves the real bottom/horizontal safe-area
        // space one level up in NavHost's padding, so this Scaffold's own
        // content insets are zeroed to avoid reserving that same space
        // twice. The TopAppBar below still handles the status bar inset
        // entirely on its own regardless of this setting.
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        person.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    // BUG FIXED (السهم والاسم فايتين ببعض / back button
                    // glued to the title): only `start` padding (space from
                    // the screen edge) was set here — nothing separated the
                    // button from the title text sitting right after it in
                    // the navigation-icon slot, so the person's name ended
                    // up crammed against the button. `end` padding opens a
                    // real gap before the title, matching the same fix
                    // already applied on Settings/MaterialCatalog's back
                    // buttons.
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                        size = 36.dp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = BrandOnGradient,
                    navigationIconContentColor = BrandOnGradient,
                    actionIconContentColor = BrandOnGradient
                ),
                // طلب "تعميم ستايل الزجاج": highlight = false + baseAlpha = 0.72f
                // — راجع الشرح بـ DashboardScreen.kt. baseBrush = topBarBrush
                // (flat gradientStart, not the usual sweep) — راجع تعليق
                // "اللونان بالشريط العلوي منفصلان بخط" فوق.
                modifier = Modifier.liquidGlassSurface(
                    androidx.compose.ui.graphics.RectangleShape,
                    baseBrush = topBarBrush,
                    highlight = false,
                    baseAlpha = 0.72f
                ),
                actions = {
                    // BUG FIXED (الزرين فايتين ببعض / overlapping icons):
                    // each button previously carried its own `padding(end
                    // = ...)`, which pads OUTSIDE that button's own box —
                    // it doesn't reserve any space from its *neighbor*.
                    // TopAppBar's actions slot is a Row that measures each
                    // child at its natural (unconstrained) width and packs
                    // them back-to-back with zero gap of its own, so with
                    // two icon buttons sitting side by side here, only
                    // 4.dp of the intended gap actually separated the two
                    // circles — nowhere near enough once IconButton's own
                    // ~48.dp minimum touch target (larger than the visible
                    // 36.dp circle drawn inside it) is added in, which is
                    // exactly what visually crowded/overlapped in the
                    // screenshot. A Row with `spacedBy` inserts a real gap
                    // *between* children instead of relying on each
                    // child's own outside padding to add up correctly.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        GlassIconButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "تعديل اسم العميل",
                            onClick = {
                                editNameText = person.name
                                showEditNameDialog = true
                            },
                            size = 36.dp
                        )
                        GlassIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "حذف العميل",
                            onClick = { showDeletePersonConfirm = true },
                            size = 36.dp
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PersonHeader(
                    name = person.name,
                    avatarColor = avatarColor,
                    total = total,
                    debtsCount = debts.size,
                    nf = nf
                )
            }

            item {
                AddDebtCard(
                    isEditing = editingDebt != null,
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

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("سجل الديون", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            if (debts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PriceCheck, contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("لا يوجد ديون مسجلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(debts, key = { _, debt -> debt.id }) { index, debt ->
                    DebtRow(
                        debt = debt,
                        nf = nf,
                        onEdit = {
                            editingDebt = debt
                            amount = debt.amount.toString()
                            date = debt.date
                            note = debt.note
                        },
                        onDelete = { deleteDebtTarget = debt.id },
                        onMarkPaid = { payDebtTarget = debt },
                        modifier = Modifier.listItemEntrance(index)
                    )
                }
            }

            // "ترابط بين الديون والملاحظات": قسم منفصل يعرض أي ملاحظة هامة
            // مربوطة بهذا العميل تحديدًا - قبل هالإضافة كانت هالعلاقة اتجاه
            // وحيد بس (من الملاحظة تقدر تفتح صفحة العميل)، هلق تقدر كمان
            // تشوف/تضيف/تعدل ملاحظات هذا العميل من صفحته مباشرة.
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ملاحظات مرتبطة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    ActionIconButton(
                        icon = Icons.Default.Add,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = "إضافة ملاحظة لهذا العميل",
                        onClick = { showAddLinkedNote = true }
                    )
                }
            }

            if (linkedNotes.isEmpty()) {
                item {
                    Text(
                        "لا توجد ملاحظات مرتبطة بهذا العميل بعد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(linkedNotes, key = { "linkedNote_${it.id}" }) { linkedNote ->
                    LinkedNoteRow(
                        note = linkedNote,
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
                TextButton(onClick = {
                    viewModel.deletePerson(person.id)
                    showDeletePersonConfirm = false
                    onBack()
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { showDeletePersonConfirm = false }) { Text("إلغاء") } }
        )
    }

    deleteDebtTarget?.let { id ->
        GlassAlertDialog(
            onDismissRequest = { deleteDebtTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذا الدين؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDebt(id)
                    deleteDebtTarget = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteDebtTarget = null }) { Text("إلغاء") } }
        )
    }

    payDebtTarget?.let { debt ->
        GlassAlertDialog(
            onDismissRequest = { payDebtTarget = null },
            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen) },
            title = { Text("تأكيد السداد") },
            text = { Text("هل \"${person.name}\" وفى ${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}؟ سيتم حذف هذا الدين من السجل وإرسال إشعار.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markDebtAsPaid(debt, person.name)
                    payDebtTarget = null
                }) { Text("تم السداد", color = SuccessGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { payDebtTarget = null }) { Text("إلغاء") } }
        )
    }
    if (showEditNameDialog) {
        GlassAlertDialog(
            onDismissRequest = { if (!isSavingName) showEditNameDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = InfoBlue) },
            title = { Text("تعديل اسم العميل") },
            text = {
                AppTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    label = "اسم العميل",
                    enabled = !isSavingName
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editNameText.isNotBlank() && !isSavingName,
                    onClick = {
                        val newName = editNameText.trim()
                        if (newName.isNotEmpty() && newName != person.name) {
                            isSavingName = true
                            // Only the name changes here — amount/date are
                            // passed through unchanged (updatePerson writes
                            // all three together), so this can't silently
                            // clobber the customer's existing balance/date.
                            viewModel.savePerson(person.id, newName, person.amount, person.date) { success ->
                                isSavingName = false
                                if (success) showEditNameDialog = false
                            }
                        } else {
                            showEditNameDialog = false
                        }
                    }
                ) {
                    if (isSavingName) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("حفظ")
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isSavingName, onClick = { showEditNameDialog = false }) { Text("إلغاء") }
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
                TextButton(onClick = { notesViewModel.deleteNote(targetNote); deleteNoteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteNoteTarget = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun PersonHeader(name: String, avatarColor: Color, total: Double, debtsCount: Int, nf: NumberFormat) {
    Box(
        Modifier
            .fillMaxWidth()
            // topFlush = true: this sits directly beneath the TopAppBar's
            // own liquidGlassSurface, so it reads as a continuation of the
            // same glass panel instead of a second one with a shadow/
            // highlight/border seam at the boundary — see the bug note on
            // `topFlush` in LiquidGlass.kt.
            // طلب "تعميم ستايل الزجاج": highlight = false (topFlush وحدها ما
            // كانت تطفي topHighlight — راجع تعريفه بـ LiquidGlass.kt) +
            // baseAlpha = 0.72f بنفس قيمة اللوحة اللي فوقها مباشرة، عشان
            // تضل تبين كصفيحة زجاج واحدة مستمرة.
            .liquidGlassSurface(
                androidx.compose.ui.graphics.RectangleShape,
                topFlush = true,
                highlight = false,
                baseAlpha = 0.72f
            )
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                // BUG FIXED (الأفاتار طالعة صندوق رمادي باهت): `avatarColor`
                // كان بيوصل كباراميتر لهاد الـ Composable بس ما حدا يستخدمه -
                // الصندوق كان دايماً أبيض شفاف بغض النظر عن اسم العميل، عكس
                // نفس الأفاتار الملوّن اللي العميل ياخده بقائمة الديون
                // (PersonRow بـ DebtsScreen.kt). هيك صار شكلها هون مختلف عن
                // باقي التطبيق - بالضبط الصندوق الرمادي الباهت المحاط
                // بالدائرة الحمرا بالسكرين شوت.
                Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("إجمالي الديون", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(
                    "${nf.format(total)} ${AppSettingsState.currencySymbol}",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("$debtsCount عملية دين مسجلة", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
            }
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
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (isEditing) "تعديل الدين" else "إضافة دين جديد",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                AppTextField(
                    value = amount, onValueChange = onAmountChange,
                    label = "المبلغ (${AppSettingsState.currencySymbol})",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                AppTextField(
                    value = date, onValueChange = onDateChange,
                    label = "التاريخ",
                    leadingIcon = Icons.Default.CalendarMonth,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = note, onValueChange = onNoteChange,
                label = "ملاحظة (اختياري)",
                leadingIcon = Icons.Default.Notes,
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isEditing) {
                    TextButton(onClick = onCancelEdit) { Text("إلغاء") }
                    Spacer(Modifier.width(4.dp))
                }
                Button(onClick = onSubmit, shape = MaterialTheme.shapes.medium) {
                    Text(if (isEditing) "حفظ التعديل" else "إضافة الدين")
                }
            }
        }
    }
}

@Composable
private fun DebtRow(debt: Debt, nf: NumberFormat, onEdit: () -> Unit, onDelete: () -> Unit, onMarkPaid: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mark-as-paid: shared circular action button (see
            // ActionIconButton) — tapping it asks for confirmation, then
            // removes the debt and fires a "paid" notification.
            ActionIconButton(
                icon = Icons.Default.Check,
                tint = SuccessGreen,
                contentDescription = "تسجيل السداد",
                onClick = onMarkPaid
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(debt.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (debt.note.isNotBlank()) {
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Notes, contentDescription = null,
                            modifier = Modifier.size(13.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            debt.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2
                        )
                    }
                }
            }
            // FIX: these two used to be bare default IconButtons sitting
            // directly next to each other with no gap, so their 48dp touch
            // targets ran into one another and made mis-taps easy. They
            // also didn't match the circular, tinted affordance used for
            // every other action in the app (the check button right above,
            // and DeleteIconButton on the person list and materials list) —
            // so this row looked like it belonged to a different screen.
            // Edit now shares the exact same ActionIconButton (info-blue
            // tint) as the check button above and the delete "×" next to
            // it, so all three are pixel-identical in size and animation.
            // Gap widened to 16dp (see the matching fix on the person
            // list's check/delete pair) — two same-style filled circles
            // sitting only 10dp apart still read as one merged shape on
            // device.
            ActionIconButton(
                icon = Icons.Default.Edit,
                tint = InfoBlue,
                contentDescription = "تعديل",
                onClick = onEdit
            )
            Spacer(Modifier.width(16.dp))
            DeleteIconButton(onClick = onDelete, contentDescription = "حذف الدين")
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
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIconButton(
                icon = if (note.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                tint = if (note.isDone) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = "تم",
                onClick = onToggleDone
            )
            Spacer(Modifier.width(10.dp))
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
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (note.reminderAt > 0) {
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule, contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            remember(note.reminderAt) {
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(note.reminderAt))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            ActionIconButton(
                icon = Icons.Default.Edit,
                tint = InfoBlue,
                contentDescription = "تعديل الملاحظة",
                onClick = onEdit
            )
            Spacer(Modifier.width(16.dp))
            DeleteIconButton(onClick = onDelete, contentDescription = "حذف الملاحظة")
        }
    }
}
