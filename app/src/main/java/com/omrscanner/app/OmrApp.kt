package com.omrscanner.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.omrscanner.app.data.AppSettings
import com.omrscanner.app.data.DocRepository
import com.omrscanner.app.data.ExamRepository
import com.omrscanner.app.data.FileStore
import com.omrscanner.app.data.bank.QuestionBank
import com.omrscanner.app.data.bank.RemoteQuestionBank
import com.omrscanner.app.data.bank.SampleQuestionBank
import com.omrscanner.app.data.db.AppDatabase
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.data.lecturer.LecturerApi
import org.opencv.android.OpenCVLoader

class OmrApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initLocal()) Log.e("OmrApp", "OpenCV failed to load")
        container = AppContainer(this)
    }
}

/** Simple manual dependency container. */
class AppContainer(context: Context) {
    val db = AppDatabase.get(context)
    val settings = AppSettings(context)
    val files = FileStore(context)
    val exams = ExamRepository(db, files)
    val docs = DocRepository(db, files)

    private var lecturerFor: Pair<String, LecturerApi>? = null

    /** Lecturer.bd at the server set in Settings (one client per address). */
    val lecturer: LecturerApi
        get() {
            val url = settings.lecturerUrl
            lecturerFor?.let { (at, api) -> if (at == url) return api }
            return LecturerApi(url) { settings.lecturerToken }.also { lecturerFor = url to it }
        }

    /** Lecturer.bd's papers when signed in there; else your own server when a URL is set in Settings; else the built-in sample bank. */
    val bank: QuestionBank
        get() = when {
            settings.lecturerSignedIn -> lecturer
            settings.bankUrl.startsWith("http") -> RemoteQuestionBank(settings.bankUrl)
            else -> SampleQuestionBank
        }

    /** First launch: one sample exam, ready to print and scan, so the app isn't empty. */
    suspend fun seedIfEmpty() {
        if (!exams.isEmpty()) return
        val names = listOf("Rahim Uddin", "Fatema Akter", "Tanvir Hasan", "Nusrat Jahan", "Sakib Hossain", "Maliha Rahman", "Arif Chowdhury",
            "Sumaiya Islam", "Imran Kabir", "Tasnim Ahmed", "Rafiq Mia", "Jannatul Ferdous", "Mahmud Hasan", "Sadia Afrin", "Nayeem Khan",
            "Farhana Yasmin", "Shakil Ahmed", "Mithila Das", "Rakib Hasan", "Sharmin Sultana", "Ashik Rahman", "Lamia Haque", "Tahsin Alam",
            "Rubaiya Karim", "Fahim Shahriar", "Anika Tabassum", "Zubair Hossain", "Puja Saha")
        val id = exams.create(
            ExamEntity(title = "Sample exam · Physics", className = "Class 10", date = "", questions = 30, options = 4, rollDigits = 3, setCode = true),
            names,
        )
        var s = 7L
        fun rnd(): Int { s = (s * 1103515245 + 12345) and 0x7fffffff; return (s % 4).toInt() }
        listOf("101", "102", "103", "104").forEach { set -> exams.saveKey(id, set, IntArray(30) { rnd() }, null, false) }
    }
}
