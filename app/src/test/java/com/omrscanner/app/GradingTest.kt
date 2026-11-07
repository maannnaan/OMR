package com.omrscanner.app

import com.omrscanner.app.omr.Grading
import com.omrscanner.app.omr.KeyCodec
import com.omrscanner.app.omr.Mark
import com.omrscanner.app.omr.MarkCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GradingTest {
    @Test fun marksRoundTrip() {
        val marks = listOf(Mark(2), Mark(Mark.BLANK), Mark(Mark.DOUBLE, listOf(1, 3)), Mark(0, erased = 2), Mark(3, fixed = true))
        assertEquals(marks, MarkCodec.decode(MarkCodec.encode(marks)))
    }

    @Test fun keyResizeKeepsAnswersAndClearsNewQuestions() {
        assertArrayEquals(intArrayOf(0, 1, -1), KeyCodec.fit(intArrayOf(4, 1), 3, 4))
    }

    @Test fun scoreSkipsUnsetKeyAndDoubles() {
        val marks = listOf(Mark(1), Mark(2), Mark(Mark.DOUBLE, listOf(0, 1)), Mark(3))
        assertEquals(1, Grading.score(marks, intArrayOf(1, 0, 0, -1)))
    }

    @Test fun doublesNeedReviewOnlyUnderReviewRule() {
        val marks = listOf(Mark(Mark.DOUBLE, listOf(0, 1)), Mark(Mark.BLANK))
        assertEquals(1, Grading.issues(marks, "review"))
        assertEquals(0, Grading.issues(marks, "wrong"))
    }
}
