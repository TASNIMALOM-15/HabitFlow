package com.habitflow.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.habitflow.app.AppVM
import com.habitflow.app.data.Engine
import com.habitflow.app.data.Habit
import com.habitflow.app.data.Snap
import com.habitflow.app.data.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private class HabitStat(val h: Habit, val rate: Float)

private class StatsData(
    val label: String, val done: Int, val missed: Int, val skip: Int, val rest: Int, val rate: Float,
    val bars: List<Float>, val barLabels: List<String>, val heat: List<Float>, val heatCols: Int,
    val habits: List<HabitStat>
)

private fun fmtD(d: Long, p: String) = LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern(p, Locale.getDefault()))

private fun compute(s: Snap, range: Int, off: Int, today: Long, ws: Int): StatsData {
    val now = System.currentTimeMillis()
    val from: Long; val to: Long; val label: String
    when (range) {
        0 -> { from = today + off; to = from; label = fmtD(from, "EEE, d MMM yyyy") }
        1 -> { from = Engine.weekStart(today, ws) + 7L * off; to = from + 6; label = fmtD(from, "d MMM") + " – " + fmtD(to, "d MMM") }
        2 -> {
            val ym = YearMonth.from(LocalDate.ofEpochDay(today)).plusMonths(off.toLong())
            from = ym.atDay(1).toEpochDay(); to = ym.atEndOfMonth().toEpochDay(); label = fmtD(from, "MMMM yyyy")
        }
        else -> {
            val y = LocalDate.ofEpochDay(today).year + off
            from = LocalDate.of(y, 1, 1).toEpochDay(); to = LocalDate.of(y, 12, 31).toEpochDay(); label = "$y"
        }
    }
    var d = 0; var m = 0; var p = 0; var sk = 0; var r = 0
    var day = from
    while (day <= minOf(to, today)) {
        val a = Engine.agg(s, day, today, now)
        d += a.done; m += a.missed; p += a.pending; sk += a.skip; r += a.rest
        day++
    }
    val rate = if (d + m + p == 0) -1f else d.toFloat() / (d + m + p)

    val bars = ArrayList<Float>(); val labels = ArrayList<String>()
    when (range) {
        0 -> s.habits.filter { Engine.inRange(it, from) && from <= today }.forEach {
            bars.add(Engine.progress(it, s.entry(it, from), now)); labels.add(it.icon)
        }
        1 -> for (i in 0..6) {
            val a = Engine.agg(s, from + i, today, now); bars.add(maxOf(0f, a.ratio)); labels.add(fmtD(from + i, "EEE").take(2))
        }
        2 -> {
            var x = from
            while (x <= to) { val a = Engine.agg(s, x, today, now); bars.add(maxOf(0f, a.ratio)); x++ }
            labels.addAll(listOf("1", "8", "15", "22", (to - from + 1).toString()))
        }
        else -> {
            val y = LocalDate.ofEpochDay(from).year
            for (mo in 1..12) {
                val ym = YearMonth.of(y, mo); var dd = 0; var tt = 0
                var x = ym.atDay(1).toEpochDay()
                while (x <= ym.atEndOfMonth().toEpochDay() && x <= today) { val a = Engine.agg(s, x, today, now); dd += a.done; tt += a.total; x++ }
                bars.add(if (tt == 0) 0f else dd.toFloat() / tt)
                labels.add("JFMAMJJASOND"[mo - 1].toString())
            }
        }
    }

    val cols = if (range == 3) 53 else 26
    val lastWs = Engine.weekStart(today, ws)
    val startWs = lastWs - 7L * (cols - 1)
    val heat = ArrayList<Float>(cols * 7)
    for (c in 0 until cols) for (rw in 0..6) {
        val dy = startWs + c * 7L + rw
        heat.add(if (dy > today) -2f else Engine.agg(s, dy, today, now).ratio)
    }
    val hs = s.habits.mapNotNull { h ->
        val rt = Engine.rate(h, s, from, to, today, now)
        if (rt < 0) null else HabitStat(h, rt)
    }.sortedByDescending { it.rate }
    return StatsData(label, d, m, sk, r, rate, bars, labels, heat, cols, hs)
}

