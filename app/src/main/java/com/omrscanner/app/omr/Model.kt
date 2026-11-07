package com.omrscanner.app.omr

import com.omrscanner.app.data.db.ExamEntity

/** Everything that decides where things are printed on the answer sheet. */
data class Layout(
    val questions: Int,
    val options: Int,
    val labels: String,
    val language: String,
    val rollDigits: Int,
    val setCode: Boolean,
    val nameFields: Boolean,
    val institution: String,
    val examName: String,
) {
    val bangla get() = language == "Bangla"

    fun optionLabels(): List<String> = when (labels) {
        "BANGLA" -> listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ")
        "123" -> listOf("1", "2", "3", "4", "5", "6")
        else -> listOf("A", "B", "C", "D", "E", "F")
    }.take(options)
}

fun ExamEntity.layout() = Layout(questions, options, labels, language, rollDigits, setCode, nameFields, institution, title)

/** What the reader saw on one question. v = option index, or BLANK / DOUBLE. */
data class Mark(
    val v: Int,
    /** the two firm marks when v == DOUBLE */
    val multi: List<Int> = emptyList(),
    /** a much lighter second mark that was treated as erased */
    val erased: Int? = null,
    /** set by the teacher in review */
    val fixed: Boolean = false,
) {
    companion object {
        const val BLANK = -1
        const val DOUBLE = -2
    }
}

/**
 * Compact text form stored in the database, one token per question, comma separated:
 *   "2" option 2 · "2e0" option 2, option 0 erased · "2f" fixed by teacher · "-" blank · "d1.3" double (1 and 3)
 */
object MarkCodec {
    fun encode(marks: List<Mark>): String = marks.joinToString(",") { m ->
        when {
            m.v == Mark.BLANK -> "-"
            m.v == Mark.DOUBLE -> "d" + m.multi.joinToString(".")
            else -> buildString {
                append(m.v)
                if (m.erased != null) append("e").append(m.erased)
                if (m.fixed) append("f")
            }
        }
    }

    fun decode(s: String): List<Mark> = if (s.isBlank()) emptyList() else s.split(",").map { t ->
        when {
            t == "-" -> Mark(Mark.BLANK)
            t.startsWith("d") -> Mark(Mark.DOUBLE, multi = t.drop(1).split(".").mapNotNull { it.toIntOrNull() })
            else -> {
                val fixed = t.endsWith("f")
                val core = t.removeSuffix("f")
                val parts = core.split("e")
                Mark(parts[0].toIntOrNull() ?: Mark.BLANK, erased = parts.getOrNull(1)?.toIntOrNull(), fixed = fixed)
            }
        }
    }
}

object KeyCodec {
    fun encode(key: IntArray) = key.joinToString(",")
    fun decode(s: String): IntArray = if (s.isBlank()) IntArray(0) else s.split(",").map { it.trim().toIntOrNull() ?: -1 }.toIntArray()
    fun blank(n: Int) = IntArray(n) { -1 }
    /** Resize to a new question / option count; new questions start unset. */
    fun fit(key: IntArray, n: Int, opts: Int) = IntArray(n) { i -> if (i < key.size && key[i] >= 0) key[i] % opts else -1 }
}

/** Set code stored for exams printed without a set-code grid. */
const val NO_SET = "—"

object Grading {
    fun score(marks: List<Mark>, key: IntArray?): Int? {
        if (key == null) return null
        return marks.indices.count { i -> i < key.size && key[i] >= 0 && marks[i].v == key[i] }
    }

    /** Questions that need the teacher's decision. A blank is just unanswered, so only doubles under the "review" rule. */
    fun issues(marks: List<Mark>, doubleRule: String) = if (doubleRule == "review") marks.count { it.v == Mark.DOUBLE } else 0

    fun doubles(marks: List<Mark>) = marks.count { it.v == Mark.DOUBLE }
}
