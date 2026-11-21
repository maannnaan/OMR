package com.omrscanner.app.ui.exams

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import com.omrscanner.app.ui.components.Banner
import com.omrscanner.app.ui.components.Bubble
import com.omrscanner.app.ui.components.Chip
import com.omrscanner.app.ui.components.ConfirmDialog
import com.omrscanner.app.ui.components.Hint
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.Screen
import com.omrscanner.app.ui.components.SectionTitle
import com.omrscanner.app.ui.components.Tone
import com.omrscanner.app.ui.components.fieldColors
import com.omrscanner.app.ui.components.plural
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.BadSoft
import com.omrscanner.app.ui.theme.Brand
import com.omrscanner.app.ui.theme.BrandSoft
import com.omrscanner.app.ui.theme.FieldLine
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.InsetBg
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Warn
import com.omrscanner.app.ui.theme.WarnSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Checking one scanned sheet ──────────────────────────────

@Composable
fun ReviewScreen(vm: AppViewModel, nav: NavController, examId: Long, sheetId: Long) {
    val b = rememberBundle(vm, examId) ?: return
    val s = b.sheets.firstOrNull { it.id == sheetId } ?: return
    // the sheets in roll order, so "next" is the next student
    val order = remember(b.sheets) { b.sheets.sortedWith(compareBy<SheetEntity> { it.roll }.thenBy { it.scannedAt }) }
    val idx = order.indexOfFirst { it.id == s.id }
    // fixed answers show at once, while they're saved in the background
    var fixed by remember(s.id) { mutableStateOf<List<Mark>?>(null) }
    val marks = fixed ?: b.marks(s)
    var dialog by remember { mutableStateOf<String?>(null) }
    fun go(to: SheetEntity) = nav.navigate(Routes.review(examId, to.id)) { popUpTo(Routes.review(examId, sheetId)) { inclusive = true } }

    ReviewContent(
        b, s, marks, position = "Sheet ${idx + 1} of ${order.size}",
        onBack = { nav.popBackStack() },
        onPrev = { if (order.size > 1) go(order[(idx - 1 + order.size) % order.size]) },
        onNext = { if (order.size > 1) go(order[(idx + 1) % order.size]) },
        onPhoto = { dialog = "photo" },
        onFixRoll = { dialog = "roll" },
        onFixSet = { dialog = "set" },
        onMark = { q, opt ->
            val next = marks.toMutableList().also { it[q] = Mark(opt, fixed = true) }
            fixed = next
            vm.updateSheet(s.copy(marks = MarkCodec.encode(next)))
        },
        onApprove = {
            vm.updateSheet(s.copy(approved = true, marks = MarkCodec.encode(marks)))
            val after = order.drop(idx + 1) + order.take(idx)
            val next = after.firstOrNull { b.needsCheck(it) }
            if (next != null) go(next) else { vm.toast("All sheets checked"); nav.popBackStack() }
        },
        onDelete = { dialog = "delete" },
    )

    when (dialog) {
        "roll" -> RollDialog(vm, b, s) { dialog = null }
        "set" -> SetDialog(vm, b, s, marks) { dialog = null }
        "photo" -> Dialog(onDismissRequest = { dialog = null }) {
            val bmp by produceState<android.graphics.Bitmap?>(null, s.imagePath) { value = withContext(Dispatchers.IO) { s.imagePath?.let { BitmapFactory.decodeFile(it) } } }
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).verticalScroll(rememberScrollState()).padding(8.dp)) {
                bmp?.let { Image(it.asImageBitmap(), contentDescription = "The scanned sheet", modifier = Modifier.fillMaxWidth()) }
                    ?: Text("No photo was kept for this sheet.", modifier = Modifier.padding(24.dp))
            }
        }
        "delete" -> ConfirmDialog(
            "Delete this sheet?", "${b.nameOf(s)}'s scanned sheet will be removed. You can scan it again.", "Delete",
            { vm.deleteSheet(s); nav.popBackStack() }, { dialog = null },
        )
    }
}

/** How one question came out on a sheet: right, wrong, blank, two answers, or no key for it. */
private data class QRow(val q: Int, val st: String, val m: Mark, val k: Int)

