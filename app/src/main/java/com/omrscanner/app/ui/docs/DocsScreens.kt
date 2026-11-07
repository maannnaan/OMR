package com.omrscanner.app.ui.docs

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.omrscanner.app.data.db.DocPageEntity
import com.omrscanner.app.docs.PageSize
import com.omrscanner.app.docs.PdfQuality
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.BackBar
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.SecondaryButton
import com.omrscanner.app.ui.components.SectionLabel
import com.omrscanner.app.ui.components.Segmented
import com.omrscanner.app.ui.components.ToggleRow
import com.omrscanner.app.ui.theme.Accent
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.CardBg
import com.omrscanner.app.ui.theme.Faint
import com.omrscanner.app.ui.theme.Good
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Mono
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Paper
import com.omrscanner.app.ui.theme.SegBg
import com.omrscanner.app.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Documents tab ───────────────────────────────────────────

@Composable
fun DocsScreen(vm: AppViewModel, nav: NavController) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    var idHelp by remember { mutableStateOf(false) }
    val scan = rememberDocumentScanner(vm) { uris, id -> vm.importScan(uris, id, null) { docId -> nav.navigate(Routes.doc(docId)) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)) {
        item {
            Text("Documents", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
            Text("Scan paper or ID cards — cropped, straightened and ready to share as PDF.", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Scan document", "Pages, notes, forms", dark = true, Modifier.weight(1f)) { scan(false) }
                ActionTile("Scan ID card", "Front & back on one page", dark = false, Modifier.weight(1f)) { idHelp = true }
            }
        }
        item { SectionLabel("Recent · ${docs.size}") }
        item {
            AppCard {
                if (docs.isEmpty()) Text("No documents yet. Scan a page or an ID card to start.", color = Muted, modifier = Modifier.padding(24.dp))
                docs.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.doc(d.doc.id)) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(52.dp).height(74.dp).shadow(2.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))) {
                            d.pages.firstOrNull()?.let { PageThumb(vm, it, 240, Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(d.doc.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val date = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(d.doc.createdAt))
                            Text("${d.pages.size} page${if (d.pages.size == 1) "" else "s"} · $date", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
                        }
                        Text("PDF", fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Bad, modifier = Modifier.border(1.5.dp, Bad.copy(alpha = 0.35f), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                        Text("  ›", color = Faint, fontSize = 18.sp)
                    }
                }
            }
        }
    }
    if (idHelp) IdCardHelp({ idHelp = false }) { idHelp = false; scan(true) }
}

@Composable
private fun ActionTile(title: String, sub: String, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 120.dp).clip(RoundedCornerShape(20.dp)).background(if (dark) Ink else CardBg).border(1.dp, if (dark) Ink else Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(if (dark) 26.dp else 38.dp, 30.dp).border(2.5.dp, if (dark) Accent else Teal, RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(24.dp))
        Text(title, color = if (dark) Paper else Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(sub, color = if (dark) Faint else Muted, fontSize = 12.sp)
    }
}

@Composable
private fun IdCardHelp(dismiss: () -> Unit, start: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss,
    title = { Text("Scan an ID card") },
    text = { Text("Scan the front of the card, then turn it over and scan the back. Both sides are placed on one A4 page at real card size, so a printout works as a photocopy.") },
    confirmButton = { TextButton(start) { Text("Start", color = Ink, fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(dismiss) { Text("Cancel", color = Muted) } },
)

// ── Document editor: order, add pages, export ──────────────

@Composable
fun DocEditorScreen(vm: AppViewModel, nav: NavController, docId: Long) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val d = docs.firstOrNull { it.doc.id == docId } ?: return
    var title by remember(d.doc.id) { mutableStateOf(d.doc.title) }
    var exporting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var idHelp by remember { mutableStateOf(false) }
    val scan = rememberDocumentScanner(vm) { uris, id -> vm.importScan(uris, id, docId) {} }
    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in d.pages.indices) return
        val list = d.pages.toMutableList(); val t = list[i]; list[i] = list[j]; list[j] = t
        vm.reorder(list)
    }

    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f).padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    BackBar("Documents", { if (title != d.doc.title) vm.renameDoc(d.doc, title); nav.popBackStack() }) {
                        TextButton({ confirmDelete = true }) { Text("Delete", color = Bad) }
                    }
                    OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Document name") }, modifier = Modifier.fillMaxWidth())
                    Text("${d.pages.size} page${if (d.pages.size == 1) "" else "s"}", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondaryButton("+ Add pages", { scan(false) })
                        SecondaryButton("+ ID card", { idHelp = true })
                        SecondaryButton("⇅ Reverse", { vm.reorder(d.pages.reversed()); vm.toast("Page order reversed") })
                    }
                    Text("Tap a page to change its filter or rotate it. Use ‹ › to set the order — the PDF follows it.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
                }
            }
            itemsIndexed(d.pages, key = { _, p -> p.id }) { i, p ->
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(CardBg).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(10.dp)).background(SegBg).clickable { nav.navigate(Routes.page(docId, p.id)) }, contentAlignment = Alignment.Center) {
                        PageThumb(vm, p, 500, Modifier.padding(10.dp).fillMaxSize())
                        Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Paper, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OrderButton("‹", i > 0) { move(i, -1) }
                        Text(if (p.kind == "id") "ID · front & back" else "Page", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        OrderButton("›", i < d.pages.size - 1) { move(i, 1) }
                    }
                }
            }
        }
        Box(Modifier.background(Paper).padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton("Export PDF", { if (title != d.doc.title) vm.renameDoc(d.doc, title); exporting = true }, enabled = d.pages.isNotEmpty())
        }
    }

    if (exporting) ExportDialog(vm, d.pages, title) { exporting = false }
    if (idHelp) IdCardHelp({ idHelp = false }) { idHelp = false; scan(true) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${d.doc.title}”?") },
        text = { Text("All ${d.pages.size} pages will be removed from this phone.") },
        confirmButton = { TextButton({ confirmDelete = false; vm.deleteDoc(docId); nav.popBackStack() }) { Text("Delete", color = Bad) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = Muted) } },
    )
}

