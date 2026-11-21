package com.omrscanner.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omrscanner.app.ui.docs.DocEditorScreen
import com.omrscanner.app.ui.docs.DocsScreen
import com.omrscanner.app.ui.docs.PageEditorScreen
import com.omrscanner.app.ui.exams.AnalysisScreen
import com.omrscanner.app.ui.exams.AnswerKeyScreen
import com.omrscanner.app.ui.exams.BankScreen
import com.omrscanner.app.ui.exams.ExamScreen
import com.omrscanner.app.ui.exams.ExamSettingsScreen
import com.omrscanner.app.ui.exams.ExamsScreen
import com.omrscanner.app.ui.exams.LecturerPapersScreen
import com.omrscanner.app.ui.exams.NewExamScreen
import com.omrscanner.app.ui.exams.PrintScreen
import com.omrscanner.app.ui.exams.ReviewScreen
import com.omrscanner.app.ui.exams.StudentsScreen
import com.omrscanner.app.ui.scan.OmrScanScreen
import com.omrscanner.app.ui.settings.SettingsScreen
import com.omrscanner.app.ui.theme.PageBg

/**
 * Where everything is. There are no tabs: Home lists the exams, each exam is three steps (answer key,
 * OMR sheets, scan) with its results under them, and Settings sits behind the gear on Home.
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val NEW_EXAM = "exam/new"
    const val LECTURER_PAPERS = "lecturer/papers"
    const val DOCS = "docs"
    fun exam(id: Long) = "exam/$id"
    fun key(id: Long) = "exam/$id/key"
    fun bank(id: Long) = "exam/$id/key/bank"
    fun print(id: Long) = "exam/$id/print"
    fun scan(id: Long) = "exam/$id/scan"
    fun review(id: Long, sheet: Long) = "exam/$id/review/$sheet"
    fun examSettings(id: Long) = "exam/$id/settings"
    fun students(id: Long) = "exam/$id/students"
    fun analysis(id: Long) = "exam/$id/analysis"
    fun doc(id: Long) = "doc/$id"
    fun page(doc: Long, page: Long) = "doc/$doc/page/$page"
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    Box(Modifier.fillMaxSize().background(PageBg)) {
        NavHost(
            nav, startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) }, popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.HOME) { ExamsScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.NEW_EXAM) { NewExamScreen(vm, nav) }
            composable(Routes.LECTURER_PAPERS) { LecturerPapersScreen(vm, nav) }
            exam("") { ExamScreen(vm, nav, it) }
            exam("/key") { AnswerKeyScreen(vm, nav, it) }
            exam("/key/bank") { BankScreen(vm, nav, it) }
            exam("/print") { PrintScreen(vm, nav, it) }
            exam("/scan") { OmrScanScreen(vm, nav, it) }
            exam("/settings") { ExamSettingsScreen(vm, nav, it) }
            exam("/students") { StudentsScreen(vm, nav, it) }
            exam("/analysis") { AnalysisScreen(vm, nav, it) }
            composable(
                "exam/{id}/review/{sheet}",
                listOf(navArgument("id") { type = NavType.LongType }, navArgument("sheet") { type = NavType.LongType }),
            ) { ReviewScreen(vm, nav, it.arguments!!.getLong("id"), it.arguments!!.getLong("sheet")) }
            composable(Routes.DOCS) { DocsScreen(vm, nav) }
            composable("doc/{id}", listOf(navArgument("id") { type = NavType.LongType })) { DocEditorScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable(
                "doc/{id}/page/{page}",
                listOf(navArgument("id") { type = NavType.LongType }, navArgument("page") { type = NavType.LongType }),
            ) { PageEditorScreen(vm, nav, it.arguments!!.getLong("id"), it.arguments!!.getLong("page")) }
        }
    }
}

/** A screen of one exam, at exam/{id}[path]. */
private fun NavGraphBuilder.exam(path: String, content: @Composable (Long) -> Unit) =
    composable("exam/{id}$path", listOf(navArgument("id") { type = NavType.LongType })) { content(it.arguments!!.getLong("id")) }
