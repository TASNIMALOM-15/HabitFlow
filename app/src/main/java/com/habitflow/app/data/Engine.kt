package com.habitflow.app.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Every tracking type is described here. Adding a new type = one enum entry + one UI branch. */
enum class HType(val label: String, val emoji: String) {
    DAILY("Daily Completion", "✅"),
    MULTIPLE("Multiple Times", "🔁"),
    DURATION("Duration", "⏱"),
    QUANTITY("Quantity", "🎯"),
    WEEKLY("Weekly Target", "📅"),
    SCHEDULED("Scheduled", "⏰"),
    INTERVAL("Interval", "⏳"),
    SLEEP("Sleep", "🌙"),
    VALUE("Value Based", "📊"),
    AVOID("Avoidance", "🚫")
}

fun Habit.t(): HType = runCatching { HType.valueOf(type) }.getOrDefault(HType.DAILY)

enum class Out { DONE, MISSED, PENDING, SKIP, REST, NONE }

class Snap(val habits: List<Habit>, val byHabit: Map<Long, Map<Long, Entry>>) {
    fun entry(h: Habit, day: Long): Entry? = byHabit[h.id]?.get(day)
    companion object {
        val EMPTY = Snap(emptyList(), emptyMap())
        fun build(h: List<Habit>, e: List<Entry>): Snap =
            Snap(h, e.groupBy { it.habitId }.mapValues { (_, l) -> l.associateBy { it.day } })
    }
}

data class DayAgg(val done: Int, val missed: Int, val pending: Int, val skip: Int, val rest: Int) {
    val total get() = done + missed + pending
    val ratio get() = if (total == 0) -1f else done.toFloat() / total
}

data class Streaks(val current: Int, val best: Int, val history: List<Triple<Long, Long, Int>>, val unit: String)

object Engine {
    fun today(): Long = LocalDate.now().toEpochDay()
    fun dow(day: Long): Int = LocalDate.ofEpochDay(day).dayOfWeek.value // Mon=1..Sun=7
    fun weekStart(day: Long, wsDow: Int): Long = day - ((dow(day) - wsDow + 7) % 7)

    fun scheduled(h: Habit, day: Long): Boolean = (h.daysMask shr (dow(day) - 1)) and 1 == 1

    fun target(h: Habit): Double = when (h.t()) {
        HType.DAILY, HType.SCHEDULED, HType.AVOID, HType.WEEKLY -> 1.0
        else -> h.target
    }

    /** Current value in the habit's own unit (DURATION => minutes). */
    fun current(h: Habit, e: Entry?, now: Long): Double {
        if (e == null) return 0.0
        return if (h.t() == HType.DURATION) {
            (e.value + (if (e.timerStart > 0) (now - e.timerStart) / 1000.0 else 0.0)) / 60.0
        } else e.value
    }

    fun progress(h: Habit, e: Entry?, now: Long): Float {
        val c = current(h, e, now)
        if (c <= 0) return 0f
        val t = target(h)
        return if (t <= 0) 1f else (c / t).coerceIn(0.0, 1.0).toFloat()
    }

    fun isDone(h: Habit, e: Entry?, now: Long): Boolean = when (h.t()) {
        HType.VALUE -> { val v = e?.value ?: 0.0; v > 0 && (h.target <= 0 || v >= h.target) }
        else -> progress(h, e, now) >= 1f
    }

    fun inRange(h: Habit, day: Long): Boolean =
        day >= h.startDay && (h.endDay < 0 || day <= h.endDay) &&
            !(h.status != 0 && h.inactiveSince in 0..day)

    fun outcome(h: Habit, e: Entry?, day: Long, today: Long, now: Long): Out {
        if (day > today || !inRange(h, day)) return Out.NONE
        if (e?.state == 1) return Out.SKIP
        if (e?.state == 2) return Out.REST
        val done = isDone(h, e, now)
        if (h.t() == HType.WEEKLY) return if (done) Out.DONE else Out.NONE
        if (!scheduled(h, day)) return if (done) Out.DONE else Out.NONE
        return when {
            done -> Out.DONE
            day == today -> Out.PENDING
            else -> Out.MISSED
        }
    }

    fun agg(s: Snap, day: Long, today: Long, now: Long): DayAgg {
        var d = 0; var m = 0; var p = 0; var sk = 0; var r = 0
        for (h in s.habits) {
            when (outcome(h, s.entry(h, day), day, today, now)) {
                Out.DONE -> d++; Out.MISSED -> m++; Out.PENDING -> p++
                Out.SKIP -> sk++; Out.REST -> r++; Out.NONE -> {}
            }
        }
        return DayAgg(d, m, p, sk, r)
    }

