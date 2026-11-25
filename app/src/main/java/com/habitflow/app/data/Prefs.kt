package com.habitflow.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hf_prefs", Context.MODE_PRIVATE)

    private var themeState by mutableIntStateOf(sp.getInt("theme", 0))
    val theme: Int get() = themeState
    private var paletteState by mutableIntStateOf(sp.getInt("palette", 0))
    val palette: Int get() = paletteState
    private var weekStartState by mutableIntStateOf(sp.getInt("weekStart", 1))
    val weekStart: Int get() = weekStartState
    private var time24State by mutableStateOf(sp.getBoolean("time24", false))
    val time24: Boolean get() = time24State
    private var dateFmtState by mutableIntStateOf(sp.getInt("dateFmt", 0))
    val dateFmt: Int get() = dateFmtState
    var bgPath by mutableStateOf(sp.getString("bgPath", "") ?: ""); private set
    var lastAutoBackup: Long
        get() = sp.getLong("lastAutoBackup", 0)
        set(v) { sp.edit().putLong("lastAutoBackup", v).apply() }

    fun setTheme(v: Int) { themeState = v; sp.edit().putInt("theme", v).apply() }
    fun setPalette(v: Int) { paletteState = v; sp.edit().putInt("palette", v).apply() }
    fun setWeekStart(v: Int) { weekStartState = v; sp.edit().putInt("weekStart", v).apply() }
    fun setTime24(v: Boolean) { time24State = v; sp.edit().putBoolean("time24", v).apply() }
    fun setDateFmt(v: Int) { dateFmtState = v; sp.edit().putInt("dateFmt", v).apply() }
    fun setBg(v: String) { bgPath = v; sp.edit().putString("bgPath", v).apply() }

    fun fmtDate(day: Long, f: Int = dateFmt): String {
        val p = when (f) { 1 -> "MM/dd/yyyy"; 2 -> "yyyy-MM-dd"; else -> "dd MMM yyyy" }
        return LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(p, Locale.getDefault()))
    }

    fun fmtTime(min: Int): String {
        val h = min / 60; val m = min % 60
        if (time24) return "%02d:%02d".format(h, m)
        val ap = if (h < 12) "AM" else "PM"
        val hh = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(hh, m, ap)
    }
}
