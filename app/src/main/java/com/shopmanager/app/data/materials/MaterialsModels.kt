package com.shopmanager.app.data.materials

/**
 * Fixed unit choices for a spice shop (بزورية) - weight is always picked from
 * these three, never free-typed. "unit" is still stored in Firestore as a
 * plain string (the Arabic label) so it stays backward-compatible with any
 * existing documents.
 */
enum class MaterialUnit(val label: String) {
    KG("كغ"),
    OKE("لوقية"),
    HALF_OKE("نص لوقية");

    companion object {
        fun fromLabel(label: String): MaterialUnit = entries.find { it.label == label } ?: KG
    }
}

data class Material(
    val id: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = MaterialUnit.KG.label,
    val section: String = "main",
    val minQuantity: Double = 0.0
)
