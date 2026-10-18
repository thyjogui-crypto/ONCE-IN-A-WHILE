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
        val channelId = "once_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes das tarefas do ONCE IN A WHILE"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            }
            nm.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            context, id.toInt(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneIntent = Intent(context, CompleteReceiver::class.java).putExtra("taskId", id)
        val donePi = PendingIntent.getBroadcast(
            context, id.toInt(), doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("ONCE IN A WHILE")
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${task.title} · ${task.category}"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Concluir", donePi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(id.toInt(), notification)

        val updated = task.copy(remindersSent = task.remindersSent + 1)
        TaskStore.upsert(context, updated)
        ReminderScheduler.scheduleRetry(context, updated)
    }
}

class CompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        if (id >= 0) {
            TaskStore.markDone(context, id)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(id.toInt())
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskStore.all(context).filter { it.active && !it.done }.forEach { task ->
            val nextAt = if (task.dueAt <= System.currentTimeMillis()) System.currentTimeMillis() + 60_000L else task.dueAt
            ReminderScheduler.schedule(context, task, nextAt)
        }
    }
}
