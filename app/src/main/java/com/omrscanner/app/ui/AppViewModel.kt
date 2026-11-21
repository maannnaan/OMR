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
import com.omrscanner.app.data.lecturer.LecturerException
import com.omrscanner.app.data.lecturer.UploadSheet
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * A new exam made here: students numbered 1, 2, 3… and one answer key per set (set codes 101, 102…;
     * one set needs no set-code grid on the sheet). The sheet's look can change before printing.
     */
    fun createExam(title: String, questions: Int, students: Int, sets: Int, onCreated: (Long) -> Unit) = io {
        val exam = ExamEntity(
            title = title, className = "", date = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date()),
            questions = questions, options = 4, rollDigits = maxOf(3, students.toString().length), setCode = sets > 1,
            institution = settings.schoolName,
        )
        val id = c.exams.create(exam, List(students) { "Student ${it + 1}" }, List(sets) { (101 + it).toString() })
        onCreated(id)
    }

    /** Whether there's a question bank to fill keys from: Lecturer.bd, or the teacher's own server. */
    val bankAvailable: Boolean get() = settings.lecturerSignedIn || settings.bankUrl.startsWith("http")
    fun updateExam(exam: ExamEntity) = io { c.exams.update(exam) }
    fun deleteExam(id: Long) = io { c.exams.delete(id); toast("Exam deleted") }
    fun setStudents(b: ExamBundle, names: List<String>) = io { c.exams.setStudents(b.exam.id, b.exam.rollDigits, names); toast("${names.size} students saved") }

    /** Key answers are saved one after another, in the order they were tapped. */
    private val keyWrites = Mutex()
    fun saveKey(examId: Long, set: String, key: IntArray, source: String? = null, edited: Boolean = false) =
        io { keyWrites.withLock { c.exams.saveKey(examId, set, key, source, edited) } }
    fun deleteKey(examId: Long, set: String) = io { c.exams.deleteKey(examId, set) }

    suspend fun searchBank(q: String): List<PaperSummary> = withContext(Dispatchers.IO) { c.bank.search(q) }
    fun fetchPaper(exam: ExamEntity, p: PaperSummary, done: (Int) -> Unit) = io {
        val paper: Paper = withContext(Dispatchers.IO) { c.bank.paper(p.id) }
        val n = c.exams.applyPaper(exam, paper)
        done(n)
    }

    // ── Lecturer.bd ─────────────────────────────────────────
    /** Runs a Lecturer.bd call; if it says the session is gone, the app signs out too. */
    private suspend fun <T> lecturer(block: suspend () -> T): T = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: LecturerException) {
        if (e.signedOut) settings.lecturerSignOut()
        throw e
    }

    fun lecturerSignIn(identifier: String, password: String, done: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            val r = lecturer { c.lecturer.login(identifier.trim(), password) }
            settings.lecturerSignIn(r.token, r.teacher.name)
            toast("Signed in to Lecturer.bd as ${r.teacher.name}")
            done(true)
        } catch (e: Exception) {
            toast(e.message ?: "Couldn't sign in")
            done(false)
        }
    }

    fun lecturerSignOut() = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.lecturer.logout() }
        settings.lecturerSignOut()
        toast("Signed out of Lecturer.bd")
    }

    suspend fun searchLecturer(q: String): List<PaperSummary> = lecturer { c.lecturer.search(q) }

    /** Sets up an exam from a Lecturer.bd paper (or opens the one made from it before). */
    fun importLecturerPaper(p: PaperSummary, opened: (Long) -> Unit, failed: () -> Unit) = viewModelScope.launch {
        try {
            val paper = lecturer { c.lecturer.lecturerPaper(p.id) }
            opened(c.exams.importLecturerPaper(paper, SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())))
        } catch (e: Exception) {
            toast(e.message ?: "Couldn't import the paper")
            failed()
        }
    }

    /**
     * Sends the exam's checked sheets that Lecturer.bd doesn't have yet (in batches), then says what
     * happened: how many were saved there, and which were refused and why.
     */
    fun uploadResults(b: ExamBundle, done: (String?) -> Unit) = viewModelScope.launch {
        val paperId = b.exam.remotePaperId ?: return@launch done(null)
        val sending = b.toUpload
        if (sending.isEmpty()) { toast("Nothing new to upload"); return@launch done(null) }
        var saved = 0
        val refused = mutableListOf<String>()
        try {
            sending.chunked(200).forEach { batch ->
                val r = lecturer { c.lecturer.upload(paperId, batch.map { UploadSheet(it.roll, it.setCode, it.marks, it.scannedAt) }) }
                c.exams.markUploaded(batch.zip(r.sheets).filter { (_, result) -> result.ok }.map { (sheet, _) -> sheet.id }, System.currentTimeMillis())
                saved += r.saved
                r.sheets.filterNot { it.ok }.forEach {
                    refused += "Roll ${it.roll}: " + when (it.error) {
                        "roll" -> "the roll can't be read"
                        "set" -> "that set code isn't on the paper"
                        "marks" -> "the marks can't be read"
                        else -> "refused"
                    }
                }
            }
            done(
                "$saved sheet${if (saved == 1) "" else "s"} uploaded to Lecturer.bd." +
                    if (refused.isEmpty()) "" else "\n\nNot uploaded (fix these in review, then upload again):\n" + refused.joinToString("\n"),
            )
        } catch (e: Exception) {
            toast(e.message ?: "Upload failed")
            done(if (saved > 0) "$saved sheets uploaded before the connection failed. Upload again for the rest." else null)
        }
    }

    suspend fun saveScan(examId: Long, r: OmrReader.Result): SheetEntity = c.exams.addScan(examId, r)
    private val sheetWrites = Mutex()
    fun updateSheet(s: SheetEntity) = io { sheetWrites.withLock { c.exams.updateSheet(s) } }
    fun deleteSheet(s: SheetEntity) = io { c.exams.deleteSheet(s) }

    fun shareResults(b: ExamBundle) = io { files.share(c.exams.exportCsv(b), "text/csv", "Share results") }

    /** The printable OMR sheets, one A4 page per student, then [then] with the PDF. */
    private fun sheetPdf(exam: ExamEntity, copies: Int, then: (File) -> Unit) = io {
        val f = files.exportFile("${exam.title} - OMR sheets.pdf")
        withContext(Dispatchers.Default) { SheetPdf.write(exam.layout(), copies.coerceAtLeast(1), f) }
        then(f)
    }

    /** Android's print dialog for the sheets (it can also save them as a PDF). */
    fun printSheets(activity: android.content.Context, b: ExamBundle) = sheetPdf(b.exam, b.total) { files.print(activity, it, b.exam.title) }

    /** The sheets as a PDF to send on, e.g. to a print shop. */
    fun shareSheets(b: ExamBundle) = sheetPdf(b.exam, b.total) { files.share(it, "application/pdf", "Send the OMR sheets") }

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
