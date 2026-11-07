package com.omrscanner.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omrscanner.app.ui.theme.Accent
import com.omrscanner.app.ui.theme.CardBg
import com.omrscanner.app.ui.theme.Divider
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Line
import com.omrscanner.app.ui.theme.Mono
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Paper
import com.omrscanner.app.ui.theme.SegBg
import com.omrscanner.app.ui.theme.Warn
import com.omrscanner.app.ui.theme.WarnBg
import com.omrscanner.app.ui.theme.WarnLine

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null, trailingColor: Color = Muted) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Muted, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontFamily = Mono, fontSize = 12.sp, color = trailingColor)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(padding),
        content = content,
    )
}

@Composable
fun BackBar(label: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("‹ $label", color = Ink, fontSize = 16.sp) }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, lineHeight = 32.sp)
    if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SegBg).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) CardBg else Color.Transparent).clickable { onSelect(value) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Accent, contentColor: Color = Ink, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor, disabledContainerColor = Line),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CardBg, contentColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Ink else CardBg).border(1.dp, if (selected) Ink else Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = if (selected) Paper else Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
fun Stepper(value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton("−", onDec)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        RoundIconButton("+", onInc)
    }
}

@Composable
fun RoundIconButton(symbol: String, onClick: () -> Unit, size: Int = 38) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(CardBg).border(1.dp, Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontSize = 18.sp) }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Ink, bg: Color = CardBg) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = valueColor, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Paper))
    }
}

@Composable
fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

@Composable
fun Banner(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WarnBg).border(1.dp, WarnLine, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Warn)
            Text(body, fontSize = 12.sp, color = Warn, modifier = Modifier.padding(top = 2.dp))
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Ink).clickable(onClick = onAction).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(action, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
