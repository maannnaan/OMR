package com.omrscanner.app.omr

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry of the printed answer sheet in a fixed coordinate space of 1000 × 1414 units (A4 ratio).
 *
 * The SAME object is used to print the sheet (SheetPdf) and to read it (OmrReader), so every bubble
 * the reader samples is exactly where the printer put it.
 */
class SheetLayout(val layout: Layout) {
    companion object {
        const val W = 1000f
        const val H = 1414f
        const val MARKER = 40f
        /** Centres of the four solid corner squares: TL, TR, BR, BL. The reader maps these to the photo's corners. */
        val markerCenters = listOf(PointF(50f, 50f), PointF(950f, 50f), PointF(950f, 1364f), PointF(50f, 1364f))
        /** Small square beside the top-left marker — tells the reader which way is up. */
        val orientationMark = RectF(86f, 30f, 102f, 46f)
        const val CONTENT_BOTTOM = 1300f
    }

    data class Panel(val title: String, val x: Float, val cols: Int)

    val nameTop = 196f
    val contentTop = if (layout.nameFields) 286f else 214f

    // ── roll and set-code grids ─────────────────────────────
    val cell = 34f
    val idBubbleR = 12f
    val panelTitleH = 30f
    val writeRowH = 34f
    val gridTop get() = contentTop + panelTitleH + writeRowH + 10f

    val panels: List<Panel> = buildList {
        var x = 70f
        if (layout.rollDigits > 0) {
            add(Panel(if (layout.bangla) "রোল" else "Roll no.", x, layout.rollDigits))
            x += layout.rollDigits * cell + 26f
        }
        if (layout.setCode) add(Panel(if (layout.bangla) "সেট কোড" else "Set code", x, 3))
    }
    val rollPanel get() = panels.firstOrNull { layout.rollDigits > 0 }
    val setPanel get() = if (layout.setCode) panels.last() else null

    fun panelRect(p: Panel) = RectF(p.x - 8f, contentTop, p.x + p.cols * cell + 8f, gridTop + 10 * cell + 8f)
    fun idBubble(p: Panel, col: Int, digit: Int) = PointF(p.x + col * cell + cell / 2, gridTop + digit * cell + cell / 2)
    fun writeBox(p: Panel, col: Int) = RectF(p.x + col * cell, contentTop + panelTitleH, p.x + (col + 1) * cell, contentTop + panelTitleH + writeRowH)

    // ── answers ─────────────────────────────────────────────
    val ansLeft = (panels.lastOrNull()?.let { it.x + it.cols * cell + 8f } ?: 40f) + 26f
    val ansRight = 950f
    val ansTop = contentTop + 40f
    val numW = 46f
    val colGap = 18f
    val cols: Int
    val perCol: Int
    val rowH: Float
    val optStep: Float
    val bubbleR: Float

    init {
        val n = layout.questions
        val areaW = ansRight - ansLeft
        val avail = CONTENT_BOTTOM - ansTop - 8f
        // comfortable: 34-unit option spacing, about 10 rows per column
        val comfortCols = max(1, floor(areaW / (numW + layout.options * 34f + colGap)).toInt())
        // tight: narrower option spacing, used only when rows would otherwise get too short to read
        val tightCols = max(1, floor(areaW / (numW + layout.options * 24f + colGap)).toInt())
        val rowsThatFit = max(1, floor(avail / 24f).toInt())
        val needed = ceil(n / rowsThatFit.toDouble()).toInt()
        cols = max(1, max(min(comfortCols, ceil(n / 10.0).toInt()), min(needed, tightCols)))
        perCol = ceil(n / cols.toDouble()).toInt()
        rowH = min(40f, (CONTENT_BOTTOM - ansTop - 8f) / perCol)
        optStep = min(34f, (areaW / cols - numW - colGap) / layout.options)
        bubbleR = min(12f, min(rowH * 0.34f, optStep * 0.38f))
    }

    private val colWidth get() = (ansRight - ansLeft) / cols

    fun answerBubble(q: Int, opt: Int): PointF {
        val c = q / perCol
        val r = q % perCol
        val x = ansLeft + c * colWidth + numW + opt * optStep + optStep / 2
        val y = ansTop + r * rowH + rowH / 2
        return PointF(x, y)
    }

    fun numberPos(q: Int): PointF {
        val c = q / perCol
        val r = q % perCol
        return PointF(ansLeft + c * colWidth + numW - 10f, ansTop + r * rowH + rowH / 2)
    }

    val answersRect get() = RectF(ansLeft - 8f, contentTop, ansRight, CONTENT_BOTTOM)
}
