package com.mizania.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF receipts and statements built from what the app recorded.
 * Every page says plainly that it comes from Mizania, not from the bank.
 */
object Receipt {

    private const val W = 420   // A5 width in points
    private const val H = 595   // A5 height in points
    private const val M = 32f   // margin

    private val pine = Color.rgb(0x16, 0x27, 0x1F)
    private val gold = Color.rgb(0xB0, 0x7F, 0x07)
    private val muted = Color.rgb(0x5F, 0x6B, 0x63)
    private val line = Color.rgb(0xD5, 0xDB, 0xD3)

    private fun stamp(t: Long) = SimpleDateFormat("d/M/yyyy  HH:mm", Locale.US).format(Date(t))
    private fun day(t: Long) = SimpleDateFormat("d/M/yyyy", Locale.US).format(Date(t))

    class Doc(
        val title: String,
        val subtitle: String,
        val bigAmount: String?,
        val rows: List<Pair<String, String>>,
        val tableHeader: List<String> = emptyList(),
        val table: List<List<String>> = emptyList(),
    )

    // ---------------- what goes on each receipt ----------------

    fun forTxn(ctx: Context, id: Long): Doc? {
        val db = Db.get(ctx)
        val t = db.txn(id) ?: return null
        val cat = db.category(t.categoryId)
        val acc = db.accounts().firstOrNull { it.id == t.accountId }
        val rows = ArrayList<Pair<String, String>>()
        rows += "التاريخ" to stamp(t.time)
        rows += "المبلغ" to fmt(t.amount)
        rows += "الحركة" to if (t.income) "دخل" else "صرف"
        rows += (if (t.income) "من" else "إلى / المزوّد") to t.merchant.ifEmpty { "—" }
        rows += "طريقة التحويل" to method(t)
        if (cat != null) rows += "البند" to cat.name
        db.debtForTxn(id)?.let { did -> db.debt(did)?.let { d -> rows += "على دين" to "${d.person} (${directionLabel(d.owedToMe)})" } }
        db.jamiyaForTxn(id)?.let { jid ->
            db.jamiyas().firstOrNull { it.id == jid }?.let { j ->
                rows += "جمعية" to "${j.name}، شهر ${JamiyaMonths.label(j, JamiyaMonths.indexAt(j, t.time))}"
            }
        }
        db.goalForTxn(id)?.let { gid -> db.goals().firstOrNull { it.id == gid }?.let { g -> rows += "ادخار" to g.name } }
        if (acc != null) rows += "الحساب" to (acc.label + if (t.acctNo.isNotEmpty()) "  ${t.acctNo}" else "")
        if (t.note.isNotBlank()) rows += "ملاحظة" to t.note
        rows += "رقم الحركة" to "#$id"
        return Doc("إيصال حركة", t.merchant.ifEmpty { t.kind }, (if (t.income) "+" else "") + fmt(t.amount), rows)
    }

    private fun method(t: Txn): String = when {
        t.manual -> "كاش"
        t.kind.contains("كليك") -> "كليك (CliQ)"
        t.kind.contains("حوالة") || t.kind.contains("تحويل") -> "حوالة بنكية"
        t.kind.contains("شراء") -> "شراء بالبطاقة"
        t.kind.contains("سحب") -> "سحب من الصراف"
        t.kind.contains("راتب") -> "راتب"
        t.kind.contains("إيداع") -> "إيداع"
        else -> t.kind.ifEmpty { "—" }
    }

    fun forDebt(ctx: Context, id: Long): Doc? {
        val db = Db.get(ctx)
        val d = db.debt(id) ?: return null
        val rows = arrayListOf(
            "الشخص" to d.person,
            "الدين" to if (d.owedToMe) "إلي (هو مستلف مني)" else "عليّ (أنا مستلف)",
            "المبلغ الأصلي" to fmt(d.amount),
        )
        if (d.added > 0) rows += "زيادات" to fmt(d.added)
        rows += (if (d.owedToMe) "رجعلي" else "سددت") to fmt(d.paid)
        rows += "الباقي" to fmt(kotlin.math.max(0.0, d.remaining))
        if (d.note.isNotBlank()) rows += "ملاحظة" to d.note
        rows += "تاريخ الدين" to day(d.createdAt)
        val table = db.debtEntries(id).sortedBy { it.time }.map { e ->
            listOf(
                day(e.time),
                if (e.note == Db.CORRECTION) "تعديل" else entryLabel(d, e.kind) + if (e.txnId != null) " (بنك)" else "",
                fmt(e.amount),
            )
        }
        return Doc("كشف دين", d.person, fmt(kotlin.math.max(0.0, d.remaining)) + " باقي", rows,
            listOf("التاريخ", "البيان", "المبلغ"), table)
    }

