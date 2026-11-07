package com.omrscanner.app.omr

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Reads a detected sheet: straightens it with a perspective transform, then measures how dark each
 * printed bubble is and decides the marks.
 */
object OmrReader {

    data class Result(
        val marks: List<Mark>,
        /** Read roll; "?" where a column was blank or double-filled. */
        val roll: String,
        /** "col:d1d2" when two bubbles were filled in one roll column, else "". */
        val rollAlt: String,
        val set: String,
        val setAlt: String,
        val tilt: Int,
        val rotation: Int,
        /** The straightened sheet, for the teacher to look at in review. */
        val image: Bitmap,
    )

    /** A bubble counts as filled when it is this much darker than the emptiest bubble in its row. */
    private const val FILLED = 0.28
    /** A second mark below this is just noise (pencil smudge, label glyph). */
    private const val PRESENT = 0.10

    fun read(gray: Mat, det: OmrDetector.Detection, layout: Layout, eraseRatio: Double): Result {
        val w = SheetLayout.W.toDouble(); val h = SheetLayout.H.toDouble()
        val src = MatOfPoint2f(*det.corners.toTypedArray())
        val dst = MatOfPoint2f(*SheetLayout.markerCenters.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val flat = Mat()
        Imgproc.warpPerspective(gray, flat, m, Size(w, h), Imgproc.INTER_LINEAR)

        // even out shadows and uneven light: divide by a heavily blurred copy so paper ≈ white everywhere
        val bg = Mat()
        Imgproc.GaussianBlur(flat, bg, Size(0.0, 0.0), 22.0)
        val norm = Mat()
        Core.divide(flat, bg, norm, 255.0)

        val sl = SheetLayout(layout)
        fun darkness(p: PointF, r: Float): Double {
            val rr = max(2, (r * 0.62f).toInt())
            val x = (p.x - rr).toInt().coerceIn(0, norm.cols() - 2 * rr - 1)
            val y = (p.y - rr).toInt().coerceIn(0, norm.rows() - 2 * rr - 1)
            val roi = norm.submat(Rect(x, y, 2 * rr, 2 * rr))
            val mean = Core.mean(roi).`val`[0]
            roi.release()
            return 1.0 - mean / 255.0
        }

        val marks = (0 until layout.questions).map { q ->
            decide((0 until layout.options).map { darkness(sl.answerBubble(q, it), sl.bubbleR) }, eraseRatio)
        }

        fun readDigits(p: SheetLayout.Panel?): Pair<String, String> {
            if (p == null) return "" to ""
            val sb = StringBuilder(); var alt = ""
            for (col in 0 until p.cols) {
                val mk = decide((0..9).map { darkness(sl.idBubble(p, col, it), sl.idBubbleR) }, eraseRatio)
                when {
                    mk.v >= 0 -> sb.append(mk.v)
                    mk.v == Mark.DOUBLE -> { sb.append('?'); if (alt.isEmpty()) alt = "$col:${mk.multi.joinToString("")}" }
                    else -> sb.append('?')
                }
            }
            return sb.toString() to alt
        }
        val (roll, rollAlt) = readDigits(sl.rollPanel)
        val (set, setAlt) = readDigits(sl.setPanel)

        val rgba = Mat()
        Imgproc.cvtColor(flat, rgba, Imgproc.COLOR_GRAY2RGBA)
        val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)
        listOf(src, dst, m, flat, bg, norm, rgba).forEach { it.release() }

        return Result(marks, roll, rollAlt, if (layout.setCode) set else NO_SET, setAlt, det.tilt.toInt(), det.rotation, bmp)
    }

    /**
     * Darkness is taken relative to the emptiest bubble of the row (which still has a printed outline and label).
     * One clear mark → that option. Two marks → if the lighter is under [eraseRatio] of the darker it was erased,
     * otherwise it is a genuine double fill.
     */
    fun decide(raw: List<Double>, eraseRatio: Double): Mark {
        val base = raw.minOrNull() ?: 0.0
        val d = raw.map { it - base }
        val order = d.indices.sortedByDescending { d[it] }
        val a = order[0]
        if (d[a] < FILLED) return Mark(Mark.BLANK)
        val b = order.getOrNull(1) ?: return Mark(a)
        if (d[b] < PRESENT) return Mark(a)
        return if (d[b] < d[a] * eraseRatio) Mark(a, erased = b) else Mark(Mark.DOUBLE, multi = listOf(min(a, b), max(a, b)))
    }
}
