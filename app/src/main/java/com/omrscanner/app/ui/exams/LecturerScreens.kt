package com.omrscanner.app.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.BackBar
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.ScreenTitle
import com.omrscanner.app.ui.components.SectionLabel
import com.omrscanner.app.ui.theme.Good
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Mono
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Paper
import com.omrscanner.app.ui.theme.SegBg
import com.omrscanner.app.ui.theme.Warn
import kotlinx.coroutines.delay

// Lecturer.bd: setting up an exam from one of the teacher's final question papers, and sending its
// scanned results back. Lecturer.bd marks every sheet again itself; the app only sends what it read.

/** The teacher's final Lecturer.bd papers, to set up an exam from one in a tap. */
@Composable
fun LecturerImportScreen(vm: AppViewModel, nav: NavController) {
    var q by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    val results by produceState<List<PaperSummary>?>(null, q) {
        delay(300)
        value = runCatching { vm.searchLecturer(q) }.getOrElse { vm.toast(it.message ?: "Lecturer.bd unreachable"); emptyList() }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { BackBar("New exam", { nav.popBackStack() }) }
        item {
            ScreenTitle("Import from Lecturer.bd", "Your final question papers. The exam comes with every set's answer key, the students' rolls and the answer sheet's layout.")
            OutlinedTextField(q, { q = it }, label = { Text("Search by title or code (P-7)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        }
        when (val list = results) {
            null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) } }
            else -> {
                if (list.isEmpty()) item {
                    Text(
                        if (q.isBlank()) "No final papers yet. On Lecturer.bd, make a paper with MCQs and press Make final; it appears here." else "No papers match “$q”.",
                        color = Muted, modifier = Modifier.padding(8.dp),
                    )
                }
                items(list, key = { it.id }) { p ->
                    AppCard(Modifier.padding(bottom = 10.dp), padding = PaddingValues(14.dp)) {
                        Row {
                            Text(p.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(p.code, fontFamily = Mono, fontSize = 11.sp, color = Muted, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SegBg).padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                        Text("${p.questions} Q · ${p.options} options · " + (if (p.setCount == 1) "set ${p.firstSet}" else "sets ${p.firstSet}–${p.lastSet}"), fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Text("${p.subject} · final ${p.updated}", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(
                            if (busy == p.id) "Setting up the exam…" else "Import as an exam",
                            {
                                if (busy == null) {
                                    busy = p.id
                                    vm.importLecturerPaper(p, { id -> busy = null; nav.popBackStack(Routes.EXAMS, false); nav.navigate(Routes.exam(id)) }, { busy = null })
                                }
                            },
                            color = Ink, contentColor = Paper,
                        )
                    }
                }
            }
        }
    }
}

/** On an exam imported from Lecturer.bd: how many sheets are there, and sending the rest. */
@Composable
fun LecturerUploadCard(vm: AppViewModel, b: ExamBundle) {
    var busy by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    val ready = b.toUpload.size
    SectionLabel("Lecturer.bd", trailing = "${b.uploadedCount} of ${b.sheets.size} uploaded", trailingColor = if (b.sheets.isNotEmpty() && b.uploadedCount == b.sheets.size) Good else Muted)
    AppCard(padding = PaddingValues(14.dp)) {
        Text(
            when {
                b.sheets.isEmpty() -> "Scanned sheets go to this paper's results on Lecturer.bd."
                ready == 0 -> "Every checked sheet is on Lecturer.bd."
                else -> "$ready checked sheet${if (ready == 1) "" else "s"} not on Lecturer.bd yet."
            },
            fontSize = 14.sp,
        )
        if (b.reviewCount > 0) Text("${b.reviewCount} to review first: those go once checked.", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (!vm.settings.lecturerSignedIn) Text("Sign in to Lecturer.bd in Settings to upload.", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        val canSend = ready > 0 && !busy && vm.settings.lecturerSignedIn
        PrimaryButton(
            if (busy) "Uploading…" else if (ready > 0) "Upload results ($ready)" else "Upload results",
            { if (canSend) { busy = true; vm.uploadResults(b) { s -> busy = false; summary = s } } },
            color = if (canSend) Ink else Line, contentColor = if (canSend) Paper else Muted, enabled = canSend,
        )
    }
    summary?.let { text ->
        AlertDialog(
            onDismissRequest = { summary = null },
            title = { Text("Lecturer.bd") },
            text = { Text(text) },
            confirmButton = { TextButton({ summary = null }) { Text("OK") } },
        )
    }
}
