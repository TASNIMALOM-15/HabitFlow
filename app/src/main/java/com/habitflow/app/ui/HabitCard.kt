package com.habitflow.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitflow.app.AppVM
import com.habitflow.app.data.Engine
import com.habitflow.app.data.HType
import com.habitflow.app.data.Habit
import com.habitflow.app.data.Snap
import com.habitflow.app.data.t
import kotlinx.coroutines.delay

@Composable
fun HabitCard(vm: AppVM, h: Habit, day: Long, today: Long, snap: Snap, onEdit: (Habit) -> Unit) {
    val prefs = vm.prefs
    val ctx = LocalContext.current
    val e = snap.entry(h, day)
    val type = h.t()
    val color = Color(h.color)
    val running = (e?.timerStart ?: 0L) > 0L
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) { now = System.currentTimeMillis(); delay(1000) }
    }
    val state = e?.state ?: 0
    val cur = Engine.current(h, e, now)
    val done = Engine.isDone(h, e, now)
    val weekDone = if (type == HType.WEEKLY) Engine.weekDone(h, snap, day, prefs.weekStart) else 0
    val prog = if (type == HType.WEEKLY) (weekDone.toFloat() / h.weeklyTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    else Engine.progress(h, e, now)
    val streak = remember(snap, today) { Engine.streaks(h, snap, today, System.currentTimeMillis(), prefs.weekStart) }
    var menu by remember { mutableStateOf(false) }
    val future = day > today
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    HCard(Modifier.animateContentSize()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ---- header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f))
                        .clickable { onEdit(h) },
                    contentAlignment = Alignment.Center
                ) { Text(h.icon, fontSize = 22.sp) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(h.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val sub = buildString {
                        append(type.label)
                        if (streak.current > 0) append("  ·  🔥 ${streak.current} ${if (streak.unit == "weeks") "wk" else "d"}")
                    }
                    Text(sub, fontSize = 12.sp, color = muted, maxLines = 1)
                }
                if (state == 0 && !future) {
                    val c = if (done) Success else MaterialTheme.colorScheme.primary
                    Pill("${(prog * 100).toInt()}%", c)
                }
                Box {
                    Text("⋮", fontSize = 22.sp, color = muted, modifier = Modifier.clickable { menu = true }.padding(start = 10.dp, end = 2.dp))
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit habit") }, onClick = { menu = false; onEdit(h) })
                        if (!future) {
                            if (state != 1) DropdownMenuItem(text = { Text("Skip this day") }, onClick = { menu = false; vm.setState(h, day, 1) })
                            if (state != 2) DropdownMenuItem(text = { Text("Rest day") }, onClick = { menu = false; vm.setState(h, day, 2) })
                            if (state != 0) DropdownMenuItem(text = { Text("Undo skip / rest") }, onClick = { menu = false; vm.setState(h, day, 0) })
                            if (e != null) DropdownMenuItem(text = { Text("Clear this day") }, onClick = { menu = false; vm.clearDay(h, day) })
                        }
                    }
                }
            }

            // ---- body ----
            when {
                future -> Text("This day hasn't arrived yet", color = muted, fontSize = 13.sp)
                state != 0 -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Pill(if (state == 1) "Skipped" else "Rest day", Warn)
                    OutlinePill("Undo", { vm.setState(h, day, 0) })
                }
                else -> HabitControls(vm, h, e, day, today, cur, done, weekDone, now, running, color)
            }

            // ---- progress bar ----
            val showBar = state == 0 && !future && when (type) {
                HType.QUANTITY, HType.DURATION, HType.SLEEP, HType.WEEKLY -> true
                HType.VALUE -> h.target > 0
                HType.MULTIPLE, HType.INTERVAL -> h.target > 12
                else -> false
            }
            if (showBar) ProgressBarH(prog, if (done) Success else color)
        }
    }
}

