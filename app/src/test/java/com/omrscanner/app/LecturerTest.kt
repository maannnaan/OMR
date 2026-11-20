package com.omrscanner.app

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.data.db.AnswerKeyEntity
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.data.db.SheetEntity
import com.omrscanner.app.data.db.StudentEntity
import com.omrscanner.app.data.lecturer.LecturerPaper
import com.omrscanner.app.data.lecturer.UploadRequest
import com.omrscanner.app.data.lecturer.UploadResponse
import com.omrscanner.app.data.lecturer.UploadSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The JSON Lecturer.bd sends and takes (its src/server/omr-api.ts), as Retrofit's Gson reads and writes it. */
class LecturerTest {
    private val gson = Gson()

    @Test fun readsAPaperListAsTheBankShowsIt() {
        val json = """[{"id":"7c5e","title":"অর্ধবার্ষিক পরীক্ষা","code":"P-7","subject":"পদার্থবিজ্ঞান","questions":4,"options":4,
            "setCount":3,"firstSet":"101","lastSet":"103","author":"Tanvir Ahmed","updated":"20 Nov 2026"}]"""
        val list = gson.fromJson(json, Array<PaperSummary>::class.java).toList()
        assertEquals(PaperSummary("7c5e", "অর্ধবার্ষিক পরীক্ষা", "P-7", "পদার্থবিজ্ঞান", 4, 4, 3, "101", "103", "Tanvir Ahmed", "20 Nov 2026"), list.single())
    }

    @Test fun readsAPaperWithItsSetsLayoutAndStudents() {
        val json = """{"id":"7c5e","title":"অর্ধবার্ষিক পরীক্ষা","code":"P-7","questions":4,"options":4,
            "sets":{"101":[1,1,3,0],"102":[1,1,0,3],"103":[1,0,3,1]},
            "layout":{"labels":"BANGLA","language":"Bangla","rollDigits":2,"setCode":true,"institution":"তানভীর স্যারের ফিজিক্স","examName":"অর্ধবার্ষিক পরীক্ষা"},
            "students":[{"roll":"01","name":"রাহিম উদ্দিন"},{"roll":"02","name":"নাফিসা রহমান"}]}"""
        val paper = gson.fromJson(json, LecturerPaper::class.java)
        assertEquals(listOf(1, 0, 3, 1), paper.sets["103"])
        assertEquals(2, paper.layout.rollDigits)
        assertEquals("BANGLA", paper.layout.labels)
        assertEquals("01", paper.students.first().roll)
        // As the question bank's Paper, for fetching keys into an exam made here.
        val asPaper = paper.asPaper()
        assertEquals(setOf("101", "102", "103"), asPaper.sets.keys)
        assertEquals(4, asPaper.questions)
    }

    @Test fun writesAnUploadWithTheFieldNamesLecturerBdReads() {
        val body = JsonParser.parseString(gson.toJson(UploadRequest(listOf(UploadSheet("02", "102", "1,-,d0.3,2f", 1_763_600_000_000))))).asJsonObject
        val sheet = body.getAsJsonArray("sheets")[0].asJsonObject
        assertEquals(setOf("roll", "setCode", "marks", "scannedAt"), sheet.keySet())
        assertEquals("02", sheet["roll"].asString)
        assertEquals("102", sheet["setCode"].asString)
        assertEquals("1,-,d0.3,2f", sheet["marks"].asString)
        assertEquals(1_763_600_000_000, sheet["scannedAt"].asLong)
    }

    @Test fun readsWhatBecameOfEachUploadedSheet() {
        val json = """{"saved":1,"sheets":[{"roll":"02","ok":true,"name":"নাফিসা রহমান","score":3.75,"total":4},{"roll":"9x","ok":false,"error":"roll"}]}"""
        val r = gson.fromJson(json, UploadResponse::class.java)
        assertEquals(1, r.saved)
        assertTrue(r.sheets[0].ok)
        assertEquals(3.75, r.sheets[0].score!!, 0.0)
        assertFalse(r.sheets[1].ok)
        assertEquals("roll", r.sheets[1].error)
        assertNull(r.sheets[1].name)
    }

    @Test fun uploadsOnlyCheckedSheetsNotSentSinceTheyChanged() {
        val exam = ExamEntity(id = 1, title = "P-7", className = "", date = "", questions = 2, options = 4, rollDigits = 2, setCode = true, remotePaperId = "7c5e")
        val students = listOf(StudentEntity(1, 1, "01", "রাহিম"), StudentEntity(2, 1, "02", "নাফিসা"))
        val key = listOf(AnswerKeyEntity(1, "101", "1,2"))
        fun sheet(id: Long, roll: String, marks: String = "1,2", approved: Boolean = false, uploadedAt: Long? = null) =
            SheetEntity(id = id, examId = 1, rollRead = roll, roll = roll, setRead = "101", setCode = "101", marks = marks, approved = approved, uploadedAt = uploadedAt)
        val b = ExamBundle(
            exam, students, key,
            listOf(
                sheet(1, "01"),                    // clean, never sent: goes
                sheet(2, "02", uploadedAt = 5),    // already there: stays
                sheet(3, "07"),                    // roll nobody has: needs review first
                sheet(4, "07", approved = true),   // approved as it is: goes
            ),
        )
        assertTrue(b.fromLecturer)
        assertEquals(listOf(1L, 4L), b.toUpload.map { it.id })
        assertEquals(1, b.uploadedCount)
    }
}
