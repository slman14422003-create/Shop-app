package com.shopmanager.app.data.materials

/**
 * Fixed unit choices for a spice shop (بزورية) - weight is always picked from
 * these options, never free-typed. "unit" is still stored in Firestore as a
 * plain string (the Arabic label) so it stays backward-compatible with any
 * existing documents.
 *
 * FIX: previously only كيلو/لوقية/نص لوقية/ربع لوقية existed, so a half-kilo
 * or quarter-kilo shortage had to be typed as a decimal quantity (e.g. "0.5"
 * with unit كيلو). نص كيلو and ربع كيلو are now their own units, same as the
 * لوقية fractions, so quantity can always stay a whole number (see
 * MaterialEditDialog's stepper) - you pick the size, then just count how
 * many of it.
 *
 * FIX: not everything on the shortage list is sold by weight (e.g. "بيض"
 * counted by the piece, or a shortage that's simply "2" of something with no
 * size at all). NONE covers that: its label is the empty string, so the
 * quantity is shown and stored on its own with nothing appended (see
 * formatQuantity / Material.quantityLabel) instead of being forced into one
 * of the weight units.
 */
enum class MaterialUnit(val label: String) {
    KG("كيلو"),
    HALF_KG("نص كيلو"),
    QUARTER_KG("ربع كيلو"),
    OKE("لوقية"),
    HALF_OKE("نص لوقية"),
    QUARTER_OKE("ربع لوقية"),
    NONE("");

    companion object {
        fun fromLabel(label: String): MaterialUnit = when (label) {
            "كغ", "كيلو" -> KG
            "" -> NONE
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

/**
 * Quantity is always entered as a whole number now (see MaterialEditDialog's
 * stepper), but is still stored/typed as Double for backward compatibility
 * with existing Firestore documents. Plain `.toString()` on a Double prints
 * a trailing ".0" (e.g. "1.0 كيلو"), which never made sense for a whole-unit
 * count - this trims it to "1 كيلو" while still showing real decimals for
 * any older document that predates the stepper.
 */
fun Double.formatQuantity(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

/**
 * Quantity + unit as shown to a person ("2 نص كيلو"), used everywhere a
 * material's amount is displayed (list rows, share text, notifications).
 * When the unit is NONE (empty label - a plain count like "بيض: 6") there is
 * no unit word to append, so this returns just the number instead of
 * leaving a trailing space.
 */
fun Material.quantityLabel(): String =
    if (unit.isBlank()) quantity.formatQuantity() else "${quantity.formatQuantity()} $unit"
