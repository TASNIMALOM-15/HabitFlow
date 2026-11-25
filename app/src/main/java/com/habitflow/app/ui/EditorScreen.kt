package com.habitflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitflow.app.AppVM
import com.habitflow.app.data.Engine
import com.habitflow.app.data.HType
import com.habitflow.app.data.Habit
import com.habitflow.app.data.t

private val Icons = listOf(
    "✅", "📖", "💧", "🏃", "💪", "🧘", "🎓", "💤", "🥗", "🍎", "🚭", "🚫",
    "💊", "🧠", "✍️", "🎧", "🎸", "🧹", "💰", "🌱", "🙏", "📵", "☀️", "🐕"
)

private fun parseRem(s: String): List<Int> = s.split(',').mapNotNull { p ->
    val x = p.trim().split(':')
    val h = x.getOrNull(0)?.toIntOrNull(); val m = x.getOrNull(1)?.toIntOrNull()
    if (x.size == 2 && h != null && m != null) h * 60 + m else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: AppVM, habitId: Long, onClose: () -> Unit) {
    val snap by vm.snap.collectAsState()
    val cats by vm.cats.collectAsState()
    val ctx = LocalContext.current
    val prefs = vm.prefs
    val existing = remember(habitId) { snap.habits.firstOrNull { it.id == habitId } }
    var h by remember { mutableStateOf(existing ?: Habit(name = "", startDay = Engine.today())) }
    val rems = remember { mutableStateListOf<Int>().apply { addAll(parseRem(existing?.reminders ?: "")) } }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = existing == null
    val type = h.t()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    BackHandler(onBack = onClose)

    fun pickType(t: HType) {
        val base = h.copy(type = t.name)
        h = when (t) {
            HType.DAILY -> base.copy(target = 1.0, unit = "")
            HType.MULTIPLE -> base.copy(target = 3.0, unit = "times")
            HType.DURATION -> base.copy(target = 30.0, unit = "min")
            HType.QUANTITY -> base.copy(target = 20.0, unit = "pages")
            HType.WEEKLY -> base.copy(target = 1.0, unit = "days", weeklyTarget = 3)
            HType.SCHEDULED -> base.copy(target = 1.0, unit = "", timeOfDay = 480)
            HType.INTERVAL -> base.copy(intervalMin = 120, winStart = 480, winEnd = 1320, unit = "times",
                target = Engine.intervalSlots(base.copy(intervalMin = 120, winStart = 480, winEnd = 1320)).size.toDouble())
            HType.SLEEP -> base.copy(target = 8.0, unit = "h")
            HType.VALUE -> base.copy(target = 0.0, unit = "kg")
            HType.AVOID -> base.copy(target = 1.0, unit = "")
        }
    }

    fun setInterval(min: Int = h.intervalMin, ws: Int = h.winStart, we: Int = h.winEnd) {
        val n = h.copy(intervalMin = min, winStart = ws, winEnd = we)
        h = n.copy(target = Engine.intervalSlots(n).size.toDouble())
    }

    fun save() {
        if (h.name.isBlank()) { vm.toast.tryEmit("Please enter a habit name"); return }
        if (h.endDay >= 0 && h.endDay < h.startDay) { vm.toast.tryEmit("End date is before start date"); return }
        if (type == HType.INTERVAL && h.winEnd <= h.winStart) { vm.toast.tryEmit("Window end must be after start"); return }
        if (type != HType.WEEKLY && type != HType.AVOID && type != HType.DAILY && type != HType.SCHEDULED && type != HType.VALUE && h.target <= 0) {
            vm.toast.tryEmit("Target must be greater than 0"); return
        }
        if (type != HType.WEEKLY && h.daysMask == 0) { vm.toast.tryEmit("Select at least one day"); return }
        val r = if (type == HType.INTERVAL) "" else rems.distinct().sorted().joinToString(",") { "%02d:%02d".format(it / 60, it % 60) }
        vm.saveHabit(h.copy(name = h.name.trim(), reminders = r), onClose)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "Create New Habit" else "Edit Habit", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("✕", fontSize = 22.sp, color = muted, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Field("Habit Name") {
                    OutlinedTextField(
                        value = h.name, onValueChange = { h = h.copy(name = it.take(40)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), placeholder = { Text("e.g. Reading Books") }
                    )
                }
                Field("Icon") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icons.forEach { ic ->
                            val sel = h.icon == ic
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) Color(h.color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                                    .then(if (sel) Modifier.border(1.5.dp, Color(h.color), RoundedCornerShape(14.dp)) else Modifier)
                                    .clickable { h = h.copy(icon = ic) },
                                contentAlignment = Alignment.Center
                            ) { Text(ic, fontSize = 22.sp) }
                        }
                    }
                }
                Field("Color") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HabitColors.forEach { c ->
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                                    .then(if (h.color == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { h = h.copy(color = c) }
                            )
                        }
                    }
                }
                Field("Tracking Type") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HType.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { t ->
                                    val sel = t == type
                                    val p = MaterialTheme.colorScheme.primary
                                    Surface(
                                        onClick = { if (!sel) pickType(t) }, modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (sel) p.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = if (sel) BorderStroke(1.5.dp, p) else null
                                    ) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(t.emoji, fontSize = 18.sp)
                                            Text(t.label, Modifier.padding(start = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ---------- dynamic configuration ----------
                val hasConfig = type !in setOf(HType.DAILY, HType.AVOID)
                if (hasConfig) Field("Target Configuration") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (type) {
                            HType.MULTIPLE -> {
                                NumField("Times per day", h.target, type) { h = h.copy(target = it) }
                                TextField2("Unit (optional)", h.unit) { h = h.copy(unit = it) }
                            }
                            HType.DURATION -> NumField("Daily target (minutes)", h.target, type) { h = h.copy(target = it) }
                            HType.QUANTITY -> {
                                NumField("Daily Target", h.target, type) { h = h.copy(target = it) }
                                TextField2("Measurement unit (pages, steps, ml ...)", h.unit) { h = h.copy(unit = it) }
                            }
                            HType.WEEKLY -> {
                                Text("Days per week", color = muted, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (i in 1..7) Chip("$i", h.weeklyTarget == i, { h = h.copy(weeklyTarget = i) }, Modifier.weight(1f))
                                }
                            }
                            HType.SCHEDULED -> TimeRow("Scheduled time", h.timeOfDay, prefs.fmtTime(h.timeOfDay)) { h = h.copy(timeOfDay = it) }
                            HType.INTERVAL -> {
                                Text("Repeat every", color = muted, fontSize = 13.sp)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(30, 45, 60, 90, 120, 180, 240).forEach {
                                        Chip(if (it < 60) "$it min" else "${it / 60}h" + if (it % 60 != 0) " ${it % 60}m" else "", h.intervalMin == it, { setInterval(min = it) })
                                    }
                                }
                                TimeRow("Window start", h.winStart, prefs.fmtTime(h.winStart)) { setInterval(ws = it) }
                                TimeRow("Window end", h.winEnd, prefs.fmtTime(h.winEnd)) { setInterval(we = it) }
                                Text("${h.target.toInt()} reminders per day — complete them with +1 each time.", color = muted, fontSize = 12.sp)
                            }
                            HType.SLEEP -> NumField("Sleep goal (hours)", h.target, type) { h = h.copy(target = it) }
                            HType.VALUE -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("Number", h.unit != "★", { h = h.copy(unit = "kg", target = 0.0) })
                                    Chip("Rating ★", h.unit == "★", { h = h.copy(unit = "★", target = 0.0) })
                                }
                                if (h.unit != "★") {
                                    TextField2("Unit (kg, mg/dL, ...)", h.unit) { h = h.copy(unit = it) }
                                    NumField("Goal (0 = none)", h.target, type, allowEmpty = true) { h = h.copy(target = it) }
                                } else {
                                    NumField("Minimum rating to count as done (0 = any)", h.target, type, allowEmpty = true) { h = h.copy(target = it.coerceIn(0.0, 5.0)) }
                                }
                            }
                            else -> {}
                        }
                    }
                }

                if (type != HType.WEEKLY) Field("Schedule") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Every day", h.daysMask == 127, { h = h.copy(daysMask = 127) })
                            Chip("Weekdays", h.daysMask == 31, { h = h.copy(daysMask = 31) })
                            Chip("Weekend", h.daysMask == 96, { h = h.copy(daysMask = 96) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                                Chip(l, (h.daysMask shr i) and 1 == 1, { h = h.copy(daysMask = h.daysMask xor (1 shl i)) }, Modifier.weight(1f))
                            }
                        }
                    }
                }

                Field("Start & End Date") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinePill("Start: ${prefs.fmtDate(h.startDay)}", { pickDate(ctx, h.startDay) { h = h.copy(startDay = it) } })
                        OutlinePill(if (h.endDay < 0) "End: none" else "End: ${prefs.fmtDate(h.endDay)}", {
                            pickDate(ctx, if (h.endDay < 0) maxOf(h.startDay, Engine.today()) + 30 else h.endDay) { h = h.copy(endDay = it) }
                        })
                    }
                    if (h.endDay >= 0) TextButton(onClick = { h = h.copy(endDay = -1) }) { Text("Remove end date") }
                }

                if (type != HType.INTERVAL) Field("Reminders") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (type == HType.SCHEDULED) Text("A reminder at the scheduled time is added automatically.", color = muted, fontSize = 12.sp)
                        rems.sorted().forEach { m ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("⏰  ${prefs.fmtTime(m)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text("✕", color = muted, modifier = Modifier.clickable { rems.remove(m) }.padding(4.dp))
                            }
                        }
                        OutlinePill("+ Add reminder", { pickTime(ctx, 480, prefs.time24) { if (it !in rems) rems.add(it) } })
                    }
                }

                Field("Category") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("None", h.categoryId == 0L, { h = h.copy(categoryId = 0) })
                        cats.forEach { c -> Chip(c.name, h.categoryId == c.id, { h = h.copy(categoryId = c.id) }) }
                    }
                    if (cats.isEmpty()) Text("Create categories in the Categories tab.", color = muted, fontSize = 12.sp)
                }

                Field("Notes") {
                    OutlinedTextField(
                        value = h.notes, onValueChange = { h = h.copy(notes = it.take(500)) }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp), minLines = 2, maxLines = 5, placeholder = { Text("Why this habit matters...") }
                    )
                }

                if (!isNew) Field("Manage") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val cur = existing!!
                            if (cur.status == 1) OutlinePill("▶ Resume", { vm.setStatus(cur, 0); onClose() })
                            else if (cur.status == 0) OutlinePill("⏸ Pause", { vm.setStatus(cur, 1); onClose() })
                            if (cur.status == 2) OutlinePill("Restore", { vm.setStatus(cur, 0); onClose() })
                            else OutlinePill("Archive", { vm.setStatus(cur, 2); onClose() })
                        }
                        PillButton("Delete habit", { confirmDelete = true }, filled = true, color = Danger)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                PillButton(if (isNew) "Create Habit" else "Save Changes", { save() }, filled = true,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${h.name}”?") },
        text = { Text("This permanently removes the habit and all of its history.") },
        confirmButton = { TextButton(onClick = { existing?.let { vm.deleteHabit(it) }; confirmDelete = false; onClose() }) { Text("Delete", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun Field(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun TextField2(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(16)) }, singleLine = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun NumField(label: String, value: Double, key: Any, allowEmpty: Boolean = false, onChange: (Double) -> Unit) {
    var text by remember(key) { mutableStateOf(if (value == 0.0 && allowEmpty) "" else fmtNum(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val f = t.filter { c -> c.isDigit() || c == '.' }.take(9)
            text = f; onChange(f.toDoubleOrNull() ?: 0.0)
        },
        singleLine = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun TimeRow(label: String, minutes: Int, shown: String, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val is24 = remember(shown) { !shown.contains("M") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        OutlinePill(shown, { pickTime(ctx, minutes, is24, onPick) })
    }
}
