package com.shopmanager.app.ui.debts

import androidx.compose.runtime.Immutable
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.OverdueRule
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.ui.common.Formatters
import kotlin.math.roundToInt

enum class DebtBucket { OVERDUE, THIS_WEEK, LATER, PAID }

@Immutable
data class PersonStatus(val bucket: DebtBucket, val days: Int)

fun classifyPersons(persons: List<Person>, debts: List<Debt>, overdueDays: Int, now: Long): Map<String, PersonStatus> {
    val overdue = OverdueRule.compute(persons, debts, overdueDays, now).associateBy { it.personId }
    val today = OverdueRule.startOfDay(now)
    val result = HashMap<String, PersonStatus>(persons.size)
    for (p in persons) {
        val late = overdue[p.id]
        result[p.id] = when {
            late != null -> PersonStatus(DebtBucket.OVERDUE, late.days)
            p.amount <= 0.0 -> PersonStatus(DebtBucket.PAID, 0)
            p.dueDate > 0L -> {
                val left = ((OverdueRule.startOfDay(p.dueDate) - today).toDouble() / OverdueRule.DAY_MS).roundToInt()
                if (left <= 7) PersonStatus(DebtBucket.THIS_WEEK, left) else PersonStatus(DebtBucket.LATER, left)
            }
            else -> PersonStatus(DebtBucket.LATER, -1)
        }
    }
    return result
}

fun statusLine(status: PersonStatus): String = when (status.bucket) {
    DebtBucket.OVERDUE -> "متأخر منذ ${Formatters.number(status.days.toDouble())} يوم"
    DebtBucket.PAID -> "لا يوجد دين حالياً"
    else -> when {
        status.days < 0 -> "بدون موعد"
        status.days == 0 -> "يستحق اليوم"
        else -> "يستحق بعد ${Formatters.number(status.days.toDouble())} يوم"
    }
}

fun debtReminderText(name: String, amount: Double, overdueDays: Int?, currency: String): String =
    if (overdueDays != null && overdueDays > 0) {
        "السلام عليكم $name، نذكّرك بوجود مبلغ ${Formatters.number(amount)} $currency مستحق منذ ${Formatters.number(overdueDays.toDouble())} يوماً."
    } else {
        "السلام عليكم $name، نذكّرك بوجود مبلغ ${Formatters.number(amount)} $currency."
    }

fun recentPersons(persons: List<Person>, debts: List<Debt>): List<Person> {
    val dateFormat = com.shopmanager.app.util.DateFormats.get("yyyy-MM-dd", java.util.Locale.US, lenient = false)
    val latestDebt = HashMap<String, Long>()
    for (d in debts) {
        val t = if (d.createdAt > 0L) d.createdAt
        else runCatching { dateFormat.parse(d.date.trim())?.time }.getOrNull() ?: 0L
        if (t > (latestDebt[d.personId] ?: 0L)) latestDebt[d.personId] = t
    }
    return persons.sortedByDescending { maxOf(it.createdAt, latestDebt[it.id] ?: 0L) }
}

fun recentNotes(debts: List<Debt>, limit: Int = 4): List<String> =
    debts.sortedByDescending { it.createdAt }
        .map { it.note.trim() }
        .filter { it.isNotEmpty() && it.length <= 24 }
        .distinct()
        .take(limit)
