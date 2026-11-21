package com.omrscanner.app.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.omrscanner.app.data.ExamBundle
import com.omrscanner.app.data.db.ExamEntity
import com.omrscanner.app.data.db.SheetEntity
import com.omrscanner.app.omr.Layout
import com.omrscanner.app.omr.SheetLayout
import com.omrscanner.app.omr.labelsFor
import com.omrscanner.app.omr.layout
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.Banner
import com.omrscanner.app.ui.components.ChoiceDialog
import com.omrscanner.app.ui.components.Chip
import com.omrscanner.app.ui.components.ConfirmDialog
import com.omrscanner.app.ui.components.EmptyState
import com.omrscanner.app.ui.components.Field
import com.omrscanner.app.ui.components.Hint
import com.omrscanner.app.ui.components.Pill
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.Screen
import com.omrscanner.app.ui.components.SecondaryButton
import com.omrscanner.app.ui.components.SectionTitle
import com.omrscanner.app.ui.components.SettingRow
import com.omrscanner.app.ui.components.StepRow
import com.omrscanner.app.ui.components.StepState
import com.omrscanner.app.ui.components.TextDialog
import com.omrscanner.app.ui.components.Tone
import com.omrscanner.app.ui.components.plural
import com.omrscanner.app.ui.components.text
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.Brand
import com.omrscanner.app.ui.theme.BrandSoft
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.InsetBg
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Warn

@Composable
fun rememberBundle(vm: AppViewModel, id: Long): ExamBundle? {
    val all by vm.bundles.collectAsStateWithLifecycle()
    return all.firstOrNull { it.exam.id == id }
}

/** "30 questions · 28 students · 4 sets" */
fun ExamBundle.meta(): String = buildList {
    add(plural(n, "question"))
    add(plural(total, "student"))
    if (exam.setCode) add(plural(sets.size, "set"))
}.joinToString(" · ")

/** How an exam stands, in a few words: what its card on Home says. */
fun ExamBundle.status(): Pair<String, Tone> = when {
    noKey -> "Add the answer key" to Tone.WARN
    keyGaps.isNotEmpty() -> "Answer key not finished" to Tone.WARN
    reviewCount > 0 -> "${plural(reviewCount, "sheet")} to check" to Tone.WARN
    sheets.isEmpty() -> "Ready to scan" to Tone.NEUTRAL
    fromLecturer && toUpload.isNotEmpty() -> "${toUpload.size} not sent to Lecturer.bd yet" to Tone.WARN
    else -> "${plural(sheets.size, "sheet")} scanned · average $average" to Tone.GOOD
}

// ── Home: the exams ─────────────────────────────────────────

@Composable
fun ExamsScreen(vm: AppViewModel, nav: NavController) {
    val bundles by vm.bundles.collectAsStateWithLifecycle()
    ExamsContent(
        bundles,
        onOpen = { nav.navigate(Routes.exam(it)) },
        onNew = { nav.navigate(Routes.NEW_EXAM) },
        onSettings = { nav.navigate(Routes.SETTINGS) },
    )
}

@Composable
fun ExamsContent(bundles: List<ExamBundle>, onOpen: (Long) -> Unit, onNew: () -> Unit, onSettings: () -> Unit) {
    Screen(
        title = "Exams",
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Ink) } },
        bottom = { PrimaryButton("New exam", onNew, icon = Icons.Outlined.Add) },
    ) {
        if (bundles.isEmpty()) item {
            EmptyState(Icons.Outlined.Quiz, "No exams yet", "Make an exam, print its OMR sheets, then scan them after the test.")
        }
        items(bundles, key = { it.exam.id }) { b -> ExamCard(b) { onOpen(b.exam.id) } }
    }
}

@Composable
private fun ExamCard(b: ExamBundle, onClick: () -> Unit) {
    val (status, tone) = b.status()
    AppCard(onClick = onClick, padding = 16.dp) {
        Text(b.exam.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(b.meta(), fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (tone == Tone.NEUTRAL) Muted else tone.text()))
            Text(status, color = if (tone == Tone.NEUTRAL) Ink else tone.text(), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp))
            if (b.fromLecturer) Pill("Lecturer.bd", Tone.NEUTRAL)
        }
        if (b.sheets.isNotEmpty() && b.total > 0) {
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(InsetBg)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((b.sheets.size.toFloat() / b.total).coerceIn(0f, 1f)).background(Brand))
            }
        }
    }
}

