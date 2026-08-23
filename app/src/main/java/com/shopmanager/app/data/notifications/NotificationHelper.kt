package com.shopmanager.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

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
        val notification = NotificationCompat.Builder(context, CHANNEL_DEBTS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("✅ تم سداد دين")
            .setContentText("$personName وفى $amount $currencySymbol")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val id = NOTIF_ID_PAID_BASE + (debtId.hashCode() and 0xFFF)
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
