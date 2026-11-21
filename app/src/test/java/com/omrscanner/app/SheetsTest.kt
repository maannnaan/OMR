package com.omrscanner.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.omrscanner.app.omr.Layout
import com.omrscanner.app.omr.SheetPdf
import org.junit.Rule
import org.junit.Test

/**
 * The OMR sheet exactly as it prints (SheetPdf draws the PDF pages and this picture alike), on a page
 * of A4 at 150 dpi. The pictures land beside the screens' in app/src/test/snapshots/images.
 */
class SheetsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1240, screenHeight = 1754, xdpi = 160, ydpi = 160, density = Density.MEDIUM, softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.5,
    )

    private fun sheet(name: String, layout: Layout) = paparazzi.snapshot(name) {
        Image(SheetPdf.preview(layout, 1240).asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
    }

    @Test fun english() = sheet("sheet-english-30", Layout(30, 4, "ABC", "English", 3, true, true, "Dhaka Ideal School", "Physics · Chapter 3 test"))

    @Test fun bangla() = sheet("sheet-bangla-50", Layout(50, 4, "BANGLA", "Bangla", 2, true, true, "ঢাকা আইডিয়াল স্কুল", "পদার্থবিজ্ঞান · মডেল টেস্ট"))

    @Test fun hundred() = sheet("sheet-english-100", Layout(100, 4, "ABC", "English", 3, false, true, "Dhaka Ideal School", "General knowledge · 100 questions"))
}
