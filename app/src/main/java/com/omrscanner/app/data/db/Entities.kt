package com.omrscanner.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An exam and the layout of the answer sheet printed for it. */
@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val className: String,
    val date: String,
    val questions: Int = 30,
    val options: Int = 4,
    /** "ABC", "BANGLA" (ক খ গ) or "123" */
    val labels: String = "ABC",
    /** "English" or "Bangla" — language printed on the sheet */
    val language: String = "English",
    val rollDigits: Int = 3,
    val setCode: Boolean = true,
    val nameFields: Boolean = true,
    val institution: String = "",
    /** What two firm marks on one question mean: "wrong" (0 marks) or "review" (teacher decides). */
    val doubleRule: String = "wrong",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "students", indices = [Index("examId")])
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val roll: String,
    val name: String,
)

/** One answer key per set code. [answers] is comma separated option indexes; -1 = not set yet. */
@Entity(tableName = "answer_keys", primaryKeys = ["examId", "setCode"])
data class AnswerKeyEntity(
    val examId: Long,
    val setCode: String,
    val answers: String,
    /** Question-bank paper code when fetched from the database, null when typed by hand. */
    val source: String? = null,
    val edited: Boolean = false,
)

/** A scanned answer sheet. Roll / set are what the teacher confirmed; *Read are what the scanner saw. */
@Entity(tableName = "sheets", indices = [Index("examId")])
data class SheetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val rollRead: String,
    val roll: String,
    val setRead: String,
    val setCode: String,
    /** Encoded marks, see [com.omrscanner.app.omr.MarkCodec]. */
    val marks: String,
    /** "col:d1d2" when two bubbles were filled in one roll / set column. */
    val rollAlt: String = "",
    val setAlt: String = "",
    val approved: Boolean = false,
    val tilt: Int = 0,
    val rotation: Int = 0,
    val imagePath: String? = null,
    val scannedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A page of a scanned document. kind = "doc" (one image) or "id" (front + back on one page). */
@Entity(tableName = "doc_pages", indices = [Index("docId")])
data class DocPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val position: Int,
    val kind: String,
    val imagePath: String,
    val backImagePath: String? = null,
    /** original | enhanced | gray | bw */
    val filter: String = "enhanced",
    val rotation: Int = 0,
)
