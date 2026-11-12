package com.shopmanager.app.assistant

import android.app.Application
import com.shopmanager.app.assistant.engine.ArabicText
import com.shopmanager.app.assistant.engine.Command
import com.shopmanager.app.assistant.engine.Intent
import com.shopmanager.app.data.backup.InstantBackupWorker
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.DebtActivityLog
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.history.ChangeHistory
import com.shopmanager.app.data.learning.LearningHub
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.materials.PriceHistoryStore
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.notes.NoteLinkType
import com.shopmanager.app.data.notes.NotesRepository
import com.shopmanager.app.data.notifications.NoteReminderWorker
import com.shopmanager.app.data.notifications.RemoteChangeProcessor
import com.shopmanager.app.data.notifications.SelfChangeLedger
import com.shopmanager.app.ui.common.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** لقطة بيانات التطبيق الحالية التي يعمل عليها المساعد (تُبنى من الـ ViewModel الموجودة). */
data class AssistantData(
    val persons: List<Person> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val materials: List<Material> = emptyList(),
    val prices: Map<String, Double> = emptyMap(),
    val catalog: List<MaterialCatalogItem> = emptyList(),
    val currency: String = "ل.س"
)

/** نتيجة تنفيذ أمر + عملية عكسية اختيارية. */
class Outcome(
    val title: String,
    val details: String,
    val success: Boolean = true,
    val undo: (suspend () -> Unit)? = null
)

/**
 * طبقة التنفيذ: تكتب عبر نفس المستودعات الموجودة (Firestore) وبنفس الأعراف: تسجيل SelfChangeLedger قبل الكتابة
 * حتى لا يُشعَر هذا الجهاز بتغييراته، ChangeHistory وDebtActivityLog، والنسخ الاحتياطي الفوري.
 * لكل أمر قابل للتراجع تُرجع Outcome.undo.
 */
class AssistantActions(private val app: Application) {
    private val debts = DebtsRepository()
    private val mats = MaterialsRepository()
    private val notes = NotesRepository()

    /** يُضبط من الـ ViewModel ليقرأ الكتالوج الحالي وقت التراجع. */
    @Volatile var catalogProvider: () -> List<MaterialCatalogItem> = { emptyList() }

