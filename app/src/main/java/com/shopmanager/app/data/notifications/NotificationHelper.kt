package com.shopmanager.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shopmanager.app.MainActivity
import com.shopmanager.app.ui.common.avatarColorFor

/**
 * Local (on-device) notifications - no server/FCM setup required. Two kinds:
 * - "shopping list": which materials are currently on the shortage list and
 *   need buying from the market. Every material added to the list is, by
 *   definition, something the shop is short on (re-shown, not re-created,
 *   whenever the set of names actually changes - not on every unrelated
 *   Firestore update).
 * - "new debt": a new customer/debt appeared (useful if a second device or
 *   employee adds one).
 */
object NotificationHelper {

    private const val CHANNEL_SHOPPING_LIST = "low_stock_channel"
    private const val CHANNEL_DEBTS = "debts_channel"
    private const val CHANNEL_NOTES = "notes_channel"
    private const val NOTIF_ID_SHOPPING_LIST = 1001
    private const val NOTIF_ID_DEBT = 1002
    private const val NOTIF_ID_PAID_BASE = 2000
    private const val NOTIF_ID_NEW_DEBT_BASE = 3000
    private const val NOTIF_ID_NOTE_BASE = 4000

    // "مجموعات الإشعارات المتقدمة": each channel gets its own notification
    // *group*, with a silent summary notification posted alongside the
    // individual ones. Without a group, several debt-paid notifications
    // fired back-to-back (e.g. paying off 3 debts in a row, or the
    // background worker catching up after being closed for a while) just
    // stack as separate unrelated entries; with a group, Android (API 24+)
    // automatically visually clusters them under the shop name instead, and
    // the summary line gives an at-a-glance count without opening the
    // shade — the same "grouped alerts" behavior iOS notification
    // grouping and modern Android apps both use.
    private const val GROUP_DEBTS = "com.shopmanager.app.GROUP_DEBTS"
    private const val NOTIF_ID_DEBTS_SUMMARY = 1900

    // Same brand indigo as res/values/colors.xml's brand_indigo / the
    // in-app theme (see ui/theme/Color.kt) — applied via setColor() below
    // so the small icon's accent circle (API 21+ notification shade) and
    // any heads-up banner tint match the rest of the app instead of
    // falling back to a generic system grey, another piece of "الإشعارات
    // مش متطورة" (the icon itself is fixed too — see ic_stat_notify.xml).
    private val BRAND_COLOR = android.graphics.Color.parseColor("#4F46E5")

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val shoppingChannel = NotificationChannel(
                CHANNEL_SHOPPING_LIST, "قائمة النواقص والمشتريات", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيه عند تغيّر قائمة المواد الناقصة"
                enableLights(true)
            }

            // Debt alerts are money-related and time-sensitive (a new debt,
            // a payment coming in) — bumped to HIGH importance so they post
            // as a heads-up/banner notification with sound instead of
            // silently landing in the shade, plus a short distinct
            // vibration pattern so it's recognizable by feel alone.
            val debtsChannel = NotificationChannel(
                CHANNEL_DEBTS, "تنبيهات الديون", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه فوري عند إضافة دين جديد أو تسديده"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 90, 180)
            }

            manager.createNotificationChannel(shoppingChannel)
            manager.createNotificationChannel(debtsChannel)

