package com.onceinawhile.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TaskStore {
    private const val PREFS = "once_in_a_while"
    private const val TASKS = "tasks"
    private val gson = Gson()

    fun all(context: Context): MutableList<Task> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TASKS, "[]") ?: "[]"
        val type = object : TypeToken<MutableList<Task>>() {}.type
        return try { gson.fromJson<MutableList<Task>>(raw, type) ?: mutableListOf() }
        catch (_: Exception) { mutableListOf() }
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
        if (task.recurrence == "Única") {
            upsert(context, task.copy(done = true, active = false))
        } else {
            val next = nextOccurrence(task)
            upsert(context, task.copy(dueAt = next, done = false, remindersSent = 0))
        }
        ReminderScheduler.cancel(context, id)
        find(context, id)?.takeIf { it.active && !it.done }?.let { ReminderScheduler.schedule(context, it) }
    }

    fun nextOccurrence(task: Task): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = task.dueAt }
        val now = System.currentTimeMillis()
        do {
            when (task.recurrence) {
                "Diária" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                "Semanal" -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                "Mensal" -> cal.add(java.util.Calendar.MONTH, 1)
                "Anual" -> cal.add(java.util.Calendar.YEAR, 1)
                else -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        } while (cal.timeInMillis <= now)
        return cal.timeInMillis
    }
}
