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
 * - "shopping list": which materials are low/out of stock and need buying
 *   from the market (re-shown, not re-created, whenever the low-stock set
 *   actually changes - not on every unrelated Firestore update).
 * - "new debt": a new customer/debt appeared (useful if a second device or
 *   employee adds one).
 */
object NotificationHelper {

    private const val CHANNEL_LOW_STOCK = "low_stock_channel"
    private const val CHANNEL_DEBTS = "debts_channel"
    private const val NOTIF_ID_LOW_STOCK = 1001
    private const val NOTIF_ID_DEBT = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_LOW_STOCK, "قائمة المشتريات (نفاد المخزون)", NotificationManager.IMPORTANCE_DEFAULT)
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

    fun showLowStockNotification(context: Context, missingNames: List<String>) {
        if (!hasPermission(context) || missingNames.isEmpty()) return
        val body = if (missingNames.size <= 4) missingNames.joinToString("، ")
        else missingNames.take(4).joinToString("، ") + " و${missingNames.size - 4} أخرى"

        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_STOCK)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🛒 قائمة مشتريات: ${missingNames.size} مادة ناقصة")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_LOW_STOCK, notification)
    }

    fun cancelLowStockNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_LOW_STOCK)
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
}
