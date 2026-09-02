package com.example.shuati.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.shuati.MainActivity
import com.example.shuati.R

/**
 * 每日提醒的闹钟接收器。
 *
 * 两种触发来源：
 * 1) [android.app.AlarmManager] 精确闹钟到点（action = [ACTION_REMIND]）→ 直接弹通知。
 * 2) 设备重启完成（action = [Intent.ACTION_BOOT_COMPLETED]）→ 重新调度闹钟，
 *    确保应用未运行也能在重启后恢复每日提醒。
 *
 * 采用前台 BroadcastReceiver + AlarmManager.setExactAndAllowWhileIdle，
 * 不依赖任何后台常驻进程/WorkManager，满足「应用未运行时也能按时提醒」。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> showReminder(context)
            Intent.ACTION_BOOT_COMPLETED -> {
                // 仅当用户开启过提醒才恢复调度
                if (ReminderScheduler.isEnabled(context)) {
                    ReminderScheduler.schedule(context)
                }
            }
        }
    }

    private fun showReminder(context: Context) {
        val channelId = CHANNEL_ID
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 通道在 Application.onCreate 已建；此处兜底确保存在（如系统清通道的极端情况）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "每日提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每天定时提醒打卡刷题"
            }
            nm.createNotificationChannel(ch)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("该刷题打卡啦 📚")
            .setContentText("今天还没完成学习计划，来刷几道题保持手感吧～")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFY_ID, notification)
    }

    companion object {
        const val ACTION_REMIND = "com.example.shuati.action.REMIND"
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFY_ID = 1001
    }
}
