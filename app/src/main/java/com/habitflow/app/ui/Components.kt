package com.habitflow.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

fun fmtNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)

@Composable
fun HCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

/** Rounded pill button used everywhere (−, +, Done ...). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, filled: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface,
               modifier: Modifier = Modifier) {
    val bg = if (filled) color else Color.Transparent
    val fg = if (filled) (if (color.luminance() > 0.5f) Color.Black else Color.White) else color
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg)
            .then(if (!filled) Modifier.background(Color.Transparent) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center) }
}

fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50),
        color = Color.Transparent, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) p.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, p) else null
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) p else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 6.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ProgressBarH(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(p).height(height).clip(CircleShape).background(color))
    }
}

@Composable
fun Ring(progress: Float, color: Color, size: Dp, stroke: Dp, label: String, sub: String) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            drawArc(color, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = (size.value / 4.6).sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NumberDialog(title: String, initial: Double, unit: String, onDismiss: () -> Unit, onOk: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (initial == 0.0) "" else fmtNum(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                singleLine = true, suffix = { if (unit.isNotBlank()) Text(unit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = { TextButton(onClick = { onOk(text.toDoubleOrNull() ?: 0.0); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun pickTime(ctx: Context, initialMin: Int, is24: Boolean, onPick: (Int) -> Unit) {
    val m = if (initialMin in 0..1439) initialMin else 480
    TimePickerDialog(ctx, { _, h, mi -> onPick(h * 60 + mi) }, m / 60, m % 60, is24).show()
}

fun pickDate(ctx: Context, initialDay: Long, onPick: (Long) -> Unit) {
    val d = LocalDate.ofEpochDay(initialDay)
    DatePickerDialog(ctx, { _, y, mo, da -> onPick(LocalDate.of(y, mo + 1, da).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

@Composable
fun SimpleBarChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier, h: Dp = 120.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val txt = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(h)) {
            val n = values.size.coerceAtLeast(1)
            val gap = 4.dp.toPx()
            val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            values.forEachIndexed { i, v ->
                val x = i * (bw + gap)
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), androidx.compose.ui.geometry.CornerRadius(bw / 3))
                if (v > 0f) {
                    val bh = size.height * v.coerceIn(0f, 1f)
                    drawRoundRect(color, Offset(x, size.height - bh), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw / 3))
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { Text(it, fontSize = 10.sp, color = txt) }
            }
        }
    }
}
