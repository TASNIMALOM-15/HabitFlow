package com.habitflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitflow.app.AppVM
import com.habitflow.app.data.Engine
import com.habitflow.app.data.HType
import com.habitflow.app.data.Habit
import com.habitflow.app.data.t
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private class Template(val name: String, val icon: String, val type: HType, val target: Double, val unit: String, val color: Long)

private val Templates = listOf(
    Template("Drink Water", "💧", HType.MULTIPLE, 8.0, "glasses", 0xFF3B82F6),
    Template("Read", "📖", HType.QUANTITY, 20.0, "pages", 0xFF10B981),
    Template("Study", "🎓", HType.DURATION, 120.0, "min", 0xFF8B5CF6),
    Template("Steps", "👟", HType.QUANTITY, 10000.0, "steps", 0xFFF97316),
    Template("Sleep", "🌙", HType.SLEEP, 8.0, "h", 0xFF6366F1),
    Template("No Sugar", "🚫", HType.AVOID, 1.0, "", 0xFFEF4444),
    Template("Workout", "💪", HType.WEEKLY, 3.0, "days", 0xFFEC4899)
)

@Composable
fun HomeScreen(vm: AppVM, pad: PaddingValues, onEdit: (Long) -> Unit) {
    val snap by vm.snap.collectAsState()
    val today by vm.today.collectAsState()
    val now = System.currentTimeMillis()

    val due = remember(snap, today) { snap.habits.filter { Engine.dueToday(it, today) } }
    val agg = remember(snap, today) { Engine.agg(snap, today, today, System.currentTimeMillis()) }
    val ringProg = remember(snap, today) {
        val active = due.filter { snap.entry(it, today)?.state.let { s -> s != 1 && s != 2 } }
        if (active.isEmpty()) -1f else (active.sumOf { Engine.progress(it, snap.entry(it, today), System.currentTimeMillis()).toDouble() } / active.size).toFloat()
    }
    val overall = remember(snap, today) {
        var d = 0; var t = 0
        for (i in 0 until 30) { val a = Engine.agg(snap, today - i, today, System.currentTimeMillis()); d += a.done; t += a.done + a.missed }
        if (t == 0) -1f else d.toFloat() / t
    }
    val streak = remember(snap, today) { Engine.overallStreak(snap, today, System.currentTimeMillis()) }
    val upcoming = remember(snap, today, now / 60000) {
        val nm = LocalDateTime.now().let { it.hour * 60 + it.minute }
        due.filter { snap.entry(it, today)?.let { e -> e.state == 0 && Engine.isDone(it, e, now) } != true }
            .flatMap { h ->
                if (h.t() == HType.INTERVAL) Engine.intervalSlots(h).filter { it > nm }.take(1).map { it to h }
                else Engine.reminderMinutes(h).filter { it > nm }.map { it to h }
            }.sortedBy { it.first }.take(4)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            val d = LocalDate.now()
            val hour = LocalDateTime.now().hour
            Column {
                Text(if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            val primary = MaterialTheme.colorScheme.primary
            HCard {
                Column(
                    Modifier.background(Brush.linearGradient(listOf(primary.copy(alpha = 0.20f), Color.Transparent))).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ring(if (ringProg < 0) 0f else ringProg, primary, 118.dp, 11.dp,
                                if (ringProg < 0) "—" else "${(ringProg * 100).toInt()}%", "${agg.done}/${agg.total} done")
                            Text("Today's Progress", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ring(if (overall < 0) 0f else overall, Success, 118.dp, 11.dp,
                                if (overall < 0) "—" else "${(overall * 100).toInt()}%", "30 days")
                            Text("Overall Progress", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("🔥", "Current Streak", "${streak.first} days", Modifier.weight(1f))
                        StatTile("🏆", "Best Streak", "${streak.second} days", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quick Add", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Templates) { t ->
                        Chip("${t.icon} ${t.name}", false, {
                            vm.quickAdd(t.name, t.icon, t.type, t.target, t.unit, t.color.toInt())
                        })
                    }
                }
            }
        }
        item {
            HCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⏰ Upcoming Reminders", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (upcoming.isEmpty()) {
                        Text("No more reminders today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    } else upcoming.forEach { (m, h) ->
                        Row(Modifier.fillMaxWidth().clickable { onEdit(h.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Text(h.icon, fontSize = 18.sp)
                            Text(h.name, Modifier.weight(1f).padding(start = 10.dp), fontSize = 15.sp, maxLines = 1)
                            Pill(vm.prefs.fmtTime(m), Color(h.color))
                        }
                    }
                }
            }
        }
        item { SectionTitle("Today's Habits") }
        if (due.isEmpty()) {
            item {
                HCard {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🌱", fontSize = 40.sp)
                        Text("No habits for today", fontWeight = FontWeight.Bold)
                        Text("Tap “New Habit” or a Quick Add chip to begin.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(due, key = { it.id }) { h ->
            HabitCard(vm, h, today, today, snap) { onEdit(it.id) }
        }
    }
}

@Composable
private fun StatTile(icon: String, label: String, value: String, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Column(Modifier.padding(start = 10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
