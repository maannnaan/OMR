package com.omrscanner.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.omrscanner.app.omr.Layout
import com.omrscanner.app.omr.SheetLayout
import com.omrscanner.app.omr.SheetPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun fiveChoices() = sheet("sheet-english-60x5", Layout(60, 5, "ABC", "English", 3, true, true, "Dhaka Ideal School", "English · 60 questions, 5 choices"))

    /** More questions, choices or roll digits shrink the bubbles; a sheet is refused only below a size that still scans well. */
    @Test fun sheetsKeepTheirBubblesBigEnough() {
        val full = Layout(100, 4, "ABC", "English", 3, true, true, "", "")
        assertTrue("100 questions of 4 choices, with a set code", SheetLayout.fits(full))
        assertTrue("…of 5 choices", SheetLayout.fits(full.copy(options = 5)))
        assertTrue("…of 6 choices, without a set code", SheetLayout.fits(full.copy(options = 6, setCode = false)))
        assertFalse("…of 6 choices, with a set code", SheetLayout.fits(full.copy(options = 6)))
        assertEquals(82, SheetLayout.maxQuestions(full.copy(options = 6)))
        assertFalse("an 8-digit roll leaves too little room for 100", SheetLayout.fits(full.copy(rollDigits = 8)))
        assertTrue("…but 60 fit", SheetLayout.fits(full.copy(rollDigits = 8, questions = 60)))
        (2..6).forEach { n -> assertTrue("30 questions of $n choices", SheetLayout.fits(full.copy(questions = 30, options = n))) }
    }
}
