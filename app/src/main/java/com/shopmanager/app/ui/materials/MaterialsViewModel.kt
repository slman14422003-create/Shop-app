package com.shopmanager.app.ui.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialsRepository
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

class MaterialsViewModel : ViewModel() {

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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    fun addMaterial(name: String, quantity: Double, unit: String, minQuantity: Double) {
        viewModelScope.launch {
            try {
                repo.addMaterial(name, quantity, unit, section.value, minQuantity)
                _message.value = "تمت إضافة المادة بنجاح"
            } catch (e: Exception) {
                _message.value = "خطأ أثناء إضافة المادة: ${e.message}"
            }
        }
    }

    fun updateMaterial(id: String, name: String, quantity: Double, unit: String, minQuantity: Double) {
        viewModelScope.launch {
            try {
                repo.updateMaterial(id, name, quantity, unit, section.value, minQuantity)
                _message.value = "تم تعديل المادة بنجاح"
            } catch (e: Exception) {
                _message.value = "خطأ أثناء التعديل: ${e.message}"
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
                _message.value = "خطأ أثناء حفظ السعر: ${e.message}"
            }
        }
    }
}
