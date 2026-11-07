package com.omrscanner.app.ui.exams

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.omr.KeyCodec
import com.omrscanner.app.omr.SheetPdf
import com.omrscanner.app.omr.layout
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.BackBar
import com.omrscanner.app.ui.components.Pill
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.ScreenTitle
import com.omrscanner.app.ui.components.SecondaryButton
import com.omrscanner.app.ui.components.SectionLabel
import com.omrscanner.app.ui.components.Segmented
import com.omrscanner.app.ui.components.Stepper
import com.omrscanner.app.ui.components.ToggleRow
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.CardBg
import com.omrscanner.app.ui.theme.Good
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Mono
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Paper
import com.omrscanner.app.ui.theme.SegBg
import com.omrscanner.app.ui.theme.Warn
import com.omrscanner.app.ui.theme.WarnBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ── Answer sheet builder ────────────────────────────────────

@Composable
fun SheetBuilderScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    var d by remember(b.exam.id) { mutableStateOf(b.exam) }
    val locked = b.sheets.isNotEmpty()
    val dirty = d != b.exam
    val preview by produceState<android.graphics.Bitmap?>(null, d) { value = withContext(Dispatchers.Default) { SheetPdf.preview(d.layout(), 1000) } }
    fun lockedToast() = vm.toast("Layout locked — ${b.sheets.size} sheets were already scanned on it")

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { BackBar(b.exam.title.substringBefore(" · "), { nav.popBackStack() }) }
        item { ScreenTitle("Answer sheet", "Set it up once — the scanner reads this exact layout.") }
        if (locked) item {
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WarnBg).padding(12.dp)) {
                Text("Questions, options, roll and set code are locked — ${b.sheets.size} sheets were scanned on this layout. Text and labels can still change.", color = Warn, fontSize = 13.sp)
            }
        }
        item {
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SegBg).padding(12.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1000f / 1414f).background(Color.White)) {
                    preview?.let { Image(it.asImageBitmap(), "Answer sheet preview", Modifier.fillMaxSize()) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(d.institution, { d = d.copy(institution = it) }, label = { Text("Institution") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.title, { d = d.copy(title = it) }, label = { Text("Exam name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AppCard(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Questions", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Stepper("${d.questions}", { if (locked) lockedToast() else d = d.copy(questions = (d.questions - 5).coerceAtLeast(5)) }, { if (locked) lockedToast() else d = d.copy(questions = (d.questions + 5).coerceAtMost(100)) })
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(20, 30, 50, 100).forEach { v -> Pill("$v", d.questions == v, { if (locked) lockedToast() else d = d.copy(questions = v) }) }
                    }
                }
                Text("Options per question", fontSize = 12.sp, color = Muted)
                Segmented((2..6).map { it to "$it" }, d.options, { if (locked) lockedToast() else d = d.copy(options = it) })
                Text("Bubble labels", fontSize = 12.sp, color = Muted)
                Segmented(listOf("ABC" to "A B C", "BANGLA" to "ক খ গ", "123" to "1 2 3"), d.labels, { d = d.copy(labels = it) })
                Text("Sheet language", fontSize = 12.sp, color = Muted)
                Segmented(listOf("English" to "English", "Bangla" to "বাংলা"), d.language, { lang ->
                    d = d.copy(language = lang, labels = if (lang == "Bangla" && d.labels == "ABC") "BANGLA" else if (lang == "English" && d.labels == "BANGLA") "ABC" else d.labels)
                })
                AppCard {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Roll number", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(if (d.rollDigits > 0) "${d.rollDigits}-digit bubble grid" else "Off — identify by name only", fontSize = 12.sp, color = Muted)
                        }
                        val minDigits = b.total.toString().length
                        Stepper("${d.rollDigits}", {
                            when {
                                locked -> lockedToast()
                                d.rollDigits - 1 in 1 until minDigits -> vm.toast("${b.total} students need at least $minDigits roll digits")
                                else -> d = d.copy(rollDigits = (d.rollDigits - 1).coerceAtLeast(0))
                            }
                        }, { if (locked) lockedToast() else d = d.copy(rollDigits = (d.rollDigits + 1).coerceAtMost(8)) })
                    }
                    RowDivider()
                    ToggleRow("Set code", "3-digit grid for question-set versions", d.setCode) { if (locked) lockedToast() else d = d.copy(setCode = it) }
                    RowDivider()
                    ToggleRow("Name & class fields", "Handwritten, not scanned", d.nameFields) { d = d.copy(nameFields = it) }
                }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            if (dirty) PrimaryButton("Save changes", { vm.updateExam(d); vm.toast("Answer sheet saved") }, color = Ink, contentColor = Paper)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Save PDF", { vm.shareSheetPdf(b.exam, b.total.coerceAtLeast(1), save = true) }, Modifier.weight(1f))
                PrimaryButton("Print ${b.total} copies", { vm.shareSheetPdf(b.exam, b.total.coerceAtLeast(1), save = false) }, Modifier.weight(1.6f))
            }
        }
    }
}

