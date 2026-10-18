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

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        val task = TaskStore.find(context, id) ?: return
        if (!task.active || task.done) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "once_reminders_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Lembretes persistentes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes do ONCE IN A WHILE"; enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            })
        }
        val open = PendingIntent.getActivity(context, id.toInt(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val donePi = PendingIntent.getBroadcast(context, id.toInt(), Intent(context, CompleteReceiver::class.java).putExtra("taskId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze15 = PendingIntent.getBroadcast(context, id.toInt()+1, Intent(context, SnoozeReceiver::class.java).putExtra("taskId", id).putExtra("minutes", 15), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze30 = PendingIntent.getBroadcast(context, id.toInt()+2, Intent(context, SnoozeReceiver::class.java).putExtra("taskId", id).putExtra("minutes", 30), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("ONCE IN A WHILE")
            .setContentText(task.title).setStyle(NotificationCompat.BigTextStyle().bigText("${task.title} · ${task.category}"))
            .setContentIntent(open).setAutoCancel(false).setOngoing(true)
            .addAction(android.R.drawable.checkbox_on_background, "Concluir", donePi)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "15 min", snooze15)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "30 min", snooze30)
            .setPriority(NotificationCompat.PRIORITY_MAX).build()
        nm.notify(id.toInt(), notification)
        TaskStore.upsert(context, task.copy(remindersSent = task.remindersSent + 1))
        ReminderScheduler.scheduleRetry(context, task)
    }
}
class CompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        if (id >= 0) {
            TaskStore.markDone(context, id)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
        }
    }
}
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        val minutes = intent.getIntExtra("minutes", 15)
        val task = TaskStore.find(context, id) ?: return
        if (task.active && !task.done) {
            ReminderScheduler.cancel(context, id)
            ReminderScheduler.schedule(context, task, System.currentTimeMillis() + minutes * 60_000L)
        }
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.resetRecurringTasks(context)
        TaskStore.all(context).filter { it.active && !it.done }.forEach { task ->
            val at = if (task.dueAt <= System.currentTimeMillis()) System.currentTimeMillis() + 60_000L else task.dueAt
            ReminderScheduler.schedule(context, task, at)
        }
    }
}
