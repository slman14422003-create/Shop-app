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

    val uiState: StateFlow<DebtsUiState> = combine(personsFlow, debtsFlow) { persons, debts ->
        DebtsUiState(
            persons = persons,
            debts = debts,
            totalPersons = persons.size,
            totalDebts = debts.size,
            totalAmount = debts.sumOf { it.amount },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DebtsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

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
                    if (repo.personNameExists(name)) {
                        _message.value = "\"$name\" موجود مسبقاً!"
                        onDone(false)
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

    fun addOrUpdateDebt(existingId: String?, personId: String, amount: Double, date: String) {
        viewModelScope.launch {
            try {
                if (existingId == null) {
                    repo.addDebt(personId, amount, date)
                    _message.value = "تم إضافة الدين"
                } else {
                    repo.updateDebt(existingId, amount, date)
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
}
