package com.onceinawhile.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        if (id < 0L) return

        val task = TaskStore.find(context, id) ?: return
        if (!task.active || task.done) return

        val now = System.currentTimeMillis()

        if (task.snoozedUntil > now) {
            ReminderScheduler.schedule(context, task, task.snoozedUntil)
            return
        }

        if (task.scheduleMode == "periods" &&
            task.periods.isNotEmpty() &&
            !insideSelectedPeriod(task.periods, now)
        ) {
            ReminderScheduler.schedule(
                context,
                task,
                nextSelectedPeriod(task.periods, now)
            )
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channelId = "once_reminders_v2"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            nm.getNotificationChannel(channelId) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Lembretes persistentes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Lembretes do ONCE IN A WHILE"
                    enableVibration(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                }
            )
        }

        val open = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val donePi = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, CompleteReceiver::class.java)
                .putExtra("taskId", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze15 = PendingIntent.getBroadcast(
            context,
            id.toInt() + 1,
            Intent(context, SnoozeReceiver::class.java)
                .putExtra("taskId", id)
                .putExtra("minutes", 15),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze30 = PendingIntent.getBroadcast(
            context,
            id.toInt() + 2,
            Intent(context, SnoozeReceiver::class.java)
                .putExtra("taskId", id)
                .putExtra("minutes", 30),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("ONCE IN A WHILE")
            .setContentText(task.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${task.title} · ${task.category}")
            )
            .setContentIntent(open)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Concluir",
                donePi
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "15 min",
                snooze15
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "30 min",
                snooze30
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        nm.notify(id.toInt(), notification)

        TaskStore.upsert(
            context,
            task.copy(
                remindersSent = task.remindersSent + 1,
                snoozedUntil = 0L
            )
        )

        // Continua a insistir de cinco em cinco minutos enquanto pendente.
        ReminderScheduler.scheduleRetry(context, task)
    }

    private fun insideSelectedPeriod(
        periods: List<String>,
        time: Long
    ): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val minute = c.get(Calendar.HOUR_OF_DAY) * 60 +
            c.get(Calendar.MINUTE)

        return periods.any { period ->
            when (period) {
                "Manhã" -> minute in 480 until 720
                "Tarde" -> minute in 720 until 1080
                "Noite" -> minute in 1080 until 1320
                else -> false
            }
        }
    }

    private fun nextSelectedPeriod(
        periods: List<String>,
        time: Long
    ): Long {
        val starts = mapOf(
            "Manhã" to 8,
            "Tarde" to 12,
            "Noite" to 18
        )

        val now = Calendar.getInstance().apply { timeInMillis = time }
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 +
            now.get(Calendar.MINUTE)

        val nextToday = periods
            .mapNotNull { starts[it]?.times(60) }
            .filter { it > minute }
            .minOrNull()

        val target = Calendar.getInstance().apply {
            timeInMillis = time

            val targetMinute = nextToday
                ?: periods.mapNotNull { starts[it] }
                    .minOrNull()?.times(60)
                ?: 480

            if (nextToday == null) add(Calendar.DAY_OF_YEAR, 1)

            set(Calendar.HOUR_OF_DAY, targetMinute / 60)
            set(Calendar.MINUTE, targetMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return target.timeInMillis
    }
}

class CompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        if (id >= 0L) {
            TaskStore.markDone(context, id)
            (context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager).cancel(id.toInt())
        }
    }
}

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        val minutes = intent.getIntExtra("minutes", 15)
        val task = TaskStore.find(context, id) ?: return

        if (task.active && !task.done) {
            val until = System.currentTimeMillis() + minutes * 60_000L
            ReminderScheduler.cancel(context, id)
            val updated = task.copy(snoozedUntil = until)
            TaskStore.upsert(context, updated)
            ReminderScheduler.schedule(context, updated, until)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.resetRecurringTasks(context)

        TaskStore.all(context)
            .filter { it.active && !it.done }
            .forEach { task ->
                val at = if (task.dueAt <= System.currentTimeMillis()) {
                    System.currentTimeMillis() + 60_000L
                } else {
                    task.dueAt
                }
                ReminderScheduler.schedule(context, task, at)
            }
    }
}