// ── New exam ────────────────────────────────────────────────

@Composable
fun NewExamScreen(vm: AppViewModel, nav: NavController) {
    NewExamContent(
        signedIn = vm.settings.lecturerSignedIn,
        school = vm.settings.schoolName,
        onBack = { nav.popBackStack() },
        onLecturer = { nav.navigate(if (vm.settings.lecturerSignedIn) Routes.LECTURER_PAPERS else Routes.SETTINGS) },
        onCreate = { title, questions, options, labels, students, sets ->
            vm.createExam(title, questions, options, labels, students, sets) { id -> nav.popBackStack(); nav.navigate(Routes.exam(id)) }
        },
    )
}

/**
 * A new exam, and with it its OMR sheet: how many questions, how many choices each and their letters,
 * how many students and sets. The sheet's picture follows every choice. [preview] draws it (in the
 * background in the app; at once in the screen-picture tests).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewExamContent(
    signedIn: Boolean, school: String, onBack: () -> Unit, onLecturer: () -> Unit,
    onCreate: (title: String, questions: Int, options: Int, labels: String, students: Int, sets: Int) -> Unit,
    preview: @Composable (Layout) -> ImageBitmap? = { rememberSheetPreview(it, 700) },
) {
    var title by rememberSaveable { mutableStateOf("") }
    var questions by rememberSaveable { mutableStateOf("30") }
    var options by rememberSaveable { mutableIntStateOf(4) }
    var letters by rememberSaveable { mutableStateOf("ABC") }
    var students by rememberSaveable { mutableStateOf("30") }
    var sets by rememberSaveable { mutableIntStateOf(1) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val q = questions.toIntOrNull()
    val s = students.toIntOrNull()
    // the sheet these choices make (with a stand-in number while one is being typed)
    val layout = ExamEntity.draft(title.trim(), (q ?: 30).coerceIn(1, 100), options, letters, (s ?: 30).coerceIn(1, 999), sets, school).layout()
    val titleError = if (tried && title.isBlank()) "Give the exam a name" else null
    val qError = when {
        q == null || q !in 1..100 -> "From 1 to 100"
        !SheetLayout.fits(layout) -> "At most ${SheetLayout.maxQuestions(layout)} fit on one sheet with ${plural(options, "choice")} each"
        else -> null
    }
    val sError = if (s == null || s !in 1..999) "From 1 to 999" else null

    Screen(
        title = "New exam", onBack = onBack,
        bottom = {
            PrimaryButton("Create exam", {
                tried = true
                if (title.isNotBlank() && qError == null && sError == null) onCreate(title.trim(), q!!, options, letters, s!!, sets)
            })
        },
    ) {
        item {
            AppCard(onClick = onLecturer, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(BrandSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Brand)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("From Lecturer.bd", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            if (signedIn) "Pick a question paper. Its answer keys and students come with it." else "Sign in first, then pick one of your question papers.",
                            fontSize = 14.sp, color = Muted,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Muted)
                }
            }
        }
        item { SectionTitle("Or make one here") }
        item { Field(title, { title = it }, "Exam name", error = titleError) }
        item {
            Field(questions, { questions = it }, "Number of questions (MCQ)", number = true, error = if (tried || questions.isNotEmpty()) qError else null)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 25, 30, 50, 100).forEach { v -> Chip("$v", q == v, { questions = "$v" }) }
            }
        }
        item {
            FormLabel("Choices per question")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..6).forEach { v -> Chip("$v", options == v, { options = v }, Modifier.weight(1f)) }
            }
        }
        item {
            FormLabel("Letters in the bubbles", "বাংলা letters print the whole sheet in Bangla.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ABC", "BANGLA", "123").forEach { l -> Chip(labelsFor(l, options).joinToString(" "), letters == l, { letters = l }) }
            }
        }
        item {
            Field(
                students, { students = it }, "Number of students", number = true,
                error = if (tried || students.isNotEmpty()) sError else null,
                hint = "Their rolls are 1, 2, 3… You can add names later.",
            )
        }
        item {
            FormLabel("Question sets", "Different versions of the paper, each with its own answer key.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { v -> Chip("$v", sets == v, { sets = v }, Modifier.weight(1f)) }
            }
        }
        item { SectionTitle("Your OMR sheet") }
        item { SheetPicture(preview(layout), widthFraction = 0.7f) }
        item { Hint("You can still change it before printing, in the exam's OMR sheets step.") }
    }
}

/** A heading over a row of choices, with an optional line under it. */
@Composable
private fun FormLabel(text: String, hint: String? = null) {
    Text(text, fontSize = 16.sp, color = Ink, modifier = Modifier.padding(start = 4.dp, bottom = if (hint == null) 8.dp else 0.dp))
    if (hint != null) Hint(hint, Modifier.padding(top = 2.dp, bottom = 8.dp))
}