// ── Answer key: set-wise, manual or from the question bank ──

@Composable
fun AnswerKeyScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    var mode by rememberSaveable { mutableStateOf(if (b.noKey) "db" else "manual") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var setQuery by rememberSaveable { mutableStateOf("") }
    var addSet by remember { mutableStateOf(false) }
    val sets = b.sets
    val cur = selected?.takeIf { it in sets } ?: b.keyGaps.firstOrNull() ?: sets.firstOrNull()
    val labels = b.layout.optionLabels()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { BackBar(b.exam.title.substringBefore(" · "), { nav.popBackStack() }) }
        item {
            ScreenTitle("Answer key", if (b.exam.setCode) "One key per set code. Enter it set by set, or fetch every set from the question bank." else "Enter the key, or fetch it from the question bank.")
            Spacer(Modifier.height(14.dp))
            Segmented(listOf("manual" to "Enter manually", "db" to "From database"), mode, { mode = it })
        }

        if (mode == "manual") {
            if (b.exam.setCode) item {
                Spacer(Modifier.height(12.dp))
                if (sets.size > 8) OutlinedTextField(setQuery, { setQuery = it.filter(Char::isDigit).take(3) }, label = { Text("Find a set (${sets.size} sets)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                val shown = sets.filter { it.startsWith(setQuery) }.take(80)
                LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown) { s ->
                        val done = b.keyDone(s)
                        val on = s == cur
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Ink else CardBg).border(1.dp, if (on) Ink else Line, RoundedCornerShape(14.dp))
                                .clickable { selected = s }.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(s, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (on) Paper else Ink)
                            Text("$done/${b.n}", fontFamily = Mono, fontSize = 11.sp, color = if (on) Paper else if (done < b.n) Warn else Muted)
                        }
                    }
                    item { Box(Modifier.clip(RoundedCornerShape(14.dp)).border(1.5.dp, Line, RoundedCornerShape(14.dp)).clickable { addSet = true }.padding(horizontal = 14.dp, vertical = 16.dp)) { Text("+ Add set", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) } }
                }
                Text("${sets.size} sets · ${sets.size - b.keyGaps.size} complete" + (if (b.keyGaps.isNotEmpty()) " · ${b.keyGaps.size} incomplete" else "") + ". Tap a filled bubble again to clear it.",
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (cur == null) item { Text("No sets yet — add a set code or fetch from the database.", color = Muted, modifier = Modifier.padding(vertical = 24.dp)) }
            else {
                val row = b.keyRows.firstOrNull { it.setCode == cur }
                if (row?.source != null) item {
                    Text("From database · ${row.source}" + (if (row.edited) " · edited by you" else ""), fontFamily = Mono, fontSize = 12.sp, color = if (row.edited) Warn else Good, modifier = Modifier.padding(top = 10.dp))
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    val key = b.key(cur) ?: KeyCodec.blank(b.n)
                    AppCard {
                        for (q in 0 until b.n) {
                            if (q > 0) RowDivider()
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Q${q + 1}", fontFamily = Mono, fontSize = 13.sp, color = Muted, modifier = Modifier.width(40.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    labels.forEachIndexed { j, lab ->
                                        val on = key[q] == j
                                        Box(
                                            Modifier.size(if (b.exam.options >= 6) 34.dp else 38.dp).clip(CircleShape).background(if (on) Ink else Color.Transparent)
                                                .border(2.dp, if (on) Ink else Line, CircleShape).clickable {
                                                    val next = key.copyOf(); next[q] = if (on) -1 else j
                                                    vm.saveKey(b.exam.id, cur, next, row?.source, edited = row?.source != null)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) { Text(lab, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (on) Paper else Muted) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item { BankPicker(vm, b) { mode = "manual"; selected = null } }
        }
    }

    if (addSet) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addSet = false },
            title = { Text("Add a set code") },
            text = { OutlinedTextField(code, { code = it.filter(Char::isDigit).take(3) }, label = { Text("3 digits, e.g. 105") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = {
                TextButton({
                    when {
                        code.length != 3 -> vm.toast("Enter all 3 digits")
                        code in sets -> vm.toast("Set $code already exists")
                        else -> { vm.saveKey(b.exam.id, code, KeyCodec.blank(b.n)); selected = code; addSet = false }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton({ addSet = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BankPicker(vm: AppViewModel, b: com.omrscanner.app.data.ExamBundle, onFetched: () -> Unit) {
    var q by rememberSaveable { mutableStateOf(b.exam.title.substringBefore(" ").substringBefore("·").trim()) }
    var busy by remember { mutableStateOf<String?>(null) }
    val results by produceState<List<PaperSummary>?>(null, q) { delay(300); value = runCatching { vm.searchBank(q) }.getOrElse { vm.toast("Question bank unreachable: ${it.message}"); emptyList() } }
    Column {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(q, { q = it }, label = { Text("Search question bank by title, subject or code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SectionLabel(if (vm.settings.bankUrl.isBlank()) "Sample question bank" else "Question bank")
        when (val list = results) {
            null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) }
            else -> {
                if (list.isEmpty()) Text("No papers match “$q”.", color = Muted, modifier = Modifier.padding(16.dp))
                list.forEach { p ->
                    val fits = p.questions == b.n && p.options == b.exam.options
                    AppCard(Modifier.padding(bottom = 10.dp), padding = PaddingValues(14.dp)) {
                        Row {
                            Text(p.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(p.code, fontFamily = Mono, fontSize = 11.sp, color = Muted, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SegBg).padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                        Text("${p.questions} Q · ${p.options} options · " + (if (p.setCount <= 4) "Sets ${p.firstSet}–${p.lastSet}" else "${p.setCount} sets (${p.firstSet}–${p.lastSet})"), fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Text("Updated ${p.updated} · ${p.author}", fontSize = 12.sp, color = Muted)
                        Text(
                            if (fits) "Fits this answer sheet" + (if (b.exam.setCode) " · brings in all ${p.setCount} set keys" else "") else "${p.questions} Q × ${p.options} options — this sheet is ${b.n} Q × ${b.exam.options}, so it can't be used",
                            color = if (fits) Good else Bad, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(
                            when { busy == p.id -> "Fetching ${p.setCount} sets…"; !fits -> "Doesn't fit this sheet"; b.exam.setCode && p.setCount > 1 -> "Fetch all ${p.setCount} set keys"; else -> "Fetch answer key" },
                            {
                                if (!fits) vm.toast("The printed sheet has ${b.n} questions × ${b.exam.options} options")
                                else if (busy == null) { busy = p.id; vm.fetchPaper(b.exam, p) { n -> busy = null; vm.toast("Fetched $n key${if (n > 1) "s" else ""} from ${p.code}"); onFetched() } }
                            },
                            color = if (fits) Ink else Line, contentColor = if (fits) Paper else Muted,
                        )
                    }
                }
            }
        }
    }
}
