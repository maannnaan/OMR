package com.omrscanner.app.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.BackBar
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.ScreenTitle
import com.omrscanner.app.ui.components.SectionLabel
import com.omrscanner.app.ui.components.Segmented
import com.omrscanner.app.ui.components.StatTile
import com.omrscanner.app.ui.components.Stepper
import com.omrscanner.app.ui.theme.Accent
import com.omrscanner.app.ui.theme.CardBg
import com.omrscanner.app.ui.theme.Divider
import com.omrscanner.app.ui.theme.Good
import com.omrscanner.app.ui.theme.GoodBg
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Mono
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Paper
import com.omrscanner.app.ui.theme.SegBg
import com.omrscanner.app.ui.theme.Warn
import com.omrscanner.app.ui.theme.WarnBg
import com.omrscanner.app.ui.theme.WarnLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberBundle(vm: AppViewModel, id: Long): ExamBundle? {
    val all by vm.bundles.collectAsStateWithLifecycle()
    return all.firstOrNull { it.exam.id == id }
}

// ── Exams list ──────────────────────────────────────────────

@Composable
fun ExamsScreen(vm: AppViewModel, nav: NavController) {
    val bundles by vm.bundles.collectAsStateWithLifecycle()
    val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(today, color = Muted, fontSize = 14.sp)
                    Text("Exams", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                }
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(CardBg).border(1.dp, Line, RoundedCornerShape(999.dp)).clickable { nav.navigate(Routes.NEW_EXAM) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("+  New exam", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
        // what needs the teacher right now
        val attention = bundles.flatMap { b ->
            buildList {
                if (b.noKey) add(Triple("!", "${b.exam.title} has no answer key", Routes.key(b.exam.id)))
                if (b.reviewCount > 0) add(Triple("${b.reviewCount}", "${b.reviewCount} sheet${if (b.reviewCount > 1) "s" else ""} to review · ${b.exam.title}", Routes.exam(b.exam.id)))
            }
        }
        if (attention.isNotEmpty()) item { Spacer(Modifier.height(16.dp)) }
        items(attention) { (n, title, route) ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(WarnBg).border(1.dp, WarnLine, RoundedCornerShape(16.dp))
                    .clickable { nav.navigate(route) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Warn), contentAlignment = Alignment.Center) { Text(n, color = Color.White, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                Text(title, color = Warn, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Text("›", color = Warn, fontSize = 18.sp)
            }
        }
        item { SectionLabel("All exams") }
        if (bundles.isEmpty()) item { Text("No exams yet — tap New exam to set one up.", color = Muted, modifier = Modifier.padding(8.dp)) }
        items(bundles, key = { it.exam.id }) { b -> ExamCard(b) { nav.navigate(Routes.exam(b.exam.id)) } }
    }
}

@Composable
private fun ExamCard(b: ExamBundle, onClick: () -> Unit) {
    val (status, bg, fg) = when {
        b.sheets.isEmpty() -> Triple("Not started", SegBg, Muted)
        b.left == 0 -> Triple("Complete", GoodBg, Good)
        else -> Triple("Grading", WarnBg, Warn)
    }
    AppCard(Modifier.padding(bottom = 10.dp).clickable(onClick = onClick), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(b.exam.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("${b.exam.className} · ${b.exam.date}", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(status, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Divider)) {
            val f = if (b.total == 0) 0f else b.sheets.size.toFloat() / b.total
            Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).background(if (b.left > 0) Accent else Good))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${b.sheets.size}/${b.total} scanned   ${b.average} avg   ${b.n} Qs", fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(1f))
            val note = when { b.noKey -> "No answer key"; b.keyGaps.isNotEmpty() -> "Key incomplete"; b.reviewCount > 0 -> "${b.reviewCount} to review"; else -> null }
            if (note != null) Text(note, color = Warn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── New exam ────────────────────────────────────────────────

@Composable
fun NewExamScreen(vm: AppViewModel, nav: NavController) {
    var title by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())) }
    var count by rememberSaveable { mutableStateOf(30) }
    var names by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar("Exams", { nav.popBackStack() }) }
        item { ScreenTitle("New exam", "You'll design the answer sheet and add the key next.") }
        item {
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Exam name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cls, { cls = it }, label = { Text("Class / section") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AppCard(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Students", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("Rolls are numbered 1, 2, 3…", fontSize = 12.sp, color = Muted)
                        }
                        Stepper("$count", { count = (count - 1).coerceAtLeast(1) }, { count = (count + 1).coerceAtMost(999) })
                    }
                }
                OutlinedTextField(
                    names, { names = it }, label = { Text("Student names (optional, one per line, in roll order)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Create exam", {
                if (title.isBlank()) { vm.toast("Give the exam a name"); return@PrimaryButton }
                val list = names.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val roster = List(maxOf(count, list.size)) { i -> list.getOrElse(i) { "Student ${i + 1}" } }
                vm.createExam(ExamEntity(title = title.trim(), className = cls.trim(), date = date.trim()), roster) { id ->
                    nav.popBackStack(); nav.navigate(Routes.exam(id))
                }
            })
        }
    }
}

// ── Exam hub ────────────────────────────────────────────────

private enum class ListTab { REVIEW, SCANNED, REMAINING }

@Composable
fun ExamDetailScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    var tab by rememberSaveable { mutableStateOf(if (b.reviewCount > 0) ListTab.REVIEW else ListTab.SCANNED) }
    var confirmDelete by remember { mutableStateOf(false) }
    val layout = b.layout
    val labels = layout.optionLabels()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            BackBar("Exams", { nav.popBackStack() }) {
                TextButton({ vm.shareResults(b) }) { Text("Export CSV", color = Ink, fontWeight = FontWeight.SemiBold) }
                TextButton({ confirmDelete = true }) { Text("Delete", color = com.omrscanner.app.ui.theme.Bad) }
            }
        }
        item { ScreenTitle(b.exam.title, "${b.exam.className} · ${b.exam.date} · ${b.total} students") }

        // setup checklist
        val keyLine = when {
            b.noKey -> "Missing — fetch from the question bank or enter it"
            b.keyGaps.isNotEmpty() -> if (b.exam.setCode) "${b.keyGaps.size} of ${b.sets.size} sets incomplete" else "${b.keyDone(b.sets.first())}/${b.n} answers entered"
            else -> (if (b.exam.setCode) "${b.sets.size} set${if (b.sets.size > 1) "s" else ""} complete" else "Complete") +
                (if (b.keyRows.any { it.source != null }) " · from question bank" else " · entered manually")
        }
        val steps = listOf(
            Step("Students", "${b.total} on the roll list", true, "Edit") { nav.navigate(Routes.students(id)) },
            Step("Answer sheet", "${b.n} questions · ${labels.first()}–${labels.last()}" + (if (layout.rollDigits > 0) " · ${layout.rollDigits}-digit roll" else "") + (if (layout.setCode) " · set code" else ""), true, if (b.sheets.isEmpty()) "Edit" else "View") { nav.navigate(Routes.sheet(id)) },
            Step("Answer key", keyLine, b.keyGaps.isEmpty(), if (b.noKey) "Add" else if (b.keyGaps.isNotEmpty()) "Finish" else "Edit") { nav.navigate(Routes.key(id)) },
            Step("Grading rules", "Double fill: " + (if (b.exam.doubleRule == "wrong") "counted wrong" else "sent to review") + " · blanks score 0", true, "Edit") { nav.navigate(Routes.rules(id)) },
        )
        val ready = steps.count { it.ok }
        item { SectionLabel("Setup", trailing = if (ready == steps.size) "Ready to scan" else "$ready of ${steps.size} ready", trailingColor = if (ready == steps.size) Good else Warn) }
        item {
            AppCard {
                steps.forEachIndexed { i, s ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable(onClick = s.go).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (s.ok) GoodBg else Warn), contentAlignment = Alignment.Center) {
                            Text(if (s.ok) "✓" else "${i + 1}", color = if (s.ok) Good else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(s.sub, fontSize = 12.sp, color = if (s.ok) Muted else Warn)
                        }
                        Text("${s.cta} ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (s.ok) Muted else Ink)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                if (b.left > 0) (if (b.sheets.isEmpty()) "Start scanning" else "Continue scanning · ${b.left} left") else "All sheets scanned",
                { if (b.left > 0) nav.navigate(Routes.scan(id)) else vm.toast("Every sheet for this exam is scanned") },
                color = if (b.left > 0) Accent else Line,
            )
            if (b.noKey && b.left > 0) Text("Add the answer key first — sheets scanned without one can't be graded.", color = Warn, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        // results
        item { SectionLabel("Results") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(b.average, "Class average", Modifier.weight(1f))
                StatTile("${b.sheets.size}/${b.total}", "Scanned", Modifier.weight(1f))
                StatTile("${b.reviewCount}", "To review", Modifier.weight(1f), valueColor = if (b.reviewCount > 0) Warn else Ink, bg = if (b.reviewCount > 0) WarnBg else CardBg)
            }
            Spacer(Modifier.height(12.dp))
            Segmented(
                listOf(ListTab.REVIEW to "To review ${b.reviewCount}", ListTab.SCANNED to "Scanned ${b.sheets.size}", ListTab.REMAINING to "Not yet ${b.unmatched.size}"),
                tab, { tab = it },
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            AppCard {
                val rows = when (tab) {
                    ListTab.REMAINING -> emptyList()
                    ListTab.REVIEW -> b.sheets.filter { b.needsCheck(it) }
                    ListTab.SCANNED -> b.sheets.sortedByDescending { b.needsCheck(it) }
                }
                if (tab == ListTab.REMAINING) {
                    if (b.unmatched.isEmpty()) Text("Everyone's sheet is in.", color = Muted, modifier = Modifier.padding(24.dp))
                    b.unmatched.forEachIndexed { i, st ->
                        if (i > 0) RowDivider()
                        StudentRow(st.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""), st.name, "Roll ${st.roll} · not scanned", null, "—", Muted) { if (b.left > 0) nav.navigate(Routes.scan(id)) }
                    }
                } else {
                    if (rows.isEmpty()) Text(if (tab == ListTab.REVIEW) (if (b.sheets.isEmpty()) "No sheets scanned yet." else "Nothing to review — every scanned sheet is clear.") else "No sheets yet. Tap scan to begin.", color = Muted, modifier = Modifier.padding(24.dp))
                    rows.forEachIndexed { i, s ->
                        if (i > 0) RowDivider()
                        val known = b.student(s.roll) != null
                        val name = b.nameOf(s)
                        StudentRow(
                            if (known) name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("") else "?",
                            name, "Roll ${s.roll} · " + (if (s.approved) "approved" else "scanned"),
                            if (b.needsCheck(s)) b.flagText(s) else null, "${b.score(s) ?: "—"}/${b.n}", if (known) Ink else Warn,
                        ) { nav.navigate(Routes.review(id, s.id)) }
                    }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this exam?") },
        text = { Text("Its students, answer keys and all ${b.sheets.size} scanned sheets will be removed.") },
        confirmButton = { TextButton({ confirmDelete = false; vm.deleteExam(id); nav.popBackStack() }) { Text("Delete", color = com.omrscanner.app.ui.theme.Bad) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}

private data class Step(val title: String, val sub: String, val ok: Boolean, val cta: String, val go: () -> Unit)

@Composable
private fun StudentRow(initials: String, name: String, meta: String, flag: String?, score: String, nameColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(SegBg), contentAlignment = Alignment.Center) { Text(initials, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = nameColor, maxLines = 1)
            Text(meta, fontSize = 12.sp, fontFamily = Mono, color = Muted)
        }
        if (flag != null) Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(999.dp)).background(WarnBg).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(flag, color = Warn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(score, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// ── Students ────────────────────────────────────────────────

@Composable
fun StudentsScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    var text by rememberSaveable(b.exam.id) { mutableStateOf(b.students.joinToString("\n") { it.name }) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar(b.exam.title.substringBefore(" · "), { nav.popBackStack() }) }
        item { ScreenTitle("Students", "One name per line, in roll order. Roll numbers are given automatically.") }
        item {
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 320.dp), label = { Text("${text.lines().count { it.isNotBlank() }} students") })
            if (b.sheets.isNotEmpty()) Text("Scanned sheets keep the roll they were read with — re-check them after renumbering.", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Save list", {
                val names = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (names.isEmpty()) vm.toast("Add at least one student") else { vm.setStudents(b, names); nav.popBackStack() }
            }, color = Ink, contentColor = Paper)
        }
    }
}

// ── Grading rules ───────────────────────────────────────────

@Composable
fun RulesScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    val doubles = b.sheets.sumOf { com.omrscanner.app.omr.Grading.doubles(b.marks(it)) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar(b.exam.title.substringBefore(" · "), { nav.popBackStack() }) }
        item { ScreenTitle("Grading rules", "How this exam treats marks the scanner can't take at face value.") }
        item { SectionLabel("Answers") }
        item {
            AppCard(padding = PaddingValues(14.dp)) {
                Text("Two bubbles filled on one question", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    (if (b.exam.doubleRule == "wrong") "Counted as wrong — no marks, as in board exams." else "Sent to review — you pick the intended answer on each sheet.") +
                        " $doubles on scanned sheets.",
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
                )
                Segmented(listOf("wrong" to "Count as wrong", "review" to "Send to review"), b.exam.doubleRule, { vm.updateExam(b.exam.copy(doubleRule = it)) })
            }
            Spacer(Modifier.height(10.dp))
            AppCard {
                InfoRow("Erased mark", "A mark much lighter than the other is treated as erased; the darker one is graded.", "under ${vm.settings.erasePct}%")
                RowDivider()
                InfoRow("Blank question", "Treated as unanswered. It scores 0 and doesn't hold the sheet for review.", "0 marks")
                RowDivider()
                InfoRow("Question with no key answer", "Not graded for any student until the key is filled in.", "skipped")
            }
        }
        item { SectionLabel("Identity") }
        item {
            AppCard {
                InfoRow("Roll not on the list, repeated, or double-filled", "The sheet is held until you match it to a student; likely rolls are suggested.", null)
                RowDivider()
                InfoRow("Set code unreadable or not in the key", "The sheet isn't graded until you choose a set; suggestions are ranked by the digits read and by score.", null)
            }
            Text("Scanner thresholds (erased-mark darkness, maximum tilt) apply to every exam — change them in Settings.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun InfoRow(title: String, desc: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(desc, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp).width(80.dp))
    }
}

