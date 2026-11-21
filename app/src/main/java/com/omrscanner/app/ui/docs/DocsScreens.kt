package com.omrscanner.app.ui.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
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
import com.omrscanner.app.ui.components.Chip
import com.omrscanner.app.ui.components.ConfirmDialog
import com.omrscanner.app.ui.components.EmptyState
import com.omrscanner.app.ui.components.Field
import com.omrscanner.app.ui.components.Hint
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.Screen
import com.omrscanner.app.ui.components.SecondaryButton
import com.omrscanner.app.ui.components.SwitchRow
import com.omrscanner.app.ui.components.TextDialog
import com.omrscanner.app.ui.components.TopBar
import com.omrscanner.app.ui.components.plural
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.Brand
import com.omrscanner.app.ui.theme.CardBg
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.InsetBg
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.PageBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A document scanner beside the OMR one: papers and ID cards, cropped and straightened, shared as PDF.

@Composable
fun DocsScreen(vm: AppViewModel, nav: NavController) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    var idHelp by remember { mutableStateOf(false) }
    val scan = rememberDocumentScanner(vm) { uris, id -> vm.importScan(uris, id, null) { docId -> nav.navigate(Routes.doc(docId)) } }
    Screen(
        title = "Documents", subtitle = "Papers and ID cards, as PDF", onBack = { nav.popBackStack() },
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("ID card", { idHelp = true }, Modifier.weight(1f), icon = Icons.Outlined.CreditCard)
                PrimaryButton("Scan pages", { scan(false) }, Modifier.weight(1.4f), icon = Icons.Outlined.DocumentScanner)
            }
        },
    ) {
        if (docs.isEmpty()) item { EmptyState(Icons.Outlined.Description, "No documents yet", "Scan a page, a form or an ID card. It's cropped and straightened, ready to share as a PDF.") }
        items(docs, key = { it.doc.id }) { d ->
            AppCard(onClick = { nav.navigate(Routes.doc(d.doc.id)) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(52.dp).height(72.dp).shadow(2.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))) {
                        d.pages.firstOrNull()?.let { PageThumb(vm, it, 240, Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(d.doc.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val date = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(d.doc.createdAt))
                        Text("${plural(d.pages.size, "page")} · $date", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Muted)
                }
            }
        }
    }
    if (idHelp) IdCardHelp({ idHelp = false }) { idHelp = false; scan(true) }
}