    fun forJamiya(ctx: Context, id: Long): Doc? {
        val db = Db.get(ctx)
        val j = db.jamiyas().firstOrNull { it.id == id } ?: return null
        val paid = j.paidMonths.count { it in 0 until j.months }
        val rows = arrayListOf(
            "الجمعية" to j.name,
            "القسط الشهري" to fmt(j.monthly),
            "عدد الأشهر" to "${j.months}",
            "دوري" to "الشهر ${j.myTurn} (${JamiyaMonths.label(j, j.myTurn - 1)})",
            "دفعت" to "$paid شهر، ${fmt(paid * j.monthly)}",
            "قبضت" to if (j.received) "نعم، ${fmt(j.monthly * j.months)}" else "لسا",
        )
        val table = (0 until j.months).map { i ->
            listOf(JamiyaMonths.label(j, i), if (i in j.paidMonths) "مدفوع" else "—", if (i == j.myTurn - 1) "دوري" else "")
        }
        return Doc("كشف جمعية", j.name, fmt(paid * j.monthly) + " مدفوع", rows, listOf("الشهر", "الدفع", ""), table)
    }

    fun forCategory(ctx: Context, cat: Category?, range: Range, periodLabel: String): Doc {
        val db = Db.get(ctx)
        val list = db.txnsInCategory(cat?.id, range.from, range.to).sortedBy { it.time }
        val total = list.sumOf { it.amount }
        val rows = arrayListOf("البند" to (cat?.name ?: "بدون بند"), "الفترة" to periodLabel, "عدد الحركات" to "${list.size}",
            "المجموع" to fmt(total))
        if (cat != null && cat.budget > 0) rows += "الميزانية" to fmt(cat.budget)
        val table = list.map { listOf(day(it.time), it.merchant.ifEmpty { it.kind }, fmt(it.amount)) }
        return Doc("كشف بند", cat?.name ?: "بدون بند", fmt(total), rows, listOf("التاريخ", "الجهة", "المبلغ"), table)
    }

    // ---------------- drawing ----------------

    private class Fonts(ctx: Context) {
        val regular: Typeface = load(ctx, "fonts/plex-regular.ttf") ?: Typeface.DEFAULT
        val bold: Typeface = load(ctx, "fonts/plex-bold.ttf") ?: Typeface.DEFAULT_BOLD
        val numbers: Typeface = load(ctx, "fonts/readex.ttf") ?: bold

        private fun load(ctx: Context, path: String): Typeface? =
            try { Typeface.createFromAsset(ctx.assets, path) } catch (e: Exception) { null }
    }

    private fun paint(tf: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; this.color = color
    }

