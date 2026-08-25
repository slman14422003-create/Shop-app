package com.shopmanager.app.ui.materials

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MaterialsUiState(
    val materials: List<Material> = emptyList(),
    val prices: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true
)

class MaterialsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MaterialsRepository()
    private val settings = SettingsRepository(application)

    private val section = MutableStateFlow("main")

    // PERF: same fix as DebtsViewModel — Firestore's listener can re-fire
    // with a list that's identical to the last one (e.g. a local write
    // getting server-acknowledged). distinctUntilChanged stops that from
    // recomputing/recomposing the whole screen for no visible change.
    // See DebtsViewModel.hasSyncError — same idea: a real listener error
    // (not just a legitimately empty collection) flips this, so Settings
    // can offer restoring the last local daily backup.
    private val _hasSyncError = MutableStateFlow(false)
    val hasSyncError: StateFlow<Boolean> = _hasSyncError

    private val materialsFlow = channelFlow {
        section.collect { s ->
            repo.listenMaterials(s).collect { send(it) }
        }
    }.catch { _hasSyncError.value = true; emit(emptyList()) }.distinctUntilChanged()

    private val pricesFlow = repo.listenPrices()
        .catch { _hasSyncError.value = true; emit(emptyMap()) }
        .distinctUntilChanged()

    val uiState: StateFlow<MaterialsUiState> = combine(materialsFlow, pricesFlow) { materials, prices ->
        MaterialsUiState(materials = materials, prices = prices, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaterialsUiState())

    val catalog: StateFlow<List<MaterialCatalogItem>> = repo.listenCatalog()
        .catch { emit(emptyList()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Pull-to-refresh: forces a real server round trip and keeps the
     * spinner up for a minimum, tactile duration either way. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val start = System.currentTimeMillis()
            try {
                repo.refreshFromServer()
            } catch (_: Exception) {
                // Live listeners remain the source of truth.
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < 400) kotlinx.coroutines.delay(400 - elapsed)
            _isRefreshing.value = false
        }
    }

    // Every material on this list is, by definition, something the shop is
    // short on (a live shopping list) - so we notify about the *whole* list,
    // not a filtered "low stock" subset. Only re-notify when the set of
    // names actually changes - not on every unrelated Firestore update
    // (e.g. a price edit).
    //
    // BUG FIXED (notification fired on every app open): this used to start
    // at emptySet() instead of null. That meant the very first emission
    // after opening the app - which is just the *existing* shortage list
    // loading from Firestore, not a new addition - always looked
    // "different from empty" and fired a shopping-list notification. Open
    // the app once with 3 items already on the list and you'd immediately
    // get a "3 مواد ناقصة" notification, every single time, even though
    // nothing had actually changed since last time. null now means "haven't
    // seen a real list yet" (same pattern as knownPersonIds in
    // DebtsViewModel): the first load only seeds the baseline silently, and
    // a notification only fires on every load *after* that one, when the
    // set of names genuinely changes - i.e. someone actually adding or
    // clearing a shortage, in this session or from another device.
    private var lastNotifiedShortages: Set<String>? = null

    init {
        viewModelScope.launch {
            NotificationHelper.ensureChannels(getApplication())
            uiState.collect { state ->
                if (state.isLoading) return@collect
                val shortageNames = state.materials.map { it.name }.toSet()
                val previous = lastNotifiedShortages
                if (previous != null && shortageNames != previous) {
                    if (shortageNames.isEmpty()) {
                        NotificationHelper.cancelShoppingListNotification(getApplication())
                    } else if (settings.notificationsEnabled) {
                        NotificationHelper.showShoppingListNotification(getApplication(), shortageNames.toList())
                    }
                }
                lastNotifiedShortages = shortageNames
            }
        }
    }

    fun addMaterial(name: String, quantity: Double, unit: String) {
        viewModelScope.launch {
            try {
                repo.addMaterial(name, quantity, unit, section.value)
                _message.value = "تمت إضافة النقص بنجاح"
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun updateMaterial(id: String, name: String, quantity: Double, unit: String) {
        viewModelScope.launch {
            try {
                repo.updateMaterial(id, name, quantity, unit, section.value)
                _message.value = "تم تعديل النقص بنجاح"
            } catch (e: Exception) {
                _message.value = "تعذر التعديل: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun deleteMaterial(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteMaterial(id)
                _message.value = "تم حذف المادة بنجاح"
            } catch (e: Exception) {
                _message.value = "خطأ أثناء الحذف: ${e.message}"
            }
        }
    }

    fun setPrice(materialName: String, price: Double) {
        viewModelScope.launch {
            try {
                repo.setPrice(materialName, price)
                _message.value = "تم حفظ السعر"
            } catch (e: Exception) {
                _message.value = "تعذر حفظ السعر: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun addCatalogItem(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (repo.catalogNameExists(name)) {
                    _message.value = "\"$name\" موجودة بالقائمة مسبقاً"
                    onDone(false)
                    return@launch
                }
                repo.addCatalogItem(name)
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun deleteCatalogItem(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteCatalogItem(id)
            } catch (e: Exception) {
                _message.value = "تعذر الحذف: ${e.message}"
            }
        }
    }
}