@Composable
private fun IdCardHelp(dismiss: () -> Unit, start: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss,
    title = { Text("Scan an ID card") },
    text = { Text("Scan the front of the card, then turn it over and scan the back. Both sides go on one A4 page at their real size, so a printout works as a photocopy.", fontSize = 15.sp) },
    confirmButton = { TextButton(onClick = start) { Text("Start", color = Brand, fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = dismiss) { Text("Cancel", color = Muted) } },
)

// ── One document: its pages in order, then a PDF ────────────

@Composable
fun DocEditorScreen(vm: AppViewModel, nav: NavController, docId: Long) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val d = docs.firstOrNull { it.doc.id == docId } ?: return
    var dialog by remember { mutableStateOf<String?>(null) }
    val scan = rememberDocumentScanner(vm) { uris, id -> vm.importScan(uris, id, docId) {} }
    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in d.pages.indices) return
        val list = d.pages.toMutableList(); val t = list[i]; list[i] = list[j]; list[j] = t
        vm.reorder(list)
    }

    Screen(
        title = d.doc.title, subtitle = plural(d.pages.size, "page"), onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete document", tint = Ink) } },
        bottom = { PrimaryButton("Make a PDF", { dialog = "export" }, enabled = d.pages.isNotEmpty(), icon = Icons.Outlined.PictureAsPdf) },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("+ Pages", false, { scan(false) })
                Chip("+ ID card", false, { dialog = "id" })
                Chip("Rename", false, { dialog = "rename" })
            }
        }
        item { Hint("Tap a page to clean it up or turn it. The arrows set the order the PDF follows.") }
        items(d.pages.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { p ->
                    val i = d.pages.indexOf(p)
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(CardBg).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(8.dp)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(10.dp)).background(InsetBg).clickable { nav.navigate(Routes.page(docId, p.id)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            PageThumb(vm, p, 500, Modifier.padding(10.dp).fillMaxSize())
                            Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = CardBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Earlier", tint = if (i > 0) Ink else Line) }
                            Text(if (p.kind == "id") "ID card" else "Page", fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            IconButton(onClick = { move(i, 1) }, enabled = i < d.pages.size - 1) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Later", tint = if (i < d.pages.size - 1) Ink else Line) }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (d.pages.size > 1) item {
            TextButton(onClick = { vm.reorder(d.pages.reversed()); vm.toast("Page order reversed") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = Brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reverse the order", color = Brand, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    when (dialog) {
        "export" -> ExportDialog(vm, d.pages, d.doc.title) { dialog = null }
        "id" -> IdCardHelp({ dialog = null }) { dialog = null; scan(true) }
        "rename" -> TextDialog("Document name", d.doc.title, { vm.renameDoc(d.doc, it.ifBlank { d.doc.title }) }, { dialog = null })
        "delete" -> ConfirmDialog(
            "Delete “${d.doc.title}”?", "Its ${plural(d.pages.size, "page")} will be removed from this phone.", "Delete",
            { vm.deleteDoc(docId); nav.popBackStack() }, { dialog = null },
        )
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
        title = { Text("Make a PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "File name")
                Text("Page size", fontSize = 14.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PageSize.entries.forEach { Chip(it.label, size == it, { size = it }) } }
                Text("Quality", fontSize = 14.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PdfQuality.entries.forEach { Chip(it.label, quality == it, { quality = it }) } }
                AppCard { SwitchRow("Page numbers", null, numbers, { numbers = it }) }
                Text(
                    "${plural(pages.size, "page")} · about ${if (estKb > 1000) "%.1f MB".format(estKb / 1024f) else "$estKb KB"}",
                    fontSize = 13.sp, color = Muted,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { vm.exportPdf(pages, name, size, quality, numbers, "save"); close() }) { Text("Save", color = Brand) }
                TextButton(onClick = { vm.exportPdf(pages, name, size, quality, numbers, "share"); close() }) { Text("Share", color = Brand, fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel", color = Muted) } },
    )
}

// ── One page: clean-up filter, turn, delete ─────────────────

@Composable
fun PageEditorScreen(vm: AppViewModel, nav: NavController, docId: Long, pageId: Long) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val d = docs.firstOrNull { it.doc.id == docId } ?: return
    val i = d.pages.indexOfFirst { it.id == pageId }
    val p = d.pages.getOrNull(i) ?: return
    fun open(j: Int) = nav.navigate(Routes.page(docId, d.pages[j].id)) { popUpTo(Routes.page(docId, pageId)) { inclusive = true } }

    Column(Modifier.fillMaxSize().background(PageBg).systemBarsPadding()) {
        TopBar("Page ${i + 1} of ${d.pages.size}", { nav.popBackStack() }, subtitle = d.doc.title) {
            IconButton(onClick = { if (i > 0) open(i - 1) }, enabled = i > 0) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous page", tint = if (i > 0) Ink else Line) }
            IconButton(onClick = { if (i < d.pages.size - 1) open(i + 1) }, enabled = i < d.pages.size - 1) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next page", tint = if (i < d.pages.size - 1) Ink else Line)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            PageThumb(vm, p, 1600, Modifier.fillMaxSize().shadow(6.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("original" to "Original", "enhanced" to "Clean", "gray" to "Grey", "bw" to "Black & white").forEach { (id, label) ->
                Column(Modifier.weight(1f).clickable { vm.updatePage(p.copy(filter = id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).border(if (p.filter == id) 3.dp else 1.dp, if (p.filter == id) Brand else Line, RoundedCornerShape(6.dp))) {
                        PageThumb(vm, p.copy(filter = id, rotation = 0), 260, Modifier.fillMaxSize())
                    }
                    Text(label, fontSize = 13.sp, color = if (p.filter == id) Brand else Ink, fontWeight = if (p.filter == id) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        RowDivider(0.dp)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Turn", { vm.updatePage(p.copy(rotation = (p.rotation + 90) % 360)) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.RotateRight)
            SecondaryButton("Delete", {
                if (d.pages.size == 1) { vm.deleteDoc(docId); nav.popBackStack(Routes.DOCS, false) } else { vm.deletePage(p); nav.popBackStack() }
            }, Modifier.weight(1f), color = Bad)
            PrimaryButton("Done", { nav.popBackStack() }, Modifier.weight(1f))
        }
    }
}