@Composable
private fun HabitControls(
    vm: AppVM, h: Habit, e: com.habitflow.app.data.Entry?, day: Long, today: Long, cur: Double, done: Boolean,
    weekDone: Int, now: Long, running: Boolean, color: Color
) {
    val prefs = vm.prefs
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurf = MaterialTheme.colorScheme.onSurface
    var numDialog by remember { mutableStateOf(false) }
    val type = h.t()

    when (type) {
        HType.DAILY, HType.SCHEDULED -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (type == HType.SCHEDULED) "⏰ ${prefs.fmtTime(h.timeOfDay)}" else if (done) "Completed 🎉" else "Not done yet",
                color = muted, fontSize = 14.sp
            )
            PillButton(if (done) "✓ Done" else "Mark done", { vm.toggle(h, day) }, filled = true, color = if (done) Success else onSurf)
        }

        HType.AVOID -> {
            val v = e?.value ?: 0.0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (v >= 1) "Avoided 💪" else if (v < 0) "Slipped" else "Stay strong", color = muted, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinePill("Slipped", { vm.setAvoid(h, day, false) })
                    PillButton("✓ Avoided", { vm.setAvoid(h, day, true) }, filled = true, color = if (v >= 1) Success else onSurf)
                }
            }
        }

        HType.MULTIPLE, HType.INTERVAL -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${fmtNum(cur)}/${fmtNum(h.target)}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(" ${h.unit.ifBlank { "times" }}", color = muted, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                    }
                    if (type == HType.INTERVAL)
                        Text("Every ${h.intervalMin} min · ${prefs.fmtTime(h.winStart)}–${prefs.fmtTime(h.winEnd)}", fontSize = 12.sp, color = muted)
                }
                OutlinePill("−", { vm.addValue(h, day, -1.0) }, Modifier.width(64.dp))
                Spacer(Modifier.width(8.dp))
                PillButton("+", { vm.addValue(h, day, 1.0) }, filled = true, modifier = Modifier.width(64.dp))
            }
            if (h.target in 1.0..12.0) {
                val n = h.target.toInt()
                val c = cur.toInt()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until n) {
                        Box(
                            Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (i < c) color else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.setValue(h, day, if (c == i + 1) i.toDouble() else (i + 1).toDouble()) }
                        )
                    }
                }
            }
        }

        HType.QUANTITY -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { numDialog = true }, verticalAlignment = Alignment.Bottom) {
                Text("${fmtNum(cur)}/${fmtNum(h.target)}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(" ${h.unit}", color = muted, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
            }
            val step = Engine.stepFor(h.target)
            OutlinePill("−", { vm.addValue(h, day, -step) }, Modifier.width(64.dp))
            Spacer(Modifier.width(8.dp))
            PillButton("+", { vm.addValue(h, day, step) }, filled = true, modifier = Modifier.width(64.dp))
        }

        HType.DURATION -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { numDialog = true }) {
                    if (running) {
                        val s = (cur * 60).toLong()
                        Text("%d:%02d".format(s / 60, s % 60), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color)
                    } else Text("${fmtNum(Math.floor(cur))} / ${fmtNum(h.target)} min", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (running) "Timer running · target ${fmtNum(h.target)} min" else h.name, fontSize = 12.sp, color = muted, maxLines = 1)
                }
                if (day == today) PillButton(
                    if (running) "⏸ Pause" else "▶ Start Timer", { vm.timer(h, !running) },
                    filled = true, color = if (running) Warn else onSurf
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill("−5 min", { vm.addValue(h, day, -5.0) })
                OutlinePill("+5 min", { vm.addValue(h, day, 5.0) })
                OutlinePill("Set", { numDialog = true })
            }
        }

        HType.WEEKLY -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("This week: $weekDone/${h.weeklyTarget} days", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (done) "Checked for this day" else "Not checked for this day", fontSize = 12.sp, color = muted)
                }
                PillButton(if (done) "✓ Done" else "Mark done", { vm.toggle(h, day) }, filled = true, color = if (done) Success else onSurf)
            }
        }

        HType.SLEEP -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val bed = e?.bedMin ?: -1
            val wake = e?.wakeMin ?: -1
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "🌙 ${if (bed >= 0) prefs.fmtTime(bed) else "--"}  →  ☀ ${if (wake >= 0) prefs.fmtTime(wake) else "--"}",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    val mins = (cur * 60).toInt()
                    Text("${mins / 60}h ${mins % 60}m  /  goal ${fmtNum(h.target)}h", fontSize = 12.sp, color = muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill("Bedtime", { pickTime(ctx, bed, prefs.time24) { vm.setSleep(h, day, it, -1) } })
                OutlinePill("Wake up", { pickTime(ctx, wake, prefs.time24) { vm.setSleep(h, day, -1, it) } })
            }
        }

        HType.VALUE -> if (h.unit == "★") {
            val v = (e?.value ?: 0.0).toInt()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..5) {
                    Text(
                        if (i <= v) "★" else "☆", fontSize = 34.sp, color = if (i <= v) Warn else muted,
                        modifier = Modifier.clickable { vm.setValue(h, day, if (v == i) 0.0 else i.toDouble()) }
                    )
                }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { numDialog = true }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val v = e?.value ?: 0.0
                    Text(if (v > 0) fmtNum(v) else "—", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(" ${h.unit}", color = muted, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                }
                if (h.target > 0) Text("Goal: ${fmtNum(h.target)} ${h.unit}", fontSize = 12.sp, color = muted)
            }
            PillButton(if ((e?.value ?: 0.0) > 0) "Update" else "Log value", { numDialog = true }, filled = true, color = onSurf)
        }
    }

    if (numDialog) {
        val init = if (type == HType.VALUE) (e?.value ?: 0.0) else Math.floor(cur)
        NumberDialog(h.name, init, if (type == HType.DURATION) "min" else h.unit, { numDialog = false }) { vm.setValue(h, day, it) }
    }
}
