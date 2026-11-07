package com.omrscanner.app.docs

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.omrscanner.app.data.db.DocPageEntity
import java.io.File
import kotlin.math.min

enum class PageSize(val label: String, val w: Int, val h: Int) {
    A4("A4", 595, 842), LETTER("Letter", 612, 792), FIT("Fit to scan", 0, 0)
}

enum class PdfQuality(val label: String, val maxDim: Int) {
    SMALL("Small", 1200), STANDARD("Standard", 1800), HIGH("High", 2600)
}

/** Builds a PDF with one page per scanned page, in the order given. */
object PdfExporter {

    fun export(pages: List<DocPageEntity>, out: File, size: PageSize, quality: PdfQuality, pageNumbers: Boolean): File {
        val doc = PdfDocument()
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 110, 110); textSize = 10f; textAlign = Paint.Align.CENTER }
        val draw = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        pages.forEachIndexed { i, page ->
            val bmp = ImageOps.renderPage(page, quality.maxDim) ?: return@forEachIndexed
            val (pw, ph) = if (size == PageSize.FIT) 595 to (595f * bmp.height / bmp.width).toInt() else size.w to size.h
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
            // ID pages are already A4-shaped at true card size — fill the page so they print life-size
            val margin = if (size == PageSize.FIT || page.kind == "id") 0f else 22f
            val bottom = if (pageNumbers && size != PageSize.FIT) 18f else 0f
            val boxW = pw - 2 * margin; val boxH = ph - 2 * margin - bottom
            val s = min(boxW / bmp.width, boxH / bmp.height)
            val w = bmp.width * s; val h = bmp.height * s
            val left = (pw - w) / 2; val top = margin + (boxH - h) / 2
            p.canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), draw)
            if (pageNumbers) p.canvas.drawText("${i + 1}", pw / 2f, ph - 10f, numPaint)
            doc.finishPage(p)
            bmp.recycle()
        }
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
