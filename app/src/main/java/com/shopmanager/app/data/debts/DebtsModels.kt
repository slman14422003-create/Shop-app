package com.shopmanager.app.data.debts

data class Person(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val createdAt: Long = 0L
)

data class Debt(
    val id: String = "",
    val personId: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val createdAt: Long = 0L
)
