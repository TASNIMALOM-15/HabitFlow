package com.habitflow.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Habit configuration. Daily tracking records live separately in [Entry]. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "✅",
    val color: Int = 0xFF3B82F6.toInt(),
    val type: String = "DAILY",
    val target: Double = 1.0,
    val unit: String = "",
    val daysMask: Int = 127,          // bit0 = Monday ... bit6 = Sunday
    val weeklyTarget: Int = 3,
    val startDay: Long = 0,           // epochDay
    val endDay: Long = -1,            // epochDay, -1 = never
    val reminders: String = "",       // "08:00,20:30"
    val intervalMin: Int = 120,
    val winStart: Int = 480,          // minutes of day
    val winEnd: Int = 1320,
    val timeOfDay: Int = 480,         // for SCHEDULED
    val notes: String = "",
    val categoryId: Long = 0,
    val status: Int = 0,              // 0 active, 1 paused, 2 archived
    val inactiveSince: Long = -1,     // epochDay when paused / archived
    val createdAt: Long = System.currentTimeMillis()
)

/** One row per habit per day. */
@Entity(tableName = "entries", primaryKeys = ["habitId", "day"], indices = [Index("day")])
data class Entry(
    val habitId: Long,
    val day: Long,
    val value: Double = 0.0,          // DURATION: seconds
    val state: Int = 0,               // 0 normal, 1 skip, 2 rest
    val timerStart: Long = 0,         // millis, >0 while a timer is running
    val bedMin: Int = -1,
    val wakeMin: Int = -1
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0xFF3B82F6.toInt()
)

@Entity(tableName = "notes")
data class Note(@PrimaryKey val day: Long, val text: String)

@Dao
abstract class HabitDao {
    @Query("SELECT * FROM habits ORDER BY id") abstract fun habits(): Flow<List<Habit>>
    @Query("SELECT * FROM habits ORDER BY id") abstract suspend fun habitsNow(): List<Habit>
    @Query("SELECT * FROM habits WHERE id = :id") abstract suspend fun habit(id: Long): Habit?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertHabit(h: Habit): Long
    @Query("DELETE FROM habits WHERE id = :id") abstract suspend fun deleteHabit(id: Long)

    @Query("SELECT * FROM entries") abstract fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM entries") abstract suspend fun entriesNow(): List<Entry>
    @Query("SELECT * FROM entries WHERE day = :d") abstract suspend fun entriesOn(d: Long): List<Entry>
    @Query("SELECT * FROM entries WHERE habitId = :h AND day = :d") abstract suspend fun entry(h: Long, d: Long): Entry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertEntry(e: Entry)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertEntries(l: List<Entry>)
    @Query("DELETE FROM entries WHERE habitId = :h") abstract suspend fun deleteEntriesOf(h: Long)
    @Query("DELETE FROM entries WHERE habitId = :h AND day = :d") abstract suspend fun deleteEntry(h: Long, d: Long)

    @Query("SELECT * FROM categories ORDER BY id") abstract fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY id") abstract suspend fun categoriesNow(): List<Category>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertCategory(c: Category): Long
    @Query("DELETE FROM categories WHERE id = :id") abstract suspend fun deleteCategory(id: Long)
    @Query("UPDATE habits SET categoryId = 0 WHERE categoryId = :id") abstract suspend fun clearCategory(id: Long)

    @Query("SELECT * FROM notes") abstract fun notes(): Flow<List<Note>>
    @Query("SELECT * FROM notes") abstract suspend fun notesNow(): List<Note>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertNote(n: Note)
    @Query("DELETE FROM notes WHERE day = :d") abstract suspend fun deleteNote(d: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertNotes(l: List<Note>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertHabits(l: List<Habit>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertCategories(l: List<Category>)
    @Query("DELETE FROM habits") abstract suspend fun wipeHabits()
    @Query("DELETE FROM entries") abstract suspend fun wipeEntries()
    @Query("DELETE FROM categories") abstract suspend fun wipeCategories()
    @Query("DELETE FROM notes") abstract suspend fun wipeNotes()

    @Transaction
    open suspend fun replaceAll(h: List<Habit>, e: List<Entry>, c: List<Category>, n: List<Note>) {
        wipeHabits(); wipeEntries(); wipeCategories(); wipeNotes()
        upsertCategories(c); upsertHabits(h); upsertEntries(e); upsertNotes(n)
    }
}

@Database(entities = [Habit::class, Entry::class, Category::class, Note::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): HabitDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "habitflow.db")
                .build().also { inst = it }
        }
    }
}
