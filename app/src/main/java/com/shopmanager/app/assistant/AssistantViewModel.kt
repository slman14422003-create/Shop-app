package com.shopmanager.app.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.assistant.engine.AmbiguityKind
import com.shopmanager.app.assistant.engine.AmbiguityOption
import com.shopmanager.app.assistant.engine.EngineOptions
import com.shopmanager.app.assistant.engine.HabitBook
import com.shopmanager.app.assistant.engine.ListMode
import com.shopmanager.app.assistant.engine.PersonalDictionary
import com.shopmanager.app.assistant.engine.RankedSpeech
import com.shopmanager.app.assistant.engine.RecentOp
import com.shopmanager.app.assistant.engine.RecurringAlert
import com.shopmanager.app.assistant.engine.SpeechRanker
import com.shopmanager.app.assistant.engine.Vocabulary
import com.shopmanager.app.assistant.engine.VoiceChoice
import com.shopmanager.app.assistant.engine.VoiceChoiceResult
import com.shopmanager.app.assistant.voice.VoiceInput
import com.shopmanager.app.assistant.voice.VoiceUiState
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

    val voice: VoiceInput = VoiceInput.get(application)

    private var habits = HabitBook.fromText(store.loadHabits())

    private val _pinned = MutableStateFlow(store.loadPinned())
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val _unheard = MutableStateFlow(store.loadUnheard())
    val unheard: StateFlow<List<String>> = _unheard.asStateFlow()

    private val _undoBar = MutableStateFlow<UndoBar?>(null)
    val undoBar: StateFlow<UndoBar?> = _undoBar.asStateFlow()

    private val _voicePreview = MutableStateFlow<RankedSpeech?>(null)
    val voicePreview: StateFlow<RankedSpeech?> = _voicePreview.asStateFlow()

    private val _listMode = MutableStateFlow(false)
    val listMode: StateFlow<Boolean> = _listMode.asStateFlow()

    private val _listItems = MutableStateFlow<List<String>>(emptyList())
    val listItems: StateFlow<List<String>> = _listItems.asStateFlow()

    private val _recurring = MutableStateFlow<List<RecurringAlert>>(emptyList())
    val recurring: StateFlow<List<RecurringAlert>> = _recurring.asStateFlow()

    private val _quick = MutableStateFlow<List<AssistantChip>>(emptyList())
    val quickButtons: StateFlow<List<AssistantChip>> = _quick.asStateFlow()

    private var dataVersion = 1L
    private var lastWasVoice = false
    private var undoBarJob: Job? = null
    private var warmed = false
    private val recentOps = ArrayList<RecentOp>()
    private var lastExecuted: Pair<Intent, Long>? = null

    private var engineCtx = ConversationContext()
    private var idSeq = System.currentTimeMillis()
    private var bound = false
    private var moodJob: Job? = null

    private class UndoEntry(val id: Long, val label: String, val block: suspend () -> Unit)
    private val undoStack = ArrayDeque<UndoEntry>()

    init {
        _messages.value = store.loadHistory()
        viewModelScope.launch { voice.results.collect { onVoiceResult(it) } }
        viewModelScope.launch {
            voice.uiState.collect { st ->
                when (st) {
                    is VoiceUiState.Listening -> setMood(RobotMood.LISTENING)
                    is VoiceUiState.Processing -> setMood(RobotMood.THINKING)
                    is VoiceUiState.Idle -> if (_mood.value == RobotMood.LISTENING) setMood(RobotMood.READY)
                    is VoiceUiState.Unavailable -> if (_mood.value == RobotMood.LISTENING) setMood(RobotMood.READY)
                    else -> {}
                }
            }
        }
    }

    override fun onCleared() {
        voice.release()
        super.onCleared()
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
            }.collect {
                _data.value = it
                dataVersion += 1
                refreshHabitViews()
                if (!warmed) {
                    warmed = true
                    viewModelScope.launch(Dispatchers.Default) {
                        val snap = buildSnapshot()
                        engine.prewarm(snap)
                        voice.preload(Vocabulary.build(snap))
                    }
                }
            }
        }
    }

    private fun refreshHabitViews() {
        val now = System.currentTimeMillis()
        val current = _data.value.materials.map { it.name }.toSet()
        _recurring.value = habits.recurring(now, current).map { a ->
            a.copy(name = displayName(a.name))
        }.take(3)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val base = listOf(
            "ضيف للنواقص" to Intent.ADD_SHORTAGE, "سجل دين" to Intent.ADD_DEBT, "شو النواقص" to Intent.LIST_SHORTAGES,
            "مجموع الديون" to Intent.QUERY_TOTAL_DEBTS, "ملخص اليوم" to Intent.SUMMARY
        )
        val ordered = habits.quickOrder(hour, base).map { (label, intent) ->
            when (intent) {
                Intent.ADD_SHORTAGE -> AssistantChip(label, "ضيف  للنواقص", false)
                Intent.ADD_DEBT -> AssistantChip(label, "سجل على ", false)
                else -> AssistantChip(label)
            }
        }
        _quick.value = _pinned.value.map { AssistantChip(it, it, true) } + ordered
    }

    private fun displayName(key: String): String {
        val d = _data.value
        val k = ArabicText.normalize(key)
        return (d.materials.map { it.name } + d.prices.keys + d.catalog.map { it.name })
            .firstOrNull { ArabicText.normalize(it.trim()) == k } ?: key
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
                typicalAmount = if (ds.size >= 2) ds.sumOf { it.amount } / ds.size else 0.0,
                debtAmounts = ds.map { it.amount }.filter { it > 0 }.takeLast(12)
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
        val now = System.currentTimeMillis()
        val recent = synchronized(recentOps) { recentOps.filter { now - it.at < 24 * 3_600_000L }.toList() }
        return habits.enrich(EngineSnapshot(persons, mats, _aliases.value, d.currency, now, version = dataVersion + _aliases.value.size, recentOps = recent))
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

    fun send(textIn: String, fromVoice: Boolean = false, listMode: Boolean = false) {
        val text = textIn.trim()
        if (text.isEmpty() || _thinking.value) return
        lastWasVoice = fromVoice
        if (!listMode && handleCardWord(text)) return
        _draft.value = ""
        addMessage(AssistantMessage(nextId(), true, text, System.currentTimeMillis()))
        _thinking.value = true
        setMood(RobotMood.THINKING)
        viewModelScope.launch {
            try {
                val snapshot = buildSnapshot()
                val ctxIn = engineCtx
                val out = withContext(Dispatchers.Default) { engine.understand(text, snapshot, ctxIn, EngineOptions(listMode = listMode)) }
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
        if (lastWasVoice && voice.settings.value.speakReplies && text.isNotBlank() && mood != RobotMood.THINKING) {
            voice.speak(text.lineSequence().first { it.isNotBlank() }.take(140))
        }
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
        c.ambiguity?.let { amb0 ->
            val amb = if (amb0.kind == AmbiguityKind.FUZZY_MATERIAL || amb0.kind == AmbiguityKind.MATERIAL) {
                amb0.copy(options = amb0.options.sortedByDescending { habits.usage[ArabicText.normalize(it.label.trim())] ?: 0 })
            } else amb0
            val sorted = if (amb === amb0) c else c.copy(ambiguity = amb)
            if (engineCtx.pending != null) engineCtx = engineCtx.copy(pending = sorted)
            reply(line.ifBlank { "" }.let { if (it.isEmpty()) amb.question else it + "\n" + amb.question }, RobotMood.CONFUSED,
                AssistantCard.Clarify(amb.question, amb.options, sorted))
            return
        }
        if (c.intent == Intent.UNKNOWN) { noteUnheard(c.rawText); reply(UNKNOWN_REPLY, RobotMood.CONFUSED, chips = starterChips()); return }
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

        if (c.isCorrection) cancelOldConfirms(c)
        val quickAdd = c.intent == Intent.ADD_SHORTAGE && !c.risky && c.confirmReasons.isEmpty()
        val mustConfirm = c.risky || c.confirmReasons.isNotEmpty() || (_settings.value.alwaysConfirm && !quickAdd) ||
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
        if (c.isCorrection) undoPreviousForCorrection(c)
        if (_settings.value.dryRun) {
            val card = AssistantCard.Result("تجربة بدون تنفيذ", c.understood.ifBlank { "لم يُنفَّذ شيء" } + "\nلم يتغير أي شيء في بياناتك", null, true)
            if (messageId != null) replaceMessage(messageId) { it.copy(card = card, time = System.currentTimeMillis()) } else reply(line, RobotMood.SUCCESS, card)
            setMood(RobotMood.SUCCESS, 3000L)
            return
        }
        val outcome = try {
            actions.execute(c, _data.value)
        } catch (e: Exception) {
            val card = AssistantCard.Result("تعذر التنفيذ", e.message ?: "تحقق من الاتصال بالإنترنت", null, false)
            if (messageId != null) replaceMessage(messageId) { it.copy(card = card, time = System.currentTimeMillis()) } else reply(line, RobotMood.CONFUSED, card)
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
        if (outcome.success) {
            logAction(outcome.title + ": " + outcome.details)
            recordHabits(c)
            lastExecuted = c.intent to System.currentTimeMillis()
            undoId?.let { showUndoBar(outcome.title + ": " + outcome.details, it) }
        }
        val card = AssistantCard.Result(outcome.title, outcome.details, undoId, outcome.success, if (outcome.success && c.intent == Intent.ADD_SHORTAGE) c else null)
        val chips = chipsFor(c)
        if (messageId != null) {
            replaceMessage(messageId) { it.copy(card = card, chips = chips, time = System.currentTimeMillis()) }
        } else {
            reply(line, if (outcome.success) RobotMood.SUCCESS else RobotMood.CONFUSED, card, chips)
        }
        setMood(if (outcome.success) RobotMood.SUCCESS else RobotMood.CONFUSED, 3000L)
        setLast(outcome.title)
    }

    private fun recordHabits(c: Command) {
        val now = System.currentTimeMillis()
        val zone = java.util.TimeZone.getDefault().getOffset(now) / 3_600_000
        habits = habits.record(c, now, zone)
        when (c.intent) {
            Intent.ADD_SHORTAGE -> c.materials.forEach { m -> synchronized(recentOps) { recentOps.add(RecentOp(Intent.ADD_SHORTAGE.name, m.name, 0.0, now)) } }
            Intent.ADD_DEBT -> c.person?.let { p -> synchronized(recentOps) { recentOps.add(RecentOp(Intent.ADD_DEBT.name, p.name, c.amount ?: 0.0, now)) } }
            else -> {}
        }
        val text = habits.toText()
        viewModelScope.launch(Dispatchers.IO) { store.saveHabits(text) }
        refreshHabitViews()
    }

    private fun showUndoBar(label: String, id: Long) {
        undoBarJob?.cancel()
        _undoBar.value = UndoBar(label, id, System.currentTimeMillis() + UNDO_BAR_MS)
        undoBarJob = viewModelScope.launch { delay(UNDO_BAR_MS); _undoBar.value = null }
    }

    fun dismissUndoBar() { undoBarJob?.cancel(); _undoBar.value = null }

    private fun noteUnheard(raw: String) {
        if (raw.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { store.addUnheard(raw); _unheard.value = store.loadUnheard() }
    }

    fun clearUnheard() { store.clearUnheard(); _unheard.value = emptyList() }

    private fun cancelOldConfirms(c: Command) {
        _messages.value.filter { it.card is AssistantCard.Confirm && (it.card as AssistantCard.Confirm).command.intent == c.intent }
            .forEach { replaceMessage(it.id) { m -> m.copy(card = null, text = m.text + "\n✖ استُبدلت بالتصحيح") } }
    }

    private suspend fun undoPreviousForCorrection(c: Command) {
        val last = lastExecuted
        val entry = undoStack.lastOrNull()
        if (last != null && entry != null && last.first == c.intent && System.currentTimeMillis() - last.second < 120_000L) {
            try {
                entry.block()
                undoStack.remove(entry)
                publishUndoable()
                _undoBar.value = null
                lastExecuted = null
            } catch (_: Exception) {
            }
        }
    }

    private fun handleCardWord(text: String): Boolean {
        if (text.split(' ').size > 3) return false
        val lastCard = _messages.value.lastOrNull { !it.fromUser }
        val conf = lastCard?.takeIf { it.card is AssistantCard.Confirm } ?: return false
        return when (VoiceChoice.parse(text)) {
            is VoiceChoiceResult.Confirm -> { addMessage(AssistantMessage(nextId(), true, text, System.currentTimeMillis())); confirm(conf.id); true }
            is VoiceChoiceResult.Cancel -> { addMessage(AssistantMessage(nextId(), true, text, System.currentTimeMillis())); cancel(conf.id); true }
            is VoiceChoiceResult.Undo -> { addMessage(AssistantMessage(nextId(), true, text, System.currentTimeMillis())); cancel(conf.id); true }
            else -> false
        }
    }

    // ------------------------------------------------------------------ الصوت

    fun startVoice() {
        _voicePreview.value = null
        viewModelScope.launch {
            val v = withContext(Dispatchers.Default) { Vocabulary.build(buildSnapshot()) }
            voice.start(v)
        }
    }

    fun stopVoice() { voice.stop() }

    fun cancelVoice() {
        voice.cancel()
        _listMode.value = false
        _listItems.value = emptyList()
    }

    private suspend fun onVoiceResult(r: com.shopmanager.app.assistant.engine.SpeechResult) {
        val snap = buildSnapshot()
        val ranked = withContext(Dispatchers.Default) { SpeechRanker.rank(r, snap) }
        val text = ranked.text.trim()
        if (text.isBlank()) {
            reply("ما سمعت شي واضح، جرّب مرة ثانية أو اكتب الطلب.", RobotMood.CONFUSED)
            if (_listMode.value) startVoice()
            return
        }
        if (_listMode.value) {
            val done = setOf("خلصت", "تم", "كفي", "انتهيت", "نفذ", "نفّذ", "خلص")
            if (ArabicText.normalize(text) in done.map { ArabicText.normalize(it) }) finishList()
            else {
                _listItems.value = _listItems.value + ListMode.items(text)
                startVoice()
            }
            return
        }
        if (voice.settings.value.autoSend && ranked.weak.isEmpty()) {
            send(text, fromVoice = true)
        } else {
            _voicePreview.value = ranked
        }
    }

    fun applyWeakAlternative(index: Int, alternative: String) {
        val p = _voicePreview.value ?: return
        val words = p.text.split(Regex("\\s+")).toMutableList()
        if (index !in words.indices) return
        val heard = words[index]
        words[index] = alternative
        learnFromCorrection(heard, alternative)
        _voicePreview.value = p.copy(text = words.joinToString(" "), weak = p.weak.filter { it.index != index })
    }

    fun sendPreview(edited: String) {
        val p = _voicePreview.value
        _voicePreview.value = null
        if (p != null) {
            val a = p.text.split(Regex("\\s+"))
            val b = edited.trim().split(Regex("\\s+"))
            if (a.size == b.size) for (i in a.indices) if (a[i] != b[i]) learnFromCorrection(a[i], b[i])
        }
        send(edited, fromVoice = true)
    }

    fun dismissPreview() { _voicePreview.value = null }

    private fun learnFromCorrection(heard: String, intended: String) {
        val canonical = nameContaining(intended) ?: return
        if (ArabicText.normalize(heard) == ArabicText.normalize(canonical)) return
        learnAlias(heard, canonical)
    }

    private fun nameContaining(token: String): String? {
        val d = _data.value
        val k = ArabicText.stripAl(ArabicText.canonicalToken(token))
        val names = d.persons.map { it.name } + d.materials.map { it.name } + d.prices.keys + d.catalog.map { it.name }
        return names.firstOrNull { n -> n.trim().split(Regex("\\s+")).any { ArabicText.stripAl(ArabicText.canonicalToken(it)) == k } }
    }

    fun startListMode() {
        _listMode.value = true
        _listItems.value = emptyList()
        startVoice()
    }

    fun editListItem(index: Int, text: String) {
        val l = _listItems.value.toMutableList()
        if (index !in l.indices) return
        if (text.isBlank()) l.removeAt(index) else l[index] = text.trim()
        _listItems.value = l
    }

    fun finishList() {
        voice.stop()
        val items = _listItems.value
        _listMode.value = false
        _listItems.value = emptyList()
        if (items.isNotEmpty()) send(items.joinToString("، "), fromVoice = true, listMode = true)
    }

    fun exportDictionary(): String = "[aliases]\n" + PersonalDictionary.export(_aliases.value) + "\n[habits]\n" + habits.toText()

    fun importDictionary(text: String): Int {
        val ai = text.indexOf("[aliases]")
        val hi = text.indexOf("[habits]")
        val aliasPart = if (ai >= 0) text.substring(ai + 9, if (hi > ai) hi else text.length) else text
        val habitPart = if (hi >= 0) text.substring(hi + 8) else ""
        val m = PersonalDictionary.import(aliasPart)
        m.forEach { (a, c) -> store.addAlias(a, c) }
        _aliases.value = store.loadAliases()
        if (habitPart.isNotBlank()) {
            habits = habits.merge(HabitBook.fromText(habitPart))
            val t = habits.toText()
            viewModelScope.launch(Dispatchers.IO) { store.saveHabits(t) }
            refreshHabitViews()
        }
        return m.size
    }

    fun pin(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val l = if (t in _pinned.value) _pinned.value - t else (_pinned.value + t).takeLast(8)
        _pinned.value = l
        viewModelScope.launch(Dispatchers.IO) { store.savePinned(l) }
        refreshHabitViews()
    }

    // ------------------------------------------------------------------ تعديل بطاقات التأكيد والنتيجة

    fun editLine(messageId: Long, index: Int, name: String?, quantity: Double?, unit: String?) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val card = msg.card as? AssistantCard.Confirm ?: return
        val c = editedCommand(card.command, index, name, quantity, unit) ?: return
        replaceMessage(messageId) { it.copy(card = confirmCard(c)) }
        engineCtx = engineCtx.copy(pending = null)
    }

    private fun editedCommand(c: Command, index: Int, name: String?, quantity: Double?, unit: String?): Command? {
        if (index !in c.materials.indices) return null
        var target = c.materials[index]
        if (name != null && ArabicText.normalize(name.trim()) != ArabicText.normalize(target.name)) {
            val found = displayNameOrNull(name) ?: run { setLast("الاسم لازم يكون من المواد الموجودة"); return null }
            target = target.copy(name = found, rawSpan = found, score = 1.0, level = MatchLevel.EXACT, candidates = emptyList(), fromHabit = false)
        }
        if (quantity != null) target = target.copy(quantity = quantity, fromHabit = false)
        if (unit != null) target = target.copy(unit = unit.ifBlank { null }, fromHabit = false)
        val list = c.materials.toMutableList()
        list[index] = target
        return c.copy(materials = list)
    }

    private fun displayNameOrNull(name: String): String? {
        val k = ArabicText.normalize(name.trim())
        val d = _data.value
        return (d.materials.map { it.name } + d.prices.keys + d.catalog.map { it.name }).firstOrNull { ArabicText.normalize(it.trim()) == k }
    }

    fun removeLine(messageId: Long, index: Int) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val card = msg.card as? AssistantCard.Confirm ?: return
        val c = card.command
        if (index !in c.materials.indices) return
        if (c.materials.size <= 1) { cancel(messageId); return }
        replaceMessage(messageId) { it.copy(card = confirmCard(c.copy(materials = c.materials.filterIndexed { i, _ -> i != index }))) }
    }

    fun toggleImportant(messageId: Long) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val card = msg.card as? AssistantCard.Confirm ?: return
        replaceMessage(messageId) { it.copy(card = confirmCard(card.command.copy(important = !card.command.important))) }
    }

    fun setQuickQuantity(messageId: Long, quantity: Double, unit: String?) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val card = msg.card as? AssistantCard.Confirm ?: return
        val c = card.command
        if (c.materials.isEmpty()) return
        val targets = c.materials.indices.filter { c.materials[it].quantity == null || c.materials[it].fromHabit }.ifEmpty { listOf(c.materials.lastIndex) }
        var cur = c
        for (i in targets) cur = editedCommand(cur, i, null, quantity, unit ?: "") ?: cur
        replaceMessage(messageId) { it.copy(card = confirmCard(cur)) }
    }

    fun editResult(messageId: Long, index: Int, name: String?, quantity: Double?, unit: String?, important: Boolean?) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val card = msg.card as? AssistantCard.Result ?: return
        val base = card.command ?: return
        var c = editedCommand(base, index, name, quantity, unit) ?: base
        if (important != null) c = c.copy(important = important)
        val entry = undoStack.firstOrNull { it.id == card.undoId }
        _thinking.value = true
        viewModelScope.launch {
            try {
                if (entry != null) { entry.block(); undoStack.remove(entry); publishUndoable(); _undoBar.value = null }
                execute(c, messageId, msg.text)
            } catch (e: Exception) {
                reply("تعذر تعديل النتيجة: ${e.message ?: "تحقق من الاتصال"}", RobotMood.CONFUSED)
            } finally { _thinking.value = false }
        }
    }

    fun otherChoice(messageId: Long) {
        val card = _messages.value.firstOrNull { it.id == messageId }?.card as? AssistantCard.Clarify ?: return
        _draft.value = card.command.rawText
        replaceMessage(messageId) { it.copy(card = null, text = it.text + "\n✎ اكتب الاسم الصحيح") }
        engineCtx = engineCtx.copy(pending = null)
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
                val raw = card.command.person?.takeIf { it.level != MatchLevel.EXACT }?.rawSpan ?: card.command.materials.firstOrNull { it.level != MatchLevel.EXACT }?.rawSpan
                if (raw != null) habits.recordChoice(raw, option.label).let { r ->
                    habits = r.book
                    r.alias?.let { a -> learnAlias(a.alias, a.canonical) }
                }
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
                f.add(CardField("الاسم", c.person?.name ?: "", toneOf(c.person?.level))); f.add(CardField("المبلغ", "${fmt(c.amount ?: 0.0)} $cur"))
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
                c.materials.forEach { f.add(CardField(it.name, materialQty(it), toneOf(it.level))) }
                if (c.important) f.add(CardField("الأهمية", "هامة ⭐"))
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
            Intent.ADD_PERSON -> {
                title = "إضافة عميل جديد"
                f.add(CardField("الاسم", c.person?.name ?: ""))
                c.phone?.let { f.add(CardField("الهاتف", it)) }
            }
            Intent.RENAME_PERSON -> {
                title = "تغيير اسم عميل"
                f.add(CardField("الاسم الحالي", c.person?.name ?: ""))
                f.add(CardField("الاسم الجديد", c.newName ?: ""))
            }
            Intent.DELETE_PERSON -> {
                title = "حذف عميل"
                f.add(CardField("الاسم", c.person?.name ?: ""))
                currentBalance(c)?.let { f.add(CardField("الرصيد الحالي", "${fmt(it)} $cur")) }
            }
            Intent.SET_PHONE -> {
                title = "حفظ رقم هاتف"
                f.add(CardField("العميل", c.person?.name ?: ""))
                f.add(CardField("الرقم", c.phone ?: ""))
            }
            Intent.DELETE_MATERIAL -> {
                title = "حذف مادة من القائمة الثابتة"
                f.add(CardField("المادة", c.materials.firstOrNull()?.name ?: ""))
            }
            Intent.RENAME_MATERIAL -> {
                title = "تغيير اسم مادة"
                f.add(CardField("الاسم الحالي", c.materials.firstOrNull()?.name ?: ""))
                f.add(CardField("الاسم الجديد", c.newName ?: ""))
            }
            else -> title = c.understood
        }
        return AssistantCard.Confirm(title, f, c.confirmReasons, c, c.risky)
    }

    private fun toneOf(level: MatchLevel?): Int = when (level) {
        MatchLevel.EXACT, MatchLevel.HIGH -> 1
        MatchLevel.MEDIUM -> 2
        else -> 0
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

    private fun starterChips(): List<AssistantChip> =
        _recurring.value.map { AssistantChip("قرّبت تخلص: ${it.name}", "ضيف ${it.name}") } + listOf(
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
            Intent.ADD_SHORTAGE -> {
                val taken = c.materials.map { it.name }.toSet()
                val inShort = _data.value.materials.map { it.name }.toSet()
                val comp = c.materials.flatMap { habits.companions(it.name, taken + inShort) }.distinct().take(2).map { displayName(it) }
                comp.map { AssistantChip("ضيف $it كمان؟", "ضيف $it") } + listOf(AssistantChip("شو النواقص"), AssistantChip("أضف مادة أخرى", "ضيف  للنواقص", false))
            }
            Intent.REMOVE_SHORTAGE -> listOf(AssistantChip("شو النواقص"), AssistantChip("أضف مادة أخرى", "ضيف  للنواقص", false))
            Intent.LIST_SHORTAGES -> listOf(AssistantChip("ضيف للنواقص", "ضيف  للنواقص", false), AssistantChip("إيجاد المواد الناقصة"))
            Intent.SET_PRICE, Intent.QUERY_PRICE -> listOf(AssistantChip("شو النواقص"), AssistantChip("ملخص اليوم"))
            Intent.ADD_PERSON -> listOfNotNull(p?.let { AssistantChip("سجل دين لـ $it", "سجل على $it ", false) }, AssistantChip("مجموع الديون"))
            Intent.RENAME_PERSON, Intent.SET_PHONE, Intent.DELETE_PERSON -> listOf(AssistantChip("مجموع الديون"), AssistantChip("ملخص اليوم"))
            Intent.RENAME_MATERIAL, Intent.DELETE_MATERIAL -> listOf(AssistantChip("شو النواقص"), AssistantChip("ملخص اليوم"))
            Intent.QUERY_TOTAL_DEBTS, Intent.QUERY_TOP_DEBTORS, Intent.QUERY_STALE_DEBTORS -> listOf(AssistantChip("أكثر 5 مدينين"), AssistantChip("من لم يسدد منذ مدة"))
            else -> listOf(AssistantChip("ملخص اليوم"))
        }
    }

    companion object {
        private const val MAX_UNDO = 10
        private const val UNDO_BAR_MS = 10_000L
        private const val UNKNOWN_REPLY = "ما فهمت الطلب تماماً 🤔 جرّب صياغة مثل: \"سجل على سامر 20 ألف\" أو اضغط ⋮ ← ما يستطيع المساعد فعله."
    }
}
