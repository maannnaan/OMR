package com.omrscanner.app.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omrscanner.app.OmrApp
import com.omrscanner.app.data.DocWithPages
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.bank.Paper
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.data.db.DocPageEntity
import com.omrscanner.app.data.db.DocumentEntity
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.data.db.SheetEntity
import com.omrscanner.app.docs.PageSize
import com.omrscanner.app.docs.PdfExporter
import com.omrscanner.app.docs.PdfQuality
import com.omrscanner.app.omr.OmrReader
import com.omrscanner.app.omr.SheetPdf
import com.omrscanner.app.omr.layout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One view model for the whole app; screens read state from it and call its actions. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as OmrApp).container
    val settings = c.settings
    val files = c.files

    val bundles: StateFlow<List<ExamBundle>> = c.exams.bundles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val documents: StateFlow<List<DocWithPages>> = c.docs.documents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { c.seedIfEmpty() } }

    fun toast(msg: String) = Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()

    private fun io(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { toast(e.message ?: "Something went wrong") }
    }

    // ── exams ───────────────────────────────────────────────
    fun createExam(exam: ExamEntity, names: List<String>, onCreated: (Long) -> Unit) = io {
        val id = c.exams.create(exam.copy(institution = exam.institution.ifBlank { settings.schoolName }), names)
        onCreated(id)
    }
    fun updateExam(exam: ExamEntity) = io { c.exams.update(exam) }
    fun deleteExam(id: Long) = io { c.exams.delete(id); toast("Exam deleted") }
    fun setStudents(b: ExamBundle, names: List<String>) = io { c.exams.setStudents(b.exam.id, b.exam.rollDigits, names); toast("${names.size} students saved") }

    fun saveKey(examId: Long, set: String, key: IntArray, source: String? = null, edited: Boolean = false) = io { c.exams.saveKey(examId, set, key, source, edited) }
    fun deleteKey(examId: Long, set: String) = io { c.exams.deleteKey(examId, set) }

    suspend fun searchBank(q: String): List<PaperSummary> = withContext(Dispatchers.IO) { c.bank.search(q) }
    fun fetchPaper(exam: ExamEntity, p: PaperSummary, done: (Int) -> Unit) = io {
        val paper: Paper = withContext(Dispatchers.IO) { c.bank.paper(p.id) }
        val n = c.exams.applyPaper(exam, paper)
        done(n)
    }

    suspend fun saveScan(examId: Long, r: OmrReader.Result): SheetEntity = c.exams.addScan(examId, r)
    fun updateSheet(s: SheetEntity) = io { c.exams.updateSheet(s) }
    fun deleteSheet(s: SheetEntity) = io { c.exams.deleteSheet(s) }

    fun shareResults(b: ExamBundle) = io { files.share(c.exams.exportCsv(b), "text/csv", "Share results") }

    /** Printable answer sheets — one copy per student. */
    fun shareSheetPdf(exam: ExamEntity, copies: Int, save: Boolean) = io {
        val f = files.exportFile("${exam.title} — answer sheet.pdf")
        withContext(Dispatchers.Default) { SheetPdf.write(exam.layout(), copies, f) }
        if (save) toast("Saved to " + files.saveToDownloads(f, "application/pdf")) else files.share(f, "application/pdf", "Print or share answer sheets")
    }

    // ── documents ───────────────────────────────────────────
    fun importScan(uris: List<Uri>, idCard: Boolean, intoDoc: Long?, opened: (Long) -> Unit) = io {
        if (uris.isEmpty()) return@io
        val stamp = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date())
        val title = if (idCard) "ID card — front & back" else "Scan $stamp"
        val id = c.docs.importScan(uris, idCard, intoDoc, title)
        if (idCard && uris.size < 2) toast("Only one side was scanned — saved as a normal page")
        opened(id)
    }
    fun renameDoc(d: DocumentEntity, t: String) = io { c.docs.rename(d, t) }
    fun reorder(pages: List<DocPageEntity>) = io { c.docs.reorder(pages) }
    fun updatePage(p: DocPageEntity) = io { c.docs.updatePage(p) }
    fun deletePage(p: DocPageEntity) = io { c.docs.deletePage(p) }
    fun deleteDoc(id: Long) = io { c.docs.deleteDoc(id); toast("Document deleted") }
    suspend fun thumbnail(p: DocPageEntity, maxDim: Int) = c.docs.thumbnail(p, maxDim)

    fun exportPdf(pages: List<DocPageEntity>, name: String, size: PageSize, quality: PdfQuality, numbers: Boolean, action: String) = io {
        val out: File = files.exportFile(name.ifBlank { "Scan" }.removeSuffix(".pdf") + ".pdf")
        withContext(Dispatchers.Default) { PdfExporter.export(pages, out, size, quality, numbers) }
        when (action) {
            "save" -> toast("Saved to " + files.saveToDownloads(out, "application/pdf"))
            "open" -> files.open(out, "application/pdf")
            else -> files.share(out, "application/pdf", "Share PDF")
        }
    }
}
