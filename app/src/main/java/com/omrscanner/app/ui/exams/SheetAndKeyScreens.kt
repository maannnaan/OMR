package com.omrscanner.app.ui.exams

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.omr.KeyCodec
import com.omrscanner.app.omr.Layout
import com.omrscanner.app.omr.SheetLayout
import com.omrscanner.app.omr.SheetPdf
import com.omrscanner.app.omr.labelsFor
import com.omrscanner.app.omr.layout
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.Banner
import com.omrscanner.app.ui.components.Bubble
import com.omrscanner.app.ui.components.ChoiceDialog
import com.omrscanner.app.ui.components.Chip
import com.omrscanner.app.ui.components.ConfirmDialog
import com.omrscanner.app.ui.components.Field
import com.omrscanner.app.ui.components.Hint
import com.omrscanner.app.ui.components.InfoDialog
import com.omrscanner.app.ui.components.Pill
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.Screen
import com.omrscanner.app.ui.components.SecondaryButton
import com.omrscanner.app.ui.components.SettingRow
import com.omrscanner.app.ui.components.SwitchRow
import com.omrscanner.app.ui.components.TextDialog
import com.omrscanner.app.ui.components.Tone
import com.omrscanner.app.ui.components.plural
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.Brand
import com.omrscanner.app.ui.theme.FieldLine
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.InsetBg
import com.omrscanner.app.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ── Step 1: the answer key ──────────────────────────────────

@Composable
fun AnswerKeyScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    var selected by rememberSaveable { mutableStateOf(b.keyGaps.firstOrNull() ?: b.sets.firstOrNull()) }
    var addSet by remember { mutableStateOf(false) }
    // Taps show at once from here, while they're saved in the background.
    val edits = remember(b.exam.id) { mutableStateMapOf<String, IntArray>() }
    fun keyOf(set: String) = edits[set] ?: b.key(set)
    val cur = selected?.takeIf { it in b.sets } ?: b.sets.firstOrNull()
    val gaps = b.sets.filter { set -> keyOf(set)?.any { it < 0 } ?: true }
    val next = gaps.firstOrNull { it != cur }
    val curDone = cur != null && cur !in gaps

    AnswerKeyContent(
        b, cur, cur?.let { keyOf(it) },
        isDone = { it !in gaps },
        bankAvailable = vm.bankAvailable && !b.fromLecturer,
        doneLabel = if (curDone && next != null) "Next: set $next" else "Done",
        onBack = { nav.popBackStack() },
        onSelect = { selected = it },
        onAnswer = { q, opt ->
            val set = cur ?: return@AnswerKeyContent
            val row = b.keyRows.firstOrNull { it.setCode == set }
            val key = (keyOf(set) ?: KeyCodec.blank(b.n)).copyOf()
            key[q] = if (key[q] == opt) -1 else opt
            edits[set] = key
            vm.saveKey(b.exam.id, set, key, row?.source, edited = row?.source != null)
        },
        onAddSet = { addSet = true },
        onBank = { nav.navigate(Routes.bank(id)) },
        onDone = { if (curDone && next != null) selected = next else nav.popBackStack() },
    )

    if (addSet) {
        val suggestion = ((b.sets.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 100) + 1).toString()
        TextDialog(
            "Add a set", suggestion, { code -> vm.saveKey(b.exam.id, code, KeyCodec.blank(b.n)); selected = code }, { addSet = false },
            label = "Set code (3 digits)", number = true, note = "The set code printed on that version of the question paper.",
            check = { if (it.length != 3) "Enter 3 digits, like 105" else if (it in b.sets) "Set $it is already there" else null },
        )
    }
}

