package com.shopmanager.app.ui.debts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

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

    private val personsFlow = repo.listenPersons().catch { emit(emptyList()) }
    private val debtsFlow = repo.listenAllDebts().catch { emit(emptyList()) }

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DebtsUiState())

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
    // existing list the very first time data loads.
    private var knownPersonIds: Set<String>? = null

    init {
        viewModelScope.launch {
            NotificationHelper.ensureChannels(getApplication())
            uiState.collect { state ->
                if (state.isLoading) return@collect
                val currentIds = state.persons.map { it.id }.toSet()
                val previous = knownPersonIds
                if (previous != null) {
                    val newIds = currentIds - previous
                    val newPerson = state.persons.firstOrNull { it.id in newIds }
                    if (newPerson != null && settings.notificationsEnabled) {
                        val nf = NumberFormat.getNumberInstance(Locale("ar"))
                        NotificationHelper.showNewDebtNotification(
                            getApplication(), newPerson.name, nf.format(newPerson.amount), settings.currencySymbol
                        )
                    }
                }
                knownPersonIds = currentIds
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
                        if (amount > 0) {
                            repo.addDebt(existingPersonId, amount, date)
                        }
                        _message.value = "\"$name\" موجود مسبقاً — تمت إضافة الدين لسجله"
                        onDone(true)
                        return@launch
                    }
                    repo.addPerson(name, amount, date)
                    _message.value = "تم إضافة \"$name\""
                } else {
                    repo.updatePerson(existingId, name, amount, date)
                    _message.value = "تم تعديل العميل"
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
                if (settings.notificationsEnabled) {
                    val nf = NumberFormat.getNumberInstance(Locale("ar"))
                    NotificationHelper.showDebtPaidNotification(
                        getApplication(), person.name, nf.format(person.amount), settings.currencySymbol, person.id
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
                if (settings.notificationsEnabled) {
                    val nf = NumberFormat.getNumberInstance(Locale("ar"))
                    NotificationHelper.showDebtPaidNotification(
                        getApplication(), personName, nf.format(debt.amount), settings.currencySymbol, debt.id
                    )
                }
            } catch (e: Exception) {
                _message.value = "تعذر تسجيل السداد: ${e.message ?: "تحقق من الاتصال بالإنترنت"}"
            }
        }
    }
}
