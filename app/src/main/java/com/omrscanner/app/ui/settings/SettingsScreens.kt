package com.omrscanner.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.Routes
import com.omrscanner.app.ui.components.AppCard
import com.omrscanner.app.ui.components.ChoiceDialog
import com.omrscanner.app.ui.components.Field
import com.omrscanner.app.ui.components.Hint
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.components.RowDivider
import com.omrscanner.app.ui.components.Screen
import com.omrscanner.app.ui.components.SecondaryButton
import com.omrscanner.app.ui.components.SectionTitle
import com.omrscanner.app.ui.components.SettingRow
import com.omrscanner.app.ui.components.SwitchRow
import com.omrscanner.app.ui.components.TextDialog
import com.omrscanner.app.ui.components.fieldColors
import com.omrscanner.app.ui.theme.Brand
import com.omrscanner.app.ui.theme.BrandSoft
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Muted

/** Everything the Settings screen shows, so it can be drawn without the phone's stored settings. */
data class SettingsState(
    val signedIn: Boolean,
    val lecturerName: String,
    val lecturerUrl: String,
    val school: String,
    val autoCapture: Boolean,
    val maxTilt: Int,
    val erasePct: Int,
    val bankUrl: String,
    val version: String,
)

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val s = vm.settings
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    var signingIn by remember { mutableStateOf(false) }
    SettingsContent(
        SettingsState(s.lecturerSignedIn, s.lecturerName, s.lecturerUrl, s.schoolName, s.autoCapture, s.maxTilt, s.erasePct, s.bankUrl, version),
        signingIn = signingIn,
        onBack = { nav.popBackStack() },
        onSignIn = { id, password, done -> signingIn = true; vm.lecturerSignIn(id, password) { ok -> signingIn = false; done(ok) } },
        onSignOut = { vm.lecturerSignOut() },
        onLecturerUrl = { s.lecturerUrl = it },
        onSchool = { s.schoolName = it },
        onAutoCapture = { s.autoCapture = it },
        onMaxTilt = { s.maxTilt = it },
        onErasePct = { s.erasePct = it },
        onBankUrl = { s.bankUrl = it },
        onDocs = { nav.navigate(Routes.DOCS) },
    )
}

@Composable
fun SettingsContent(
    state: SettingsState, signingIn: Boolean,
    onBack: () -> Unit, onSignIn: (identifier: String, password: String, done: (Boolean) -> Unit) -> Unit, onSignOut: () -> Unit,
    onLecturerUrl: (String) -> Unit, onSchool: (String) -> Unit, onAutoCapture: (Boolean) -> Unit,
    onMaxTilt: (Int) -> Unit, onErasePct: (Int) -> Unit, onBankUrl: (String) -> Unit, onDocs: () -> Unit,
) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    Screen(title = "Settings", onBack = onBack) {
        item { SectionTitle("Lecturer.bd") }
        item { LecturerAccount(state, signingIn, onSignIn, onSignOut) { dialog = "server" } }

        item { SectionTitle("Answer sheets") }
        item { AppCard { SettingRow("School name", state.school.ifBlank { "Not set" }, { dialog = "school" }) } }
        item { Hint("Printed at the top of new answer sheets.") }

        item { SectionTitle("Scanning") }
        item {
            AppCard {
                SwitchRow("Scan by itself", if (state.autoCapture) "Takes the photo as soon as the sheet is steady" else "You tap the round button for each sheet", state.autoCapture, onAutoCapture)
            }
        }

        item { SectionTitle("More") }
        item {
            AppCard {
                SettingRow("Scan documents", "Papers and ID cards, saved as PDF", onDocs)
                RowDivider()
                SettingRow("Advanced", if (advanced) "Hide" else "Scanner limits and your own question bank", { advanced = !advanced })
                if (advanced) {
                    RowDivider()
                    SettingRow("Steepest angle", "${state.maxTilt}°: steeper photos are refused", { dialog = "tilt" })
                    RowDivider()
                    SettingRow("Rubbed-out marks", "A mark lighter than ${state.erasePct}% of the darkest one is ignored", { dialog = "erase" })
                    if (!state.signedIn) {
                        RowDivider()
                        SettingRow("Question bank server", state.bankUrl.ifBlank { "Not set" }, { dialog = "bank" })
                    }
                }
            }
        }
        item {
            Text(
                "OMR Scanner ${state.version}", fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }

    when (dialog) {
        "server" -> TextDialog("Lecturer.bd address", state.lecturerUrl, onLecturerUrl, { dialog = null }, note = "Only change this if you were told to.")
        "school" -> TextDialog("School name", state.school, onSchool, { dialog = null }, note = "Printed at the top of new answer sheets.")
        "tilt" -> ChoiceDialog("Steepest angle", (10..40 step 5).map { it to "$it°" + if (it == 25) " (usual)" else "" }, state.maxTilt, onMaxTilt, { dialog = null }, note = "Photos taken at a steeper angle are refused, because the bubbles look squashed.")
        "erase" -> ChoiceDialog("Rubbed-out marks", (30..80 step 5).map { it to "$it%" + if (it == 55) " (usual)" else "" }, state.erasePct, onErasePct, { dialog = null }, note = "When a question has two marks and one is this much lighter, it counts as rubbed out.")
        "bank" -> TextDialog("Question bank server", state.bankUrl, onBankUrl, { dialog = null }, label = "https://…", note = "Your own server with answer keys (see the README). Leave empty if you don't have one.")
    }
}

/** Signing in to Lecturer.bd with the teacher's own mobile number and password, or out again. */
@Composable
private fun LecturerAccount(state: SettingsState, signingIn: Boolean, onSignIn: (String, String, (Boolean) -> Unit) -> Unit, onSignOut: () -> Unit, onServer: () -> Unit) {
    AppCard(padding = 16.dp) {
        if (state.signedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(BrandSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Brand)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Signed in", fontSize = 14.sp, color = Muted)
                    Text(state.lecturerName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }
            Text("Your question papers are under New exam → From Lecturer.bd.", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 12.dp))
            SecondaryButton("Sign out", onSignOut, Modifier.fillMaxWidth().padding(top = 12.dp))
        } else {
            var identifier by rememberSaveable { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            Text("Sign in with your Lecturer.bd mobile number and password to use your question papers and send results back.", fontSize = 15.sp, color = Muted)
            Field(identifier, { identifier = it }, "Mobile number or email", Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            PrimaryButton(
                if (signingIn) "Signing in…" else "Sign in",
                { if (identifier.isNotBlank() && password.isNotEmpty() && !signingIn) onSignIn(identifier, password) { ok -> if (ok) password = "" } },
                Modifier.padding(top = 16.dp), enabled = !signingIn && identifier.isNotBlank() && password.isNotEmpty(),
            )
            TextButton(onClick = onServer, modifier = Modifier.padding(top = 4.dp)) {
                Text("Server: ${state.lecturerUrl.removePrefix("https://")}", fontSize = 13.sp, color = Muted)
            }
        }
    }
}
