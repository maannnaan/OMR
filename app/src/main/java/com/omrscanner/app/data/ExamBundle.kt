package com.omrscanner.app.data

import com.omrscanner.app.data.db.AnswerKeyEntity
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.data.db.SheetEntity
import com.omrscanner.app.data.db.StudentEntity
import com.omrscanner.app.omr.Grading
import com.omrscanner.app.omr.KeyCodec
import com.omrscanner.app.omr.Mark
import com.omrscanner.app.omr.MarkCodec
import com.omrscanner.app.omr.NO_SET
import com.omrscanner.app.omr.layout

enum class IdIssue { ROLL_MISSING, ROLL_DUP, SET }

/** An exam with its roster, answer keys and scanned sheets, plus the grading questions the screens ask. */
data class ExamBundle(
    val exam: ExamEntity,
    val students: List<StudentEntity>,
    val keyRows: List<AnswerKeyEntity>,
    val sheets: List<SheetEntity>,
) {
    val layout get() = exam.layout()
    val n get() = exam.questions
    val keys: Map<String, IntArray> = keyRows.associate { it.setCode to KeyCodec.fit(KeyCodec.decode(it.answers), exam.questions, exam.options) }
    val sets: List<String> get() = if (exam.setCode) keys.keys.sortedWith(compareBy({ it.length }, { it })) else listOf(NO_SET)
    val total get() = students.size

    private val marksCache = HashMap<Long, List<Mark>>()
    fun marks(s: SheetEntity): List<Mark> = marksCache.getOrPut(s.id) { MarkCodec.decode(s.marks) }

    fun key(set: String): IntArray? = keys[if (exam.setCode) set else NO_SET]
    fun keyDone(set: String) = key(set)?.count { it >= 0 } ?: 0
    val keyGaps: List<String> get() = sets.filter { (key(it)?.any { k -> k < 0 }) ?: true }
    val noKey: Boolean get() = sets.none { keyDone(it) > 0 }

    fun score(s: SheetEntity): Int? = Grading.score(marks(s), key(s.setCode))
    fun student(roll: String) = students.firstOrNull { it.roll == roll }
    fun nameOf(s: SheetEntity) = student(s.roll)?.name ?: "Unknown student"

    fun idIssue(s: SheetEntity): IdIssue? {
        if (exam.rollDigits > 0) {
            if (student(s.roll) == null) return IdIssue.ROLL_MISSING
            if (sheets.any { it.id != s.id && it.roll == s.roll }) return IdIssue.ROLL_DUP
        }
        if (exam.setCode && key(s.setCode) == null) return IdIssue.SET
        return null
    }

    fun needsCheck(s: SheetEntity) = !s.approved && (Grading.issues(marks(s), exam.doubleRule) > 0 || idIssue(s) != null)
    val reviewCount get() = sheets.count { needsCheck(it) }
    val unmatched get() = students.filter { st -> sheets.none { it.roll == st.roll } }
    val left get() = (total - sheets.size).coerceAtLeast(0)

    fun flagText(s: SheetEntity): String = when (idIssue(s)) {
        IdIssue.SET -> "Check set"
        IdIssue.ROLL_MISSING -> if (s.roll.contains('?') && s.rollAlt.isNotEmpty()) "Roll double" else "Check roll"
        IdIssue.ROLL_DUP -> "Check roll"
        null -> "${Grading.issues(marks(s), exam.doubleRule)} to check"
    }

    val percents: List<Double> get() = sheets.mapNotNull { s -> score(s)?.let { it * 100.0 / n } }
    val average: String get() = percents.takeIf { it.isNotEmpty() }?.let { "${it.average().toInt()}%" } ?: "—"
}
