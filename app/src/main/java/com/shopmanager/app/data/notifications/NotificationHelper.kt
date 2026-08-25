package com.shopmanager.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shopmanager.app.MainActivity

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
    private const val NOTIF_ID_SHOPPING_LIST = 1001
    private const val NOTIF_ID_DEBT = 1002
    private const val NOTIF_ID_PAID_BASE = 2000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SHOPPING_LIST, "قائمة النواقص والمشتريات", NotificationManager.IMPORTANCE_DEFAULT)
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DEBTS, "تنبيهات الديون", NotificationManager.IMPORTANCE_DEFAULT)
            )
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

    fun showShoppingListNotification(context: Context, shortageNames: List<String>) {
        if (!hasPermission(context) || shortageNames.isEmpty()) return
        val body = if (shortageNames.size <= 4) shortageNames.joinToString("، ")
        else shortageNames.take(4).joinToString("، ") + " و${shortageNames.size - 4} أخرى"

        val notification = NotificationCompat.Builder(context, CHANNEL_SHOPPING_LIST)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🛒 قائمة مشتريات: ${shortageNames.size} مادة ناقصة")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, NOTIF_ID_SHOPPING_LIST, NotificationAction.ShoppingList(shortageNames)))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_SHOPPING_LIST, notification)
    }

    fun cancelShoppingListNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_SHOPPING_LIST)
    }

    fun showNewDebtNotification(context: Context, personName: String, amount: String, currencySymbol: String = "ل.س") {
        if (!hasPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DEBTS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💰 عميل جديد بالديون")
            .setContentText("$personName — $amount $currencySymbol")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, NOTIF_ID_DEBT, NotificationAction.NewDebt(personName, amount, currencySymbol)))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_DEBT, notification)
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
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("✅ تم سداد دين")
            .setContentText("$personName وفى $amount $currencySymbol")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, id, NotificationAction.DebtPaid(personName, amount, currencySymbol)))
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
