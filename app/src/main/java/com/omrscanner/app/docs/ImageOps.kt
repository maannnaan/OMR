package com.omrscanner.app.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.omrscanner.app.data.db.DocPageEntity
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

/** Image work for scanned documents: loading, filters, rotation and the ID-card page. */
object ImageOps {

    /** Decode a file no larger than [maxDim] on its longest side. */
    fun load(path: String, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun applyFilter(src: Bitmap, filter: String): Bitmap = when (filter) {
        "enhanced" -> colorMatrix(src, ColorMatrix().apply {
            // brighter paper, deeper ink, a little more colour
            val c = 1.28f; val t = (-0.5f * c + 0.5f) * 255f + 18f
            set(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
            postConcat(ColorMatrix().apply { setSaturation(1.15f) })
        })
        "gray" -> colorMatrix(src, ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(1.12f, 0f, 0f, 0f, -8f, 0f, 1.12f, 0f, 0f, -8f, 0f, 0f, 1.12f, 0f, -8f, 0f, 0f, 0f, 1f, 0f)))
        })
        "bw" -> blackAndWhite(src)
        else -> src
    }

    private fun colorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    /** Adaptive threshold: crisp black text on pure white, like a photocopy, even with shadows. */
    private fun blackAndWhite(src: Bitmap): Bitmap {
        val rgba = Mat(); val gray = Mat(); val bin = Mat()
        Utils.bitmapToMat(src.copy(Bitmap.Config.ARGB_8888, false), rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val block = (max(src.width, src.height) / 40).let { if (it % 2 == 0) it + 1 else it }.coerceAtLeast(15)
        Imgproc.adaptiveThreshold(gray, bin, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 12.0)
        Imgproc.cvtColor(bin, rgba, Imgproc.COLOR_GRAY2RGBA)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, out)
        rgba.release(); gray.release(); bin.release()
        return out
    }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Front above back on an A4-shaped white page at true card size (85.6 × 54 mm at 200 dpi),
     * so a printout of the page is a life-size photocopy of the card.
     */
    fun composeIdCard(front: Bitmap, back: Bitmap?): Bitmap {
        val pageW = 1654; val pageH = 2339                        // A4 at 200 dpi
        val cardW = (85.6 / 25.4 * 200).roundToInt()               // 674 px
        val page = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val c = Canvas(page)
        c.drawColor(Color.WHITE)
        val cards = listOfNotNull(front, back)
        val heights = cards.map { (cardW * it.height.toFloat() / it.width) }
        val gap = 140f
        var y = (pageH - heights.sum() - gap * (cards.size - 1)) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        cards.forEachIndexed { i, b ->
            val x = (pageW - cardW) / 2f
            c.drawBitmap(b, null, RectF(x, y, x + cardW, y + heights[i]), paint)
            y += heights[i] + gap
        }
        return page
    }

    /** The finished page exactly as it will appear in the PDF. */
    fun renderPage(page: DocPageEntity, maxDim: Int): Bitmap? {
        val base: Bitmap = if (page.kind == "id") {
            val f = load(page.imagePath, maxDim) ?: return null
            val b = page.backImagePath?.let { load(it, maxDim) }
            composeIdCard(f, b)
        } else load(page.imagePath, maxDim) ?: return null
        return rotate(applyFilter(base, page.filter), page.rotation)
    }

    fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val m = max(src.width, src.height)
        if (m <= maxDim) return src
        val s = maxDim.toFloat() / m
        return Bitmap.createScaledBitmap(src, (src.width * s).roundToInt(), (src.height * s).roundToInt(), true)
    }
}
