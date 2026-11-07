package com.omrscanner.app.omr

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the answer sheet in a camera frame by its four solid corner squares.
 *
 * Returns the squares' centres ordered TL, TR, BR, BL **in sheet terms** — the small orientation square
 * next to the printed top-left marker tells us which photo corner is the sheet's top-left, so a sheet
 * that is turned or upside down is still read the right way round.
 */
object OmrDetector {

    data class Detection(
        /** Marker centres TL, TR, BR, BL in full-frame pixel coordinates. */
        val corners: List<Point>,
        /** Rough camera tilt from the perspective distortion, in degrees. */
        val tilt: Double,
        /** 0, 90, 180 or 270 — how far the sheet is turned in the photo. */
        val rotation: Int,
        val frameW: Int,
        val frameH: Int,
    )

    private data class Blob(val c: Point, val size: Double)

    fun detect(gray: Mat): Detection? {
        val maxSide = max(gray.cols(), gray.rows()).toDouble()
        val scale = if (maxSide > 1200) 1200.0 / maxSide else 1.0
        val small = Mat()
        Imgproc.resize(gray, small, Size(gray.cols() * scale, gray.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        Imgproc.GaussianBlur(small, small, Size(3.0, 3.0), 0.0)
        // local threshold copes with shadows and with a desk darker than the paper
        val bin = Mat()
        Imgproc.adaptiveThreshold(small, bin, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 51, 25.0)

        // RETR_LIST, not RETR_EXTERNAL: on a dark desk the markers sit inside the paper's outline
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(bin, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val imgArea = small.cols().toDouble() * small.rows()
        var blobs = ArrayList<Blob>()
        for (ct in contours) {
            val area = Imgproc.contourArea(ct)
            if (area < imgArea * 0.00003 || area > imgArea * 0.02) continue
            val c2f = MatOfPoint2f(*ct.toArray())
            val box = Imgproc.minAreaRect(c2f)
            c2f.release()
            val w = box.size.width; val h = box.size.height
            if (w <= 0 || h <= 0 || w / h < 0.6 || w / h > 1.65) continue
            if (area / (w * h) < 0.86) continue                 // squares fill their rotated box; bubbles (circles) only ~0.79
            val hullIdx = org.opencv.core.MatOfInt()
            Imgproc.convexHull(ct, hullIdx)
            val pts = ct.toArray(); val hull = MatOfPoint(*hullIdx.toArray().map { pts[it] }.toTypedArray())
            val hullArea = Imgproc.contourArea(hull)
            hullIdx.release(); hull.release()
            if (hullArea > area * 1.08) continue                 // solid, no notches (not a letter)
            blobs += Blob(Point(box.center.x, box.center.y), sqrt(area))
        }
        small.release(); bin.release(); contours.forEach { it.release() }
        // a thick outline yields two contours (outer and inner edge) — keep one blob per position
        val uniq = ArrayList<Blob>()
        for (bl in blobs.sortedByDescending { it.size }) if (uniq.all { u -> hypot(bl.c.x - u.c.x, bl.c.y - u.c.y) > u.size * 0.5 }) uniq += bl
        blobs = uniq
        if (blobs.size < 4) return null

        // the four markers: the biggest quadrilateral made of similar-sized squares
        val top = blobs.sortedByDescending { it.size }.take(10)
        var best: List<Blob>? = null
        var bestArea = 0.0
        for (a in 0 until top.size) for (b in a + 1 until top.size) for (c in b + 1 until top.size) for (d in c + 1 until top.size) {
            val four = listOf(top[a], top[b], top[c], top[d])
            val sMin = four.minOf { it.size }; val sMax = four.maxOf { it.size }
            if (sMin < sMax * 0.45) continue
            val ordered = orderClockwise(four.map { it.c })
            val area = quadArea(ordered)
            if (area > bestArea) { bestArea = area; best = four }
        }
        val markers = best ?: return null
        if (bestArea < imgArea * 0.12) return null       // too small in the frame to read
        var pts = orderClockwise(markers.map { it.c })

        // orientation: the small square sits right beside the sheet's top-left marker
        val markerSize = markers.map { it.size }.average()
        val orient = blobs.filter { it !in markers && it.size in markerSize * 0.22..markerSize * 0.7 }
            .map { o -> o to pts.indices.minBy { hypot(pts[it].x - o.c.x, pts[it].y - o.c.y) } }
            .filter { (o, i) -> hypot(pts[i].x - o.c.x, pts[i].y - o.c.y) < markerSize * 2.4 }
            .minByOrNull { (o, i) -> hypot(pts[i].x - o.c.x, pts[i].y - o.c.y) }
        val k = orient?.second ?: 0
        pts = List(4) { pts[(it + k) % 4] }

        val full = pts.map { Point(it.x / scale, it.y / scale) }
        return Detection(full, estimateTilt(full), k * 90, gray.cols(), gray.rows())
    }

    /** Clockwise from the top-left-most point (image coordinates, y down). */
    private fun orderClockwise(p: List<Point>): List<Point> {
        val cx = p.sumOf { it.x } / 4; val cy = p.sumOf { it.y } / 4
        val sorted = p.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }   // -π..π : starts left, goes clockwise on screen
        val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
        return List(4) { sorted[(start + it) % 4] }
    }

    private fun quadArea(q: List<Point>): Double {
        var s = 0.0
        for (i in 0 until 4) { val a = q[i]; val b = q[(i + 1) % 4]; s += a.x * b.y - b.x * a.y }
        return abs(s) / 2
    }

    /**
     * Opposite sides of a flat-on photo are equal; tilting the phone makes the far side shorter.
     * acos(short/long) on each axis gives a usable estimate of how far the camera leans.
     */
    private fun estimateTilt(q: List<Point>): Double {
        fun d(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
        val top = d(q[0], q[1]); val bottom = d(q[3], q[2]); val left = d(q[0], q[3]); val right = d(q[1], q[2])
        val tx = Math.toDegrees(acos((min(top, bottom) / max(top, bottom)).coerceIn(0.0, 1.0)))
        val ty = Math.toDegrees(acos((min(left, right) / max(left, right)).coerceIn(0.0, 1.0)))
        return sqrt(tx * tx + ty * ty) * 2.2          // keystone is small for a given lean; scale to degrees of lean
    }

    /** Has the sheet stayed put since the last frame? */
    fun isSteady(a: Detection?, b: Detection?): Boolean {
        if (a == null || b == null) return false
        val tol = max(a.frameW, a.frameH) * 0.012
        return a.corners.indices.all { hypot(a.corners[it].x - b.corners[it].x, a.corners[it].y - b.corners[it].y) < tol }
    }
}