// ── One exam: three steps, then its results ─────────────────

@Composable
fun ExamScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    var uploading by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    ExamContent(
        b, signedIn = vm.settings.lecturerSignedIn, uploading = uploading,
        onBack = { nav.popBackStack() },
        onSettings = { nav.navigate(Routes.examSettings(id)) },
        onKey = { nav.navigate(Routes.key(id)) },
        onPrint = { nav.navigate(Routes.print(id)) },
        onScan = { nav.navigate(Routes.scan(id)) },
        onSheet = { nav.navigate(Routes.review(id, it)) },
        onAnalysis = { nav.navigate(Routes.analysis(id)) },
        onShare = { vm.shareResults(b) },
        onUpload = { if (!uploading) { uploading = true; vm.uploadResults(b) { s -> uploading = false; summary = s } } },
        onSignIn = { nav.navigate(Routes.SETTINGS) },
    )
    summary?.let { text ->
        AlertDialog(
            onDismissRequest = { summary = null },
            title = { Text("Lecturer.bd") },
            text = { Text(text, fontSize = 15.sp) },
            confirmButton = { TextButton(onClick = { summary = null }) { Text("OK", color = Brand, fontWeight = FontWeight.SemiBold) } },
        )
    }
}

@Composable
fun ExamContent(
    b: ExamBundle, signedIn: Boolean, uploading: Boolean,
    onBack: () -> Unit, onSettings: () -> Unit, onKey: () -> Unit, onPrint: () -> Unit, onScan: () -> Unit,
    onSheet: (Long) -> Unit, onAnalysis: () -> Unit, onShare: () -> Unit, onUpload: () -> Unit, onSignIn: () -> Unit,
) {
    var showMissing by rememberSaveable { mutableStateOf(false) }
    val keyReady = !b.noKey && b.keyGaps.isEmpty()
    val keyDetail = when {
        b.noKey -> "Not added yet"
        b.keyGaps.isNotEmpty() -> if (b.exam.setCode) "${plural(b.keyGaps.size, "set")} not finished" else "${b.keyDone(b.sets.first())} of ${b.n} answered"
        b.exam.setCode -> "Done for all ${plural(b.sets.size, "set")}"
        else -> "Done"
    }
    val scanDetail = if (b.sheets.isEmpty()) "After the exam, scan the filled sheets" else "${b.sheets.size} of ${b.total} scanned"
    val toCheck = b.sheets.sortedBy { it.scannedAt }.firstOrNull { b.needsCheck(it) }

    Screen(
        title = b.exam.title, subtitle = b.meta(), onBack = onBack,
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Exam settings", tint = Ink) } },
        bottom = {
            if (b.noKey) PrimaryButton("Add the answer key", onKey, icon = Icons.Outlined.Key)
            else PrimaryButton(if (b.sheets.isEmpty()) "Scan sheets" else "Scan more sheets", onScan, icon = Icons.Outlined.PhotoCamera)
        },
    ) {
        item {
            AppCard {
                StepRow(1, "Answer key", keyDetail, if (keyReady) StepState.DONE else StepState.TODO, onKey)
                RowDivider()
                StepRow(2, "OMR sheets", "Make and print one for each student", StepState.PLAIN, onPrint)
                RowDivider()
                StepRow(3, "Scan", scanDetail, if (b.sheets.isNotEmpty() && b.left == 0) StepState.DONE else StepState.PLAIN, onScan)
            }
        }
        if (b.fromLecturer) item { LecturerCard(b, signedIn, uploading, onUpload, onSignIn) }

        item { SectionTitle("Results", action = if (b.sheets.isNotEmpty()) "Share" else null, onAction = onShare) }
        if (b.sheets.isEmpty()) {
            item { Hint("Scanned sheets show up here, with each student's marks.") }
        } else {
            item { SummaryCard(b, onAnalysis) }
            if (toCheck != null) item {
                Banner(
                    "${plural(b.reviewCount, "sheet")} need${if (b.reviewCount == 1) "s" else ""} a look before the marks are final.",
                    action = "Check", onAction = { onSheet(toCheck.id) },
                )
            }
            item { ResultsCard(b, onSheet) }
            if (b.unmatched.isNotEmpty()) item { NotScannedCard(b, showMissing) { showMissing = !showMissing } }
        }
    }
}

