package com.omrscanner.app.ui.exams

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.IdIssue
import com.omrscanner.app.data.db.SheetEntity
import com.omrscanner.app.omr.Grading
import com.omrscanner.app.omr.Mark
import com.omrscanner.app.omr.MarkCodec
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.BackBar
import com.omrscanner.app.ui.components.Pill
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.SectionLabel
import com.omrscanner.app.ui.components.StatTile
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.BadBg
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Review one scanned sheet ────────────────────────────────

@Composable
fun ReviewScreen(vm: AppViewModel, nav: NavController, examId: Long, sheetId: Long) {
    val b = rememberBundle(vm, examId) ?: return
    val s = b.sheets.firstOrNull { it.id == sheetId } ?: return
    val order = remember(b.sheets) { b.sheets.sortedWith(compareByDescending<SheetEntity> { b.needsCheck(it) }.thenBy { it.scannedAt }) }
    val idx = order.indexOfFirst { it.id == s.id }
    var filter by rememberSaveable { mutableStateOf("all") }
    var editRoll by remember { mutableStateOf(false) }
    var editSet by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    fun go(to: SheetEntity) = nav.navigate(Routes.review(examId, to.id)) { popUpTo(Routes.review(examId, sheetId)) { inclusive = true } }

    val marks = b.marks(s)
    val key = b.key(s.setCode)
    val labels = b.layout.optionLabels()
    val score = b.score(s)
    val issue = b.idIssue(s)
    val dblWrong = b.exam.doubleRule == "wrong"

    data class QRow(val q: Int, val st: String, val m: Mark, val k: Int)
    val rows = marks.mapIndexed { q, m ->
        val k = key?.getOrNull(q) ?: -9
        val st = when { m.v == Mark.BLANK -> "blank"; m.v == Mark.DOUBLE -> if (dblWrong) "double" else "multi"; key == null || k < 0 -> "none"; m.v == k -> "right"; else -> "wrong" }
        QRow(q, st, m, k)
    }
    val counts = mapOf(
        "all" to rows.size, "review" to rows.count { it.st == "multi" }, "double" to rows.count { it.st == "double" || it.st == "multi" },
        "wrong" to rows.count { it.st == "wrong" || it.st == "double" }, "blank" to rows.count { it.st == "blank" }, "right" to rows.count { it.st == "right" },
    )
    val shown = rows.filter { r ->
        when (filter) { "all" -> true; "review" -> r.st == "multi"; "double" -> r.st == "double" || r.st == "multi"; "wrong" -> r.st == "wrong" || r.st == "double"; else -> r.st == filter }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                BackBar("Sheets", { nav.popBackStack() }) {
                    TextButton({ if (order.isNotEmpty()) go(order[(idx - 1 + order.size) % order.size]) }) { Text("‹", fontSize = 20.sp, color = Ink) }
                    Text("${idx + 1} of ${order.size}", fontFamily = Mono, fontSize = 13.sp, color = Muted)
                    TextButton({ if (order.isNotEmpty()) go(order[(idx + 1) % order.size]) }) { Text("›", fontSize = 20.sp, color = Ink) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(b.nameOf(s), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (b.student(s.roll) != null) Ink else Warn)
                        val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(s.scannedAt))
                        Text("scanned $t" + (if (s.tilt >= 5) " · straightened ${s.tilt}°" else "") + (if (s.rotation != 0) " · rotated ${s.rotation}°" else ""), fontFamily = Mono, fontSize = 12.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${score ?: "—"}/${b.n}", fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
                        Text(score?.let { "${it * 100 / b.n}%" } ?: "not graded", fontFamily = Mono, fontSize = 13.sp, color = Muted)
                    }
                }
            }
            item {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (b.exam.rollDigits > 0) IdCard("Roll", s.roll, issue == IdIssue.ROLL_MISSING || issue == IdIssue.ROLL_DUP, s.roll != s.rollRead, Modifier.weight(1f)) { editRoll = true }
                    if (b.exam.setCode) IdCard("Set code", s.setCode, issue == IdIssue.SET, s.setCode != s.setRead, Modifier.weight(1f)) { editSet = true }
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(CardBg).border(1.dp, Line, RoundedCornerShape(14.dp)).clickable { showScan = true }, contentAlignment = Alignment.Center) {
                        Text("Scan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val msg = when (issue) {
                    IdIssue.ROLL_MISSING -> if (s.roll.contains('?') && s.rollAlt.isNotEmpty()) "Two bubbles filled in roll column ${s.rollAlt.substringBefore(':').toInt() + 1} (${s.rollAlt.substringAfter(':').toList().joinToString(" and ")}) — tap Roll to pick the right student."
                        else "Roll ${s.roll} isn't on the class list — tap Roll to match this sheet to a student."
                    IdIssue.ROLL_DUP -> "Another sheet also reads roll ${s.roll}. Check the written name and fix whichever is wrong."
                    IdIssue.SET -> if (s.setAlt.isNotEmpty()) "Two bubbles filled in a set-code column, so this sheet isn't graded. Tap Set code to choose." else "Set code couldn't be read or isn't in the key, so this sheet isn't graded."
                    null -> null
                }
                if (msg != null) Text(msg, color = Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                LazyRow(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val f = listOf("all" to "All", "review" to "Needs review", "double" to "Double-filled", "wrong" to "Wrong", "blank" to "Blank", "right" to "Correct")
                        .filter { (id, _) -> id !in setOf("review", "double", "blank") || (counts[id] ?: 0) > 0 }
                    items(f) { (id, label) -> Pill("$label ${counts[id]}", filter == id, { filter = id }) }
                }
            }
            item {
                AppCard {
                    if (shown.isEmpty()) Text("Nothing here — all clear.", color = Muted, modifier = Modifier.padding(24.dp))
                    shown.forEachIndexed { i, r ->
                        if (i > 0) RowDivider()
                        val bg = when (r.st) { "multi" -> WarnBg; "double" -> BadBg; else -> Color.Transparent }
                        Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Q${r.q + 1}", fontFamily = Mono, fontSize = 13.sp, color = Muted, modifier = Modifier.width(38.dp))
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                labels.forEachIndexed { j, lab ->
                                    val marked = r.m.v == j || (r.m.v == Mark.DOUBLE && j in r.m.multi)
                                    val (fill, border, fg) = when {
                                        marked -> { val c = when (r.st) { "right" -> Good; "wrong", "double" -> Bad; "none" -> Ink; else -> Warn }; Triple(c, c, Color.White) }
                                        r.m.erased == j -> Triple(Divider, Line, Muted)
                                        j == r.k && r.st != "right" -> Triple(Color.Transparent, Good, Good)
                                        else -> Triple(Color.Transparent, Line, Muted)
                                    }
                                    Box(
                                        Modifier.size(if (b.exam.options >= 6) 32.dp else 34.dp).clip(CircleShape).background(fill).border(2.dp, border, CircleShape).clickable {
                                            val next = marks.toMutableList(); next[r.q] = Mark(j, fixed = true)
                                            vm.updateSheet(s.copy(marks = MarkCodec.encode(next)))
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(lab, color = fg, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                                }
                                Spacer(Modifier.weight(1f))
                                val note = when { r.st == "double" -> "double · 0"; r.st == "multi" -> "pick one"; r.m.erased != null -> "erased ${labels.getOrElse(r.m.erased) { "?" }}"; r.m.fixed -> "fixed"; else -> "" }
                                if (note.isNotEmpty()) Text(note, fontSize = 11.sp, color = when (r.st) { "double" -> Bad; "multi" -> Warn; else -> Muted })
                            }
                            val (mk, mbg, mfg) = when (r.st) {
                                "right" -> Triple("✓", GoodBg, Good); "wrong" -> Triple("✕", BadBg, Bad); "double" -> Triple("2×", BadBg, Bad)
                                "multi" -> Triple("?", WarnBg, Warn); "blank" -> Triple("–", SegBg, Muted); else -> Triple("·", SegBg, Muted)
                            }
                            Box(Modifier.padding(start = 6.dp).size(26.dp).clip(CircleShape).background(mbg), contentAlignment = Alignment.Center) { Text(mk, color = mfg, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                    }
                }
                Text("Tap a bubble to correct a misread mark. Green ring = correct answer. Grey = erased mark, ignored.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(12.dp))
            }
        }
        val pending = Grading.issues(marks, b.exam.doubleRule)
        Box(Modifier.background(Paper).padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(
                when { issue == IdIssue.SET -> "Fix set code to grade this sheet"; issue != null -> "Fix roll number to approve"; pending > 0 -> "Approve with $pending unresolved"; s.approved -> "Approved · next sheet"; else -> "Approve & next sheet" },
                {
                    when (issue) {
                        IdIssue.SET -> editSet = true
                        IdIssue.ROLL_MISSING, IdIssue.ROLL_DUP -> editRoll = true
                        null -> {
                            vm.updateSheet(s.copy(approved = true))
                            val next = order.firstOrNull { it.id != s.id && b.needsCheck(it) }
                            if (next != null) go(next) else { vm.toast("${b.nameOf(s)} approved · all sheets reviewed"); nav.popBackStack() }
                        }
                    }
                },
                color = if (issue != null || pending > 0) Warn else Ink, contentColor = Paper,
            )
        }
    }

    if (editRoll) RollDialog(vm, b, s) { editRoll = false }
    if (editSet) SetDialog(vm, b, s) { editSet = false }
    if (showScan) Dialog(onDismissRequest = { showScan = false }) {
        val bmp by produceState<android.graphics.Bitmap?>(null, s.imagePath) { value = withContext(Dispatchers.IO) { s.imagePath?.let { BitmapFactory.decodeFile(it) } } }
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).verticalScroll(rememberScrollState()).padding(8.dp)) {
            bmp?.let { Image(it.asImageBitmap(), "Scanned sheet", Modifier.fillMaxWidth()) } ?: Text("No image saved for this sheet.", modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun IdCard(label: String, value: String, bad: Boolean, edited: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(14.dp)).background(if (bad) WarnBg else CardBg).border(1.dp, if (bad) WarnLine else Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Muted)
            Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = if (bad) Warn else Ink)
        }
        Text(if (bad) "Fix" else if (edited) "Edited ✎" else "Edit ✎", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (bad) Warn else Ink)
    }
}

@Composable
private fun RollDialog(vm: AppViewModel, b: ExamBundle, s: SheetEntity, close: () -> Unit) {
    var v by remember { mutableStateOf(if (b.student(s.roll) != null) s.roll else "") }
    val digits = b.students.firstOrNull()?.roll?.length ?: b.exam.rollDigits
    // a double-filled column gives exactly two candidate rolls
    val cand = if (s.rollAlt.isNotEmpty() && s.roll.contains('?')) {
        val col = s.rollAlt.substringBefore(':').toInt()
        s.rollAlt.substringAfter(':').map { d -> s.roll.substring(0, col) + d + s.roll.substring(col + 1) }
    } else emptyList()
    val suggestions = (cand.mapNotNull { b.student(it) } + b.unmatched).distinctBy { it.roll }.take(12)
    val stu = b.student(v)
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Correct roll number") },
        text = {
            Column {
                Text(if (cand.isNotEmpty()) "Two bubbles filled — could be ${cand.joinToString(" or ")}." else "Scanner read “${s.rollRead}”. Check the name written on the sheet.", fontSize = 13.sp, color = Muted)
                OutlinedTextField(v, { v = it.filter(Char::isDigit).take(digits) }, label = { Text("Roll") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.padding(top = 10.dp).fillMaxWidth())
                Text(
                    when { v.length < digits -> "Enter $digits digits"; stu == null -> "Not on the class list"; b.sheets.any { it.id != s.id && it.roll == v } -> "${stu.name} · already has a sheet"; else -> stu.name },
                    color = when { v.length < digits -> Muted; stu == null -> Bad; else -> Good }, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
                if (suggestions.isNotEmpty()) {
                    Text("Not scanned yet · likely match", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { st -> Pill("${st.roll} ${st.name.substringBefore(' ')}" + (if (st.roll in cand) " · bubbled" else ""), v == st.roll, { v = st.roll }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (stu == null) vm.toast("Pick a roll that's on the class list")
                else { vm.updateSheet(s.copy(roll = v)); vm.toast("Sheet now belongs to ${stu.name}"); close() }
            }) { Text(if (stu != null) "Assign to ${stu.name.substringBefore(' ')}" else "Save", color = Ink) }
        },
        dismissButton = { TextButton(close) { Text("Cancel", color = Muted) } },
    )
}

@Composable
private fun SetDialog(vm: AppViewModel, b: ExamBundle, s: SheetEntity, close: () -> Unit) {
    var v by remember { mutableStateOf(if (b.key(s.setCode) != null) s.setCode else "") }
    val marks = b.marks(s)
    fun scoreFor(set: String) = Grading.score(marks, b.key(set)) ?: 0
    // narrow by what was read: a double-filled column → its two digits; a blank digit → that pattern; then rank by score
    val read = s.setRead
    val pool = when {
        s.setAlt.isNotEmpty() -> { val col = s.setAlt.substringBefore(':').toInt(); s.setAlt.substringAfter(':').map { read.substring(0, col) + it + read.substring(col + 1) }.filter { b.key(it) != null } }
        read.contains('?') -> { val re = Regex("^" + read.replace("?", "\\d") + "$"); b.sets.filter { re.matches(it) } }
        else -> emptyList()
    }.ifEmpty { b.sets }
    val best = pool.sortedByDescending { scoreFor(it) }.take(4)
    val ok = b.key(v) != null
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Correct set code") },
        text = {
            Column {
                Text("Scanner read “$read”. Check the set written on the question paper.", fontSize = 13.sp, color = Muted)
                OutlinedTextField(v, { v = it.filter(Char::isDigit).take(3) }, label = { Text("Set code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.padding(top = 10.dp).fillMaxWidth())
                Text(if (ok) "would score ${scoreFor(v)}/${b.n}" else if (v.length < 3) "type 3 digits or pick a match" else "not in this exam's ${b.sets.size} sets",
                    color = if (ok) Good else if (v.length < 3) Muted else Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Best matches", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                best.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        pair.forEach { code ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (v == code) Ink else CardBg).border(1.dp, if (v == code) Ink else Line, RoundedCornerShape(14.dp))
                                    .clickable { v = code }.padding(10.dp),
                            ) {
                                Text(code, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = if (v == code) Paper else Ink)
                                Text("would score ${scoreFor(code)}/${b.n}", fontSize = 11.sp, color = if (v == code) Paper else Muted)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton({ if (!ok) vm.toast("Pick one of the exam's set codes") else { vm.updateSheet(s.copy(setCode = v)); vm.toast("Re-graded as set $v · ${scoreFor(v)}/${b.n}"); close() } }) { Text("Re-grade", color = Ink) } },
        dismissButton = { TextButton(close) { Text("Cancel", color = Muted) } },
    )
}

// ── Insights ────────────────────────────────────────────────

@Composable
fun InsightsScreen(vm: AppViewModel) {
    val all by vm.bundles.collectAsStateWithLifecycle()
    val withSheets = all.filter { it.sheets.isNotEmpty() }
    var pick by rememberSaveable { mutableStateOf<Long?>(null) }
    val b = withSheets.firstOrNull { it.exam.id == pick } ?: withSheets.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)) {
        item { Text("Insights", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp) }
        if (b == null) { item { Text("Scan some sheets to see class results here.", color = Muted, modifier = Modifier.padding(vertical = 24.dp)) }; return@LazyColumn }
        item {
            LazyRow(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(withSheets) { e -> Pill(e.exam.title, e.exam.id == b.exam.id, { pick = e.exam.id }) }
            }
            val p = b.percents.sorted()
            val med = if (p.isEmpty()) null else if (p.size % 2 == 1) p[p.size / 2] else (p[p.size / 2 - 1] + p[p.size / 2]) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile(b.average, "Mean", Modifier.weight(1f))
                StatTile(med?.let { "${it.toInt()}%" } ?: "—", "Median", Modifier.weight(1f))
                StatTile(p.lastOrNull()?.let { "${it.toInt()}%" } ?: "—", "High", Modifier.weight(1f))
                StatTile(p.firstOrNull()?.let { "${it.toInt()}%" } ?: "—", "Low", Modifier.weight(1f))
            }
        }
        item {
            SectionLabel("Score distribution · ${b.sheets.size} students")
            val bins = IntArray(5); b.percents.forEach { bins[(it / 20).toInt().coerceIn(0, 4)]++ }
            val max = (bins.maxOrNull() ?: 1).coerceAtLeast(1)
            AppCard(padding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    bins.forEachIndexed { i, n ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", fontFamily = Mono, fontSize = 12.sp)
                            Box(Modifier.padding(top = 4.dp).fillMaxWidth().fillMaxHeight((n.toFloat() / max).coerceAtLeast(0.03f) * 0.85f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (i < 2) Bad else if (i < 3) Warn else Ink))
                        }
                    }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until 5).forEach { i -> Text("${i * 20}–${i * 20 + 20}", fontFamily = Mono, fontSize = 10.sp, color = Muted, modifier = Modifier.weight(1f)) }
                }
            }
        }
        item {
            SectionLabel("Question analysis · hardest first")
            val labels = b.layout.optionLabels()
            val stats = (0 until b.n).map { q ->
                var right = 0; var total = 0; val wrong = IntArray(b.exam.options)
                b.sheets.forEach { s ->
                    val k = b.key(s.setCode)?.getOrNull(q) ?: -1
                    if (k >= 0) { total++; val v = b.marks(s).getOrNull(q)?.v ?: -1; if (v == k) right++ else if (v >= 0) wrong[v]++ }
                }
                Triple(q, if (total == 0) 0 else right * 100 / total, wrong.indices.maxByOrNull { wrong[it] }?.takeIf { wrong[it] > 0 })
            }.sortedBy { it.second }
            AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                stats.forEach { (q, pct, trap) ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Q${q + 1}", fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.width(40.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Divider)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(if (pct < 50) Bad else if (pct < 70) Warn else Good))
                        }
                        Text("$pct%", fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(48.dp).padding(start = 8.dp))
                        Text(trap?.let { "trap: ${labels[it]}" } ?: "", fontSize = 11.sp, color = Muted, modifier = Modifier.width(64.dp))
                    }
                }
            }
        }
    }
}
