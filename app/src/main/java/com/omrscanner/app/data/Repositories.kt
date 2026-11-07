package com.omrscanner.app.data

import android.net.Uri
import com.omrscanner.app.data.bank.Paper
import com.omrscanner.app.data.db.AnswerKeyEntity
import com.omrscanner.app.data.db.AppDatabase
import com.omrscanner.app.data.db.DocPageEntity
import com.omrscanner.app.data.db.DocumentEntity
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.data.db.SheetEntity
import com.omrscanner.app.data.db.StudentEntity
import com.omrscanner.app.docs.ImageOps
import com.omrscanner.app.omr.KeyCodec
import com.omrscanner.app.omr.MarkCodec
import com.omrscanner.app.omr.NO_SET
import com.omrscanner.app.omr.OmrReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File

class ExamRepository(private val db: AppDatabase, private val files: FileStore) {
    private val dao = db.exams()

    val bundles: Flow<List<ExamBundle>> = combine(dao.exams(), dao.students(), dao.keys(), dao.sheets()) { exams, students, keys, sheets ->
        exams.map { e -> ExamBundle(e, students.filter { it.examId == e.id }, keys.filter { it.examId == e.id }, sheets.filter { it.examId == e.id }) }
    }

    suspend fun create(exam: ExamEntity, names: List<String>): Long {
        val id = dao.insertExam(exam)
        setStudents(id, exam.rollDigits, names)
        if (!exam.setCode) dao.upsertKeys(listOf(AnswerKeyEntity(id, NO_SET, KeyCodec.encode(KeyCodec.blank(exam.questions)))))
        return id
    }

    suspend fun setStudents(examId: Long, rollDigits: Int, names: List<String>) {
        dao.clearStudents(examId)
        val width = maxOf(rollDigits, names.size.toString().length, 1)
        dao.insertStudents(names.mapIndexed { i, n -> StudentEntity(examId = examId, roll = (i + 1).toString().padStart(width, '0'), name = n.ifBlank { "Student ${i + 1}" }) })
    }

    /** Saves layout changes; keys are resized when the question / option count changes. */
    suspend fun update(exam: ExamEntity) {
        val old = dao.exam(exam.id)
        dao.updateExam(exam)
        if (old != null && (old.questions != exam.questions || old.options != exam.options)) {
            val keys = dao.keysFor(exam.id).map { it.copy(answers = KeyCodec.encode(KeyCodec.fit(KeyCodec.decode(it.answers), exam.questions, exam.options)), source = null) }
            dao.upsertKeys(keys)
        }
        if (old != null && old.setCode != exam.setCode) {
            dao.clearKeys(exam.id)
            if (!exam.setCode) dao.upsertKeys(listOf(AnswerKeyEntity(exam.id, NO_SET, KeyCodec.encode(KeyCodec.blank(exam.questions)))))
        }
    }

    suspend fun delete(examId: Long) {
        dao.sheetsFor(examId).forEach { files.delete(it.imagePath) }
        dao.clearSheets(examId); dao.clearKeys(examId); dao.clearStudents(examId); dao.deleteExam(examId)
    }

    suspend fun saveKey(examId: Long, set: String, key: IntArray, source: String?, edited: Boolean) =
        dao.upsertKeys(listOf(AnswerKeyEntity(examId, set, KeyCodec.encode(key), source, edited)))

    suspend fun deleteKey(examId: Long, set: String) = dao.deleteKey(examId, set)

    /** Brings in every set of a question-bank paper. Returns how many sets were written. */
    suspend fun applyPaper(exam: ExamEntity, paper: Paper): Int {
        val rows = if (exam.setCode) paper.sets.map { (set, ans) -> AnswerKeyEntity(exam.id, set, KeyCodec.encode(KeyCodec.fit(ans.toIntArray(), exam.questions, exam.options)), paper.code) }
        else listOf(AnswerKeyEntity(exam.id, NO_SET, KeyCodec.encode(KeyCodec.fit(paper.sets.values.first().toIntArray(), exam.questions, exam.options)), paper.code))
        dao.upsertKeys(rows)
        return rows.size
    }

