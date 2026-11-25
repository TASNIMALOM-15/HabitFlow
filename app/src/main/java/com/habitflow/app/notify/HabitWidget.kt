package com.habitflow.app.notify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.habitflow.app.MainActivity
import com.habitflow.app.R
import com.habitflow.app.data.AppDb
import com.habitflow.app.data.Engine
import com.habitflow.app.data.HType
import com.habitflow.app.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try { render(app, mgr, ids) } finally { pending.finish() }
        }
    }

    companion object {
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val mgr = AppWidgetManager.getInstance(app)
                val ids = mgr.getAppWidgetIds(ComponentName(app, HabitWidget::class.java))
                if (ids.isNotEmpty()) render(app, mgr, ids)
            }
        }

        private suspend fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val dao = AppDb.get(ctx).dao()
            val today = Engine.today()
            val now = System.currentTimeMillis()
            val habits = dao.habitsNow().filter { Engine.dueToday(it, today) }
            val entries = dao.entriesOn(today).associateBy { it.habitId }
            val active = habits.filter { entries[it.id]?.state.let { s -> s != 1 && s != 2 } }
            val sum = active.sumOf { Engine.progress(it, entries[it.id], now).toDouble() }
            val pct = if (active.isEmpty()) 0 else (sum / active.size * 100).toInt()
            val done = active.count { Engine.isDone(it, entries[it.id], now) }

            val openApp = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val lineIds = intArrayOf(R.id.w_l1, R.id.w_l2, R.id.w_l3, R.id.w_l4, R.id.w_l5)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_habits)
            rv.setTextViewText(R.id.w_title, "Today · $done/${active.size} done · $pct%")
            rv.setProgressBar(R.id.w_bar, 100, pct, false)
            // Unfinished habits first.
            val sorted = active.sortedBy { Engine.isDone(it, entries[it.id], now) }
            lineIds.forEachIndexed { i, id ->
                val h = sorted.getOrNull(i)
                if (h == null) {
                    rv.setViewVisibility(id, if (i == 0 && active.isEmpty()) View.VISIBLE else View.GONE)
                    if (i == 0 && active.isEmpty()) rv.setTextViewText(id, "No habits for today")
                } else {
                    val e = entries[h.id]
                    val d = Engine.isDone(h, e, now)
                    val cur = Engine.current(h, e, now)
                    val detail = when (h.t()) {
                        HType.QUANTITY, HType.MULTIPLE, HType.INTERVAL, HType.DURATION, HType.SLEEP ->
                            "  ${fmt(cur)}/${fmt(h.target)}"
                        else -> ""
                    }
                    rv.setViewVisibility(id, View.VISIBLE)
                    rv.setTextViewText(id, (if (d) "✓ " else "○ ") + h.icon + " " + h.name + detail)
                }
            }
            rv.setOnClickPendingIntent(R.id.w_root, openApp)
            mgr.updateAppWidget(ids, rv)
        }

        private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
    }
}
