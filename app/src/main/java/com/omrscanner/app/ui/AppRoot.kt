package com.omrscanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omrscanner.app.ui.docs.DocEditorScreen
import com.omrscanner.app.ui.docs.DocsScreen
import com.omrscanner.app.ui.docs.PageEditorScreen
import com.omrscanner.app.ui.exams.AnswerKeyScreen
import com.omrscanner.app.ui.exams.ExamDetailScreen
import com.omrscanner.app.ui.exams.ExamsScreen
import com.omrscanner.app.ui.exams.InsightsScreen
import com.omrscanner.app.ui.exams.NewExamScreen
import com.omrscanner.app.ui.exams.ReviewScreen
import com.omrscanner.app.ui.exams.RulesScreen
import com.omrscanner.app.ui.exams.SheetBuilderScreen
import com.omrscanner.app.ui.exams.StudentsScreen
import com.omrscanner.app.ui.scan.OmrScanScreen
import com.omrscanner.app.ui.settings.QuestionBankScreen
import com.omrscanner.app.ui.settings.SettingsScreen
import com.omrscanner.app.ui.theme.Accent
import com.omrscanner.app.ui.theme.CardBg
import com.omrscanner.app.ui.theme.Faint
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Paper

object Routes {
    const val EXAMS = "exams"
    const val INSIGHTS = "insights"
    const val DOCS = "docs"
    const val SETTINGS = "settings"
    const val NEW_EXAM = "exam/new"
    const val BANK = "bank"
    fun exam(id: Long) = "exam/$id"
    fun sheet(id: Long) = "exam/$id/sheet"
    fun key(id: Long) = "exam/$id/key"
    fun rules(id: Long) = "exam/$id/rules"
    fun students(id: Long) = "exam/$id/students"
    fun scan(id: Long) = "exam/$id/scan"
    fun review(id: Long, sheet: Long) = "exam/$id/review/$sheet"
    fun doc(id: Long) = "doc/$id"
    fun page(doc: Long, page: Long) = "doc/$doc/page/$page"
}

private val tabs = setOf(Routes.EXAMS, Routes.INSIGHTS, Routes.DOCS, Routes.SETTINGS)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route
    val bundles by vm.bundles.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            if (route in tabs) BottomBar(route, nav) {
                // Scan: continue the exam that still has sheets to scan
                val target = bundles.firstOrNull { it.sheets.isNotEmpty() && it.left > 0 } ?: bundles.firstOrNull { it.left > 0 }
                if (target != null) nav.navigate(Routes.scan(target.exam.id)) else vm.toast("Create an exam first — nothing left to scan")
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.EXAMS, modifier = Modifier.padding(pad)) {
            composable(Routes.EXAMS) { ExamsScreen(vm, nav) }
            composable(Routes.INSIGHTS) { InsightsScreen(vm) }
            composable(Routes.DOCS) { DocsScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.BANK) { QuestionBankScreen(vm, nav) }
            composable(Routes.NEW_EXAM) { NewExamScreen(vm, nav) }
            composable("exam/{id}", listOf(navArgument("id") { type = NavType.LongType })) { ExamDetailScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("exam/{id}/sheet", listOf(navArgument("id") { type = NavType.LongType })) { SheetBuilderScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("exam/{id}/key", listOf(navArgument("id") { type = NavType.LongType })) { AnswerKeyScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("exam/{id}/rules", listOf(navArgument("id") { type = NavType.LongType })) { RulesScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("exam/{id}/students", listOf(navArgument("id") { type = NavType.LongType })) { StudentsScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("exam/{id}/scan", listOf(navArgument("id") { type = NavType.LongType })) { OmrScanScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable(
                "exam/{id}/review/{sheet}",
                listOf(navArgument("id") { type = NavType.LongType }, navArgument("sheet") { type = NavType.LongType }),
            ) { ReviewScreen(vm, nav, it.arguments!!.getLong("id"), it.arguments!!.getLong("sheet")) }
            composable("doc/{id}", listOf(navArgument("id") { type = NavType.LongType })) { DocEditorScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable(
                "doc/{id}/page/{page}",
                listOf(navArgument("id") { type = NavType.LongType }, navArgument("page") { type = NavType.LongType }),
            ) { PageEditorScreen(vm, nav, it.arguments!!.getLong("id"), it.arguments!!.getLong("page")) }
        }
    }
}

@Composable
private fun BottomBar(route: String?, nav: NavHostController, onScan: () -> Unit) {
    fun go(r: String) = nav.navigate(r) { popUpTo(Routes.EXAMS) { saveState = true }; launchSingleTop = true; restoreState = true }
    val colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, selectedTextColor = Ink, indicatorColor = Paper, unselectedIconColor = Faint, unselectedTextColor = Faint)
    NavigationBar(containerColor = CardBg) {
        NavigationBarItem(route == Routes.EXAMS, { go(Routes.EXAMS) }, { Icon(Icons.Outlined.ViewList, null) }, label = { Text("Exams") }, colors = colors)
        NavigationBarItem(route == Routes.INSIGHTS, { go(Routes.INSIGHTS) }, { Icon(Icons.Outlined.BarChart, null) }, label = { Text("Insights") }, colors = colors)
        NavigationBarItem(false, onScan, {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CameraAlt, "Scan sheets", tint = Ink) }
        }, label = { Text("Scan") }, colors = colors)
        NavigationBarItem(route == Routes.DOCS, { go(Routes.DOCS) }, { Icon(Icons.Outlined.Description, null) }, label = { Text("Docs") }, colors = colors)
        NavigationBarItem(route == Routes.SETTINGS, { go(Routes.SETTINGS) }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("Settings") }, colors = colors)
    }
}

@Composable
fun ScreenColumn(content: @Composable () -> Unit) = Column(Modifier.fillMaxSize()) { content() }