@Composable
private fun SummaryCard(b: ExamBundle, onAnalysis: () -> Unit) {
    val scores = b.scores
    AppCard {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Stat(b.average, "Average", Modifier.weight(1f))
            Stat(scores.maxOrNull()?.let { "$it/${b.n}" } ?: "—", "Highest", Modifier.weight(1f))
            Stat(scores.minOrNull()?.let { "$it/${b.n}" } ?: "—", "Lowest", Modifier.weight(1f))
        }
        RowDivider(0.dp)
        SettingRow("Question analysis", "Which questions were hardest", onAnalysis)
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
        Text(label, fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun ResultsCard(b: ExamBundle, onSheet: (Long) -> Unit) {
    val rows = remember(b) { b.sheets.sortedWith(compareByDescending<SheetEntity> { b.needsCheck(it) }.thenBy { it.roll }) }
    AppCard {
        rows.forEachIndexed { i, s ->
            if (i > 0) RowDivider()
            ResultRow(b, s) { onSheet(s.id) }
        }
    }
}

@Composable
private fun ResultRow(b: ExamBundle, s: SheetEntity, onClick: () -> Unit) {
    val known = b.student(s.roll) != null
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(b.nameOf(s), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (known) Ink else Warn, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Roll ${s.roll.ifBlank { "—" }}" + if (b.exam.setCode) " · Set ${s.setCode}" else "", fontSize = 14.sp, color = Muted)
        }
        if (b.needsCheck(s)) Pill(b.flagText(s), Tone.WARN, Modifier.padding(horizontal = 8.dp))
        Text(b.score(s)?.let { "$it/${b.n}" } ?: "—", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun NotScannedCard(b: ExamBundle, open: Boolean, onToggle: () -> Unit) {
    AppCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Not scanned yet", fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f))
            Text("${b.unmatched.size}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Hide" else "Show", tint = Muted, modifier = Modifier.padding(start = 8.dp))
        }
        if (open) b.unmatched.forEach { st ->
            RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(st.name, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Roll ${st.roll}", fontSize = 14.sp, color = Muted)
            }
        }
    }
}

/** On an exam from a Lecturer.bd paper: how many results are there, and sending the rest. */
@Composable
private fun LecturerCard(b: ExamBundle, signedIn: Boolean, uploading: Boolean, onUpload: () -> Unit, onSignIn: () -> Unit) {
    val ready = b.toUpload.size
    AppCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(BrandSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Brand)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Lecturer.bd", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    when {
                        b.sheets.isEmpty() -> "The results go to this paper on Lecturer.bd."
                        ready == 0 && b.reviewCount == 0 -> "All ${plural(b.uploadedCount, "result")} sent."
                        ready == 0 -> "${b.uploadedCount} sent. Check the rest first."
                        else -> "${b.uploadedCount} of ${b.sheets.size} sent."
                    },
                    fontSize = 14.sp, color = Muted,
                )
            }
        }
        when {
            b.sheets.isEmpty() -> {}
            !signedIn -> SecondaryButton("Sign in to send results", onSignIn, Modifier.fillMaxWidth().padding(top = 12.dp), color = Brand)
            ready > 0 -> SecondaryButton(
                if (uploading) "Sending…" else "Send ${plural(ready, "result")}", onUpload,
                Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !uploading, icon = Icons.Outlined.CloudUpload, color = Brand,
            )
        }
    }
}

