package com.habitflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitflow.app.AppVM
import com.habitflow.app.data.Engine
import com.habitflow.app.data.HType
import com.habitflow.app.data.t
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(vm: AppVM, pad: PaddingValues, onEdit: (Long) -> Unit) {
    val snap by vm.snap.collectAsState()
    val today by vm.today.collectAsState()
    val notes by vm.notes.collectAsState()
    val prefs = vm.prefs
    val ws = prefs.weekStart
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var sel by remember { mutableLongStateOf(today) }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Calendar", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        HCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 30.sp, modifier = Modifier.clickable { ym = ym.minusMonths(1) }.padding(horizontal = 14.dp))
                    Text(
                        ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), Modifier.weight(1f),
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp
                    )
                    Text("›", fontSize = 30.sp, modifier = Modifier.clickable { ym = ym.plusMonths(1) }.padding(horizontal = 14.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val d = ((ws - 1 + i) % 7) + 1
                        Text(Engine.dayName(d), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = muted)
                    }
                }
                val first = ym.atDay(1)
                val offset = (Engine.dow(first.toEpochDay()) - ws + 7) % 7
                val total = offset + ym.lengthOfMonth()
                val rows = (total + 6) / 7
                val nowMs = System.currentTimeMillis()
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0..6) {
                            val idx = r * 7 + c
                            val dn = idx - offset + 1
                            if (dn < 1 || dn > ym.lengthOfMonth()) {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val day = first.toEpochDay() + dn - 1
                                val a = remember(snap, day, today) { Engine.agg(snap, day, today, nowMs) }
                                val fill = when {
                                    day > today -> Color.Transparent
                                    a.total > 0 && a.done == a.total -> Success.copy(alpha = 0.85f)
                                    a.done > 0 -> Success.copy(alpha = 0.25f + 0.4f * a.done / a.total)
                                    a.missed > 0 -> Danger.copy(alpha = 0.3f)
                                    a.rest + a.skip > 0 -> Warn.copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                                val isSel = day == sel
                                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                                    Box(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(fill)
                                            .then(if (isSel) Modifier.border(2.dp, primary, RoundedCornerShape(12.dp)) else Modifier)
                                            .clickable { sel = day },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$dn", fontSize = 13.sp,
                                            fontWeight = if (day == today) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (day == today) primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(Success, "Done"); Legend(Danger, "Missed"); Legend(Warn, "Rest/Skip")
                    Box(Modifier.weight(1f))
                    Chip("Today", false, { ym = YearMonth.now(); sel = today })
                }
            }
        }

        // ---------- selected day ----------
        val future = sel > today
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(prefs.fmtDate(sel), Modifier.weight(1f))
            if (sel == today) Pill("Today", primary)
        }
        if (!future) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinePill("All Rest", { vm.markDayState(sel, 2) })
            OutlinePill("All Skip", { vm.markDayState(sel, 1) })
            OutlinePill("Reset", { vm.markDayState(sel, 0) })
        }
        val list = snap.habits.filter { h ->
            Engine.inRange(h, sel) && (h.t() == HType.WEEKLY || Engine.scheduled(h, sel) || snap.entry(h, sel) != null)
        }
        if (list.isEmpty()) {
            Text("No habits on this day.", color = muted, fontSize = 14.sp)
        }
        list.forEach { h -> androidx.compose.runtime.key(h.id, sel) { HabitCard(vm, h, sel, today, snap) { onEdit(it.id) } } }

        // ---------- journal ----------
        SectionTitle("📝 Daily Journal")
        var text by remember(sel) { mutableStateOf(notes[sel] ?: "") }
        val latest by rememberUpdatedState(text)
        val selNow by rememberUpdatedState(sel)
        LaunchedEffect(text, sel) {
            if (text != (vm.notes.value[sel] ?: "")) { delay(700); vm.saveNote(sel, text) }
        }
        DisposableEffect(sel) {
            val d = sel
            onDispose { if (latest != (vm.notes.value[d] ?: "")) vm.saveNote(d, latest) }
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(2000) }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp), minLines = 3, maxLines = 8,
            placeholder = { Text("How was your day? Write anything...") }
        )
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 4.dp).clip(RoundedCornerShape(4.dp)).background(c.copy(alpha = 0.8f)).padding(5.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
