package com.omrscanner.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.omrscanner.app.data.bank.PaperSummary
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.BackBar
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.SectionLabel
import com.omrscanner.app.ui.components.Segmented
import com.omrscanner.app.ui.components.Stepper
import com.omrscanner.app.ui.theme.Faint
import com.omrscanner.app.ui.theme.Good
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Mono
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.SegBg
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val s = vm.settings
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)) {
        item { Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp) }
        item { SectionLabel("OMR scanner") }
        item {
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text("Capture", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(if (s.autoCapture) "Captures on its own once the 4 corners are steady." else "You press the shutter when the corners turn green.", fontSize = 12.sp, color = Muted)
                    Segmented(listOf(true to "Automatic", false to "Shutter button"), s.autoCapture, { s.autoCapture = it }, Modifier.padding(top = 10.dp))
                }
                RowDivider()
                SettingStepper("Maximum tilt", "Steeper photos are refused and the teacher is asked to hold the phone flatter.", "${s.maxTilt}°", { s.maxTilt -= 5 }, { s.maxTilt += 5 })
                RowDivider()
                SettingStepper("Erased-mark threshold", "A second mark lighter than this share of the darker one counts as erased.", "${s.erasePct}%", { s.erasePct -= 5 }, { s.erasePct += 5 })
            }
        }
        item { SectionLabel("Question bank") }
        item {
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 10.dp).clip(RoundedCornerShape(5.dp)).background(Good).padding(5.dp))
                        Text(if (s.bankUrl.isBlank()) "Using the built-in sample bank" else "Connected to your server", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    }
                    var url by rememberSaveable { mutableStateOf(s.bankUrl) }
                    OutlinedTextField(url, { url = it; s.bankUrl = it }, label = { Text("Server URL (optional)") }, placeholder = { Text("https://school.example/api/") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    Text("Answer keys are fetched from GET {url}/papers and {url}/papers/{id}. See README.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
                RowDivider()
                Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.BANK) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Browse question bank", fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("›", color = Faint, fontSize = 18.sp)
                }
            }
        }
        item { SectionLabel("School") }
        item {
            AppCard(padding = PaddingValues(14.dp)) {
                var school by rememberSaveable { mutableStateOf(s.schoolName) }
                Text("Printed on new answer sheets", fontSize = 12.sp, color = Muted)
                OutlinedTextField(school, { school = it; s.schoolName = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            Text("OMR Scanner · version 1.0", fontSize = 12.sp, color = Faint, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun SettingStepper(title: String, desc: String, value: String, dec: () -> Unit, inc: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(desc, fontSize = 12.sp, color = Muted)
        }
        Stepper(value, dec, inc)
    }
}

@Composable
fun QuestionBankScreen(vm: AppViewModel, nav: NavController) {
    val exams by vm.bundles.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val results by produceState<List<PaperSummary>?>(null, q) { delay(250); value = runCatching { vm.searchBank(q) }.getOrElse { vm.toast("Question bank unreachable: ${it.message}"); emptyList() } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar("Settings", { nav.popBackStack() }) }
        item {
            Text("Question bank", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(q, { q = it }, label = { Text("Search by title, subject or code") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        }
        val list = results
        if (list == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) } }
        else items(list) { p ->
            val match = exams.firstOrNull { it.n == p.questions && it.exam.options == p.options }
            AppCard(Modifier.padding(bottom = 10.dp).clickable { match?.let { nav.navigate(Routes.key(it.exam.id)) } ?: vm.toast("No exam uses a ${p.questions} × ${p.options} sheet yet") }, padding = PaddingValues(14.dp)) {
                Row {
                    Text(p.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(p.code, fontFamily = Mono, fontSize = 11.sp, color = Muted, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SegBg).padding(horizontal = 6.dp, vertical = 3.dp))
                }
                Text("${p.questions} Q · ${p.options} options · ${p.setCount} set${if (p.setCount > 1) "s" else ""} (${p.firstSet}–${p.lastSet})", fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 6.dp)) {
                    Text("Updated ${p.updated} · ${p.author}", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(match?.let { "Use for ${it.exam.title.substringBefore(" · ")} ›" } ?: "No matching exam", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (match != null) Ink else Faint)
                }
            }
        }
    }
}
