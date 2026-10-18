package com.onceinawhile.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TaskStore {
    private const val PREFS = "once_in_a_while"
    private const val TASKS = "tasks"
    private val gson = Gson()
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    fun all(context: Context): MutableList<Task> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TASKS, "[]") ?: "[]"
        val type = object : TypeToken<MutableList<Task>>() {}.type
        val loaded = try { gson.fromJson<MutableList<Task>>(raw, type) ?: mutableListOf() }
        catch (_: Exception) { mutableListOf() }
        var changed = false
        val normalized = loaded.map { task ->
            if (task.recurrence != "Única" && task.done && task.dailyDoneDate != today()) {
                changed = true
                task.copy(done = false, dailyDoneDate = "", remindersSent = 0)
            } else task
        }.toMutableList()
        if (changed) saveAll(context, normalized)
        return normalized
    }

    @Synchronized fun saveAll(context: Context, tasks: List<Task>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TASKS, gson.toJson(tasks)).apply()
    }

    @Synchronized fun upsert(context: Context, task: Task) {
        val tasks = all(context)
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = task else tasks.add(task)
        saveAll(context, tasks)
    }

    @Synchronized fun find(context: Context, id: Long): Task? = all(context).firstOrNull { it.id == id }

    @Synchronized fun markDone(context: Context, id: Long) {
        val task = find(context, id) ?: return
        val updated = if (task.recurrence == "Única") {
            task.copy(done = true, active = false)
        } else {
            task.copy(done = true, dailyDoneDate = today(), remindersSent = 0, dueAt = nextOccurrence(task))
        }
        upsert(context, updated)
        ReminderScheduler.cancel(context, id)
        if (updated.active && updated.recurrence != "Única") {
            // A receiver can reset the stored daily completion when the date changes.
            ReminderScheduler.schedule(context, updated.copy(done = false), updated.dueAt)
        }
    }

    fun resetRecurringTasks(context: Context) {
        val date = today()
        all(context).filter { it.active && it.recurrence != "Única" && it.done && it.dailyDoneDate != date }.forEach { old ->
            val next = old.copy(done = false, dailyDoneDate = "", remindersSent = 0, dueAt = nextOccurrence(old))
            upsert(context, next)
            ReminderScheduler.schedule(context, next, maxOf(next.dueAt, System.currentTimeMillis() + 1000L))
        }
    }

    fun nextOccurrence(task: Task): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = task.dueAt }
        val now = System.currentTimeMillis()
        do {
            when (task.recurrence) {
                "Diária" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "Semanal" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                "Mensal" -> cal.add(Calendar.MONTH, 1)
                "Anual" -> cal.add(Calendar.YEAR, 1)
                else -> cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } while (cal.timeInMillis <= now)
        return cal.timeInMillis
    }

    fun exportJson(context: Context): String = gson.toJson(all(context))
    fun importJson(context: Context, json: String): Boolean {
        return try {
            val type = object : TypeToken<List<Task>>() {}.type
            val imported: List<Task> = gson.fromJson(json, type) ?: return false
            saveAll(context, imported)
            true
        } catch (_: Exception) { false }
    }
}