    private fun dateStr(millis: Long?): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis ?: System.currentTimeMillis()))

    private fun money(v: Double, cur: String) = "${Formatters.number(v)} $cur"
    private fun same(a: String, b: String) = ArabicText.normalize(a.trim()) == ArabicText.normalize(b.trim())
    private fun after() = InstantBackupWorker.requestNow(app)

    suspend fun execute(cmd: Command, data: AssistantData): Outcome = when (cmd.intent) {
        Intent.ADD_DEBT -> addDebt(cmd, data)
        Intent.PAY_DEBT -> pay(cmd, data, reduceOnly = false)
        Intent.REDUCE_DEBT -> pay(cmd, data, reduceOnly = true)
        Intent.DELETE_DEBT -> deleteDebt(cmd, data)
        Intent.ADD_SHORTAGE -> addShortages(cmd, data)
        Intent.REMOVE_SHORTAGE -> removeShortages(cmd, data)
        Intent.SET_PRICE -> setPrice(cmd, data)
        Intent.ADD_MATERIAL -> addMaterial(cmd, data)
        Intent.ADD_NOTE, Intent.REMINDER -> addNote(cmd)
        Intent.ADD_PERSON -> addPerson(cmd, data)
        Intent.RENAME_PERSON -> renamePerson(cmd, data)
        Intent.DELETE_PERSON -> deletePerson(cmd, data)
        Intent.SET_PHONE -> setPhone(cmd, data)
        Intent.DELETE_MATERIAL -> deleteMaterial(cmd, data)
        Intent.RENAME_MATERIAL -> renameMaterial(cmd, data)
        else -> Outcome("غير مدعوم", "هذا الأمر لا يحتاج تنفيذاً", success = false)
    }

    // ------------------------------------------------------------------ الديون

    private suspend fun addDebt(cmd: Command, data: AssistantData): Outcome {
        val p = cmd.person ?: error("لا يوجد شخص")
        val amount = cmd.amount ?: error("لا يوجد مبلغ")
        val date = dateStr(cmd.date?.startMillis)
        val cur = data.currency
        if (p.id != null) {
            val id = debts.addDebt(p.id, amount, date, "") { SelfChangeLedger.markCreated(app, SelfChangeLedger.KIND_DEBT, it) }
            ChangeHistory.log(app, "إضافة دين بمبلغ ${Formatters.number(amount)} (المساعد)")
            DebtActivityLog.log(app, DebtActivityLog.Kind.ADDED, p.name, amount)
            after()
            return Outcome("تم تسجيل الدين", "${money(amount, cur)} على ${p.name}", undo = {
                debts.deleteDebt(id)
                DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, p.name, amount)
                after()
            })
        }
        debts.addPerson(p.name, amount, date, "", "") { SelfChangeLedger.markCreated(app, SelfChangeLedger.KIND_DEBT, it) }
        ChangeHistory.log(app, "إضافة عميل جديد \"${p.name}\" بدين ${Formatters.number(amount)} (المساعد)")
        DebtActivityLog.log(app, DebtActivityLog.Kind.NEW_CUSTOMER, p.name, amount)
        after()
        return Outcome("تم إنشاء العميل وتسجيل الدين", "${p.name} — ${money(amount, cur)}", undo = {
            debts.findPersonIdByName(p.name)?.let { debts.deletePersonWithDebts(it) }
            DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, p.name, amount)
            after()
        })
    }

    private suspend fun pay(cmd: Command, data: AssistantData, reduceOnly: Boolean): Outcome {
        val p = cmd.person ?: error("لا يوجد شخص")
        val pid = p.id ?: return Outcome("ما لقيت العميل", "\"${p.name}\" غير موجود", success = false)
        val snapshot = data.debts.filter { it.personId == pid }
        val total = snapshot.sumOf { it.amount }
        val cur = data.currency
        if (snapshot.isEmpty() || total <= 0.0) return Outcome("ما في دين", "${p.name} ما عليه شي", success = false)

        val full = !reduceOnly && (cmd.full || cmd.amount == null || cmd.amount >= total - 0.000001)
        if (full) {
            debts.markAllDebtsAsPaid(pid)
            ChangeHistory.log(app, "سداد كامل للعميل \"${p.name}\" بمبلغ ${Formatters.number(total)} (المساعد)")
            DebtActivityLog.log(app, DebtActivityLog.Kind.PAID_ALL, p.name, total)
            after()
            return Outcome("تم تسجيل السداد الكامل", "${p.name} — ${money(total, cur)}", undo = {
                debts.restoreDebts(snapshot)
                DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, p.name, total)
                after()
            })
        }
        var remaining = minOf(cmd.amount ?: 0.0, total).coerceAtLeast(0.0)
        val paid = remaining
        val ordered = snapshot.filter { it.amount > 0 }.sortedWith(compareBy<Debt>({ it.date }, { it.createdAt }))
        for (d in ordered) {
            if (remaining <= 0.000001) break
            if (d.amount <= remaining + 0.000001) {
                debts.markDebtAsPaid(d.id); remaining -= d.amount
            } else {
                debts.updateDebt(d.id, d.amount - remaining, d.date, d.note); remaining = 0.0
            }
        }
        val verb = if (reduceOnly) "تخفيض" else "سداد جزئي"
        ChangeHistory.log(app, "$verb للعميل \"${p.name}\" بمبلغ ${Formatters.number(paid)} (المساعد)")
        DebtActivityLog.log(app, if (reduceOnly) DebtActivityLog.Kind.EDITED else DebtActivityLog.Kind.PAID, p.name, paid)
        after()
        return Outcome(
            if (reduceOnly) "تم تخفيض الدين" else "تم تسجيل السداد",
            "${p.name} — ${money(paid, cur)} • المتبقي ${money(total - paid, cur)}",
            undo = {
                debts.restoreDebts(snapshot)
                DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, p.name, paid)
                after()
            }
        )
    }

    private suspend fun deleteDebt(cmd: Command, data: AssistantData): Outcome {
        val p = cmd.person ?: error("لا يوجد شخص")
        val pid = p.id ?: return Outcome("ما لقيت العميل", "\"${p.name}\" غير موجود", success = false)
        val list = data.debts.filter { it.personId == pid }.sortedByDescending { it.createdAt }
        val target = (if (cmd.amount != null) list.firstOrNull { kotlin.math.abs(it.amount - cmd.amount) < 0.01 } else null)
            ?: list.firstOrNull() ?: return Outcome("ما في دين", "${p.name} ما عليه عمليات", success = false)
        debts.deleteDebt(target.id)
        ChangeHistory.log(app, "حذف دين بمبلغ ${Formatters.number(target.amount)} (المساعد)")
        DebtActivityLog.log(app, DebtActivityLog.Kind.DELETED, p.name, target.amount)
        after()
        return Outcome("تم حذف العملية", "${p.name} — ${money(target.amount, data.currency)}", undo = {
            debts.restoreDebts(listOf(target))
            DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, p.name, target.amount)
            after()
        })
    }

    // ------------------------------------------------------------------ النواقص والأسعار

    private suspend fun addShortages(cmd: Command, data: AssistantData): Outcome {
        val ids = ArrayList<String>()
        val added = ArrayList<String>()
        val skipped = ArrayList<String>()
        val starredIds = ArrayList<String>()
        val starredNames = ArrayList<String>()
        var topOrder = (data.materials.minOfOrNull { it.order } ?: 0L) - 1
        for (m in cmd.materials) {
            val existing = data.materials.firstOrNull { same(it.name, m.name) }
            if (existing != null) {
                if (cmd.important && !existing.important) {
                    mats.setImportant(existing.id, true, topOrder)
                    topOrder -= 1
                    starredIds.add(existing.id); starredNames.add(existing.name)
                } else skipped.add(m.name)
                continue
            }
            val qty = m.quantity ?: 1.0
            val unit = m.unit ?: ""
            val id = mats.addMaterial(m.name, qty, unit, "main", m.pack ?: "") { newId ->
                SelfChangeLedger.markMaterialWritten(app, newId, RemoteChangeProcessor.materialSignature(m.name, qty, unit))
            }
            if (cmd.important) { mats.setImportant(id, true, topOrder); topOrder -= 1 }
            LearningHub.store(app).recordAdded(m.name, qty, unit, cmd.important)
            ids.add(id); added.add(m.name)
        }
        if (added.isEmpty() && starredNames.isEmpty()) return Outcome("موجودة مسبقاً", skipped.joinToString("، ") + " بالنواقص أصلاً", success = false)
        ChangeHistory.log(app, "إضافة للنواقص: ${(added + starredNames).joinToString("، ")}" + (if (cmd.important) " (هامة)" else "") + " (المساعد)")
        after()
        val star = if (cmd.important) " ⭐" else ""
        val extra = if (skipped.isNotEmpty()) " • موجودة مسبقاً: ${skipped.joinToString("، ")}" else ""
        val undo: suspend () -> Unit = {
            SelfChangeLedger.markMaterialsDeleted(app, ids)
            ids.forEach { mats.deleteMaterial(it) }
            starredIds.forEach { mats.setImportant(it, false) }
            after()
        }
        if (added.isEmpty()) return Outcome("تم وضعها كهامة", starredNames.joinToString("، ") + star + extra, undo = undo)
        val starNote = if (starredNames.isNotEmpty()) " • صارت هامة: ${starredNames.joinToString("، ")}" else ""
        return Outcome("تمت الإضافة للنواقص", added.joinToString("، ") + star + starNote + extra, undo = undo)
    }

    private suspend fun removeShortages(cmd: Command, data: AssistantData): Outcome {
        val found = cmd.materials.mapNotNull { m -> data.materials.firstOrNull { same(it.name, m.name) } }
        if (found.isEmpty()) return Outcome("مو بالنواقص", cmd.materials.joinToString("، ") { it.name } + " مو موجودة بالنواقص", success = false)
        SelfChangeLedger.markMaterialsDeleted(app, found.map { it.id })
        found.forEach { mats.deleteMaterial(it.id) }
        LearningHub.store(app).recordResolved(found.map { it.name })
        ChangeHistory.log(app, "حذف من النواقص: ${found.joinToString("، ") { it.name }} (المساعد)")
        after()
        return Outcome("تم الحذف من النواقص", found.joinToString("، ") { it.name }, undo = {
            found.forEach { m ->
                mats.addMaterial(m.name, m.quantity, m.unit, "main", m.notes) { newId ->
                    SelfChangeLedger.markMaterialWritten(app, newId, RemoteChangeProcessor.materialSignature(m.name, m.quantity, m.unit))
                }
            }
            after()
        })
    }

    private suspend fun setPrice(cmd: Command, data: AssistantData): Outcome {
        val m = cmd.materials.firstOrNull() ?: error("لا توجد مادة")
        val price = cmd.amount ?: error("لا يوجد سعر")
        val key = data.prices.keys.firstOrNull { same(it, m.name) } ?: m.name
        val before = data.prices[key]
        mats.setPrice(key, price)
        PriceHistoryStore.record(app, key, before, price)
        ChangeHistory.log(app, "تعديل سعر $key إلى ${Formatters.number(price)} (المساعد)")
        after()
        val details = "$key: " + (before?.let { "${money(it, data.currency)} ← " } ?: "") + money(price, data.currency)
        return Outcome(
            "تم حفظ السعر", details,
            undo = if (before == null) null else ({
                mats.setPrice(key, before)
                PriceHistoryStore.record(app, key, price, before)
                after()
            })
        )
    }

    private suspend fun addMaterial(cmd: Command, data: AssistantData): Outcome {
        val m = cmd.materials.firstOrNull() ?: error("لا توجد مادة")
        if (data.catalog.any { same(it.name, m.name) }) return Outcome("موجودة مسبقاً", "${m.name} موجودة بالكتالوج", success = false)
        mats.addCatalogItem(m.name)
        cmd.amount?.let { mats.setPrice(m.name, it); PriceHistoryStore.record(app, m.name, null, it) }
        ChangeHistory.log(app, "إضافة مادة جديدة: ${m.name} (المساعد)")
        after()
        val priceText = cmd.amount?.let { " بسعر ${money(it, data.currency)}" } ?: ""
        return Outcome("تمت إضافة المادة", m.name + priceText, undo = {
            catalogProvider().firstOrNull { same(it.name, m.name) }?.let { mats.deleteCatalogItem(it.id) }
            after()
        })
    }

    // ------------------------------------------------------------------ إدارة العملاء

    private suspend fun addPerson(cmd: Command, data: AssistantData): Outcome {
        val name = cmd.person?.name?.trim().orEmpty()
        if (name.isEmpty()) error("لا يوجد اسم")
        if (data.persons.any { same(it.name, name) }) {
            return Outcome("موجود مسبقاً", "$name موجود بين العملاء", success = false)
        }
        val phone = cmd.phone.orEmpty()
        debts.addPerson(name, 0.0, dateStr(null), "", phone)
        ChangeHistory.log(app, "إضافة عميل جديد \"$name\" (المساعد)")
        DebtActivityLog.log(app, DebtActivityLog.Kind.NEW_CUSTOMER, name, 0.0)
        after()
        return Outcome("تمت إضافة العميل", name + (if (phone.isNotEmpty()) " — $phone" else ""), undo = {
            debts.findPersonIdByName(name)?.let { debts.deletePersonWithDebts(it) }
            DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, name, 0.0)
            after()
        })
    }

    private suspend fun renamePerson(cmd: Command, data: AssistantData): Outcome {
        val ref = cmd.person ?: error("لا يوجد عميل")
        val newName = cmd.newName?.trim().orEmpty()
        if (newName.isEmpty()) error("لا يوجد اسم جديد")
        val person = data.persons.firstOrNull { it.id == ref.id }
            ?: return Outcome("ما لقيت العميل", "\"${ref.name}\" غير موجود", success = false)
        if (same(person.name, newName)) return Outcome("نفس الاسم", "الاسم الجديد مثل القديم", success = false)
        if (data.persons.any { it.id != person.id && same(it.name, newName) }) {
            return Outcome("الاسم مستخدم", "\"$newName\" لعميل آخر", success = false)
        }
        debts.updatePerson(person.id, newName, person.amount, person.date)
        ChangeHistory.log(app, "تغيير اسم العميل \"${person.name}\" إلى \"$newName\" (المساعد)")
        DebtActivityLog.log(app, DebtActivityLog.Kind.EDITED, newName, person.amount)
        after()
        return Outcome("تم تغيير الاسم", "${person.name} ← $newName", undo = {
            debts.updatePerson(person.id, person.name, person.amount, person.date)
            DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, person.name, person.amount)
            after()
        })
    }

    private suspend fun deletePerson(cmd: Command, data: AssistantData): Outcome {
        val ref = cmd.person ?: error("لا يوجد عميل")
        val person = data.persons.firstOrNull { it.id == ref.id }
            ?: return Outcome("ما لقيت العميل", "\"${ref.name}\" غير موجود", success = false)
        val personDebts = data.debts.filter { it.personId == person.id }
        debts.deletePersonWithDebts(person.id)
        ChangeHistory.log(app, "حذف العميل \"${person.name}\" مع ديونه (المساعد)")
        DebtActivityLog.log(app, DebtActivityLog.Kind.DELETED_CUSTOMER, person.name, person.amount)
        after()
        return Outcome("تم حذف العميل", "${person.name} (${personDebts.size} عملية)", undo = {
            debts.restorePerson(person, personDebts)
            DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, person.name, person.amount)
            after()
        })
    }

    private suspend fun setPhone(cmd: Command, data: AssistantData): Outcome {
        val ref = cmd.person ?: error("لا يوجد عميل")
        val phone = cmd.phone?.trim().orEmpty()
        if (phone.isEmpty()) error("لا يوجد رقم")
        val person = data.persons.firstOrNull { it.id == ref.id }
            ?: return Outcome("ما لقيت العميل", "\"${ref.name}\" غير موجود", success = false)
        debts.updatePerson(person.id, person.name, person.amount, person.date, phone)
        ChangeHistory.log(app, "حفظ رقم هاتف العميل \"${person.name}\" (المساعد)")
        DebtActivityLog.log(app, DebtActivityLog.Kind.EDITED, person.name, person.amount)
        after()
        return Outcome("تم حفظ الرقم", "${person.name}: $phone", undo = {
            debts.updatePerson(person.id, person.name, person.amount, person.date, person.phone)
            DebtActivityLog.log(app, DebtActivityLog.Kind.UNDO, person.name, person.amount)
            after()
        })
    }

    // ------------------------------------------------------------------ القائمة الثابتة

    private suspend fun deleteMaterial(cmd: Command, data: AssistantData): Outcome {
        val m = cmd.materials.firstOrNull() ?: error("لا توجد مادة")
        val item = data.catalog.firstOrNull { same(it.name, m.name) }
            ?: return Outcome("ما لقيت المادة", "${m.name} مو بالقائمة الثابتة", success = false)
        mats.deleteCatalogItem(item.id)
        ChangeHistory.log(app, "حذف مادة من القائمة الثابتة: ${item.name} (المساعد)")
        after()
        return Outcome("تم حذف المادة", item.name, undo = {
            mats.addCatalogItem(item.name, item.group)
            after()
        })
    }

    private suspend fun renameMaterial(cmd: Command, data: AssistantData): Outcome {
        val m = cmd.materials.firstOrNull() ?: error("لا توجد مادة")
        val newName = cmd.newName?.trim().orEmpty()
        if (newName.isEmpty()) error("لا يوجد اسم جديد")
        val item = data.catalog.firstOrNull { same(it.name, m.name) }
            ?: return Outcome("ما لقيت المادة", "${m.name} مو بالقائمة الثابتة", success = false)
        if (same(item.name, newName)) return Outcome("نفس الاسم", "الاسم الجديد مثل القديم", success = false)
        if (data.catalog.any { it.id != item.id && same(it.name, newName) }) {
            return Outcome("موجودة مسبقاً", "\"$newName\" موجودة بالقائمة", success = false)
        }
        mats.updateCatalogItem(item.id, newName, item.group)
        ChangeHistory.log(app, "تغيير اسم المادة \"${item.name}\" إلى \"$newName\" (المساعد)")
        after()
        return Outcome("تم تغيير اسم المادة", "${item.name} ← $newName", undo = {
            mats.updateCatalogItem(item.id, item.name, item.group)
            after()
        })
    }

    // ------------------------------------------------------------------ الملاحظات والتذكير

    private suspend fun addNote(cmd: Command): Outcome {
        val text = cmd.text?.trim().orEmpty()
        if (text.isEmpty()) error("لا يوجد نص")
        val reminderAt = if (cmd.intent == Intent.REMINDER) (cmd.date?.timeMillis ?: cmd.date?.startMillis?.plus(9 * 3_600_000L) ?: 0L) else 0L
        val title = text.take(40)
        val id = notes.addNote(
            ImportantNote(title = title, content = text, linkType = NoteLinkType.NONE, reminderAt = reminderAt)
        ) { SelfChangeLedger.markCreated(app, SelfChangeLedger.KIND_NOTE, it) }
        if (reminderAt > 0L) NoteReminderWorker.schedule(app, id, title, text, reminderAt)
        after()
        val label = if (reminderAt > 0L) "تم ضبط التذكير" else "تمت إضافة الملاحظة"
        val details = text + (cmd.date?.takeIf { reminderAt > 0L }?.let { " • ${it.label}" } ?: "")
        return Outcome(label, details, undo = {
            notes.deleteNote(id)
            NoteReminderWorker.cancel(app, id)
            after()
        })
    }
}
