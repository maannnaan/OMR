package com.omrscanner.app

import android.util.Size
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.android.resources.Density
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.db.AnswerKeyEntity
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.data.db.SheetEntity
import com.omrscanner.app.data.db.StudentEntity
import com.omrscanner.app.omr.KeyCodec
import com.omrscanner.app.omr.Mark
import com.omrscanner.app.omr.MarkCodec
import com.omrscanner.app.omr.NO_SET
import com.omrscanner.app.omr.SheetPdf
import com.omrscanner.app.omr.layout
import com.omrscanner.app.ui.exams.AnalysisContent
import com.omrscanner.app.ui.exams.AnswerKeyContent
import com.omrscanner.app.ui.exams.ExamContent
import com.omrscanner.app.ui.exams.ExamSettingsContent
import com.omrscanner.app.ui.exams.ExamsContent
import com.omrscanner.app.ui.exams.NewExamContent
import com.omrscanner.app.ui.exams.PrintContent
import com.omrscanner.app.ui.exams.ReviewContent
import com.omrscanner.app.ui.scan.ScanOverlay
import com.omrscanner.app.ui.scan.ScanResultUi
import com.omrscanner.app.ui.settings.SettingsContent
import com.omrscanner.app.ui.settings.SettingsState
import com.omrscanner.app.ui.theme.OmrTheme
import org.junit.Rule
import org.junit.Test

/**
 * Every screen drawn with sample exams on a small phone (360 × 800 dp), so the design can be looked at
 * without one, and so a screen that can't be drawn fails here. `gradlew recordPaparazziDebug` writes the
 * pictures to app/src/test/snapshots/images; a plain test run puts them under app/build/reports/paparazzi.
 */
class ScreensTest {
    private val phone = DeviceConfig.PIXEL_5.copy(screenWidth = 1080, screenHeight = 2400, xdpi = 480, ydpi = 480, density = Density.XXHIGH, softButtons = false)

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = phone,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.5,
    )

    private fun shot(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) { OmrTheme { content() } }

    private val physics = Samples.bundle(1, "Physics · Chapter 3 test", 28, listOf("101", "102", "103", "104"), keyed = true, scanned = 18, remote = "p1", problems = true, uploaded = 10)
    private val chemistry = Samples.bundle(2, "Chemistry weekly MCQ", 28, listOf("101"), keyed = false, scanned = 0)
    private val biology = Samples.bundle(3, "Biology model test", 24, listOf("101", "102"), keyed = true, scanned = 0, bangla = true)
    private val math = Samples.bundle(4, "Higher Math quiz", 20, listOf("101"), keyed = true, scanned = 20)
    private val none: () -> Unit = {}

    @Test fun home() = shot("1-home") { ExamsContent(listOf(physics, chemistry, biology, math), {}, none, none) }

    @Test fun homeEmpty() = shot("1-home-empty") { ExamsContent(emptyList(), {}, none, none) }

    @Composable
    private fun NewExam() = NewExamContent(
        signedIn = true, school = "Dhaka Ideal School", onBack = none, onLecturer = none, onCreate = { _, _, _, _, _, _ -> },
        preview = { SheetPdf.preview(it, 500).asImageBitmap() },
    )

    @Test fun newExam() = shot("2-new-exam") { NewExam() }

    /** The whole form on one tall picture, down to the sheet it makes. */
    @Test fun newExamWhole() {
        paparazzi.unsafeUpdateConfig(deviceConfig = phone.copy(screenHeight = 4800))
        shot("2-new-exam-whole") { NewExam() }
    }

    @Test fun examWithResults() = shot("3-exam") { ExamContent(physics, true, false, none, none, none, none, none, {}, none, none, none, none) }

    @Test fun examWithoutKey() = shot("3-exam-no-key") { ExamContent(chemistry, false, false, none, none, none, none, none, {}, none, none, none, none) }

    @Test fun examReady() = shot("3-exam-ready") { ExamContent(biology, false, false, none, none, none, none, none, {}, none, none, none, none) }

    @Test fun answerKey() = shot("4-answer-key") {
        val key = IntArray(30) { if (it < 12) (it * 7 + 3) % 4 else -1 }
        AnswerKeyContent(physics, "102", key, isDone = { it == "101" }, bankAvailable = false, doneLabel = "Done", onBack = none, onSelect = {}, onAnswer = { _, _ -> }, onAddSet = none, onBank = none, onDone = none)
    }

    @Test fun omrSheets() = shot("5-omr-sheets") {
        PrintContent(biology, SheetPdf.preview(biology.exam.layout(), 700).asImageBitmap(), none, {}, none, none)
    }

    @Test fun scanLooking() = shot("6-scan-looking") {
        ScanOverlay("Physics · Chapter 3 test", "18 of 28 scanned", "Fit the whole sheet in the frame", Color(0xE6FFFFFF), null, Size(1080, 1920), null, false, none, none) { Camera() }
    }

    @Test fun scanRead() = shot("6-scan-read") {
        val corners = listOf(Offset(170f, 380f), Offset(915f, 360f), Offset(950f, 1440f), Offset(140f, 1465f))
        ScanOverlay(
            "Physics · Chapter 3 test", "19 of 28 scanned", "Done! Put the next sheet", Color(0xFF4ADE80), corners, Size(1080, 1920),
            ScanResultUi("Nusrat Jahan", "Roll 004 · Set 104", "26/30", null), true, none, none,
        ) { Camera() }
    }

    @Test fun sheetToCheck() = shot("7-sheet-check") {
        val s = physics.sheets[3]
        ReviewContent(physics, s, physics.marks(s), "Sheet 18 of 18", none, none, none, none, none, none, { _, _ -> }, none, none)
    }

    @Test fun sheetFine() = shot("7-sheet") {
        val s = physics.sheets[0]
        ReviewContent(physics, s, physics.marks(s), "Sheet 1 of 18", none, none, none, none, none, none, { _, _ -> }, none, none)
    }

    @Test fun analysis() = shot("8-analysis") { AnalysisContent(math, none) }

    @Test fun examSettings() = shot("9-exam-settings") { ExamSettingsContent(physics, none, {}, none, none) }

    @Test fun settingsSignedOut() = shot("9-settings") {
        SettingsContent(
            SettingsState(false, "", "https://lecturer-nine.vercel.app", "Dhaka Ideal School", true, 25, 55, "", "1.2.0"), false,
            none, { _, _, _ -> }, none, {}, {}, {}, {}, {}, {}, none,
        )
    }

    @Test fun settingsSignedIn() = shot("9-settings-signed-in") {
        SettingsContent(
            SettingsState(true, "Tanvir Ahmed", "https://lecturer-nine.vercel.app", "Dhaka Ideal School", true, 25, 55, "", "1.2.0"), false,
            none, { _, _, _ -> }, none, {}, {}, {}, {}, {}, {}, none,
        )
    }

    /** A stand-in for the camera picture: a dark desk with a sheet on it. */
    @Composable
    private fun Camera() = Box(Modifier.fillMaxSize().background(Color(0xFF34332F)))
}

