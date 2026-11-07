package com.omrscanner.app.omr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

/** Draws the printable answer sheet — to a PDF for printing, or to a bitmap for the in-app preview. */
object SheetPdf {

    /** One A4 page per copy. */
    fun write(layout: Layout, copies: Int, file: File) {
        val doc = PdfDocument()
        val s = 595f / SheetLayout.W
        val sheet = SheetLayout(layout)
        repeat(copies.coerceIn(1, 500)) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            page.canvas.scale(s, s)
            draw(page.canvas, sheet)
            doc.finishPage(page)
        }
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    fun preview(layout: Layout, widthPx: Int): Bitmap {
        val s = widthPx / SheetLayout.W
        val bmp = Bitmap.createBitmap(widthPx, (SheetLayout.H * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(s, s)
        draw(c, SheetLayout(layout))
        return bmp
    }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private fun stroke(w: Float, c: Int = Color.rgb(120, 126, 134)) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = w }
    private fun text(size: Float, c: Int = Color.BLACK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c; textSize = size; textAlign = align
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun draw(c: Canvas, sl: SheetLayout) {
        val l = sl.layout
        val bn = l.bangla
        val digits = if (bn) "০১২৩৪৫৬৭৮৯" else "0123456789"
        fun num(n: Int) = n.toString().map { digits[it - '0'] }.joinToString("")

        // corner markers + orientation mark (the reader depends on these)
        SheetLayout.markerCenters.forEach { p -> c.drawRect(p.x - 20f, p.y - 20f, p.x + 20f, p.y + 20f, ink) }
        c.drawRect(SheetLayout.orientationMark, ink)

        // header
        val inst = l.institution.ifBlank { if (bn) "শিক্ষা প্রতিষ্ঠানের নাম" else "School name" }
        c.drawText(inst, 500f, 112f, text(30f, bold = true, align = Paint.Align.CENTER))
        c.drawText(l.examName, 500f, 148f, text(22f, Color.rgb(50, 50, 50), true, Paint.Align.CENTER))
        val instr = if (bn) "কালো কলম দিয়ে বৃত্তগুলো সম্পূর্ণ ভরাট করুন" else "Use a black pen. Fill each bubble completely — no ticks or crosses."
        c.drawText(instr, 500f, 176f, text(16f, Color.rgb(107, 112, 121), align = Paint.Align.CENTER))

        // name / class boxes (handwritten, not read)
        if (l.nameFields) {
            val label = text(17f)
            val box = stroke(1.4f, Color.rgb(190, 194, 200))
            c.drawText(if (bn) "নাম" else "Name", 60f, sl.nameTop + 24f, label)
            c.drawRect(150f, sl.nameTop + 4f, 940f, sl.nameTop + 32f, box)
            c.drawText(if (bn) "শ্রেণী" else "Class", 60f, sl.nameTop + 64f, label)
            c.drawRect(150f, sl.nameTop + 44f, 940f, sl.nameTop + 72f, box)
        }

        // roll / set-code grids
        val frame = stroke(1.5f, Color.rgb(170, 174, 182))
        val bubble = stroke(1.6f)
        val digitText = text(13f, Color.rgb(150, 154, 160), align = Paint.Align.CENTER)
        sl.panels.forEach { p ->
            val r = sl.panelRect(p)
            c.drawRoundRect(r, 4f, 4f, frame)
            c.drawText(p.title, (r.left + r.right) / 2, sl.contentTop + 21f, text(15f, align = Paint.Align.CENTER))
            for (col in 0 until p.cols) {
                c.drawRect(sl.writeBox(p, col), frame)
                for (d in 0..9) {
                    val b = sl.idBubble(p, col, d)
                    c.drawCircle(b.x, b.y, sl.idBubbleR, bubble)
                    c.drawText(digits[d].toString(), b.x, b.y + 4.5f, digitText)
                }
            }
        }

        // answers
        val ar = sl.answersRect
        c.drawRoundRect(ar, 4f, 4f, frame)
        c.drawText(if (bn) "উত্তর" else "Answers", (ar.left + ar.right) / 2, sl.contentTop + 26f, text(18f, bold = true, align = Paint.Align.CENTER))
        val labels = l.optionLabels()
        val numPaint = text(min(15f, sl.rowH * 0.45f), Color.rgb(120, 125, 133), align = Paint.Align.RIGHT)
        val optPaint = text(sl.bubbleR * 1.05f, Color.rgb(160, 164, 170), align = Paint.Align.CENTER)
        val ab = stroke(if (sl.bubbleR < 9f) 1.2f else 1.6f)
        for (q in 0 until l.questions) {
            val np = sl.numberPos(q)
            c.drawText(num(q + 1) + ".", np.x, np.y + numPaint.textSize * 0.35f, numPaint)
            for (o in 0 until l.options) {
                val b = sl.answerBubble(q, o)
                c.drawCircle(b.x, b.y, sl.bubbleR, ab)
                c.drawText(labels[o], b.x, b.y + optPaint.textSize * 0.36f, optPaint)
            }
        }

        val warn = if (bn) "কোণার কালো চিহ্নের কাছে কিছু লিখবেন না বা দাগ দেবেন না" else "Do not write or mark near the black corner squares."
        c.drawText(warn, 500f, 1330f, text(14f, Color.rgb(107, 112, 121), align = Paint.Align.CENTER))
    }

    private fun min(a: Float, b: Float) = if (a < b) a else b
    @Suppress("unused") private fun RectF.inset(d: Float) = RectF(left + d, top + d, right - d, bottom - d)
}
