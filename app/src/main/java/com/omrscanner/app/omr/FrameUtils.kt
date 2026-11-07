package com.omrscanner.app.omr

import androidx.camera.core.ImageProxy
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.min

/** The luminance plane of a CameraX frame as an upright grayscale OpenCV Mat. */
fun ImageProxy.toUprightGray(): Mat {
    val plane = planes[0]
    val buf = plane.buffer.duplicate()
    val stride = plane.rowStride
    val mat = Mat(height, width, CvType.CV_8UC1)
    if (stride == width) {
        val bytes = ByteArray(width * height)
        buf.get(bytes, 0, min(bytes.size, buf.remaining()))
        mat.put(0, 0, bytes)
    } else {
        val row = ByteArray(width)
        for (r in 0 until height) {
            buf.position(r * stride)
            buf.get(row, 0, min(width, buf.remaining()))
            mat.put(r, 0, row)
        }
    }
    val code = when (imageInfo.rotationDegrees) {
        90 -> Core.ROTATE_90_CLOCKWISE
        180 -> Core.ROTATE_180
        270 -> Core.ROTATE_90_COUNTERCLOCKWISE
        else -> -1
    }
    if (code < 0) return mat
    val out = Mat()
    Core.rotate(mat, out, code)
    mat.release()
    return out
}