// ── Exam settings: name, class, students, marking, delete ───

@Composable
fun ExamSettingsScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    ExamSettingsContent(
        b,
        onBack = { nav.popBackStack() },
        onSave = { vm.updateExam(it) },
        onStudents = { nav.navigate(Routes.students(id)) },
        onDelete = { vm.deleteExam(id); nav.popBackStack(Routes.HOME, false) },
    )
}

@Composable
fun ExamSettingsContent(b: ExamBundle, onBack: () -> Unit, onSave: (ExamEntity) -> Unit, onStudents: () -> Unit, onDelete: () -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) }
    Screen(title = "Exam settings", subtitle = b.exam.title, onBack = onBack) {
        item {
            AppCard {
                SettingRow("Exam name", b.exam.title, { dialog = "name" })
                RowDivider()
                SettingRow("Class", b.exam.className.ifBlank { "Not set" }, { dialog = "class" })
                RowDivider()
                SettingRow("Students", plural(b.total, "student") + if (b.fromLecturer) " · from Lecturer.bd" else " · add or change names", onStudents)
            }
        }
        item { SectionTitle("Marking") }
        item {
            AppCard {
                SettingRow(
                    "Two answers on one question",
                    if (b.exam.doubleRule == "wrong") "Counted wrong, as in board exams" else "I choose on each sheet",
                    { dialog = "double" },
                )
            }
        }
        item { Hint("A much lighter second mark counts as rubbed out, so the darker one is marked. A question left blank gets 0.") }
        item {
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Delete exam", { dialog = "delete" }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Delete, color = Bad)
        }
    }
    when (dialog) {
        "name" -> TextDialog("Exam name", b.exam.title, { onSave(b.exam.copy(title = it)) }, { dialog = null }, note = "It's printed on the OMR sheets.", check = { if (it.isBlank()) "Give the exam a name" else null })
        "class" -> TextDialog("Class", b.exam.className, { onSave(b.exam.copy(className = it)) }, { dialog = null }, label = "For example: Class 10, Section B")
        "double" -> ChoiceDialog(
            "Two answers on one question",
            listOf("wrong" to "Count it wrong (board rule)", "review" to "Let me choose on each sheet"),
            b.exam.doubleRule, { onSave(b.exam.copy(doubleRule = it)) }, { dialog = null },
        )
        "delete" -> ConfirmDialog(
            "Delete this exam?",
            "Its answer keys, students and ${plural(b.sheets.size, "scanned sheet")} will be removed from this phone. This can't be undone.",
            "Delete", onDelete, { dialog = null },
        )
    }
}

// ── Students ────────────────────────────────────────────────

@Composable
fun StudentsScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val b = rememberBundle(vm, id) ?: return
    if (b.fromLecturer) {
        Screen(title = "Students", subtitle = plural(b.total, "student"), onBack = { nav.popBackStack() }) {
            item { Hint("These came with the paper from Lecturer.bd, with the rolls their sheets are read by.") }
            item {
                AppCard {
                    b.students.forEachIndexed { i, st ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(st.name, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Roll ${st.roll}", fontSize = 14.sp, color = Muted)
                        }
                    }
                }
            }
        }
        return
    }
    var text by rememberSaveable(b.exam.id) { mutableStateOf(b.students.joinToString("\n") { it.name }) }
    val names = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    Screen(
        title = "Students", subtitle = plural(names.size, "student"), onBack = { nav.popBackStack() },
        bottom = {
            PrimaryButton("Save", {
                if (names.isEmpty()) vm.toast("Add at least one student") else { vm.setStudents(b, names); nav.popBackStack() }
            })
        },
    ) {
        item { Hint("One name per line, in roll order: the first line is roll 1, the next is roll 2, and so on.") }
        item { Field(text, { text = it }, "Names", singleLine = false, minHeight = 320.dp) }
        if (b.sheets.isNotEmpty()) item { Banner("Sheets already scanned keep the roll they were read with.", Tone.WARN) }
    }
}