            // "الملاحظات الهامة" reminders - their own channel (not the
            // generic shopping-list one) since a person may want reminders
            // to make sound/vibrate distinctly from a shortage-list update.
            val notesChannel = NotificationChannel(
                CHANNEL_NOTES, "تذكيرات الملاحظات الهامة", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه عند حلول موعد تذكير لملاحظة هامة"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 90, 180)
            }
            manager.createNotificationChannel(notesChannel)
        }
    }

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * BUG FIXED (tapping a notification did nothing): none of the three
     * notifications below ever set a `contentIntent`, so tapping them had
     * no effect at all - not an OEM (Xiaomi/Samsung) quirk, just a missing
     * PendingIntent. Every notification now opens [MainActivity] carrying
     * its own [NotificationAction] (via [NotificationAction.applyExtras]),
     * which the Activity reads in `onCreate`/`onNewIntent` and turns into
     * the matching confirmation dialog ("تم تسديد الدين"، إلخ) or
     * navigation. `requestCode` must be unique per *distinct* notification
     * (not shared across all of them) or Android reuses/overwrites a
     * previous PendingIntent's extras instead of building a fresh one -
     * the same `id` each notification is posted under is reused here for
     * exactly that reason.
     */
    private fun buildContentIntent(context: Context, requestCode: Int, action: NotificationAction): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action.applyExtras(this)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * "إشعارات أكثر تطورًا وكفاءة": a broadcast-backed action button that
     * does its work (snooze a reminder, dismiss the shopping-list card)
     * directly from the notification shade via [NotificationActionReceiver]
     * — no need to open [MainActivity] first the way every `contentIntent`
     * tap above does. Distinct `requestCode`s per action/id (same rotating-
     * id convention as [buildContentIntent]) so several action buttons
     * across different notifications never collide and silently overwrite
     * one another's extras.
     */
    private fun buildActionIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * A small circular "avatar" bitmap — the customer's own brand color
     * (via [avatarColorFor], the exact same deterministic palette every
     * in-app debt/customer avatar already uses) with their first letter
     * centered on it — used as the notification's `setLargeIcon`. Purely
     * decorative/drawn on-device (no network image), so this never adds a
     * loading state or a failure path; the small brand-colored status
     * icon (`ic_stat_notify`) stays as-is alongside it, exactly like a
     * messaging app pairs a contact photo with its own small app glyph.
     */
    private fun buildAvatarBitmap(name: String, sizePx: Int = 128): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val composeColor = avatarColorFor(name)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(
                (composeColor.alpha * 255).toInt(),
                (composeColor.red * 255).toInt(),
                (composeColor.green * 255).toInt(),
                (composeColor.blue * 255).toInt()
            )
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, backgroundPaint)

        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "؟"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.46f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, radius, textY, textPaint)
        return bitmap
    }

    /**
     * Fires a note's reminder - see [com.shopmanager.app.data.notifications.NoteReminderWorker].
     * Id is derived from the note id (same rotating-id pattern as the debt
     * notifications above) so several reminders firing close together each
     * stay visible instead of overwriting one another.
     */
    fun showNoteReminderNotification(context: Context, noteId: String, title: String, content: String) {
        if (!hasPermission(context)) return
        val id = NOTIF_ID_NOTE_BASE + (noteId.hashCode() and 0xFFF)
        val body = content.ifBlank { "تذكير بملاحظة هامة" }
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_NOTE
            putExtra(NotificationActionReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(NotificationActionReceiver.EXTRA_NOTE_TITLE, title)
            putExtra(NotificationActionReceiver.EXTRA_NOTE_CONTENT, content)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, id)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_NOTES)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("📌 $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.NoteReminder(noteId, title)))
            // "كفاءة أعلى": تأجيل التذكير ساعة كاملة دون فتح التطبيق —
            // يُنفَّذ مباشرة عبر NotificationActionReceiver.
            .addAction(0, "تأجيل ساعة", buildActionIntent(context, id, snoozeIntent))
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancelNoteReminderNotification(context: Context, noteId: String) {
        val id = NOTIF_ID_NOTE_BASE + (noteId.hashCode() and 0xFFF)
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun showShoppingListNotification(context: Context, shortageNames: List<String>) {
        if (!hasPermission(context) || shortageNames.isEmpty()) return
        val body = if (shortageNames.size <= 4) shortageNames.joinToString("، ")
        else shortageNames.take(4).joinToString("، ") + " و${shortageNames.size - 4} أخرى"
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_SHOPPING
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SHOPPING_LIST)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("🛒 قائمة مشتريات: ${shortageNames.size} مادة ناقصة")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(true)
            // عدد المواد الناقصة كـ"شارة" رقمية على أيقونة الإشعار
            // (المكان الذي تدعمه واجهة المستخدم) — لمحة سريعة دون فتح الشريط.
            .setNumber(shortageNames.size)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, NOTIF_ID_SHOPPING_LIST, NotificationAction.ShoppingList(shortageNames)))
            .addAction(0, "تم الشراء", buildActionIntent(context, NOTIF_ID_SHOPPING_LIST, dismissIntent))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_SHOPPING_LIST, notification)
    }

    fun cancelShoppingListNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_SHOPPING_LIST)
    }

    /**
     * BUG FIXED (اشعارات دين جديد تختفي): this used to post every "new
     * debt" notification under the same fixed [NOTIF_ID_DEBT] id. Both
     * callers (DebtsViewModel's live-listener diff and
     * BackgroundSyncWorker's periodic diff) can detect *several* new debts
     * at once — e.g. reconnecting after being offline, or another device
     * adding two debts in a row — and loop over them calling this once per
     * debt. Since `NotificationManagerCompat.notify(id, ...)` replaces any
     * existing notification already posted under that same id, only the
     * *last* debt in the loop ever stayed visible; every earlier one in
     * the same batch was silently overwritten before the person ever saw
     * it. [showDebtPaidNotification] already avoided exactly this by
     * deriving its id from the debt id - this now does the same (falling
     * back to the shared [NOTIF_ID_DEBT] only when no id is available, so
     * existing behavior for a single new debt is unchanged).
     */
    fun showNewDebtNotification(context: Context, personName: String, amount: String, currencySymbol: String = "ل.س", debtId: String = "") {
        if (!hasPermission(context)) return
        val id = if (debtId.isEmpty()) NOTIF_ID_DEBT else NOTIF_ID_NEW_DEBT_BASE + (debtId.hashCode() and 0xFFF)
        val notification = NotificationCompat.Builder(context, CHANNEL_DEBTS)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            // "صورة رمزية": نفس لون ولون الحرف الأول اللذين يستخدمهما تطبيق
            // العملاء داخليًا (avatarColorFor) — تُظهر هوية العميل مباشرة
            // من شريط الإشعارات بدل الاعتماد على الأيقونة العامة فقط.
            .setLargeIcon(buildAvatarBitmap(personName))
            .setContentTitle("💰 عميل جديد بالديون")
            .setContentText("$personName — $amount $currencySymbol")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$personName — $amount $currencySymbol"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_DEBTS)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.NewDebt(personName, amount, currencySymbol)))
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
        postDebtsGroupSummary(context)
    }

    /**
     * Fired when a debt is marked paid (the checkmark button next to each
     * debt in Person Detail). Uses a rotating id derived from the debt id so
     * paying off several debts in a row shows several notifications instead
     * of each one silently replacing the last.
     */
    fun showDebtPaidNotification(context: Context, personName: String, amount: String, currencySymbol: String = "ل.س", debtId: String = "") {
        if (!hasPermission(context)) return
        val id = NOTIF_ID_PAID_BASE + (debtId.hashCode() and 0xFFF)
        val notification = NotificationCompat.Builder(context, CHANNEL_DEBTS)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setLargeIcon(buildAvatarBitmap(personName))
            .setContentTitle("✅ تم سداد دين")
            .setContentText("$personName وفى $amount $currencySymbol")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$personName وفى $amount $currencySymbol"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_DEBTS)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.DebtPaid(personName, amount, currencySymbol)))
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
        postDebtsGroupSummary(context)
    }

    /**
     * The silent group-summary notification required (API 24+) for
     * multiple grouped debt notifications to actually cluster visually
     * instead of just sharing an invisible group tag. `setGroupSummary`
     * marks it as the "stack cover" rather than a notification in its own
     * right, and it deliberately carries no sound/vibration of its own
     * (the individual notification that triggered it already made noise) —
     * it exists purely so the shade shows "٣ إشعارات" collapsed instead of
     * three separate cards.
     */
    private fun postDebtsGroupSummary(context: Context) {
        if (!hasPermission(context)) return
        val summary = NotificationCompat.Builder(context, CHANNEL_DEBTS)
            .setSmallIcon(com.shopmanager.app.R.drawable.ic_stat_notify)
            .setColor(BRAND_COLOR)
            .setContentTitle("تنبيهات الديون")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("إدارة المحل"))
            .setGroup(GROUP_DEBTS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DEBTS_SUMMARY, summary)
    }
}
