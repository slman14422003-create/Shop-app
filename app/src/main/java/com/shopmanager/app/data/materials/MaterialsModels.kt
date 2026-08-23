package com.shopmanager.app.data.materials

/**
 * Fixed unit choices for a spice shop (بزورية) - weight is always picked from
 * these four, never free-typed. "unit" is still stored in Firestore as a
 * plain string (the Arabic label) so it stays backward-compatible with any
 * existing documents.
 */
enum class MaterialUnit(val label: String) {
    KG("كيلو"),
    OKE("لوقية"),
    HALF_OKE("نص لوقية"),
    QUARTER_OKE("ربع لوقية");

    companion object {
        fun fromLabel(label: String): MaterialUnit = when (label) {
            "كغ", "كيلو" -> KG
            else -> entries.find { it.label == label } ?: KG
        }
    }
}

/**
 * A "material" here is a shortage entry, not a stock count: every row in
 * this list is by definition something the shop is out of and needs to buy
 * (a live shopping list), like adding "فلفل اسود، الكمية 2" when the shop is
 * out of black pepper. There is deliberately no "current stock" or
 * "low-stock threshold" concept - an item simply stays on the list until
 * it's bought and removed.
 */
data class Material(
    val id: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = MaterialUnit.KG.label,
    val section: String = "main",
    val updatedAt: Long = 0L
)

/** A fixed catalog entry (the shop's own standing list of spice names). */
data class MaterialCatalogItem(
    val id: String = "",
    val name: String = ""
)