    /** Draws right-to-left text in a box; returns its height. */
    private fun text(c: Canvas, s: String, p: TextPaint, x: Float, y: Float, width: Float, align: Layout.Alignment): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(10))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, 1.15f)
            .build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    fun render(ctx: Context, doc: Doc, fileName: String): File {
        val f = Fonts(ctx)
        val pdf = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f
        val inner = W - 2 * M
        val made = SimpleDateFormat("d/M/yyyy HH:mm", Locale.US).format(Date())

        fun finish() {
            val cc = c ?: return
            val foot = "صادر من تطبيق «ميزانيتي» بتاريخ $made. ملخّص من رسائل البنك على الهاتف، وليس مستنداً رسمياً من البنك."
            val lp = Paint().apply { color = line; strokeWidth = 0.8f }
            cc.drawLine(M, H - 52f, W - M, H - 52f, lp)
            text(cc, foot, paint(f.regular, 7.5f, muted), M, H - 46f, inner, Layout.Alignment.ALIGN_NORMAL)
            text(cc, "صفحة $pageNo", paint(f.regular, 7.5f, muted), M, H - 22f, inner, Layout.Alignment.ALIGN_OPPOSITE)
            pdf.finishPage(page!!)
        }

        fun newPage() {
            if (page != null) finish()
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page!!.canvas
            y = M
        }

        fun ensure(h: Float) { if (y + h > H - 64f) newPage() }

        newPage()
        val cc0 = c!!
        // Header band.
        val band = Paint().apply { color = pine }
        cc0.drawRect(0f, 0f, W.toFloat(), 96f, band)
        text(cc0, "ميزانيتي", paint(f.bold, 10f, Color.rgb(0xF0, 0xBE, 0x45)), M, 18f, inner, Layout.Alignment.ALIGN_NORMAL)
        text(cc0, doc.title, paint(f.bold, 20f, Color.WHITE), M, 34f, inner, Layout.Alignment.ALIGN_NORMAL)
        text(cc0, doc.subtitle, paint(f.regular, 10f, Color.rgb(0xD6, 0xDD, 0xD8)), M, 66f, inner, Layout.Alignment.ALIGN_NORMAL)
        y = 112f
        doc.bigAmount?.let {
            y += text(cc0, "$it د.أ", paint(f.numbers, 26f, gold), M, y, inner, Layout.Alignment.ALIGN_NORMAL) + 10f
        }

        // Label / value rows.
        val labelP = paint(f.regular, 10f, muted)
        val valueP = paint(f.bold, 10.5f, pine)
        val lp = Paint().apply { color = line; strokeWidth = 0.6f }
        for ((k, v) in doc.rows) {
            ensure(28f)
            val cc = c!!
            val h1 = text(cc, k, labelP, M + inner * 0.55f, y + 6f, inner * 0.45f, Layout.Alignment.ALIGN_NORMAL)
            val h2 = text(cc, v, valueP, M, y + 6f, inner * 0.55f, Layout.Alignment.ALIGN_OPPOSITE)
            y += maxOf(h1, h2) + 12f
            cc.drawLine(M, y, W - M, y, lp)
        }

        // Table.
        if (doc.table.isNotEmpty()) {
            y += 16f
            val cols = doc.tableHeader.size.coerceAtLeast(doc.table.first().size)
            val colW = inner / cols
            val headP = paint(f.bold, 9.5f, Color.WHITE)
            val cellP = paint(f.regular, 9.5f, pine)
            fun header() {
                val cc = c!!
                cc.drawRect(M, y, W - M, y + 22f, Paint().apply { color = pine })
                doc.tableHeader.forEachIndexed { i, h ->
                    // Column 0 sits on the right, like the rest of the page.
                    text(cc, h, headP, W - M - (i + 1) * colW + 6f, y + 5f, colW - 12f, Layout.Alignment.ALIGN_NORMAL)
                }
                y += 22f
            }
            ensure(48f)
            header()
            doc.table.forEachIndexed { r, row ->
                if (y + 22f > H - 64f) { newPage(); header() }
                val cc = c!!
                if (r % 2 == 1) cc.drawRect(M, y, W - M, y + 20f, Paint().apply { color = Color.rgb(0xF1, 0xF4, 0xEF) })
                var rowH = 20f
                row.forEachIndexed { i, cell ->
                    val h = text(cc, cell, cellP, W - M - (i + 1) * colW + 6f, y + 4f, colW - 12f, Layout.Alignment.ALIGN_NORMAL)
                    rowH = maxOf(rowH, h + 8f)
                }
                y += rowH
            }
        }
        finish()

        val dir = File(ctx.cacheDir, "receipts").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val out = File(dir, fileName)
        out.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    /**
     * Saves a rendered PDF to the phone: Downloads/Mizania on Android 10+, otherwise the app's
     * Documents folder. Returns where it went, for the message to the user.
     */
    fun save(ctx: Context, doc: Doc?, fileName: String): String? {
        doc ?: return null
        val file = render(ctx, doc, fileName)
        val name = fileName.removeSuffix(".pdf") + "-" +
            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".pdf"
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/Mizania")
                }
                val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                "التنزيلات ← Mizania"
            } else {
                val dir = File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "Mizania").apply { mkdirs() }
                file.copyTo(File(dir, name), overwrite = true)
                dir.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Opens the share sheet (WhatsApp, Drive, Files…) for a rendered PDF. */
    fun share(ctx: Context, doc: Doc?, fileName: String) {
        doc ?: return
        val file = render(ctx, doc, fileName)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, doc.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** "Share" and "Save to phone" side by side, for any receipt or statement. */
@Composable
fun ReceiptButtons(label: String, fileName: String, build: (Context) -> Receipt.Doc?) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Receipt.share(ctx, build(ctx), fileName) }, modifier = Modifier.weight(1f)) {
                Text("مشاركة")
            }
            OutlinedButton(
                onClick = {
                    val where = Receipt.save(ctx, build(ctx), fileName)
                    Toast.makeText(ctx, if (where != null) "انحفظ الملف بـ $where" else "ما زبط الحفظ", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.weight(1f),
            ) { Text("حفظ على الجهاز") }
        }
    }
}
