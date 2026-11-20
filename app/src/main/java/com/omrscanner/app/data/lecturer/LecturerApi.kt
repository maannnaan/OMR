package com.omrscanner.app.data.lecturer

import com.omrscanner.app.data.bank.Paper
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.data.bank.QuestionBank
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Lecturer.bd, unless changed in Settings. */
const val LECTURER_URL = "https://lecturer-nine.vercel.app"

// The JSON Lecturer.bd answers with (its src/server/omr-api.ts). Kept free of logic so Gson can
// fill them, and listed in proguard-rules.pro so release builds keep their field names.

data class LoginRequest(val identifier: String, val password: String)
data class LecturerTeacher(val name: String, val handle: String)
data class LoginResponse(val token: String, val teacher: LecturerTeacher)

/** How Lecturer.bd says this paper's answer sheet should look. */
data class SheetLayout(val labels: String, val language: String, val rollDigits: Int, val setCode: Boolean, val institution: String, val examName: String)

/** A student, by the roll their answer sheet's roll grid reads (already padded to the grid's digits). */
data class RosterEntry(val roll: String, val name: String)

/** A final Lecturer.bd paper with what an exam needs: every set's key, the sheet's layout and the students. */
data class LecturerPaper(
    val id: String,
    val title: String,
    val code: String,
    val questions: Int,
    val options: Int,
    val sets: Map<String, List<Int>>,
    val layout: SheetLayout,
    val students: List<RosterEntry>,
) {
    fun asPaper() = Paper(id, title, code, questions, options, sets)
}

/** One scanned sheet as Lecturer.bd takes it: its marks in MarkCodec's form; Lecturer.bd marks it again itself. */
data class UploadSheet(val roll: String, val setCode: String, val marks: String, val scannedAt: Long)
data class UploadRequest(val sheets: List<UploadSheet>)

/** What became of one uploaded sheet: whose it was and its score, or why it was refused ("roll", "set", "marks"). */
data class UploadSheetResult(val roll: String, val ok: Boolean, val name: String?, val score: Double?, val total: Double?, val error: String?)
data class UploadResponse(val saved: Int, val sheets: List<UploadSheetResult>)

/** A call to Lecturer.bd that failed, said for a toast. `signedOut`: the session is gone, so the app forgets it. */
class LecturerException(message: String, val signedOut: Boolean = false) : Exception(message)

private interface LecturerService {
    @POST("api/omr/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @POST("api/omr/logout") suspend fun logout(): Response<Unit>
    @GET("api/omr/papers") suspend fun papers(@Query("q") q: String): List<PaperSummary>
    @GET("api/omr/papers/{id}") suspend fun paper(@Path("id") id: String): LecturerPaper
    @POST("api/omr/papers/{id}/results") suspend fun upload(@Path("id") id: String, @Body body: UploadRequest): UploadResponse
}

/**
 * Lecturer.bd, the teacher's own website. Its final question papers are this app's question bank,
 * an exam can be set up from one in a tap, and scanned sheets go back to it. The app signs in with
 * the teacher's mobile number and password; the session token then rides on every call.
 */
class LecturerApi(baseUrl: String, private val token: () -> String?) : QuestionBank {
    private val service: LecturerService = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val t = token()
                    chain.proceed(if (t.isNullOrEmpty()) chain.request() else chain.request().newBuilder().header("Authorization", "Bearer $t").build())
                }
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LecturerService::class.java)

    suspend fun login(identifier: String, password: String): LoginResponse =
        call(onUnauthorized = "The mobile number or password didn't match") { service.login(LoginRequest(identifier, password)) }

    /** Ends the session on Lecturer.bd too; a failure here doesn't matter, the app forgets the token anyway. */
    suspend fun logout() {
        runCatching { service.logout() }
    }

    override suspend fun search(query: String): List<PaperSummary> = call { service.papers(query) }
    override suspend fun paper(id: String): Paper = lecturerPaper(id).asPaper()
    suspend fun lecturerPaper(id: String): LecturerPaper = call { service.paper(id) }
    suspend fun upload(paperId: String, sheets: List<UploadSheet>): UploadResponse = call { service.upload(paperId, UploadRequest(sheets)) }

    private suspend fun <T> call(onUnauthorized: String? = null, block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw when (e.code()) {
            401 -> if (onUnauthorized != null) LecturerException(onUnauthorized) else LecturerException("Signed out of Lecturer.bd. Sign in again in Settings.", signedOut = true)
            404 -> LecturerException("That paper isn't on Lecturer.bd any more")
            409 -> LecturerException("That paper is no longer final on Lecturer.bd")
            429 -> LecturerException("Too many tries. Wait 15 minutes, then try again.")
            else -> LecturerException("Lecturer.bd answered with an error (${e.code()}). Try again.")
        }
    } catch (e: IOException) {
        throw LecturerException("Can't reach Lecturer.bd. Check the internet connection.")
    }
}
