package com.omrscanner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omrscanner.app.ui.theme.Bad
import com.omrscanner.app.ui.theme.BadSoft
import com.omrscanner.app.ui.theme.Brand
import com.omrscanner.app.ui.theme.BrandSoft
import com.omrscanner.app.ui.theme.CardBg
import com.omrscanner.app.ui.theme.FieldLine
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.InsetBg
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.PageBg
import com.omrscanner.app.ui.theme.Warn
import com.omrscanner.app.ui.theme.WarnSoft

// The few pieces every screen is built from. A screen is a title, a scrolling column of cards, and at
// most one big button at the bottom for the thing to do next.

/** How something is going: plain, done, needs the teacher, or wrong. */
enum class Tone { NEUTRAL, GOOD, WARN, BAD }

private fun Tone.colors(): Pair<Color, Color> = when (this) {
    Tone.NEUTRAL -> InsetBg to Ink
    Tone.GOOD -> BrandSoft to Brand
    Tone.WARN -> WarnSoft to Warn
    Tone.BAD -> BadSoft to Bad
}

fun Tone.text(): Color = when (this) {
    Tone.NEUTRAL -> Muted
    Tone.GOOD -> Brand
    Tone.WARN -> Warn
    Tone.BAD -> Bad
}

/** A whole screen: the top bar, the scrolling content, and the buttons pinned under it. */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PageBg).systemBarsPadding().imePadding()) {
        TopBar(title, onBack, subtitle, actions)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (bottom != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Column(Modifier.fillMaxWidth().background(PageBg).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = bottom)
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (onBack != null) 4.dp else 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink) }
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp, end = 8.dp)) {
            Text(title, fontSize = if (onBack == null) 28.sp else 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** A white card. Pass [onClick] to make the whole card one button. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(CardBg).border(1.dp, Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun RowDivider(inset: Dp = 16.dp) = Box(Modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(Line))

/** The one thing to do next on a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = Brand) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = Line, disabledContentColor = Muted),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = Ink) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, FieldLine),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CardBg, contentColor = color, disabledContentColor = Muted),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A heading between cards, with an optional link at its end. */
@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Brand, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

enum class StepState { DONE, TODO, PLAIN }

/** One numbered step of an exam: a circle (a tick once done), what it is, how it stands. */
@Composable
fun StepRow(number: Int, title: String, detail: String, state: StepState, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (bg, fg) = when (state) {
            StepState.DONE -> Brand to Color.White
            StepState.TODO -> WarnSoft to Warn
            StepState.PLAIN -> InsetBg to Ink
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (state == StepState.DONE) Text("✓", color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            else Text("$number", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(detail, fontSize = 14.sp, color = if (state == StepState.TODO) Warn else Muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Muted)
    }
}

/** A setting: its name and current value. Tapping it opens a small dialog to change it. */
@Composable
fun SettingRow(title: String, value: String? = null, onClick: (() -> Unit)? = null, locked: Boolean = false, valueColor: Color = Muted) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 16.sp, color = Ink)
            if (value != null) Text(value, fontSize = 14.sp, color = valueColor)
        }
        when {
            locked -> Icon(Icons.Outlined.Lock, contentDescription = "Can't change", tint = Muted, modifier = Modifier.size(18.dp))
            onClick != null -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, color = if (enabled) Ink else Muted)
            if (detail != null) Text(detail, fontSize = 14.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Brand, checkedThumbColor = Color.White, uncheckedTrackColor = InsetBg))
    }
}

/** A choice among a few, e.g. a filter or a count. Green when chosen. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier.clip(shape).background(if (selected) BrandSoft else CardBg).border(1.dp, if (selected) Brand else FieldLine, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 44.dp).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Brand else Ink, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1) }
}

/** A short label with a tinted background, e.g. "Check". */
@Composable
fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (bg, fg) = tone.colors()
    Box(modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A message across the screen, with an optional button at its end. */
@Composable
fun Banner(text: String, tone: Tone = Tone.WARN, action: String? = null, onAction: () -> Unit = {}) {
    val (bg, fg) = tone.colors()
    val icon = when (tone) { Tone.GOOD -> Icons.Outlined.CheckCircle; Tone.NEUTRAL -> Icons.Outlined.Info; else -> Icons.Outlined.WarningAmber }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Text(text, color = fg, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(BrandSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Brand, modifier = Modifier.size(36.dp))
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(body, fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Plain explanatory text between cards. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(text, fontSize = 14.sp, color = color, modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp))

/** A round answer bubble, as printed on the sheet. */
@Composable
fun Bubble(label: String, fill: Color, ring: Color, textColor: Color, size: Dp, onClick: (() -> Unit)?) {
    Box(
        Modifier.size(size).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand, unfocusedBorderColor = FieldLine, focusedLabelColor = Brand, cursorColor = Brand,
    focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
)

/** A text box. Numbers get the number keyboard and keep only digits. */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    number: Boolean = false, error: String? = null, hint: String? = null, singleLine: Boolean = true, minHeight: Dp = 0.dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (number) it.filter(Char::isDigit).take(4) else it) },
        label = { Text(label) },
        singleLine = singleLine,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        colors = fieldColors(),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight),
    )
}

/** Pick one of a few values; picking closes the dialog. */
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit, onDismiss: () -> Unit, note: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (note != null) Text(note, fontSize = 14.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
                options.forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).selectable(selected = v == selected, role = Role.RadioButton) { onPick(v); onDismiss() }
                            .heightIn(min = 52.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = v == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Brand))
                        Text(label, fontSize = 16.sp, color = Ink, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}

/** Change one line of text (or a number). [check] returns an error to show, or null when it's fine. */
@Composable
fun TextDialog(
    title: String, value: String, onSave: (String) -> Unit, onDismiss: () -> Unit,
    label: String? = null, number: Boolean = false, note: String? = null, check: (String) -> String? = { null },
) {
    var v by remember { mutableStateOf(value) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (note != null) Text(note, fontSize = 14.sp, color = Muted, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(
                    value = v, onValueChange = { v = if (number) it.filter(Char::isDigit).take(4) else it; error = null },
                    label = label?.let { { Text(it) } }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = check(v.trim())
                if (problem != null) error = problem else { onSave(v.trim()); onDismiss() }
            }) { Text("Save", color = Brand, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}

/** "Are you sure?" for things that can't be undone. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, fontSize = 15.sp) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = if (danger) Bad else Brand, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}

/** "1 sheet", "3 sheets". */
fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
