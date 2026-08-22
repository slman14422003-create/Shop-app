package com.shopmanager.app.data.materials

data class Material(
    val id: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "كغ",
    val section: String = "main",
    val minQuantity: Double = 0.0
)
