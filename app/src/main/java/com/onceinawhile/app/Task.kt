package com.onceinawhile.app

data class Task(
    val id: Long,
    val title: String,
    val category: String,
    val dueAt: Long,
    val recurrence: String,
    val done: Boolean = false,
    val remindersSent: Int = 0,
    val active: Boolean = true
)
