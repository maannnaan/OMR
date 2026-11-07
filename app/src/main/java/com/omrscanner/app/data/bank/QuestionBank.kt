package com.omrscanner.app.data.bank

import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** A paper in the question bank, without its keys. */
data class PaperSummary(
    val id: String,
    val title: String,
    val code: String,
    val subject: String,
    val questions: Int,
    val options: Int,
    val setCount: Int,
    val firstSet: String,
    val lastSet: String,
    val author: String,
    val updated: String,
)

/** A paper with every set's answer key: sets["101"] = [2, 0, 3, …] (option indexes). */
data class Paper(
    val id: String,
    val title: String,
    val code: String,
    val questions: Int,
    val options: Int,
    val sets: Map<String, List<Int>>,
)

interface QuestionBank {
    suspend fun search(query: String): List<PaperSummary>
    suspend fun paper(id: String): Paper
}

/**
 * REST client for your own question-bank server.
 *
 *   GET {base}/papers?q=physics        → [PaperSummary]
 *   GET {base}/papers/{id}             → Paper
 */
class RemoteQuestionBank(baseUrl: String) : QuestionBank {
    private interface Api {
        @GET("papers") suspend fun search(@Query("q") q: String): List<PaperSummary>
        @GET("papers/{id}") suspend fun paper(@Path("id") id: String): Paper
    }

    private val api: Api = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(Api::class.java)

    override suspend fun search(query: String) = api.search(query)
    override suspend fun paper(id: String) = api.paper(id)
}

/** Built-in sample bank so the app works before a server is connected. */
object SampleQuestionBank : QuestionBank {
    private data class Def(val id: String, val title: String, val code: String, val subject: String, val n: Int, val opts: Int, val first: Int, val count: Int, val author: String, val updated: String)

    private val defs = listOf(
        Def("qb1", "Physics · Unit 3 Quiz — board set bank", "QB-PHY-0311", "Physics", 30, 4, 101, 320, "Md. Rahman", "Nov 5, 10:42 AM"),
        Def("qb2", "Physics · Unit 3 Retake", "QB-PHY-0318", "Physics", 25, 4, 101, 4, "Md. Rahman", "Nov 6, 4:15 PM"),
        Def("qb3", "Chemistry Midterm 2026", "QB-CHM-0207", "Chemistry", 20, 5, 101, 150, "S. Akter", "Oct 28, 9:05 AM"),
        Def("qb4", "Biology · Cells Test (Bangla version)", "QB-BIO-0412", "Biology", 40, 4, 101, 3, "F. Hossain", "Nov 7, 7:50 AM"),
        Def("qb5", "Biology · Cells Test — all versions", "QB-BIO-0413", "Biology", 40, 4, 101, 360, "F. Hossain", "Nov 7, 7:52 AM"),
        Def("qb6", "General Math · Algebra Practice", "QB-MTH-0120", "Mathematics", 50, 4, 101, 12, "R. Karim", "Oct 30, 1:10 PM"),
    )

    private fun Def.summary() = PaperSummary(id, title, code, subject, n, opts, count, first.toString(), (first + count - 1).toString(), author, updated)

    override suspend fun search(query: String): List<PaperSummary> {
        delay(250)
        val q = query.trim().lowercase()
        return defs.filter { q.isEmpty() || "${it.title} ${it.code} ${it.subject}".lowercase().contains(q) }.map { it.summary() }
    }

    override suspend fun paper(id: String): Paper {
        val d = defs.first { it.id == id }
        delay(400L + d.count * 2L)
        var seed = d.code.hashCode().toLong()
        fun next(): Int { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % d.opts).toInt().let { if (it < 0) -it else it } }
        val sets = (0 until d.count).associate { i -> (d.first + i).toString() to List(d.n) { next() } }
        return Paper(d.id, d.title, d.code, d.n, d.opts, sets)
    }
}