    /** Completion rate for one habit in [from, to]; -1 when nothing was expected. */
    fun rate(h: Habit, s: Snap, from: Long, to: Long, today: Long, now: Long): Float {
        var d = 0; var m = 0; var active = 0
        val last = minOf(to, today)
        var day = maxOf(from, h.startDay)
        while (day <= last) {
            if (inRange(h, day)) {
                active++
                when (outcome(h, s.entry(h, day), day, today, now)) {
                    Out.DONE -> d++; Out.MISSED -> m++; else -> {}
                }
            }
            day++
        }
        if (h.t() == HType.WEEKLY) {
            val expected = h.weeklyTarget * (active / 7.0)
            return if (expected <= 0) -1f else (d / expected).coerceAtMost(1.0).toFloat()
        }
        return if (d + m == 0) -1f else d.toFloat() / (d + m)
    }

    fun weekDone(h: Habit, s: Snap, day: Long, wsDow: Int): Int {
        val ws = weekStart(day, wsDow)
        var c = 0
        for (i in 0..6) if (isDone(h, s.entry(h, ws + i), 0)) c++
        return c
    }

    fun streaks(h: Habit, s: Snap, today: Long, now: Long, wsDow: Int): Streaks {
        val map = s.byHabit[h.id] ?: emptyMap()
        val first = maxOf(h.startDay, minOf(map.keys.minOrNull() ?: today, today), today - 1825)
        val runs = ArrayList<Triple<Long, Long, Int>>()
        var run = 0; var runStart = first; var last = first
        fun close() { if (run > 0) runs.add(Triple(runStart, last, run)); run = 0 }

        if (h.t() == HType.WEEKLY) {
            var ws = weekStart(first, wsDow)
            val curWs = weekStart(today, wsDow)
            while (ws <= curWs) {
                var c = 0
                for (i in 0..6) if (isDone(h, map[ws + i], 0)) c++
                val ok = c >= h.weeklyTarget
                if (ok) { if (run == 0) runStart = ws; run++; last = ws + 6 }
                else if (ws != curWs) close()
                ws += 7
            }
            val cur = run
            close()
            return Streaks(cur, runs.maxOfOrNull { it.third } ?: 0, runs.sortedByDescending { it.first }.take(20), "weeks")
        }

        var day = first
        while (day <= today) {
            when (outcome(h, map[day], day, today, now)) {
                Out.DONE -> { if (run == 0) runStart = day; run++; last = day }
                Out.MISSED -> close()
                else -> {}
            }
            day++
        }
        val cur = run
        close()
        return Streaks(cur, runs.maxOfOrNull { it.third } ?: 0, runs.sortedByDescending { it.first }.take(20), "days")
    }

    /** Overall "perfect day" streak: done > 0 and nothing missed. */
    fun overallStreak(s: Snap, today: Long, now: Long): Pair<Int, Int> {
        val first = s.habits.minOfOrNull { it.startDay }?.let { maxOf(it, today - 1095) } ?: return 0 to 0
        var run = 0; var best = 0
        var day = first
        while (day <= today) {
            val a = agg(s, day, today, now)
            if (a.missed > 0) run = 0
            else if (a.done > 0) { run++; if (run > best) best = run }
            day++
        }
        return run to best
    }

    fun stepFor(t: Double): Double = when {
        t <= 30 -> 1.0; t <= 100 -> 5.0; t <= 500 -> 25.0; t <= 2000 -> 100.0; else -> 500.0
    }

    fun dueToday(h: Habit, today: Long): Boolean =
        h.status == 0 && inRange(h, today) && (h.t() == HType.WEEKLY || scheduled(h, today))

    fun reminderMinutes(h: Habit): List<Int> {
        val out = ArrayList<Int>()
        h.reminders.split(',').forEach { r ->
            val p = r.trim().split(':')
            if (p.size == 2) { val m = (p[0].toIntOrNull() ?: -1) * 60 + (p[1].toIntOrNull() ?: -1); if (m in 0..1439) out.add(m) }
        }
        if (h.t() == HType.SCHEDULED) out.add(h.timeOfDay)
        return out.distinct().sorted()
    }

    fun intervalSlots(h: Habit): List<Int> {
        val out = ArrayList<Int>()
        var m = h.winStart
        val step = maxOf(5, h.intervalMin)
        while (m <= h.winEnd) { out.add(m); m += step }
        return out
    }

    fun dayName(dow: Int): String = DayOfWeek.of(dow).name.take(3).lowercase().replaceFirstChar { it.uppercase() }
}
