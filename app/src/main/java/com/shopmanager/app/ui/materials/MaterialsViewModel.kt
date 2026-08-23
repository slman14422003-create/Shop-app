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
    private val materialsFlow = channelFlow {
        section.collect { s ->
            repo.listenMaterials(s).collect { send(it) }
        }
    }.catch { emit(emptyList()) }.distinctUntilChanged()

    private val pricesFlow = repo.listenPrices().catch { emit(emptyMap()) }.distinctUntilChanged()

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
    private var lastNotifiedShortages: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            NotificationHelper.ensureChannels(getApplication())
            uiState.collect { state ->
                if (state.isLoading) return@collect
                val shortageNames = state.materials.map { it.name }.toSet()
                if (shortageNames.isEmpty()) {
                    if (lastNotifiedShortages.isNotEmpty()) NotificationHelper.cancelShoppingListNotification(getApplication())
                    lastNotifiedShortages = emptySet()
                } else if (shortageNames != lastNotifiedShortages) {
                    lastNotifiedShortages = shortageNames
                    if (settings.notificationsEnabled) {
                        NotificationHelper.showShoppingListNotification(getApplication(), shortageNames.toList())
                    }
                }
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