@Composable
fun StatsScreen(vm: AppVM, pad: PaddingValues) {
    val snap by vm.snap.collectAsState()
    val today by vm.today.collectAsState()
    val ws = vm.prefs.weekStart
    var range by remember { mutableIntStateOf(1) }
    var off by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Habit?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val data by produceState<StatsData?>(null, snap, range, off, today, ws) {
        value = withContext(Dispatchers.Default) { compute(snap, range, off, today, ws) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Statistics", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Day", "Week", "Month", "Year").forEachIndexed { i, l ->
                Chip(l, range == i, { range = i; off = 0 }, Modifier.weight(1f))
            }
        }
        val dt = data
        if (dt == null) { Text("Loading…", color = muted); return@Column }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 30.sp, modifier = Modifier.clickable { off-- }.padding(horizontal = 14.dp))
            Text(dt.label, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("›", fontSize = 30.sp, color = if (off < 0) MaterialTheme.colorScheme.onSurface else muted.copy(alpha = 0.3f),
                modifier = Modifier.clickable { if (off < 0) off++ }.padding(horizontal = 14.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Completion", if (dt.rate < 0) "—" else "${(dt.rate * 100).toInt()}%", primary, Modifier.weight(1f))
            Tile("Completed", "${dt.done}", Success, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Missed", "${dt.missed}", Danger, Modifier.weight(1f))
            Tile("Rest · Skip", "${dt.rest} · ${dt.skip}", Warn, Modifier.weight(1f))
        }

        HCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (range == 0) "Progress by habit" else "Progress", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (dt.bars.isEmpty()) Text("No data", color = muted, fontSize = 13.sp)
                else SimpleBarChart(dt.bars, dt.barLabels, primary)
            }
        }

        HCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Activity Heatmap", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Heatmap(dt.heat, dt.heatCols, primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text("Less ", fontSize = 11.sp, color = muted)
                    listOf(0.12f, 0.35f, 0.6f, 0.8f, 1f).forEach {
                        Box(Modifier.padding(2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(primary.copy(alpha = it)))
                    }
                    Text(" More", fontSize = 11.sp, color = muted)
                }
            }
        }

        SectionTitle("Habit Completion")
        if (dt.habits.isEmpty()) Text("Nothing to show for this period.", color = muted, fontSize = 13.sp)
        dt.habits.forEach { hs ->
            HCard(Modifier.clickable { detail = hs.h }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hs.h.icon, fontSize = 20.sp)
                        Text(hs.h.name, Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Pill("${(hs.rate * 100).toInt()}%", if (hs.rate >= 0.8f) Success else if (hs.rate >= 0.5f) Warn else Danger)
                    }
                    ProgressBarH(hs.rate, Color(hs.h.color))
                }
            }
        }
    }

    detail?.let { h -> HabitAnalytics(vm, h, snap, today) { detail = null } }
}

@Composable
private fun Tile(title: String, value: String, color: Color, modifier: Modifier) {
    HCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Heatmap(cells: List<Float>, cols: Int, color: Color) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().aspectRatio(cols.coerceAtLeast(1) / 7.3f)) {
        val gap = 2.dp.toPx()
        val cell = ((size.width - gap * (cols - 1)) / cols).coerceAtLeast(2f)
        val rowGap = gap
        for (c in 0 until cols) for (r in 0..6) {
            val v = cells.getOrNull(c * 7 + r) ?: continue
            if (v < -1.5f) continue
            val col = when {
                v < 0f -> empty.copy(alpha = 0.55f)
                v == 0f -> color.copy(alpha = 0.12f)
                v < 0.34f -> color.copy(alpha = 0.35f)
                v < 0.67f -> color.copy(alpha = 0.6f)
                v < 1f -> color.copy(alpha = 0.8f)
                else -> color
            }
            drawRoundRect(col, Offset(c * (cell + gap), r * (cell + rowGap)), Size(cell, cell), CornerRadius(cell / 4))
        }
    }
}

@Composable
private fun HabitAnalytics(vm: AppVM, h: Habit, s: Snap, today: Long, onClose: () -> Unit) {
    val now = System.currentTimeMillis()
    val ws = vm.prefs.weekStart
    val st = remember(s, h) { Engine.streaks(h, s, today, now, ws) }
    val r30 = remember(s, h) { Engine.rate(h, s, today - 29, today, today, now) }
    val days30 = remember(s, h) { (29 downTo 0).map { Engine.progress(h, s.entry(h, today - it), now) } }
    val weeks = remember(s, h) {
        (11 downTo 0).map {
            val w = Engine.weekStart(today, ws) - 7L * it
            maxOf(0f, Engine.rate(h, s, w, w + 6, today, now))
        }
    }
    val total = remember(s, h) { (s.byHabit[h.id] ?: emptyMap()).values.count { Engine.isDone(h, it, 0) && it.state == 0 } }
    val color = Color(h.color)
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.icon, fontSize = 28.sp)
                    Text(h.name, Modifier.weight(1f).padding(start = 10.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Current", "${st.current}${if (st.unit == "weeks") "w" else "d"}", Modifier.weight(1f))
                    MiniStat("Best", "${st.best}${if (st.unit == "weeks") "w" else "d"}", Modifier.weight(1f))
                    MiniStat("30-day", if (r30 < 0) "—" else "${(r30 * 100).toInt()}%", Modifier.weight(1f))
                    MiniStat("Total", "$total", Modifier.weight(1f))
                }
                Text("Last 30 days", fontWeight = FontWeight.SemiBold)
                SimpleBarChart(days30, emptyList(), color, h = 90.dp)
                Text("Last 12 weeks", fontWeight = FontWeight.SemiBold)
                SimpleBarChart(weeks, emptyList(), color, h = 90.dp)
                Text("Streak history", fontWeight = FontWeight.SemiBold)
                if (st.history.isEmpty()) Text("No streaks yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                st.history.take(8).forEach { (a, b, n) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmtD(a, "d MMM yy")} → ${fmtD(b, "d MMM yy")}", Modifier.weight(1f), fontSize = 13.sp)
                        Pill("$n ${st.unit}", color)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
