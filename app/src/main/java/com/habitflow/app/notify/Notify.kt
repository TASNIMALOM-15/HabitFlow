package com.habitflow.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.habitflow.app.MainActivity
import com.habitflow.app.R
import com.habitflow.app.data.AppDb
import com.habitflow.app.data.Engine
import com.habitflow.app.data.Entry
import com.habitflow.app.data.HType
import com.habitflow.app.data.Habit
import com.habitflow.app.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

const val CHANNEL = "habit_reminders"
const val EXTRA_HABIT = "habitId"
const val EXTRA_IDX = "idx"
const val ACTION_DONE = "com.habitflow.app.ACTION_DONE"
private const val INTERVAL_IDX = 99

object Scheduler {
    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun pi(ctx: Context, habitId: Long, idx: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_HABIT, habitId).putExtra(EXTRA_IDX, idx)
        return PendingIntent.getBroadcast(
            ctx, (habitId * 100 + idx).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(ctx: Context, at: Long, p: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    private fun nextAt(minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        var t = LocalDate.now().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!t.isAfter(now.plusSeconds(1))) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun nextInterval(h: Habit): Long {
        val slots = Engine.intervalSlots(h)
        val nowMin = LocalDateTime.now().let { it.hour * 60 + it.minute }
        val s = slots.firstOrNull { it > nowMin }
        return if (s != null) {
            LocalDate.now().atTime(s / 60, s % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else nextAt(slots.firstOrNull() ?: h.winStart)
    }

    fun cancel(ctx: Context, habitId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (i in 0..40) am.cancel(pi(ctx, habitId, i))
        am.cancel(pi(ctx, habitId, INTERVAL_IDX))
    }

    fun schedule(ctx: Context, h: Habit) {
        cancel(ctx, h.id)
        if (h.status != 0) return
        if (h.t() == HType.INTERVAL) {
            setAlarm(ctx, nextInterval(h), pi(ctx, h.id, INTERVAL_IDX))
        } else {
            Engine.reminderMinutes(h).take(40).forEachIndexed { i, m -> setAlarm(ctx, nextAt(m), pi(ctx, h.id, i)) }
        }
    }

    /** Re-arms the single alarm that just fired. */
    fun rearm(ctx: Context, h: Habit, idx: Int) {
        if (h.status != 0) return
        if (idx == INTERVAL_IDX) setAlarm(ctx, nextInterval(h), pi(ctx, h.id, idx))
        else Engine.reminderMinutes(h).getOrNull(idx)?.let { setAlarm(ctx, nextAt(it), pi(ctx, h.id, idx)) }
    }

    fun scheduleAll(ctx: Context, habits: List<Habit>) = habits.forEach { schedule(ctx, it) }

    fun test(ctx: Context) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("HabitFlow").setContentText("Notifications are working 🎉").setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(9999, n) } catch (_: SecurityException) {}
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_HABIT, -1)
        val idx = intent.getIntExtra(EXTRA_IDX, 0)
        val pending = goAsync()
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(app).dao()
                val h = dao.habit(id)
                if (h != null) {
                    Scheduler.rearm(app, h, idx)
                    val today = Engine.today()
                    val e = dao.entry(h.id, today)
                    val now = System.currentTimeMillis()
                    val show = Engine.dueToday(h, today) && e?.state != 1 && e?.state != 2 &&
                        !Engine.isDone(h, e, now) && !(h.t() == HType.DURATION && (e?.timerStart ?: 0) > 0)
                    if (show) post(app, h, e, now)
                }
            } finally { pending.finish() }
        }
    }

    private fun post(ctx: Context, h: Habit, e: Entry?, now: Long) {
        Scheduler.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cur = Engine.current(h, e, now)
        val text = when (h.t()) {
            HType.MULTIPLE, HType.INTERVAL, HType.QUANTITY ->
                "${fmt(cur)}/${fmt(h.target)} ${h.unit} done so far"
            HType.DURATION -> "${fmt(cur)}/${fmt(h.target)} min so far"
            HType.AVOID -> "Stay strong today 💪"
            HType.SLEEP -> "Time to log your sleep"
            else -> "Time for your habit"
        }
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${h.icon} ${h.name}")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (h.t() in setOf(HType.DAILY, HType.SCHEDULED, HType.AVOID, HType.MULTIPLE, HType.INTERVAL, HType.QUANTITY)) {
            val label = if (h.t() == HType.MULTIPLE || h.t() == HType.INTERVAL || h.t() == HType.QUANTITY) "+1" else "Done"
            val a = PendingIntent.getBroadcast(
                ctx, h.id.toInt(), Intent(ctx, ActionReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_HABIT, h.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(0, label, a)
        }
        try { NotificationManagerCompat.from(ctx).notify(h.id.toInt(), b.build()) } catch (_: SecurityException) {}
    }

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_HABIT, -1)
        val pending = goAsync()
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(app).dao()
                val h = dao.habit(id) ?: return@launch
                val today = Engine.today()
                val e = dao.entry(id, today) ?: Entry(id, today)
                val nv = when (h.t()) {
                    HType.MULTIPLE, HType.INTERVAL, HType.QUANTITY -> minOf(e.value + 1, maxOf(h.target, e.value + 1))
                    else -> 1.0
                }
                dao.upsertEntry(e.copy(value = nv, state = 0))
                NotificationManagerCompat.from(app).cancel(id.toInt())
                HabitWidget.refresh(app)
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Scheduler.scheduleAll(app, AppDb.get(app).dao().habitsNow())
                HabitWidget.refresh(app)
            } finally { pending.finish() }
        }
    }
}
