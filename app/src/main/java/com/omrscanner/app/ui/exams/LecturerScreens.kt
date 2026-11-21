package com.omrscanner.app.ui.exams

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.Field
import com.omrscanner.app.ui.components.Hint
import com.omrscanner.app.ui.components.Screen
import com.omrscanner.app.ui.theme.Brand
import kotlinx.coroutines.delay

// Lecturer.bd: an exam set up from one of the teacher's final question papers, in a tap. Its results
// go back from the exam's page; Lecturer.bd marks every sheet again itself.

/** The teacher's final papers on Lecturer.bd. Picking one makes it an exam (or opens the one made before). */
@Composable
fun LecturerPapersScreen(vm: AppViewModel, nav: NavController) {
    var q by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    val results by produceState<List<PaperSummary>?>(null, q) {
        delay(300)
        value = runCatching { vm.searchLecturer(q) }.getOrElse { vm.toast(it.message ?: "Lecturer.bd can't be reached"); emptyList() }
    }
    Screen(title = "From Lecturer.bd", subtitle = vm.settings.lecturerName.ifBlank { null }, onBack = { nav.popBackStack() }) {
        item { Hint("Your final question papers. The exam comes with every set's answer key, the students and their rolls.") }
        item { Field(q, { q = it }, "Search by name or code (P-7)") }
        when (val list = results) {
            null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) } }
            else -> {
                if (list.isEmpty()) item {
                    Hint(if (q.isBlank()) "No final papers yet. On Lecturer.bd, make a paper with MCQs and press “Make final”; it shows up here." else "No papers match “$q”.")
                }
                items(list, key = { it.id }) { p ->
                    PaperCard(p, note = null, button = if (busy == p.id) "Setting up the exam…" else "Use this paper") {
                        if (busy == null) {
                            busy = p.id
                            vm.importLecturerPaper(p, { id -> busy = null; nav.popBackStack(Routes.HOME, false); nav.navigate(Routes.exam(id)) }, { busy = null })
                        }
                    }
                }
            }
        }
    }
}
