package com.habitflow.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.data.AppDb
import com.habitflow.app.data.Category
import com.habitflow.app.data.Engine
import com.habitflow.app.data.Entry
import com.habitflow.app.data.HType
import com.habitflow.app.data.Habit
import com.habitflow.app.data.Note
import com.habitflow.app.data.Prefs
import com.habitflow.app.data.Repo
import com.habitflow.app.data.Snap
import com.habitflow.app.data.t
import com.habitflow.app.notify.HabitWidget
import com.habitflow.app.notify.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AppVM(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val repo = Repo(app, dao)
    val prefs = Prefs(app)
    private val mu = Mutex()

    val toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val today = MutableStateFlow(Engine.today())

    val snap: StateFlow<Snap> = combine(dao.habits(), dao.entries()) { h, e -> Snap.build(h, e) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Snap.EMPTY)

    val cats: StateFlow<List<Category>> = dao.categories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notes: StateFlow<Map<Long, String>> = dao.notes().map { l -> l.associate { it.day to it.text } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init { autoBackup() }

    fun refreshToday() { today.value = Engine.today() }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try { mu.withLock { block() } } catch (e: Exception) { toast.tryEmit("Error: ${e.message}") }
        HabitWidget.refresh(app)
    }

    // ---------- tracking ----------
    private suspend fun mutate(h: Habit, day: Long, f: (Entry) -> Entry) {
        val cur = dao.entry(h.id, day) ?: Entry(h.id, day)
        dao.upsertEntry(f(cur))
    }

    fun setValue(h: Habit, day: Long, v: Double) = io {
        mutate(h, day) {
            val nv = maxOf(0.0, v)
            // DURATION is stored in seconds; the UI works in minutes.
            if (h.t() == HType.DURATION) it.copy(value = nv * 60, state = 0, timerStart = if (it.timerStart > 0) System.currentTimeMillis() else 0)
            else it.copy(value = nv, state = 0)
        }
    }

    fun addValue(h: Habit, day: Long, delta: Double) = io {
        mutate(h, day) { e ->
            if (h.t() == HType.DURATION) {
                val now = System.currentTimeMillis()
                val secs = e.value + (if (e.timerStart > 0) (now - e.timerStart) / 1000.0 else 0.0)
                e.copy(value = maxOf(0.0, secs + delta * 60), state = 0, timerStart = if (e.timerStart > 0) now else 0)
            } else e.copy(value = maxOf(0.0, e.value + delta), state = 0)
        }
    }

    fun toggle(h: Habit, day: Long) = io {
        mutate(h, day) { it.copy(value = if (it.value >= 1) 0.0 else 1.0, state = 0) }
    }

    fun setAvoid(h: Habit, day: Long, success: Boolean) = io {
        mutate(h, day) { it.copy(value = if (success) 1.0 else -1.0, state = 0) }
    }

    fun setState(h: Habit, day: Long, state: Int) = io {
        mutate(h, day) { it.copy(state = state) }
    }

    fun clearDay(h: Habit, day: Long) = io { dao.deleteEntry(h.id, day) }

    fun markDayState(day: Long, state: Int) = io {
        snap.value.habits.filter { it.status == 0 && Engine.inRange(it, day) && (it.t() == HType.WEEKLY || Engine.scheduled(it, day)) }.forEach { h ->
            if (state == 0) { dao.entry(h.id, day)?.takeIf { it.state != 0 }?.let { dao.upsertEntry(it.copy(state = 0)) } }
            else mutate(h, day) { it.copy(state = state) }
        }
    }

    fun timer(h: Habit, start: Boolean) = io {
        val today = Engine.today()
        val now = System.currentTimeMillis()
        mutate(h, today) { e ->
            if (start && e.timerStart == 0L) e.copy(timerStart = now, state = 0)
            else if (!start && e.timerStart > 0) e.copy(value = e.value + (now - e.timerStart) / 1000.0, timerStart = 0)
            else e
        }
    }

    fun setSleep(h: Habit, day: Long, bed: Int, wake: Int) = io {
        mutate(h, day) { e ->
            val b = if (bed >= 0) bed else e.bedMin
            val w = if (wake >= 0) wake else e.wakeMin
            val hours = if (b >= 0 && w >= 0) ((w - b + 1440) % 1440) / 60.0 else e.value
            e.copy(bedMin = b, wakeMin = w, value = hours, state = 0)
        }
    }

    // ---------- habits ----------
    fun saveHabit(h: Habit, onDone: () -> Unit = {}) = io {
        val id = dao.upsertHabit(h)
        val saved = dao.habit(if (h.id == 0L) id else h.id)
        if (saved != null) Scheduler.schedule(app, saved)
        withContext(Dispatchers.Main) { onDone() }
    }

    fun quickAdd(name: String, icon: String, type: HType, target: Double, unit: String, color: Int) =
        saveHabit(Habit(name = name, icon = icon, type = type.name, target = target, unit = unit,
            color = color, startDay = Engine.today()))

    fun setStatus(h: Habit, status: Int) = io {
        val upd = h.copy(status = status, inactiveSince = if (status == 0) -1 else Engine.today())
        dao.upsertHabit(upd)
        Scheduler.schedule(app, upd)
    }

    fun deleteHabit(h: Habit) = io {
        Scheduler.cancel(app, h.id)
        dao.deleteEntriesOf(h.id)
        dao.deleteHabit(h.id)
    }

    // ---------- categories & notes ----------
    fun saveCategory(c: Category) = io { dao.upsertCategory(c) }
    fun deleteCategory(c: Category) = io { dao.clearCategory(c.id); dao.deleteCategory(c.id) }

    fun saveNote(day: Long, text: String) = viewModelScope.launch(Dispatchers.IO) {
        if (text.isBlank()) dao.deleteNote(day) else dao.upsertNote(Note(day, text))
    }

    // ---------- background ----------
    fun setBackground(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val f = File(app.filesDir, "bg_${System.currentTimeMillis()}.img")
            app.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            val old = prefs.bgPath
            withContext(Dispatchers.Main) { prefs.setBg(f.absolutePath) }
            if (old.isNotBlank()) File(old).delete()
        } catch (e: Exception) { toast.tryEmit("Could not set background") }
    }

    fun clearBackground() = viewModelScope.launch(Dispatchers.IO) {
        val old = prefs.bgPath
        withContext(Dispatchers.Main) { prefs.setBg("") }
        if (old.isNotBlank()) File(old).delete()
    }

    // ---------- backup ----------
    private fun autoBackup() = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (System.currentTimeMillis() - prefs.lastAutoBackup > 24 * 3600_000L && dao.habitsNow().isNotEmpty()) {
                repo.localBackup(); prefs.lastAutoBackup = System.currentTimeMillis()
            }
        } catch (_: Exception) {}
    }

    fun reschedule() = viewModelScope.launch(Dispatchers.IO) {
        Scheduler.scheduleAll(app, dao.habitsNow()); toast.tryEmit("Reminders refreshed")
    }

    fun exportTo(uri: Uri, csv: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = if (csv) repo.exportCsv() else repo.exportJson()
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            toast.tryEmit("Exported")
        } catch (e: Exception) { toast.tryEmit("Export failed: ${e.message}") }
    }

    fun importFrom(uri: Uri, csv: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = app.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
            if (csv) { val n = repo.importCsv(text); toast.tryEmit("Imported $n rows") }
            else { repo.importJson(text); afterRestore(); toast.tryEmit("Data restored") }
        } catch (e: Exception) { toast.tryEmit("Import failed: ${e.message}") }
    }

    fun backupNow() = viewModelScope.launch(Dispatchers.IO) {
        try { repo.localBackup(); toast.tryEmit("Local backup saved") } catch (e: Exception) { toast.tryEmit("Backup failed") }
    }

    fun restoreLocal(f: File) = viewModelScope.launch(Dispatchers.IO) {
        try { repo.importJson(f.readText()); afterRestore(); toast.tryEmit("Backup restored") }
        catch (e: Exception) { toast.tryEmit("Restore failed: ${e.message}") }
    }

    private suspend fun afterRestore() {
        val hs = dao.habitsNow()
        hs.forEach { Scheduler.cancel(app, it.id) }
        Scheduler.scheduleAll(app, hs)
        HabitWidget.refresh(app)
    }
}
