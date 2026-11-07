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

    /** Your server when a URL is set in Settings, otherwise the built-in sample bank. */
    val bank: QuestionBank
        get() = settings.bankUrl.takeIf { it.startsWith("http") }?.let { RemoteQuestionBank(it) } ?: SampleQuestionBank

    /** First launch: one ready-to-scan exam so the app isn't empty. */
    suspend fun seedIfEmpty() {
        if (!exams.isEmpty()) return
        val names = listOf("Aditi Sharma", "Rohan Mehta", "Kavya Iyer", "Arjun Nair", "Meera Pillai", "Ishaan Gupta", "Sara Khan", "Vikram Rao",
            "Ananya Das", "Kabir Singh", "Nisha Verma", "Dev Patel", "Tara Joshi", "Yash Malhotra", "Riya Kapoor", "Aman Bhatt", "Zoya Ali",
            "Neel Desai", "Pooja Reddy", "Karan Sethi", "Leela Menon", "Omar Shaikh", "Diya Bose", "Farhan Qureshi", "Ira Chawla", "Manav Jain",
            "Sana Mirza", "Tejas Kulkarni")
        val id = exams.create(
            ExamEntity(title = "Physics · Unit 3 Quiz", className = "Grade 10 · Section B", date = "Nov 6", questions = 30, options = 4, rollDigits = 3, setCode = true),
            names,
        )
        var s = 7L
        fun rnd(): Int { s = (s * 1103515245 + 12345) and 0x7fffffff; return (s % 4).toInt() }
        listOf("101", "102", "103", "104").forEach { set -> exams.saveKey(id, set, IntArray(30) { rnd() }, null, false) }
    }
}