@Composable
fun AnswerKeyContent(
    b: ExamBundle, cur: String?, key: IntArray?, isDone: (String) -> Boolean, bankAvailable: Boolean, doneLabel: String,
    onBack: () -> Unit, onSelect: (String) -> Unit, onAnswer: (q: Int, option: Int) -> Unit, onAddSet: () -> Unit, onBank: () -> Unit, onDone: () -> Unit,
) {
    val labels = b.layout.optionLabels()
    val answered = key?.count { it >= 0 } ?: 0
    val bubble = if (b.exam.options <= 5) 44.dp else 38.dp
    val gap = if (b.exam.options <= 5) 10.dp else 6.dp

    Screen(title = "Answer key", subtitle = b.exam.title, onBack = onBack, bottom = { PrimaryButton(doneLabel, onDone) }) {
        if (bankAvailable) item {
            AppCard(onClick = onBank) {
                Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Brand)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Fill from the question bank", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Copy every set's answers from a paper", fontSize = 14.sp, color = Muted)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Muted)
                }
            }
        }
        if (b.exam.setCode) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(b.sets) { set -> Chip(if (isDone(set)) "$set ✓" else set, set == cur, { onSelect(set) }) }
                item { Chip("+ Add set", false, onAddSet) }
            }
        }
        if (cur == null) {
            item { Hint("No sets yet. Add the set code printed on the question paper, then its answers.") }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (b.exam.setCode) "Set $cur" else "Answers", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                    if (answered == b.n) Pill("All ${b.n} done", Tone.GOOD) else Text("$answered of ${b.n} done", fontSize = 15.sp, color = Muted)
                }
                Hint("Tap the right answer for each question. Tap it again to clear it.", Modifier.padding(top = 4.dp))
            }
            // ten questions to a card, like the columns on the sheet
            items((0 until b.n).chunked(10)) { group ->
                AppCard {
                    group.forEachIndexed { i, q ->
                        if (i > 0) RowDivider(12.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${q + 1}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Muted, textAlign = TextAlign.End, modifier = Modifier.width(32.dp).padding(end = 10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                labels.forEachIndexed { j, lab ->
                                    val on = key?.getOrNull(q) == j
                                    Bubble(lab, if (on) Brand else Color.Transparent, if (on) Brand else FieldLine, if (on) Color.White else Ink, bubble) { onAnswer(q, j) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Filling the key from a question bank ────────────────────

@Composable
fun BankScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    var q by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    val results by produceState<List<PaperSummary>?>(null, q) {
        delay(300)
        value = runCatching { vm.searchBank(q) }.getOrElse { vm.toast(it.message ?: "The question bank can't be reached"); emptyList() }
    }
    Screen(title = "Question bank", subtitle = if (vm.settings.lecturerSignedIn) "Your papers on Lecturer.bd" else "Your question bank", onBack = { nav.popBackStack() }) {
        item { Field(q, { q = it }, "Search by name or code") }
        when (val list = results) {
            null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) } }
            else -> {
                if (list.isEmpty()) item { Hint(if (q.isBlank()) "No papers yet." else "No papers match “$q”.") }
                items(list, key = { it.id }) { p ->
                    val fits = p.questions == b.n && p.options == b.exam.options
                    PaperCard(
                        p,
                        note = if (fits) null else "Has ${p.questions} questions with ${p.options} choices; this exam's sheet has ${b.n} with ${b.exam.options}.",
                        button = when { busy == p.id -> "Copying…"; fits -> "Use these answers"; else -> null },
                    ) {
                        if (busy == null) {
                            busy = p.id
                            vm.fetchPaper(b.exam, p) { n -> busy = null; vm.toast("Copied ${plural(n, "set")} from ${p.code}"); nav.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}

/** A question paper: its name and code, its size, and one button (or why it can't be used). */
@Composable
fun PaperCard(p: PaperSummary, note: String?, button: String?, onClick: () -> Unit) {
    AppCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            Pill(p.code, Tone.NEUTRAL, Modifier.padding(start = 8.dp))
        }
        Text(
            "${plural(p.questions, "question")} · ${plural(p.options, "choice")} · " + if (p.setCount == 1) "set ${p.firstSet}" else "${p.setCount} sets (${p.firstSet}–${p.lastSet})",
            fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
        )
        if (note != null) Text(note, fontSize = 14.sp, color = Bad, modifier = Modifier.padding(top = 8.dp))
        if (button != null) SecondaryButton(button, onClick, Modifier.fillMaxWidth().padding(top = 12.dp), color = Brand)
    }
}

// ── Step 2: the OMR sheets ──────────────────────────────────

/** The OMR sheet as it prints, drawn in the background whenever its layout changes. */
@Composable
fun rememberSheetPreview(layout: Layout, widthPx: Int = 900): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, layout) {
        value = withContext(Dispatchers.Default) { SheetPdf.preview(layout, widthPx).asImageBitmap() }
    }
    return image
}

/** A page of A4 on a grey tray, with the sheet on it once it's drawn. */
@Composable
fun SheetPicture(image: ImageBitmap?, widthFraction: Float = 0.78f) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(InsetBg).padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(widthFraction).aspectRatio(1000f / 1414f).shadow(3.dp).background(Color.White)) {
            image?.let { Image(it, contentDescription = "How the OMR sheet looks", modifier = Modifier.fillMaxSize()) }
        }
    }
}

@Composable
fun PrintScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    val ctx = LocalContext.current
    PrintContent(
        b, rememberSheetPreview(b.exam.layout()),
        onBack = { nav.popBackStack() },
        onChange = { vm.updateExam(it) },
        onPrint = { vm.printSheets(ctx, b) },
        onShare = { vm.shareSheets(b) },
    )
}

private fun ExamEntity.languageKey() = when (labels) { "BANGLA" -> "bn"; "123" -> "num"; else -> "en" }

@Composable
fun PrintContent(b: ExamBundle, preview: ImageBitmap?, onBack: () -> Unit, onChange: (ExamEntity) -> Unit, onPrint: () -> Unit, onShare: () -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) }
    val e = b.exam
    val locked = b.sheets.isNotEmpty()
    // a Lecturer.bd paper decides how many questions there are, each with its 4 choices
    val byPaper = b.fromLecturer
    val labels = e.layout().optionLabels()
    fun fits(x: ExamEntity) = SheetLayout.fits(x.layout())

    Screen(
        title = "OMR sheets", subtitle = e.title, onBack = onBack,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Share", onShare, Modifier.weight(1f), icon = Icons.Outlined.Share)
                PrimaryButton("Print sheets", onPrint, Modifier.weight(1.6f), icon = Icons.Outlined.Print)
            }
        },
    ) {
        item { SheetPicture(preview) }
        item { Hint("One A4 OMR sheet for each of the ${plural(b.total, "student")}. Print at actual size (100%), not “fit to page”.") }
        if (locked) item { Banner("Sheets were scanned on this layout, so its questions, choices, roll and set code stay as they are.", Tone.NEUTRAL) }
        if (!fits(e)) item { Banner("The bubbles on this sheet come out small. Fewer questions or a shorter roll number would make them easier to fill and to scan.") }
        item {
            AppCard {
                SettingRow("Questions", "${e.questions}", if (locked || byPaper) null else { { dialog = "questions" } }, locked = locked || byPaper)
                RowDivider()
                SettingRow("Choices per question", "${e.options} · ${labels.joinToString(" ")}", if (locked || byPaper) null else { { dialog = "options" } }, locked = locked || byPaper)
                RowDivider()
                SettingRow("Language", (if (e.language == "Bangla") "বাংলা" else "English") + " · " + labels.joinToString(" "), { dialog = "language" })
                RowDivider()
                SettingRow("Roll number", if (e.rollDigits > 0) plural(e.rollDigits, "digit") else "None", if (locked) null else { { dialog = "roll" } }, locked = locked)
                RowDivider()
                SwitchRow(
                    "Set code", if (e.setCode) "A 3-digit set code on each sheet" else "Off: everyone has the same paper", e.setCode,
                    { on ->
                        when {
                            on && !fits(e.copy(setCode = true)) -> dialog = "setcode-room"
                            !on && b.keyRows.count { it.answers.split(",").any { a -> a.trim() != "-1" } } > 1 -> dialog = "setcode"
                            else -> onChange(e.copy(setCode = on))
                        }
                    },
                    enabled = !locked,
                )
                RowDivider()
                SwitchRow("Name and class lines", "For students to write on", e.nameFields, { onChange(e.copy(nameFields = it)) })
                RowDivider()
                SettingRow("School name", e.institution.ifBlank { "Not set" }, { dialog = "school" })
            }
        }
        if (byPaper && !locked) item { Hint("The number of questions and choices come from the question paper on Lecturer.bd.") }
    }

    when (dialog) {
        "questions" -> TextDialog(
            "Questions", "${e.questions}", { onChange(e.copy(questions = it.toInt())) }, { dialog = null },
            label = "Number of questions", number = true,
            check = {
                val n = it.toIntOrNull() ?: 0
                when {
                    n !in 1..100 -> "From 1 to 100"
                    !fits(e.copy(questions = n)) -> "At most ${SheetLayout.maxQuestions(e.copy(questions = n).layout())} fit on one sheet with ${plural(e.options, "choice")} each"
                    else -> null
                }
            },
        )
        "options" -> {
            val room = (2..6).filter { fits(e.copy(options = it)) }
            ChoiceDialog(
                "Choices per question", room.map { it to "$it · ${labelsFor(e.labels, it).joinToString(" ")}" }, e.options, { onChange(e.copy(options = it)) }, { dialog = null },
                note = if (room.size < 5) "More choices don't fit on one sheet with ${plural(e.questions, "question")}." else null,
            )
        }
        "language" -> ChoiceDialog(
            "Language",
            listOf("en" to "English · A B C D", "bn" to "বাংলা · ক খ গ ঘ", "num" to "English · 1 2 3 4"),
            e.languageKey(),
            {
                onChange(
                    when (it) {
                        "bn" -> e.copy(language = "Bangla", labels = "BANGLA")
                        "num" -> e.copy(language = "English", labels = "123")
                        else -> e.copy(language = "English", labels = "ABC")
                    },
                )
            },
            { dialog = null },
        )
        "roll" -> {
            val min = maxOf(1, b.total.toString().length, b.students.maxOfOrNull { it.roll.trimStart('0').length } ?: 1)
            val room = (min..8).filter { fits(e.copy(rollDigits = it)) }
            ChoiceDialog(
                "Roll number",
                listOf(0 to "None (I'll match names myself)") + room.map { it to "${plural(it, "digit")} · rolls up to ${"9".repeat(it)}" },
                e.rollDigits, { onChange(e.copy(rollDigits = it)) }, { dialog = null },
                note = if (min <= 8 && room.size < 9 - min) "Longer rolls don't fit on one sheet with ${plural(e.questions, "question")}." else null,
            )
        }
        "setcode" -> ConfirmDialog(
            "Turn off set codes?", "Set ${b.sets.firstOrNull() ?: "101"}'s answers stay as the answer key; the other sets' answers are removed.",
            "Turn off", { onChange(e.copy(setCode = false)) }, { dialog = null },
        )
        "setcode-room" -> InfoDialog(
            "No room for a set code",
            "With ${plural(e.questions, "question")} of ${plural(e.options, "choice")} each, the set-code boxes would make the bubbles too small. Use fewer questions or choices first.",
        ) { dialog = null }
        "school" -> TextDialog("School name", e.institution, { onChange(e.copy(institution = it)) }, { dialog = null }, note = "Printed at the top of the sheet.")
    }
}