@Composable
fun ReviewContent(
    b: ExamBundle, s: SheetEntity, marks: List<Mark>, position: String,
    onBack: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onPhoto: () -> Unit,
    onFixRoll: () -> Unit, onFixSet: () -> Unit, onMark: (q: Int, option: Int) -> Unit, onApprove: () -> Unit, onDelete: () -> Unit,
) {
    var filter by rememberSaveable(s.id) { mutableStateOf("all") }
    val key = b.key(s.setCode)
    val labels = b.layout.optionLabels()
    val score = Grading.score(marks, key)
    val issue = b.idIssue(s)
    val dblWrong = b.exam.doubleRule == "wrong"
    val known = b.student(s.roll) != null

    val rows = marks.mapIndexed { q, m ->
        val k = key?.getOrNull(q) ?: -9
        val st = when {
            m.v == Mark.BLANK -> "blank"
            m.v == Mark.DOUBLE -> if (dblWrong) "double" else "multi"
            key == null || k < 0 -> "none"
            m.v == k -> "right"
            else -> "wrong"
        }
        QRow(q, st, m, k)
    }
    fun matches(r: QRow, f: String) = when (f) {
        "all" -> true
        "wrong" -> r.st == "wrong" || r.st == "double"
        else -> r.st == f
    }
    val counts = listOf("all", "multi", "wrong", "blank", "right").associateWith { f -> rows.count { matches(it, f) } }
    val shown = rows.filter { matches(it, filter) }
    val bubble = when { b.exam.options <= 4 -> 44.dp; b.exam.options == 5 -> 40.dp; else -> 34.dp }
    val gap = when { b.exam.options <= 4 -> 8.dp; b.exam.options == 5 -> 6.dp; else -> 4.dp }

    Screen(
        title = position, subtitle = b.exam.title, onBack = onBack,
        actions = {
            IconButton(onClick = onPrev) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous sheet", tint = Ink) }
            IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next sheet", tint = Ink) }
            IconButton(onClick = onPhoto) { Icon(Icons.Outlined.Image, contentDescription = "See the photo", tint = Ink) }
        },
        bottom = {
            when (issue) {
                IdIssue.SET -> PrimaryButton("Choose the set", onFixSet)
                IdIssue.ROLL_MISSING, IdIssue.ROLL_DUP -> PrimaryButton("Choose the student", onFixRoll)
                null -> PrimaryButton("Looks right · next", onApprove)
            }
        },
    ) {
        item {
            AppCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(b.nameOf(s), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = if (known) Ink else Warn, maxLines = 2)
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (b.exam.rollDigits > 0) EditChip("Roll ${s.roll}", issue == IdIssue.ROLL_MISSING || issue == IdIssue.ROLL_DUP, onFixRoll)
                            if (b.exam.setCode) EditChip("Set ${s.setCode}", issue == IdIssue.SET, onFixSet)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                        Text(score?.let { "$it/${b.n}" } ?: "—", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(score?.let { "${it * 100 / b.n}%" } ?: "Not marked", fontSize = 14.sp, color = Muted)
                    }
                }
            }
        }
        val problem = when (issue) {
            IdIssue.ROLL_MISSING -> if (s.roll.contains('?') && s.rollAlt.isNotEmpty()) "Two bubbles are filled in the roll number." else "Roll ${s.roll} isn't on the student list."
            IdIssue.ROLL_DUP -> "Another sheet has roll ${s.roll} too. Check the name written on this one."
            IdIssue.SET -> "The set code couldn't be read, so this sheet isn't marked yet."
            null -> null
        }
        if (problem != null) item { Banner(problem, Tone.WARN, if (issue == IdIssue.SET) "Choose" else "Fix", if (issue == IdIssue.SET) onFixSet else onFixRoll) }
        val multi = counts["multi"] ?: 0
        if (multi > 0) item { Banner("${plural(multi, "question")} with two answers. Tap the one the student meant.", Tone.WARN) }
        item {
            val filters = listOf("all" to "All", "multi" to "Two answers", "wrong" to "Wrong", "blank" to "Blank", "right" to "Right")
                .filter { (f, _) -> f == "all" || f == "wrong" || f == "right" || (counts[f] ?: 0) > 0 }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { (f, label) -> Chip("$label ${counts[f]}", filter == f, { filter = f }) }
            }
        }
        if (shown.isEmpty()) item { Hint("No questions here.") }
        items(shown.chunked(10)) { group ->
            AppCard {
                group.forEachIndexed { i, r ->
                    if (i > 0) RowDivider(12.dp)
                    Row(
                        Modifier.fillMaxWidth().background(if (r.st == "multi") WarnSoft else Color.Transparent).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${r.q + 1}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Muted, textAlign = TextAlign.End, modifier = Modifier.width(32.dp).padding(end = 10.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            labels.forEachIndexed { j, lab ->
                                val marked = r.m.v == j || (r.m.v == Mark.DOUBLE && j in r.m.multi)
                                val c = when (r.st) { "right" -> Brand; "wrong", "double" -> Bad; "none" -> Ink; else -> Warn }
                                when {
                                    marked -> Bubble(lab, c, c, Color.White, bubble) { onMark(r.q, j) }
                                    r.m.erased == j -> Bubble(lab, InsetBg, Line, Muted, bubble) { onMark(r.q, j) }
                                    j == r.k && r.st != "right" -> Bubble(lab, Color.Transparent, Brand, Brand, bubble) { onMark(r.q, j) }
                                    else -> Bubble(lab, Color.Transparent, FieldLine, Muted, bubble) { onMark(r.q, j) }
                                }
                            }
                        }
                        ResultMark(r.st)
                    }
                }
            }
        }
        item { Hint("Tap a bubble to fix a misread answer. A green ring shows the right answer; a grey bubble was rubbed out.") }
        item {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Bad, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete this sheet", color = Bad, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ResultMark(st: String) {
    val (mark, bg, fg) = when (st) {
        "right" -> Triple("✓", BrandSoft, Brand)
        "wrong" -> Triple("✕", BadSoft, Bad)
        "double" -> Triple("2", BadSoft, Bad)
        "multi" -> Triple("?", WarnSoft, Warn)
        "blank" -> Triple("–", InsetBg, Muted)
        else -> Triple("·", InsetBg, Muted)
    }
    Box(Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(mark, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** The roll or set read from a sheet, which the teacher can tap to change. */
@Composable
private fun EditChip(text: String, warn: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (warn) WarnSoft else InsetBg).clickable(onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (warn) Warn else Ink)
        Icon(Icons.Outlined.Edit, contentDescription = "Change", tint = if (warn) Warn else Muted, modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

@Composable
private fun RollDialog(vm: AppViewModel, b: ExamBundle, s: SheetEntity, close: () -> Unit) {
    var v by remember { mutableStateOf(if (b.student(s.roll) != null) s.roll else "") }
    val digits = b.students.firstOrNull()?.roll?.length ?: b.exam.rollDigits
    // a roll column with two bubbles filled gives exactly two rolls it could be
    val cand = if (s.rollAlt.isNotEmpty() && s.roll.contains('?')) {
        val col = s.rollAlt.substringBefore(':').toInt()
        s.rollAlt.substringAfter(':').map { d -> s.roll.substring(0, col) + d + s.roll.substring(col + 1) }
    } else emptyList()
    val suggestions = (cand.mapNotNull { b.student(it) } + b.unmatched).distinctBy { it.roll }.take(12)
    val stu = b.student(v)
    val taken = stu != null && b.sheets.any { it.id != s.id && it.roll == v }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Whose sheet is this?") },
        text = {
            Column {
                Text(
                    if (cand.isNotEmpty()) "Two bubbles are filled: it could be ${cand.joinToString(" or ")}." else "The scanner read “${s.rollRead}”. Check the name written on the sheet.",
                    fontSize = 14.sp, color = Muted,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = v, onValueChange = { v = it.filter(Char::isDigit).take(digits) }, label = { Text("Roll") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                )
                Text(
                    when { v.length < digits -> "Enter $digits digits"; stu == null -> "Not on the student list"; taken -> "${stu.name} · already has a sheet"; else -> stu.name },
                    color = when { v.length < digits -> Muted; stu == null -> Bad; taken -> Warn; else -> Brand }, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
                )
                if (suggestions.isNotEmpty()) {
                    Text("Not scanned yet", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { st -> Chip("${st.roll} · ${st.name.substringBefore(' ')}", v == st.roll, { v = st.roll }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (stu == null) vm.toast("Pick a roll that's on the student list")
                else { vm.updateSheet(s.copy(roll = v)); vm.toast("This sheet is now ${stu.name}'s"); close() }
            }) { Text("Save", color = Brand, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel", color = Muted) } },
    )
}

@Composable
private fun SetDialog(vm: AppViewModel, b: ExamBundle, s: SheetEntity, marks: List<Mark>, close: () -> Unit) {
    var v by remember { mutableStateOf(if (b.key(s.setCode) != null) s.setCode else "") }
    fun scoreFor(set: String) = Grading.score(marks, b.key(set)) ?: 0
    // narrow by what was read (a column with two bubbles gives two digits; a blank digit matches any), then rank by marks
    val read = s.setRead
    val pool = when {
        s.setAlt.isNotEmpty() -> {
            val col = s.setAlt.substringBefore(':').toInt()
            s.setAlt.substringAfter(':').map { read.substring(0, col) + it + read.substring(col + 1) }.filter { b.key(it) != null }
        }
        read.contains('?') -> { val re = Regex("^" + read.replace("?", "\\d") + "$"); b.sets.filter { re.matches(it) } }
        else -> emptyList()
    }.ifEmpty { b.sets }
    val best = pool.sortedByDescending { scoreFor(it) }.take(4)
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Which set is it?") },
        text = {
            Column {
                Text("The scanner read “$read”. Check the set code written on the question paper.", fontSize = 14.sp, color = Muted)
                Text("Most likely", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                best.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        pair.forEach { code ->
                            val on = v == code
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) BrandSoft else InsetBg).clickable { v = code }.padding(12.dp),
                            ) {
                                Text("Set $code", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = if (on) Brand else Ink)
                                Text("would get ${scoreFor(code)}/${b.n}", fontSize = 13.sp, color = if (on) Brand else Muted)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (b.sets.size > best.size) {
                    androidx.compose.material3.OutlinedTextField(
                        value = v, onValueChange = { v = it.filter(Char::isDigit).take(3) }, label = { Text("Or type the set code") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    )
                    if (v.length == 3 && b.key(v) == null) Text("This exam has no set $v", color = Bad, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (b.key(v) == null) vm.toast("Pick one of the exam's sets")
                else { vm.updateSheet(s.copy(setCode = v)); vm.toast("Marked as set $v: ${scoreFor(v)}/${b.n}"); close() }
            }) { Text("Save", color = Brand, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel", color = Muted) } },
    )
}

// ── Question analysis ───────────────────────────────────────

@Composable
fun AnalysisScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    AnalysisContent(b) { nav.popBackStack() }
}

@Composable
fun AnalysisContent(b: ExamBundle, onBack: () -> Unit) {
    Screen(title = "Question analysis", subtitle = b.exam.title, onBack = onBack) {
        if (b.sheets.isEmpty()) {
            item { Hint("Scan some sheets to see how the class did.") }
            return@Screen
        }
        val p = b.percents.sorted()
        val median = if (p.isEmpty()) null else if (p.size % 2 == 1) p[p.size / 2] else (p[p.size / 2 - 1] + p[p.size / 2]) / 2
        item {
            AppCard {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Stat(b.average, "Average", Modifier.weight(1f))
                    Stat(median?.let { "${it.toInt()}%" } ?: "—", "Middle", Modifier.weight(1f))
                    Stat(p.lastOrNull()?.let { "${it.toInt()}%" } ?: "—", "Highest", Modifier.weight(1f))
                    Stat(p.firstOrNull()?.let { "${it.toInt()}%" } ?: "—", "Lowest", Modifier.weight(1f))
                }
            }
        }
        item { SectionTitle("How many got each mark") }
        item {
            val bins = IntArray(5).also { bins -> b.percents.forEach { bins[(it / 20).toInt().coerceIn(0, 4)]++ } }
            val max = (bins.maxOrNull() ?: 1).coerceAtLeast(1)
            AppCard(padding = 16.dp) {
                Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    bins.forEachIndexed { i, n ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Box(
                                Modifier.padding(top = 4.dp).fillMaxWidth().fillMaxHeight((n.toFloat() / max).coerceAtLeast(0.03f) * 0.82f)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(if (i < 2) Bad else if (i < 3) Warn else Brand),
                            )
                        }
                    }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0 until 5).forEach { i -> Text("${i * 20}–${i * 20 + 20}%", fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                }
            }
        }
        item { SectionTitle("Questions, hardest first") }
        item { Hint("How many students got each question right, and the wrong answer most of the others chose.") }
        val labels = b.layout.optionLabels()
        val stats = (0 until b.n).map { q ->
            var right = 0; var total = 0
            val wrong = IntArray(b.exam.options)
            b.sheets.forEach { s ->
                val k = b.key(s.setCode)?.getOrNull(q) ?: -1
                if (k >= 0) {
                    total++
                    val v = b.marks(s).getOrNull(q)?.v ?: -1
                    if (v == k) right++ else if (v >= 0) wrong[v]++
                }
            }
            Triple(q, if (total == 0) 0 else right * 100 / total, wrong.indices.maxByOrNull { wrong[it] }?.takeIf { wrong[it] > 0 })
        }.sortedBy { it.second }
        items(stats.chunked(10)) { group ->
            AppCard(padding = 16.dp) {
                group.forEach { (q, pct, trap) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${q + 1}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Muted, modifier = Modifier.width(36.dp))
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(InsetBg)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(if (pct < 50) Bad else if (pct < 70) Warn else Brand))
                        }
                        Text("$pct%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                        Text(trap?.let { "most: ${labels[it]}" } ?: "", fontSize = 13.sp, color = Muted, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                    }
                }
            }
        }
    }
}