    suspend fun addScan(examId: Long, r: OmrReader.Result): SheetEntity = withContext(Dispatchers.IO) {
        val path = files.saveJpeg(r.image, "scans", 82)
        val s = SheetEntity(examId = examId, rollRead = r.roll, roll = r.roll, setRead = r.set, setCode = r.set, marks = MarkCodec.encode(r.marks),
            rollAlt = r.rollAlt, setAlt = r.setAlt, tilt = r.tilt, rotation = r.rotation, imagePath = path)
        s.copy(id = dao.insertSheet(s))
    }

    suspend fun updateSheet(s: SheetEntity) = dao.updateSheet(s)
    suspend fun deleteSheet(s: SheetEntity) { files.delete(s.imagePath); dao.deleteSheet(s.id) }

    /** results_<exam>.csv: roll, name, set, score, percent, then one column per question. */
    suspend fun exportCsv(b: ExamBundle): File = withContext(Dispatchers.IO) {
        val labels = b.layout.optionLabels()
        val f = files.exportFile("results_${b.exam.title}.csv")
        f.bufferedWriter().use { w ->
            w.write("Roll,Name,Set,Score,Percent," + (1..b.n).joinToString(",") { "Q$it" }); w.newLine()
            b.sheets.sortedBy { it.roll }.forEach { s ->
                val sc = b.score(s)
                val ans = b.marks(s).joinToString(",") { m -> when { m.v >= 0 -> labels.getOrElse(m.v) { "?" }; m.v == -2 -> "*"; else -> "" } }
                w.write(listOf(s.roll, "\"${b.nameOf(s)}\"", s.setCode, sc?.toString() ?: "", sc?.let { it * 100 / b.n }?.toString() ?: "", ans).joinToString(","))
                w.newLine()
            }
        }
        f
    }

    suspend fun isEmpty() = dao.examCount() == 0
}

data class DocWithPages(val doc: DocumentEntity, val pages: List<DocPageEntity>)

class DocRepository(private val db: AppDatabase, private val files: FileStore) {
    private val dao = db.docs()

    val documents: Flow<List<DocWithPages>> = combine(dao.documents(), dao.pages()) { docs, pages ->
        docs.map { d -> DocWithPages(d, pages.filter { it.docId == d.id }.sortedBy { it.position }) }
    }

    /**
     * Saves pages returned by the document scanner. In ID mode the first two images are the card's
     * front and back and become one page; otherwise each image is its own page.
     */
    suspend fun importScan(uris: List<Uri>, idCard: Boolean, intoDoc: Long?, title: String): Long = withContext(Dispatchers.IO) {
        val paths = uris.mapNotNull { files.importUri(it, "docs") }
        val docId = intoDoc ?: dao.insertDoc(DocumentEntity(title = title))
        val start = dao.pagesFor(docId).size
        val pages = if (idCard && paths.size >= 2) {
            listOf(DocPageEntity(docId = docId, position = start, kind = "id", imagePath = paths[0], backImagePath = paths[1])) +
                paths.drop(2).mapIndexed { i, p -> DocPageEntity(docId = docId, position = start + 1 + i, kind = "doc", imagePath = p) }
        } else paths.mapIndexed { i, p -> DocPageEntity(docId = docId, position = start + i, kind = "doc", imagePath = p) }
        dao.insertPages(pages)
        docId
    }

    suspend fun rename(doc: DocumentEntity, title: String) = dao.updateDoc(doc.copy(title = title))

    /** Writes the given order back as positions 0..n-1. */
    suspend fun reorder(pages: List<DocPageEntity>) = dao.updatePages(pages.mapIndexed { i, p -> p.copy(position = i) })

    suspend fun updatePage(p: DocPageEntity) = dao.updatePages(listOf(p))

    suspend fun deletePage(p: DocPageEntity) {
        files.delete(p.imagePath); files.delete(p.backImagePath); dao.deletePage(p.id)
        reorder(dao.pagesFor(p.docId))
    }

    suspend fun deleteDoc(docId: Long) {
        dao.pagesFor(docId).forEach { files.delete(it.imagePath); files.delete(it.backImagePath) }
        dao.deletePagesOf(docId); dao.deleteDoc(docId)
    }

    suspend fun thumbnail(p: DocPageEntity, maxDim: Int) = withContext(Dispatchers.Default) { ImageOps.renderPage(p, maxDim) }
}