@Composable
private fun OrderButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Paper).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(symbol, fontSize = 16.sp, color = if (enabled) Ink else Line)
    }
}

@Composable
private fun ExportDialog(vm: AppViewModel, pages: List<DocPageEntity>, defaultName: String, close: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var size by rememberSaveable { mutableStateOf(PageSize.A4) }
    var quality by rememberSaveable { mutableStateOf(PdfQuality.STANDARD) }
    var numbers by rememberSaveable { mutableStateOf(true) }
    val estKb = pages.size * when (quality) { PdfQuality.SMALL -> 140; PdfQuality.STANDARD -> 320; PdfQuality.HIGH -> 760 }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Export PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("File name") }, singleLine = true, suffix = { Text(".pdf") })
                Text("Page size", fontSize = 12.sp, color = Muted)
                Segmented(PageSize.entries.map { it to it.label }, size, { size = it })
                Text("Quality", fontSize = 12.sp, color = Muted)
                Segmented(PdfQuality.entries.map { it to it.label }, quality, { quality = it })
                AppCard { ToggleRow("Page numbers", "Printed at the bottom of each page", numbers) { numbers = it } }
                Text("${pages.size} page${if (pages.size == 1) "" else "s"} · ${size.label} · about ${if (estKb > 1000) "%.1f MB".format(estKb / 1024f) else "$estKb KB"}", fontFamily = Mono, fontSize = 12.sp, color = Muted)
            }
        },
        confirmButton = {
            Row {
                TextButton({ vm.exportPdf(pages, name, size, quality, numbers, "save"); close() }) { Text("Save", color = Ink) }
                TextButton({ vm.exportPdf(pages, name, size, quality, numbers, "share"); close() }) { Text("Share", color = Ink, fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = { TextButton(close) { Text("Cancel", color = Muted) } },
    )
}

// ── Page editor: filter, rotate, delete ─────────────────────

@Composable
fun PageEditorScreen(vm: AppViewModel, nav: NavController, docId: Long, pageId: Long) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val d = docs.firstOrNull { it.doc.id == docId } ?: return
    val i = d.pages.indexOfFirst { it.id == pageId }
    val p = d.pages.getOrNull(i) ?: return
    fun open(j: Int) = nav.navigate(Routes.page(docId, d.pages[j].id)) { popUpTo(Routes.page(docId, pageId)) { inclusive = true } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            BackBar(d.doc.title.take(18), { nav.popBackStack() }) {
                TextButton({ if (i > 0) open(i - 1) }) { Text("‹", fontSize = 20.sp, color = if (i > 0) Ink else Line) }
                Text("Page ${i + 1} of ${d.pages.size}", fontFamily = Mono, fontSize = 13.sp, color = Muted)
                TextButton({ if (i < d.pages.size - 1) open(i + 1) }) { Text("›", fontSize = 20.sp, color = if (i < d.pages.size - 1) Ink else Line) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                PageThumb(vm, p, 1600, Modifier.fillMaxSize().shadow(6.dp))
            }
            Text(
                "✓ " + if (p.kind == "id") "Front and back placed on one page at real card size" else "Cropped, straightened and background removed by the scanner",
                color = Good, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("original" to "Original", "enhanced" to "Enhanced", "gray" to "Grayscale", "bw" to "B & W").forEach { (id, label) ->
                    Column(Modifier.weight(1f).clickable { vm.updatePage(p.copy(filter = id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).border(2.5.dp, if (p.filter == id) Ink else Line, RoundedCornerShape(6.dp))) {
                            PageThumb(vm, p.copy(filter = id, rotation = 0), 260, Modifier.fillMaxSize())
                        }
                        Text(label, fontSize = 12.sp, fontWeight = if (p.filter == id) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Row(Modifier.background(Paper).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("⟳ Rotate", { vm.updatePage(p.copy(rotation = (p.rotation + 90) % 360)) }, Modifier.weight(1f))
            SecondaryButton("Delete", {
                if (d.pages.size == 1) { vm.deleteDoc(docId); nav.popBackStack(Routes.DOCS, false) } else { vm.deletePage(p); nav.popBackStack() }
            }, Modifier.weight(1f), color = Bad)
            PrimaryButton("Done", { nav.popBackStack() }, Modifier.weight(1.3f), color = Ink, contentColor = Paper)
        }
    }
}

