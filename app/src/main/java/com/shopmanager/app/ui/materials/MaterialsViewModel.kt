package com.shopmanager.app.ui.materials

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MaterialsUiState(
    val materials: List<Material> = emptyList(),
    val prices: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true
)

class MaterialsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MaterialsRepository()

    private val section = MutableStateFlow("main")

    private val materialsFlow = channelFlow {
        section.collect { s ->
            repo.listenMaterials(s).collect { send(it) }
        }
    }.catch { emit(emptyList()) }

    private val pricesFlow = repo.listenPrices().catch { emit(emptyMap()) }

    val uiState: StateFlow<MaterialsUiState> = combine(materialsFlow, pricesFlow) { materials, prices ->
        MaterialsUiState(materials = materials, prices = prices, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaterialsUiState())

    val catalog: StateFlow<List<MaterialCatalogItem>> = repo.listenCatalog()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    // Only re-notify when the *set* of low-stock names actually changes -
    // not on every unrelated Firestore update (e.g. a price edit).
    private var lastNotifiedLowStock: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            NotificationHelper.ensureChannels(getApplication())
            uiState.collect { state ->
                if (state.isLoading) return@collect
                val lowStockNames = state.materials
                    .filter { it.minQuantity > 0 && it.quantity <= it.minQuantity }
                    .map { it.name }
                    .toSet()
                if (lowStockNames.isEmpty()) {
                    if (lastNotifiedLowStock.isNotEmpty()) NotificationHelper.cancelLowStockNotification(getApplication())
                    lastNotifiedLowStock = emptySet()
                } else if (lowStockNames != lastNotifiedLowStock) {
                    lastNotifiedLowStock = lowStockNames
                    NotificationHelper.showLowStockNotification(getApplication(), lowStockNames.toList())
                }
            }
        }
    }

    fun addMaterial(name: String, quantity: Double, unit: String, minQuantity: Double) {
        viewModelScope.launch {
            try {
                repo.addMaterial(name, quantity, unit, section.value, minQuantity)
                _message.value = "تمت إضافة المادة بنجاح"
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun updateMaterial(id: String, name: String, quantity: Double, unit: String, minQuantity: Double) {
        viewModelScope.launch {
            try {
                repo.updateMaterial(id, name, quantity, unit, section.value, minQuantity)
                _message.value = "تم تعديل المادة بنجاح"
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
