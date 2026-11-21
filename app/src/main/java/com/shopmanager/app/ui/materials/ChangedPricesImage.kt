package com.shopmanager.app.ui.materials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.util.DateFormats
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** يرسم قائمة الأسعار المتغيّرة كصورة PNG بخلفية صلبة (للمشاركة المباشرة عبر واتساب). */
internal object ChangedPricesImage {

    private const val WIDTH = 1080
    private const val PAD = 48f
    private const val HEADER_H = 250f
    private const val ROW_H = 190f
    private const val ROW_GAP = 16f
    private const val RADIUS = 28f

    fun generate(context: Context, items: List<ChangedPriceItem>, brandColor: Int): Uri {
        val bodyH = if (items.isEmpty()) 160f else items.size * (ROW_H + ROW_GAP)
        val height = (HEADER_H + 36f + bodyH + 60f).toInt()
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F4F5F7"))

        val currency = AppSettingsState.currencySymbol
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(color: Int, size: Float, bold: Boolean, align: Paint.Align) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; textAlign = align
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        val ink = Color.parseColor("#17181C")
        val muted = Color.parseColor("#7A7F8A")
        val red = Color.parseColor("#E5383B")
        val green = Color.parseColor("#2E9E5B")
        val right = WIDTH - PAD

        fill.color = brandColor
        val headerPath = Path().apply {
            addRoundRect(RectF(0f, -80f, WIDTH.toFloat(), HEADER_H), floatArrayOf(0f, 0f, 0f, 0f, 56f, 56f, 56f, 56f), Path.Direction.CW)
        }
        canvas.drawPath(headerPath, fill)
        canvas.drawText("الأسعار المعدّلة", right, 120f, text(Color.WHITE, 64f, true, Paint.Align.RIGHT))
        val df = DateFormats.get("EEEE d MMMM yyyy، h:mm a", Locale("ar"))
        canvas.drawText(df.format(Date()), right, 180f, text(Color.argb(215, 255, 255, 255), 30f, false, Paint.Align.RIGHT))

        var y = HEADER_H + 36f
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val namePaint = text(ink, 42f, true, Paint.Align.RIGHT)
        val oldNewPaint = text(muted, 32f, false, Paint.Align.RIGHT)
        val sellPaint = text(brandColor, 36f, true, Paint.Align.RIGHT)
        val deltaPaint = text(ink, 36f, true, Paint.Align.LEFT)

        if (items.isEmpty()) {
            canvas.drawText("لا توجد أسعار متغيّرة ضمن موادك", WIDTH / 2f, y + 80f, text(muted, 36f, false, Paint.Align.CENTER))
        }
        for (item in items) {
            canvas.drawRoundRect(RectF(PAD, y, right, y + ROW_H), RADIUS, RADIUS, card)
            canvas.drawText(item.name, right - 32f, y + 62f, namePaint)
            canvas.drawText(
                "من ${Formatters.number(item.oldPrice)} إلى ${Formatters.number(item.newPrice)} $currency",
                right - 32f, y + 112f, oldNewPaint
            )
            canvas.drawText(
                "سعر البيع الجديد: ${Formatters.number(item.sellPrice)} $currency",
                right - 32f, y + 160f, sellPaint
            )
            val up = item.delta > 0.0
            val color = if (up) red else green
            deltaPaint.color = color
            val label = Formatters.number(abs(item.delta))
            val tx = PAD + 32f + 40f
            canvas.drawText(label, tx, y + 112f, deltaPaint)
            val tri = Path().apply {
                val cx = PAD + 32f + 14f
                val cy = y + 98f
                if (up) { moveTo(cx, cy - 16f); lineTo(cx + 16f, cy + 14f); lineTo(cx - 16f, cy + 14f) }
                else { moveTo(cx, cy + 16f); lineTo(cx + 16f, cy - 14f); lineTo(cx - 16f, cy - 14f) }
                close()
            }
            fill.color = color
            canvas.drawPath(tri, fill)
            y += ROW_H + ROW_GAP
        }

        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "changed_prices_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
