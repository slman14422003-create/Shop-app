package com.shopmanager.app.ui.debts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.backup.InstantBackupWorker
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DebtsUiState(
    val persons: List<Person> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val totalPersons: Int = 0,
    val totalDebts: Int = 0,
    val totalAmount: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class DebtsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DebtsRepository()
    private val settings = SettingsRepository(application)

    // PERF: Firestore's snapshot listener can re-fire with metadata-only
    // changes (e.g. local write acknowledged by the server) that produce an
    // identical list. Without distinctUntilChanged, each of those re-runs
    // the groupBy/sum below and pushes a new UI state, which the whole
    // screen recomposes for even though nothing the person can see changed
    // — a real contributor to the app feeling heavier than it should,
    // especially right after a write.
    // Flips true if a live listener ever errors out (as opposed to just
    // legitimately returning an empty list) — surfaced below as
    // [hasSyncError] so Settings can offer restoring the last local daily
    // backup (see BackupManager) instead of silently showing "no data".
    private val _hasSyncError = MutableStateFlow(false)
    val hasSyncError: StateFlow<Boolean> = _hasSyncError

    // BUG FIXED (النسخ الاحتياطي التلقائي لا يعمل إلا على الجهاز الذي أضاف
    // المادة): InstantBackupWorker used to be requested only from this
    // ViewModel's own add/edit/delete functions further below — i.e. only
    // on the device where the person physically made the change. Every
    // other device signed into the same shop only ever learns about that
    // change through this same Firestore listener (personsFlow/
    // debtsFlow), so it never took its own local snapshot until *its own*
    // user happened to add something. personsFlow/debtsFlow below now
    // request a local instant backup on every real update they see —
    // regardless of which device produced it — so every signed-in device
    // stays backed up together instead of only the one that made the
    // edit. That onEach also resets [_hasSyncError] back to false: it
    // used to only ever be set true (on a listener error) and never
    // cleared, so a single network blip left Settings offering to
    // restore from the local backup forever afterward, even long after
    // the connection and live data had fully recovered.
    private val personsFlow = repo.listenPersons()
        .catch { _hasSyncError.value = true; emit(emptyList()) }
        .distinctUntilChanged()
        .onEach { _hasSyncError.value = false; InstantBackupWorker.requestNow(getApplication()) }
    private val debtsFlow = repo.listenAllDebts()
        .catch { _hasSyncError.value = true; emit(emptyList()) }
        .distinctUntilChanged()
        .onEach { _hasSyncError.value = false; InstantBackupWorker.requestNow(getApplication()) }

    /**
     * BUG FIXED: `persons.amount` used to be a separately-maintained field
     * that only ever got set once at creation (and, previously, via a manual
     * edit) — it was never recalculated when a debt was added, edited,
     * deleted, or paid off. So it silently drifted away from the real sum of
     * that person's debts (visible as "أكبر الديون" showing numbers that
     * didn't match the person's own debt history). Every displayed total
     * now comes from summing `debts` grouped by personId, computed live
     * alongside everything else — there's no separate field left to fall
     * out of sync.
     */
    // BUG FIXED (dashboard totals "jitter" on cold start): Firestore's
    // snapshot listener fires once from the local cache and again once the
    // server responds - separately for persons AND for debts. combine()
    // re-emits on every one of those, so a fresh app launch could push 2-4
    // different partial totals in quick succession before landing on the
    // real one. Each of those was a separate target for
    // AnimatedCounterText's count-up animation, so the number visibly
    // jumped/vibrated through intermediate values instead of counting up
    // once to the final total. debounce() coalesces that burst into a
    // single emission once both listeners have gone quiet for a moment -
    // normal live updates (a debt added/edited later) are a single change
    // with nothing to coalesce, so they still feel instant.
    val uiState: StateFlow<DebtsUiState> = combine(personsFlow, debtsFlow) { persons, debts ->
        val totalsByPerson = debts.groupBy { it.personId }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val enrichedPersons = persons.map { it.copy(amount = totalsByPerson[it.id] ?: 0.0) }
        DebtsUiState(
            persons = enrichedPersons,
            debts = debts,
            totalPersons = persons.size,
            totalDebts = debts.size,
            totalAmount = debts.sumOf { it.amount },
            isLoading = false
        )
    }.debounce(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DebtsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Pull-to-refresh: forces a real server round trip (see repo docs) and
     * keeps the spinner up for a minimum, tactile duration either way. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val start = System.currentTimeMillis()
            try {
                repo.refreshFromServer()
            } catch (_: Exception) {
                // The live listeners are the source of truth; a failed
                // manual refresh just means "nothing new from the server
                // right now", not an error worth surfacing.
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < 400) kotlinx.coroutines.delay(400 - elapsed)
            _isRefreshing.value = false
        }
    }

    // null = haven't loaded once yet; used to avoid notifying for the whole
    // existing list the very first time data loads (same pattern as
    // MaterialsViewModel.lastNotifiedShortages).
    //
    // BUG FIXED (missing notification): this used to track *person* ids,
    // so it only ever fired for a brand-new customer. Adding a new debt to
    // an *existing* customer from Person Detail — "إضافة دين" on someone
    // already in the list, which is the far more common case day to day —
    // never notified anything, in-app or otherwise. Tracking debt ids
    // instead of person ids catches both: a new customer's first debt (one
    // new debt id) and a new debt on an existing customer (also one new
    // debt id), with one consistent code path instead of two different,
    // incompletely-covered ones. Also now handles more than one new debt
    // appearing between two updates (e.g. from another device) instead of
    // only ever notifying about the first.
    private var knownDebtIds: Set<String>? = null

    init {
        viewModelScope.launch {
            NotificationHelper.ensureChannels(getApplication())
            uiState.collect { state ->
                if (state.isLoading) return@collect
                val currentDebtIds = state.debts.map { it.id }.toSet()
                val previous = knownDebtIds
                if (previous != null) {
                    val newDebtIds = currentDebtIds - previous
                    if (newDebtIds.isNotEmpty() && settings.notificationsEnabled) {
                        val personsById = state.persons.associateBy { it.id }
                        state.debts.filter { it.id in newDebtIds }.forEach { debt ->
                            val personName = personsById[debt.personId]?.name ?: "عميل"
                            NotificationHelper.showNewDebtNotification(
                                getApplication(), personName, Formatters.number(debt.amount), settings.currencySymbol
                            )
                        }
                    }
                }
                knownDebtIds = currentDebtIds
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun savePerson(existingId: String?, name: String, amount: Double, date: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (existingId == null) {
                    val existingPersonId = repo.findPersonIdByName(name)
                    if (existingPersonId != null) {
                        // Same name already exists (most commonly: this
                        // customer was paid off earlier, so they're still in
                        // the list at a zero balance). Route this into
                        // "add a debt to them" instead of a dead-end
                        // duplicate error — see findPersonIdByName().
                        //
                        // BUG FIXED (misleading message): the "تمت إضافة
                        // الدين لسجله" (debt added to their record) message
                        // used to show unconditionally here, even when
                        // amount was 0 — where addDebt() below it is never
                        // actually called. Someone re-adding an existing
                        // name with no amount would be told a debt was
                        // added when nothing was written at all.
                        if (amount > 0) {
                            repo.addDebt(existingPersonId, amount, date)
                            _message.value = "\"$name\" موجود مسبقاً — تمت إضافة الدين لسجله"
                            InstantBackupWorker.requestNow(getApplication())
                        } else {
                            _message.value = "\"$name\" موجود مسبقاً بالفعل"
                        }
                        onDone(true)
                        return@launch
                    }
                    repo.addPerson(name, amount, date)
                    _message.value = "تم إضافة \"$name\""
                    InstantBackupWorker.requestNow(getApplication())
                } else {
                    repo.updatePerson(existingId, name, amount, date)
                    _message.value = "تم تعديل العميل"
                    InstantBackupWorker.requestNow(getApplication())
                }
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذر الحفظ: ${e.message ?: "تحقق من الاتصال بالإنترنت"}"
                onDone(false)
            }
        }
    }

    fun deletePerson(id: String) {
        viewModelScope.launch {
            try {
                repo.deletePersonWithDebts(id)
                _message.value = "تم حذف العميل وديونه"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "خطأ في الحذف: ${e.message}"
            }
        }
    }

    fun debtsForPerson(personId: String) = repo.listenDebtsForPerson(personId)

    fun addOrUpdateDebt(existingId: String?, personId: String, amount: Double, date: String, note: String = "") {
        viewModelScope.launch {
            try {
                if (existingId == null) {
                    repo.addDebt(personId, amount, date, note)
                    _message.value = "تم إضافة الدين"
                } else {
                    repo.updateDebt(existingId, amount, date, note)
                    _message.value = "تم تعديل الدين"
                }
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر الحفظ: ${e.message ?: "تحقق من الاتصال بالإنترنت"}"
            }
        }
    }

    fun deleteDebt(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteDebt(id)
                _message.value = "تم حذف الدين"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "خطأ في الحذف: ${e.message}"
            }
        }
    }

    /**
     * Settles a person's whole balance from the main debts list (the
     * checkmark on the person row): removes all their debts and fires the
     * same "paid" notification as marking one debt as paid.
     */
    fun markPersonAsPaid(person: Person) {
        viewModelScope.launch {
            try {
                repo.markAllDebtsAsPaid(person.id)
                _message.value = "تم تسجيل سداد \"${person.name}\" ✅"
                InstantBackupWorker.requestNow(getApplication())
                if (settings.notificationsEnabled) {
                    NotificationHelper.showDebtPaidNotification(
                        getApplication(), person.name, Formatters.number(person.amount), settings.currencySymbol, person.id
                    )
                }
            } catch (e: Exception) {
                _message.value = "تعذر تسجيل السداد: ${e.message ?: "تحقق من الاتصال بالإنترنت"}"
            }
        }
    }

    /** Marks [debt] as paid: removes it (the person's total then updates automatically) and (if enabled) notifies. */
    fun markDebtAsPaid(debt: Debt, personName: String) {
        viewModelScope.launch {
            try {
                repo.markDebtAsPaid(debt.id)
                _message.value = "تم تسجيل السداد ✅"
                InstantBackupWorker.requestNow(getApplication())
                if (settings.notificationsEnabled) {
                    NotificationHelper.showDebtPaidNotification(
                        getApplication(), personName, Formatters.number(debt.amount), settings.currencySymbol, debt.id
                    )
                }
            } catch (e: Exception) {
                _message.value = "تعذر تسجيل السداد: ${e.message ?: "تحقق من الاتصال بالإنترنت"}"
            }
        }
    }
}
