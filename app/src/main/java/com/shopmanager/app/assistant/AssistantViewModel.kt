package com.shopmanager.app.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.assistant.engine.AmbiguityOption
import com.shopmanager.app.assistant.engine.ArabicText
import com.shopmanager.app.assistant.engine.AssistantEngine
import com.shopmanager.app.assistant.engine.Command
import com.shopmanager.app.assistant.engine.ConversationContext
import com.shopmanager.app.assistant.engine.EngineSnapshot
import com.shopmanager.app.assistant.engine.Intent
import com.shopmanager.app.assistant.engine.KnownMaterial
import com.shopmanager.app.assistant.engine.KnownPerson
import com.shopmanager.app.assistant.engine.MatchLevel
import com.shopmanager.app.assistant.engine.Screen
import com.shopmanager.app.data.materials.PriceChange
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** أحداث تنقل يطلبها المساعد؛ يستمع لها الجذر (ShopManagerApp) وينفذها عبر NavController. */
sealed interface AssistantNav {
    data class Open(val screen: Screen) : AssistantNav
    data class OpenPerson(val personId: String) : AssistantNav
    data class OpenMaterial(val name: String) : AssistantNav
    object OpenFinder : AssistantNav
}

/**
 * المساعد المحلي: يربط محرك الفهم (assistant/engine) بمستودعات التطبيق عبر [AssistantActions]، ويدير المحادثة والتأكيد والتراجع.
 * يُنشأ مرة واحدة على مستوى النشاط (viewModel()) ثم يُربط بـ DebtsViewModel وMaterialsViewModel عبر [bind].
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AssistantStore(application)
    private val engine = AssistantEngine()
    private val actions = AssistantActions(application)
    private val currencyRepo = SettingsRepository(application)

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _mood = MutableStateFlow(RobotMood.READY)
    val mood: StateFlow<RobotMood> = _mood.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<AssistantSettings> = _settings.asStateFlow()

    private val _aliases = MutableStateFlow(store.loadAliases())
    val aliases: StateFlow<Map<String, String>> = _aliases.asStateFlow()

    private val _actionLog = MutableStateFlow(store.loadActions())
    val actionLog: StateFlow<List<ActionLogEntry>> = _actionLog.asStateFlow()

    private val _undoable = MutableStateFlow<Set<Long>>(emptySet())
    val undoable: StateFlow<Set<Long>> = _undoable.asStateFlow()

    private val _lastStatus = MutableStateFlow("جاهز لأوامرك")
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    /** نص يُملأ في حقل الكتابة (زر "تعديل" أو اقتراح غير مكتمل). */
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _nav = MutableSharedFlow<AssistantNav>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<AssistantNav> = _nav.asSharedFlow()

    private val _data = MutableStateFlow(AssistantData())
    val data: StateFlow<AssistantData> = _data.asStateFlow()
    private val _history = MutableStateFlow<Map<String, List<PriceChange>>>(emptyMap())

    private var engineCtx = ConversationContext()
    private var idSeq = System.currentTimeMillis()
    private var bound = false
    private var moodJob: Job? = null

    private class UndoEntry(val id: Long, val label: String, val block: suspend () -> Unit)
    private val undoStack = ArrayDeque<UndoEntry>()

    init {
        _messages.value = store.loadHistory()
    }

    private fun nextId(): Long { idSeq += 1; return idSeq }

    // ------------------------------------------------------------------ الربط بالبيانات

    fun bind(debtsVm: DebtsViewModel, materialsVm: MaterialsViewModel) {
        if (bound) return
        bound = true
        actions.catalogProvider = { materialsVm.catalog.value }
        viewModelScope.launch {
            combine(
                debtsVm.uiState, materialsVm.uiState, materialsVm.catalog, materialsVm.priceHistory
            ) { d, m, catalog, history ->
                _history.value = history
                AssistantData(d.persons, d.debts, m.materials, m.prices, catalog, currencyRepo.currencySymbol)
            }.collect { _data.value = it }
        }
    }

    private fun buildSnapshot(): EngineSnapshot {
        val d = _data.value
        val byPerson = d.debts.groupBy { it.personId }
        val persons = d.persons.map { p ->
            val ds = byPerson[p.id].orEmpty()
            KnownPerson(
                id = p.id, name = p.name, balance = p.amount,
                oldestDebtAt = ds.filter { it.createdAt > 0 }.minOfOrNull { it.createdAt } ?: 0L,
                lastDebtAt = ds.maxOfOrNull { it.createdAt } ?: 0L,
                typicalAmount = if (ds.size >= 2) ds.sumOf { it.amount } / ds.size else 0.0
            )
        }
        val seen = HashSet<String>()
        val mats = ArrayList<KnownMaterial>()
        fun add(name: String, inShortage: Boolean) {
            val k = ArabicText.normalize(name.trim())
            if (k.isEmpty()) return
            if (seen.add(k)) mats.add(KnownMaterial(name.trim(), d.prices[name], inShortage))
        }
        d.materials.forEach { add(it.name, true) }
        d.prices.keys.forEach { add(it, false) }
        d.catalog.forEach { add(it.name, false) }
        return EngineSnapshot(persons, mats, _aliases.value, d.currency, System.currentTimeMillis())
    }

    // ------------------------------------------------------------------ المحادثة

    private fun addMessage(m: AssistantMessage) {
        _messages.value = (_messages.value + m).takeLast(AssistantStore.MAX_HISTORY)
        persist()
    }

    private fun replaceMessage(id: Long, transform: (AssistantMessage) -> AssistantMessage) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    private fun persist() {
        val snapshot = _messages.value
        viewModelScope.launch(Dispatchers.IO) { store.saveHistory(snapshot) }
    }

    private fun setMood(m: RobotMood, resetAfterMs: Long = 0L) {
        moodJob?.cancel()
        _mood.value = m
        if (resetAfterMs > 0) moodJob = viewModelScope.launch { delay(resetAfterMs); _mood.value = RobotMood.READY }
    }

    fun setDraft(text: String) { _draft.value = text }

    fun send(textIn: String) {
        val text = textIn.trim()
        if (text.isEmpty() || _thinking.value) return
        _draft.value = ""
        addMessage(AssistantMessage(nextId(), true, text, System.currentTimeMillis()))
        _thinking.value = true
        setMood(RobotMood.THINKING)
        viewModelScope.launch {
            try {
                val snapshot = buildSnapshot()
                val ctxIn = engineCtx
                val out = withContext(Dispatchers.Default) { engine.understand(text, snapshot, ctxIn) }
                engineCtx = out.context
                handle(out.commands)
            } catch (e: Exception) {
                reply("صار خطأ بسيط وأنا بحاول أفهم الطلب. جرّب مرة ثانية.", RobotMood.CONFUSED)
            } finally {
                _thinking.value = false
            }
        }
    }

    private fun reply(text: String, mood: RobotMood, card: AssistantCard? = null, chips: List<AssistantChip> = emptyList()): Long {
        val id = nextId()
        addMessage(AssistantMessage(id, false, text, System.currentTimeMillis(), card, chips))
        setMood(mood, if (mood == RobotMood.READY) 0L else 3000L)
        return id
    }

    private fun understoodLine(c: Command) = if (c.understood.isBlank()) "" else "فهمت: ${c.understood}"

    private suspend fun handle(commands: List<Command>) {
        if (commands.isEmpty()) { reply(UNKNOWN_REPLY, RobotMood.CONFUSED, chips = starterChips()); return }
        for (c in commands) process(c)
    }

    private suspend fun process(c: Command) {
        val line = understoodLine(c)
        // غموض: بطاقة توضيح بأزرار
        c.ambiguity?.let { amb ->
            reply(line.ifBlank { "" }.let { if (it.isEmpty()) amb.question else it + "\n" + amb.question }, RobotMood.CONFUSED,
                AssistantCard.Clarify(amb.question, amb.options, c))
            return
        }
        if (c.intent == Intent.UNKNOWN) { reply(UNKNOWN_REPLY, RobotMood.CONFUSED, chips = starterChips()); return }
        if (c.missing.isNotEmpty()) { reply(listOf(line, c.prompt ?: "ناقصني معلومة.").filter { it.isNotBlank() }.joinToString("\n"), RobotMood.CONFUSED); return }

        when (c.intent) {
            Intent.GREETING -> { reply("أهلاً! شو بتحب أساعدك اليوم؟ 👋", RobotMood.SUCCESS, chips = starterChips()); return }
            Intent.THANKS -> { reply("بالخدمة! 🙌", RobotMood.SUCCESS); return }
            Intent.HELP -> { reply("هاي أبرز الأشياء اللي بقدر أعملها:", RobotMood.SUCCESS, helpCard(), starterChips()); return }
            Intent.CANCEL -> { cancelPendingConfirms(); reply("تمام، لغيت. ✋", RobotMood.READY); return }
            Intent.CONFIRM -> {
                val last = _messages.value.lastOrNull { it.card is AssistantCard.Confirm }
                if (last != null) confirm(last.id) else reply("ما في شي أأكده الآن.", RobotMood.CONFUSED)
                return
            }
            Intent.UNDO -> { undoLast(); return }
            Intent.REPEAT -> { reply("ما في شي أكرره بعد.", RobotMood.CONFUSED); return }
            Intent.OPEN -> { openFor(c); reply(line, RobotMood.SUCCESS, chips = chipsFor(c)); return }
            Intent.RUN_MISSING_FINDER -> { _nav.tryEmit(AssistantNav.OpenFinder); reply(line, RobotMood.SUCCESS); return }
            else -> {}
        }

        if (c.isReadOnly) {
            val card = AssistantQueries.answer(c, _data.value, _history.value, System.currentTimeMillis())
            if (card == null) { reply(UNKNOWN_REPLY, RobotMood.CONFUSED); return }
            setLast(card.title)
            reply(line, RobotMood.SUCCESS, card, chipsFor(c))
            return
        }

        // أوامر تغيّر البيانات
        val mustConfirm = c.risky || c.confirmReasons.isNotEmpty() || _settings.value.alwaysConfirm ||
            c.person?.level == MatchLevel.NEW || c.intent == Intent.DELETE_LAST
        if (mustConfirm) {
            reply(line, RobotMood.READY, confirmCard(c))
        } else {
            execute(c, messageId = null, line = line)
        }
    }

    // ------------------------------------------------------------------ التأكيد والتنفيذ

    fun confirm(messageId: Long) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val card = msg.card as? AssistantCard.Confirm ?: return
        _thinking.value = true
        viewModelScope.launch {
            try { execute(card.command, messageId, msg.text) } finally { _thinking.value = false }
        }
    }

    fun cancel(messageId: Long) {
        replaceMessage(messageId) { it.copy(card = null, text = it.text.ifBlank { "" } + "\n✖ أُلغيت العملية") }
        engineCtx = engineCtx.copy(pending = null)
        setMood(RobotMood.READY)
    }

    private fun cancelPendingConfirms() {
        _messages.value.filter { it.card is AssistantCard.Confirm }.forEach { cancel(it.id) }
    }

    /** زر "تعديل": يعيد نص الطلب إلى حقل الكتابة ويلغي البطاقة. */
    fun edit(messageId: Long) {
        val card = _messages.value.firstOrNull { it.id == messageId }?.card as? AssistantCard.Confirm ?: return
        _draft.value = card.command.rawText
        cancel(messageId)
    }

    private suspend fun execute(c: Command, messageId: Long?, line: String) {
        if (c.intent == Intent.DELETE_LAST) {
            messageId?.let { replaceMessage(it) { m -> m.copy(card = null) } }
            undoLast(); return
        }
        val outcome = try {
            actions.execute(c, _data.value)
        } catch (e: Exception) {
            val card = AssistantCard.Result("تعذر التنفيذ", e.message ?: "تحقق من الاتصال بالإنترنت", null, false)
            if (messageId != null) replaceMessage(messageId) { it.copy(card = card) } else reply(line, RobotMood.CONFUSED, card)
            setMood(RobotMood.CONFUSED, 3000L)
            return
        }
        c.aliasToLearn?.let { learnAlias(it.alias, it.canonical) }
        var undoId: Long? = null
        outcome.undo?.let { block ->
            val id = nextId()
            undoStack.addLast(UndoEntry(id, outcome.title + " — " + outcome.details, block))
            while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            publishUndoable()
            undoId = id
        }
        if (outcome.success) logAction(outcome.title + ": " + outcome.details)
        val card = AssistantCard.Result(outcome.title, outcome.details, undoId, outcome.success)
        val chips = chipsFor(c)
        if (messageId != null) {
            replaceMessage(messageId) { it.copy(card = card, chips = chips) }
        } else {
            reply(line, if (outcome.success) RobotMood.SUCCESS else RobotMood.CONFUSED, card, chips)
        }
        setMood(if (outcome.success) RobotMood.SUCCESS else RobotMood.CONFUSED, 3000L)
        setLast(outcome.title)
    }

    // ------------------------------------------------------------------ توضيح الغموض

    fun choose(messageId: Long, option: AmbiguityOption) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val card = msg.card as? AssistantCard.Clarify ?: return
        replaceMessage(messageId) { it.copy(card = null, text = it.text + "\n✔ ${option.label}") }
        _thinking.value = true
        viewModelScope.launch {
            try {
                val resolved = withContext(Dispatchers.Default) {
                    engine.resolveChoice(card.command, option, buildSnapshot(), engineCtx)
                }
                engineCtx = engineCtx.copy(pending = if (resolved.isReady) null else resolved)
                resolved.aliasToLearn?.let { learnAlias(it.alias, it.canonical) }
                process(resolved)
            } finally { _thinking.value = false }
        }
    }

    /** "لا" على سؤال "تقصد X؟". */
    fun dismissClarify(messageId: Long) {
        replaceMessage(messageId) { it.copy(card = null, text = it.text + "\n✖ تمام، اكتب الاسم بشكل أوضح") }
        engineCtx = engineCtx.copy(pending = null)
    }

    // ------------------------------------------------------------------ التراجع

    fun undo(undoId: Long) {
        val entry = undoStack.firstOrNull { it.id == undoId } ?: return
        runUndo(entry)
    }

    fun undoLast() {
        val entry = undoStack.lastOrNull()
        if (entry == null) { reply("ما في عملية أتراجع عنها.", RobotMood.CONFUSED); return }
        runUndo(entry)
    }

    private fun runUndo(entry: UndoEntry) {
        _thinking.value = true
        viewModelScope.launch {
            try {
                entry.block()
                undoStack.remove(entry)
                publishUndoable()
                // أُغلق زر التراجع في البطاقة القديمة
                _messages.value = _messages.value.map { m ->
                    val c = m.card
                    if (c is AssistantCard.Result && c.undoId == entry.id) m.copy(card = c.copy(undoId = null, title = "تم التراجع")) else m
                }
                persist()
                reply("تم التراجع عن: ${entry.label}", RobotMood.SUCCESS)
                logAction("تراجع: ${entry.label}")
            } catch (e: Exception) {
                reply("تعذر التراجع: ${e.message ?: "تحقق من الاتصال"}", RobotMood.CONFUSED)
            } finally { _thinking.value = false }
        }
    }

    private fun publishUndoable() { _undoable.value = undoStack.map { it.id }.toSet() }

    // ------------------------------------------------------------------ سجل وإعدادات

    private fun logAction(text: String) {
        val list = (_actionLog.value + ActionLogEntry(nextId(), text, System.currentTimeMillis())).takeLast(AssistantStore.MAX_ACTIONS)
        _actionLog.value = list
        viewModelScope.launch(Dispatchers.IO) { store.saveActions(list) }
    }

    private fun setLast(text: String) { _lastStatus.value = text }

    /** تراجع عن عملية من سجل "آخر ما نفّذه المساعد" إن كانت ما تزال ضمن آخر 10. */
    fun undoFromLog(entry: ActionLogEntry) {
        val match = undoStack.lastOrNull { entry.text.endsWith(it.label) || it.label == entry.text.substringAfter(": ", entry.text) }
            ?: undoStack.lastOrNull { entry.text.contains(it.label.substringAfter(" — ")) }
        if (match != null) runUndo(match)
    }

    fun canUndoLog(entry: ActionLogEntry): Boolean =
        undoStack.any { entry.text.endsWith(it.label) || it.label == entry.text.substringAfter(": ", entry.text) }

    fun updateSettings(s: AssistantSettings) {
        _settings.value = s
        viewModelScope.launch(Dispatchers.IO) { store.saveSettings(s) }
    }

    fun clearHistory() {
        _messages.value = emptyList()
        engineCtx = ConversationContext()
        viewModelScope.launch(Dispatchers.IO) { store.clearHistory() }
    }

    private fun learnAlias(alias: String, canonical: String) {
        store.addAlias(alias, canonical)
        _aliases.value = store.loadAliases()
    }

    fun removeAlias(alias: String) { store.removeAlias(alias); _aliases.value = store.loadAliases() }
    fun clearAliases() { store.clearAliases(); _aliases.value = emptyMap() }

    // ------------------------------------------------------------------ بطاقات وأزرار

    private fun openFor(c: Command) {
        val pid = c.person?.id
        val scr = c.screen
        when {
            pid != null -> _nav.tryEmit(AssistantNav.OpenPerson(pid))
            c.materials.isNotEmpty() -> _nav.tryEmit(AssistantNav.OpenMaterial(c.materials[0].name))
            scr != null -> _nav.tryEmit(AssistantNav.Open(scr))
        }
    }

    private fun fmt(v: Double) = Formatters.number(v)

    private fun confirmCard(c: Command): AssistantCard.Confirm {
        val cur = _data.value.currency
        val f = ArrayList<CardField>()
        val title: String
        when (c.intent) {
            Intent.ADD_DEBT -> {
                title = if (c.person?.level == MatchLevel.NEW) "تسجيل دين لعميل جديد" else "تسجيل دين"
                f.add(CardField("الاسم", c.person?.name ?: "")); f.add(CardField("المبلغ", "${fmt(c.amount ?: 0.0)} $cur"))
                c.date?.let { f.add(CardField("التاريخ", it.label)) }
            }
            Intent.PAY_DEBT -> {
                title = if (c.full || c.amount == null) "تسديد كامل" else "تسديد جزئي"
                f.add(CardField("الاسم", c.person?.name ?: ""))
                f.add(CardField("المبلغ", if (c.full || c.amount == null) "كامل الرصيد" else "${fmt(c.amount)} $cur"))
                currentBalance(c)?.let { f.add(CardField("الرصيد الحالي", "${fmt(it)} $cur")) }
            }
            Intent.REDUCE_DEBT -> {
                title = "تخفيض دين"
                f.add(CardField("الاسم", c.person?.name ?: "")); f.add(CardField("بمقدار", "${fmt(c.amount ?: 0.0)} $cur"))
                currentBalance(c)?.let { f.add(CardField("الرصيد الحالي", "${fmt(it)} $cur")) }
            }
            Intent.DELETE_DEBT -> {
                title = "حذف عملية دين"
                f.add(CardField("الاسم", c.person?.name ?: ""))
                f.add(CardField("العملية", c.amount?.let { "${fmt(it)} $cur" } ?: "آخر عملية"))
            }
            Intent.ADD_SHORTAGE -> {
                title = "إضافة للنواقص"
                c.materials.forEach { f.add(CardField(it.name, materialQty(it))) }
            }
            Intent.REMOVE_SHORTAGE -> {
                title = "حذف من النواقص"
                c.materials.forEach { f.add(CardField(it.name, "")) }
            }
            Intent.SET_PRICE -> {
                title = "تعديل السعر"
                val m = c.materials.firstOrNull()
                f.add(CardField("المادة", m?.name ?: ""))
                val old = m?.let { mm -> _data.value.prices.entries.firstOrNull { ArabicText.normalize(it.key) == ArabicText.normalize(mm.name) }?.value }
                old?.let { f.add(CardField("السعر الحالي", "${fmt(it)} $cur")) }
                f.add(CardField("السعر الجديد", "${fmt(c.amount ?: 0.0)} $cur"))
            }
            Intent.ADD_MATERIAL -> {
                title = "إضافة مادة جديدة"
                f.add(CardField("الاسم", c.materials.firstOrNull()?.name ?: ""))
                c.amount?.let { f.add(CardField("السعر", "${fmt(it)} $cur")) }
            }
            Intent.ADD_NOTE -> { title = "إضافة ملاحظة"; f.add(CardField("النص", c.text ?: "")) }
            Intent.REMINDER -> {
                title = "تذكير"
                f.add(CardField("النص", c.text ?: "")); f.add(CardField("الموعد", c.date?.label ?: ""))
            }
            Intent.DELETE_LAST -> { title = "التراجع عن آخر عملية"; f.add(CardField("العملية", undoStack.lastOrNull()?.label ?: "—")) }
            else -> title = c.understood
        }
        return AssistantCard.Confirm(title, f, c.confirmReasons, c, c.risky)
    }

    private fun materialQty(m: com.shopmanager.app.assistant.engine.MaterialRef): String {
        val q = m.quantity ?: return ""
        return listOfNotNull(fmt(q).takeIf { !(q == 1.0 && m.unit != null) }, m.unit, m.pack).joinToString(" ")
    }

    private fun currentBalance(c: Command): Double? =
        _data.value.persons.firstOrNull { it.id == c.person?.id }?.amount

    private fun helpCard(): AssistantCard.Answer =
        AssistantCard.Answer("أمثلة لكل أمر", AssistantQueries.capabilityGroups.flatMap { (g, ex) ->
            listOf(CardField("— $g —", "")) + ex.take(4).map { CardField(it, "") }
        })

    private fun starterChips(): List<AssistantChip> = listOf(
        AssistantChip("ملخص اليوم"), AssistantChip("شو النواقص"), AssistantChip("مجموع الديون"), AssistantChip("أكثر 5 مدينين")
    )

    private fun chipsFor(c: Command): List<AssistantChip> {
        val p = c.person?.name
        return when (c.intent) {
            Intent.ADD_DEBT -> listOfNotNull(
                AssistantChip("سجل دين آخر", "سجل على ", false),
                p?.let { AssistantChip("عرض الرصيد", "كم على $it") }
            )
            Intent.PAY_DEBT, Intent.REDUCE_DEBT -> listOfNotNull(p?.let { AssistantChip("عرض الرصيد", "كم على $it") }, AssistantChip("مجموع الديون"))
            Intent.QUERY_BALANCE -> listOfNotNull(p?.let { AssistantChip("سجل دين آخر", "سجل على $it ", false) }, AssistantChip("مجموع الديون"))
            Intent.ADD_SHORTAGE, Intent.REMOVE_SHORTAGE -> listOf(AssistantChip("شو النواقص"), AssistantChip("أضف مادة أخرى", "ضيف  للنواقص", false))
            Intent.LIST_SHORTAGES -> listOf(AssistantChip("ضيف للنواقص", "ضيف  للنواقص", false), AssistantChip("إيجاد المواد الناقصة"))
            Intent.SET_PRICE, Intent.QUERY_PRICE -> listOf(AssistantChip("شو النواقص"), AssistantChip("ملخص اليوم"))
            Intent.QUERY_TOTAL_DEBTS, Intent.QUERY_TOP_DEBTORS, Intent.QUERY_STALE_DEBTORS -> listOf(AssistantChip("أكثر 5 مدينين"), AssistantChip("من لم يسدد منذ مدة"))
            else -> listOf(AssistantChip("ملخص اليوم"))
        }
    }

    companion object {
        private const val MAX_UNDO = 10
        private const val UNKNOWN_REPLY = "ما فهمت الطلب تماماً 🤔 جرّب صياغة مثل: \"سجل على سامر 20 ألف\" أو اضغط ⋮ ← ما يستطيع المساعد فعله."
    }
}
