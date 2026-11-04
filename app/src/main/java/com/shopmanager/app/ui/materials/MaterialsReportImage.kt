package com.shopmanager.app.ui.materials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.linePrice
import com.shopmanager.app.data.materials.priceBreakdown
import com.shopmanager.app.data.materials.weightSummary
import com.shopmanager.app.data.materials.priceSummary
import android.graphics.RectF
import android.graphics.Typeface
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the materials shortage list as one formatted PNG "report card" -
 * a branded header, one row per material (name, quantity, price), sized to
 * fit the list - and hands it back as a content Uri ready to drop into a
 * share Intent (see MaterialsScreen's "مشاركة" button).
 *
 * FIX (was plain-text share): sharing raw text ("• اسم: كمية — سعر") pastes
 * badly into WhatsApp/Telegram (no alignment, no branding, wraps
 * unpredictably per-recipient). Rendering it once as an image means every
 * recipient sees the exact same clean, aligned layout no matter which app
 * they open it in.
 *
 * Built with plain android.graphics/Canvas rather than capturing a
 * Composable to a bitmap - this only ever needs to run once, from a plain
 * onClick, with no Compose frame/recomposition timing to line up for an
 * accurate capture.
 */
object MaterialsReportImage {

    private const val WIDTH = 1080
    private const val PAD = 48f
    private const val HEADER_H = 300f
    private const val SECTION_H = 92f
    private const val ROW_H = 118f
    private const val ROW_GAP = 10f
    private const val SUMMARY_H = 200f
    private const val RADIUS = 28f

    private sealed class Item {
        data class Section(val name: String, val count: Int, val total: Double) : Item()
        data class Row(val m: Material, val first: Boolean, val last: Boolean) : Item()
    }

    fun generate(
        context: Context,
        materials: List<Material>,
        prices: Map<String, Double>,
        brandColor: Int,
        groupOf: (String) -> String = { "" }
    ): Uri {
        val sorted = materials.sortedWith(compareByDescending<Material> { it.important }.thenBy { com.shopmanager.app.ui.common.arabicSortKey(it.name) })
        val byGroup = sorted.groupBy { groupOf(it.name).trim() }
        val items = ArrayList<Item>()
        val hasFolders = byGroup.keys.any { it.isNotEmpty() }
        val orderedKeys = byGroup.keys.filter { it.isNotEmpty() }.sortedBy { com.shopmanager.app.ui.common.arabicSortKey(it) } + (if (byGroup.containsKey("")) listOf("") else emptyList())
        for (key in orderedKeys) {
            val list = byGroup.getValue(key)
            if (hasFolders) {
                items.add(Item.Section(if (key.isEmpty()) "أخرى" else key, list.size, list.priceSummary(prices).total))
            }
            list.forEachIndexed { i, m -> items.add(Item.Row(m, i == 0, i == list.lastIndex)) }
        }

        val breakdown = sorted.priceBreakdown(prices)
        val weights = sorted.weightSummary()
        val hasTotal = breakdown.all.total > 0.0

        var bodyH = 0f
        for (entry in items) bodyH += when (entry) {
            is Item.Section -> SECTION_H
            is Item.Row -> ROW_H + ROW_GAP
        }
        if (items.isEmpty()) bodyH = 160f
        val summaryH = if (hasTotal || weights.hasAnything) SUMMARY_H + (if (hasTotal && breakdown.important.total > 0.0) 64f else 0f) else 0f
        val height = (HEADER_H + 36f + bodyH + (if (summaryH > 0f) summaryH + 36f else 0f) + 90f).toInt()

        val reportScale = com.shopmanager.app.ui.common.ReportScale.factor(context)
        val bitmap = Bitmap.createBitmap((WIDTH * reportScale).toInt(), (height * reportScale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(reportScale, reportScale)
        canvas.drawColor(Color.parseColor("#F4F5F7"))

        val currency = AppSettingsState.currencySymbol
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(color: Int, size: Float, bold: Boolean, align: Paint.Align) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; textAlign = align
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        val ink = Color.parseColor("#17181C")
        val muted = Color.parseColor("#7A7F8A")
        val right = WIDTH - PAD

        // Header: brand band with rounded bottom corners, title, date, count chips.
        fill.color = brandColor
        val headerPath = android.graphics.Path().apply {
            addRoundRect(RectF(0f, -80f, WIDTH.toFloat(), HEADER_H), floatArrayOf(0f, 0f, 0f, 0f, 56f, 56f, 56f, 56f), android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(headerPath, fill)
        canvas.drawText("قائمة النواقص", right, 120f, text(Color.WHITE, 64f, true, Paint.Align.RIGHT))
        val df = SimpleDateFormat("EEEE d MMMM yyyy، h:mm a", Locale("ar"))
        canvas.drawText(df.format(Date()), right, 176f, text(Color.argb(215, 255, 255, 255), 30f, false, Paint.Align.RIGHT))

        var cx = right
        val chips = buildList {
            add("${sorted.size} مادة")
            if (hasFolders) add("${byGroup.keys.count { it.isNotEmpty() }} مجلد")
            val star = sorted.count { it.important }
            if (star > 0) add("★ $star هامة")
        }
        val chipText = text(Color.WHITE, 28f, true, Paint.Align.CENTER)
        val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
        for (c in chips) {
            val w = chipText.measureText(c) + 48f
            canvas.drawRoundRect(RectF(cx - w, 212f, cx, 264f), 26f, 26f, chipBg)
            canvas.drawText(c, cx - w / 2f, 248f, chipText)
            cx -= w + 14f
        }

        var y = HEADER_H + 36f
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val divider = Paint().apply { color = Color.parseColor("#EEF0F3"); strokeWidth = 2f }
        val namePaint = text(ink, 40f, true, Paint.Align.RIGHT)
        val qtyPaint = text(muted, 30f, false, Paint.Align.RIGHT)
        val pricePaint = text(brandColor, 38f, true, Paint.Align.LEFT)
        val secPaint = text(ink, 38f, true, Paint.Align.RIGHT)
        val secMeta = text(muted, 28f, false, Paint.Align.LEFT)
        val starPaint = text(Color.parseColor("#F5A623"), 40f, true, Paint.Align.LEFT)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brandColor }

        if (items.isEmpty()) {
            canvas.drawText("لا توجد نواقص حالياً", WIDTH / 2f, y + 80f, text(muted, 36f, false, Paint.Align.CENTER))
        }
        for (entry in items) {
            when (entry) {
                is Item.Section -> {
                    y += 14f
                    canvas.drawRoundRect(RectF(right - 10f, y + 10f, right, y + 58f), 5f, 5f, accent)
                    canvas.drawText(entry.name, right - 30f, y + 50f, secPaint)
                    val meta = if (entry.total > 0.0) "${entry.count} - ${Formatters.number(entry.total)} $currency" else "${entry.count}"
                    canvas.drawText(meta, PAD, y + 48f, secMeta)
                    y += SECTION_H - 14f
                }
                is Item.Row -> {
                    val top = y
                    val bottom = y + ROW_H
                    val rect = RectF(PAD, top, right, bottom)
                    val r = floatArrayOf(
                        if (entry.first) RADIUS else 6f, if (entry.first) RADIUS else 6f,
                        if (entry.first) RADIUS else 6f, if (entry.first) RADIUS else 6f,
                        if (entry.last) RADIUS else 6f, if (entry.last) RADIUS else 6f,
                        if (entry.last) RADIUS else 6f, if (entry.last) RADIUS else 6f
                    )
                    val path = android.graphics.Path().apply { addRoundRect(rect, r, android.graphics.Path.Direction.CW) }
                    canvas.drawPath(path, card)
                    canvas.drawText(entry.m.name, right - 32f, top + 54f, namePaint)
                    canvas.drawText(entry.m.quantityLabel(), right - 32f, top + 96f, qtyPaint)
                    val price = entry.m.linePrice(prices)
                    var left = PAD + 32f
                    if (price != null) {
                        canvas.drawText("${Formatters.number(price)} $currency", left, top + 72f, pricePaint)
                    }
                    if (entry.m.important) {
                        val pw = if (price != null) pricePaint.measureText("${Formatters.number(price)} $currency") + 20f else 0f
                        canvas.drawText("★", left + pw, top + 72f, starPaint)
                    }
                    if (!entry.last) canvas.drawLine(PAD + 32f, bottom + ROW_GAP / 2f, right - 32f, bottom + ROW_GAP / 2f, divider)
                    y += ROW_H + ROW_GAP
                }
            }
        }

        // Summary card: weights + totals.
        if (summaryH > 0f) {
            y += 36f
            val rect = RectF(PAD, y, right, y + summaryH)
            fill.color = ink
            canvas.drawRoundRect(rect, RADIUS + 8f, RADIUS + 8f, fill)
            val light = text(Color.WHITE, 40f, true, Paint.Align.RIGHT)
            val dim = text(Color.argb(180, 255, 255, 255), 28f, false, Paint.Align.RIGHT)
            val parts = buildList {
                if (weights.kilos > 0.0) add("${Formatters.number(weights.kilos)} كيلو")
                if (weights.okes > 0.0) add("${Formatters.number(weights.okes)} لوقية")
                if (weights.pieces > 0.0) add("${Formatters.number(weights.pieces)} بالعدد")
            }
            canvas.drawText("الأوزان المطلوبة", right - 36f, y + 56f, dim)
            canvas.drawText(parts.joinToString("  •  ").ifEmpty { "-" }, right - 36f, y + 108f, light)
            if (hasTotal) {
                val totalPaint = text(Color.WHITE, 46f, true, Paint.Align.LEFT)
                canvas.drawText("الإجمالي", PAD + 36f, y + 56f, text(Color.argb(180, 255, 255, 255), 28f, false, Paint.Align.LEFT))
                canvas.drawText("${Formatters.number(breakdown.all.total)} $currency", PAD + 36f, y + 108f, totalPaint)
                if (breakdown.important.total > 0.0) {
                    val sub = text(Color.argb(210, 255, 255, 255), 30f, false, Paint.Align.RIGHT)
                    canvas.drawText("★ الهامة: ${Formatters.number(breakdown.important.total)} $currency", right - 36f, y + 168f, sub)
                    canvas.drawText("البقية: ${Formatters.number(breakdown.regular.total)} $currency", right - 36f - 380f, y + 168f, sub)
                }
            }
            y += summaryH
        }

        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "materials_report_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
