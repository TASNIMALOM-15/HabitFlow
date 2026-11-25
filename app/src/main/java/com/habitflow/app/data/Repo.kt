package com.habitflow.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** JSON / CSV export-import and local backups. */
class Repo(private val ctx: Context, private val dao: HabitDao) {

    private fun habitJson(h: Habit) = JSONObject().apply {
        put("id", h.id); put("name", h.name); put("icon", h.icon); put("color", h.color)
        put("type", h.type); put("target", h.target); put("unit", h.unit)
        put("daysMask", h.daysMask); put("weeklyTarget", h.weeklyTarget)
        put("startDay", h.startDay); put("endDay", h.endDay); put("reminders", h.reminders)
        put("intervalMin", h.intervalMin); put("winStart", h.winStart); put("winEnd", h.winEnd)
        put("timeOfDay", h.timeOfDay); put("notes", h.notes); put("categoryId", h.categoryId)
        put("status", h.status); put("inactiveSince", h.inactiveSince); put("createdAt", h.createdAt)
    }

    private fun habitFrom(o: JSONObject) = Habit(
        id = o.optLong("id"), name = o.optString("name"), icon = o.optString("icon", "✅"),
        color = o.optInt("color", 0xFF3B82F6.toInt()), type = o.optString("type", "DAILY"),
        target = o.optDouble("target", 1.0), unit = o.optString("unit"),
        daysMask = o.optInt("daysMask", 127), weeklyTarget = o.optInt("weeklyTarget", 3),
        startDay = o.optLong("startDay"), endDay = o.optLong("endDay", -1),
        reminders = o.optString("reminders"), intervalMin = o.optInt("intervalMin", 120),
        winStart = o.optInt("winStart", 480), winEnd = o.optInt("winEnd", 1320),
        timeOfDay = o.optInt("timeOfDay", 480), notes = o.optString("notes"),
        categoryId = o.optLong("categoryId"), status = o.optInt("status"),
        inactiveSince = o.optLong("inactiveSince", -1), createdAt = o.optLong("createdAt")
    )

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "HabitFlow"); root.put("version", 1); root.put("exportedAt", System.currentTimeMillis())
        root.put("habits", JSONArray().also { a -> dao.habitsNow().forEach { a.put(habitJson(it)) } })
        root.put("entries", JSONArray().also { a ->
            dao.entriesNow().forEach {
                a.put(JSONObject().put("habitId", it.habitId).put("day", it.day).put("value", it.value)
                    .put("state", it.state).put("timerStart", it.timerStart)
                    .put("bedMin", it.bedMin).put("wakeMin", it.wakeMin))
            }
        })
        root.put("categories", JSONArray().also { a ->
            dao.categoriesNow().forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        })
        root.put("notes", JSONArray().also { a ->
            dao.notesNow().forEach { a.put(JSONObject().put("day", it.day).put("text", it.text)) }
        })
        return root.toString()
    }

    /** Replaces all data. Throws on invalid input (nothing is changed in that case). */
    suspend fun importJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("app") == "HabitFlow") { "Not a HabitFlow backup" }
        val habits = root.getJSONArray("habits").let { a -> (0 until a.length()).map { habitFrom(a.getJSONObject(it)) } }
        val entries = root.getJSONArray("entries").let { a ->
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Entry(o.getLong("habitId"), o.getLong("day"), o.optDouble("value", 0.0), o.optInt("state"),
                    o.optLong("timerStart"), o.optInt("bedMin", -1), o.optInt("wakeMin", -1))
            }
        }
        val cats = root.optJSONArray("categories")?.let { a ->
            (0 until a.length()).map { val o = a.getJSONObject(it); Category(o.getLong("id"), o.getString("name"), o.optInt("color")) }
        } ?: emptyList()
        val notes = root.optJSONArray("notes")?.let { a ->
            (0 until a.length()).map { val o = a.getJSONObject(it); Note(o.getLong("day"), o.getString("text")) }
        } ?: emptyList()
        dao.replaceAll(habits, entries, cats, notes)
    }

    private fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** value column: DURATION habits are in seconds. */
    suspend fun exportCsv(): String {
        val names = dao.habitsNow().associate { it.id to it.name }
        val sb = StringBuilder("habit,date,value,state,bed_min,wake_min\n")
        dao.entriesNow().sortedWith(compareBy({ it.habitId }, { it.day })).forEach {
            val n = names[it.habitId] ?: return@forEach
            sb.append(esc(n)).append(',').append(LocalDate.ofEpochDay(it.day)).append(',')
                .append(it.value).append(',').append(it.state).append(',')
                .append(it.bedMin).append(',').append(it.wakeMin).append('\n')
        }
        return sb.toString()
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    /** Merges entries into existing habits (matched by name). Returns number of imported rows. */
    suspend fun importCsv(text: String): Int {
        val byName = dao.habitsNow().associateBy { it.name.lowercase() }
        val rows = ArrayList<Entry>()
        text.lineSequence().drop(1).filter { it.isNotBlank() }.forEach { line ->
            val p = parseCsvLine(line)
            if (p.size < 4) return@forEach
            val h = byName[p[0].lowercase()] ?: return@forEach
            val day = runCatching { LocalDate.parse(p[1]).toEpochDay() }.getOrNull() ?: return@forEach
            rows.add(Entry(h.id, day, p[2].toDoubleOrNull() ?: 0.0, p[3].toIntOrNull() ?: 0, 0,
                p.getOrNull(4)?.toIntOrNull() ?: -1, p.getOrNull(5)?.toIntOrNull() ?: -1))
        }
        dao.upsertEntries(rows)
        return rows.size
    }

    private fun dir() = File(ctx.filesDir, "backups").also { it.mkdirs() }

    suspend fun localBackup(): File {
        val f = File(dir(), "backup_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json")
        f.writeText(exportJson())
        dir().listFiles()?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
        return f
    }

    fun listBackups(): List<File> = dir().listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.name } ?: emptyList()
}