/** Sample exams, with marks made up the same way every run. */
private object Samples {
    private val names = listOf(
        "Rahim Uddin", "Fatema Akter", "Tanvir Hasan", "Nusrat Jahan", "Sakib Hossain", "Maliha Rahman", "Arif Chowdhury",
        "Sumaiya Islam", "Imran Kabir", "Tasnim Ahmed", "Rafiq Mia", "Jannatul Ferdous", "Mahmud Hasan", "Sadia Afrin", "Nayeem Khan",
        "Farhana Yasmin", "Shakil Ahmed", "Mithila Das", "Rakib Hasan", "Sharmin Sultana", "Ashik Rahman", "Lamia Haque", "Tahsin Alam",
        "Rubaiya Karim", "Fahim Shahriar", "Anika Tabassum", "Zubair Hossain", "Puja Saha",
    )

    private var seed = 11L
    private fun rnd(n: Int): Int { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % n).toInt() }

    fun bundle(
        id: Long, title: String, students: Int, sets: List<String>, keyed: Boolean, scanned: Int,
        remote: String? = null, problems: Boolean = false, uploaded: Int = 0, bangla: Boolean = false,
    ): ExamBundle {
        seed = 11L + id * 7919 // each exam's marks come out the same whichever test runs first
        val setCode = sets.size > 1
        val exam = ExamEntity(
            id = id, title = title, className = "Class 10", date = "", questions = 30, options = 4,
            labels = if (bangla) "BANGLA" else "ABC", language = if (bangla) "Bangla" else "English",
            rollDigits = 3, setCode = setCode, institution = "Dhaka Ideal School", remotePaperId = remote,
        )
        val roster = names.take(students).mapIndexed { i, n -> StudentEntity(id = id * 1000 + i, examId = id, roll = (i + 1).toString().padStart(3, '0'), name = n) }
        val keys = sets.associateWith { if (keyed) IntArray(30) { rnd(4) } else KeyCodec.blank(30) }
        val keyRows = keys.map { (set, k) -> AnswerKeyEntity(id, if (setCode) set else NO_SET, KeyCodec.encode(k)) }
        val sheets = (0 until scanned).map { i ->
            val set = sets[i % sets.size]
            val marks = keys.getValue(set).map { a ->
                when (rnd(12)) {
                    0 -> Mark(Mark.BLANK)
                    1, 2 -> Mark((a + 1 + rnd(3)) % 4)
                    else -> Mark(a)
                }
            }
            SheetEntity(
                id = id * 1000 + i, examId = id, rollRead = roster[i].roll, roll = roster[i].roll, setRead = set, setCode = if (setCode) set else NO_SET,
                marks = MarkCodec.encode(marks), scannedAt = 1_000_000L + i, uploadedAt = if (i < uploaded) 2_000_000L else null,
            )
        }.toMutableList()
        if (problems) {
            // one sheet's roll isn't on the list, and another's set code couldn't be read
            sheets[3] = sheets[3].copy(rollRead = "031", roll = "031")
            sheets[7] = sheets[7].copy(setRead = "1?3", setCode = "1?3")
        }
        return ExamBundle(exam, roster, keyRows, sheets)
    }
}
