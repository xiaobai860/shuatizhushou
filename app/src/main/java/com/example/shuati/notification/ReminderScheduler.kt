package com.example.shuati.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.shuati.data.AppRepository
import java.util.Calendar

/**
 * 每日提醒调度器（无后台进程机制）。
 *
 * 用 [AlarmManager.setExactAndAllowWhileIdle] 设定「每天指定时刻」的精确闹钟：
 * - 即使应用进程不在运行，系统仍会在到点时唤醒 [ReminderReceiver] 弹出通知；
 * - 设备重启后由 [ReminderReceiver] 的 BOOT_COMPLETED 分支重新调用 [schedule] 恢复。
 *
 * 注意：Android 12+(API 31+) 精确闹钟需 `SCHEDULE_EXACT_ALARM` 权限（本项目已声明）。
 * 用 setExactAndAllowWhileIdle 而非 setRepeating，因为系统对重复闹钟有漂移/节流，
 * 且每日只需在到点时触发一次，由 AlarmManager 保证。
 */
object ReminderScheduler {

    /** 是否已开启每日提醒（读取持久化偏好）。 */
    fun isEnabled(context: Context): Boolean {
        val repo = AppRepository(context.applicationContext)
        return repo.reminderEnabled
    }

    /** 调度今日（或次日）指定时刻的闹钟。已存在则覆盖。 */
    fun schedule(context: Context) {
        val ctx = context.applicationContext
        val repo = AppRepository(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val (hour, minute) = repo.reminderTime
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 若今天该时刻已过，顺延到明天
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }

        val pi = pendingIntent(ctx)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !am.canScheduleExactAlarms()
                ) {
                    // 未获精确闹钟权限（Android 12+ 管控）：退化为宽松窗口闹钟，
                    // 仍能在到点附近弹通知，避免 SecurityException 闪退。
                    am.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        trigger.timeInMillis,
                        10 * 60 * 1000L,
                        pi
                    )
                } else {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        trigger.timeInMillis,
                        pi
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            // 极端情况无精确闹钟权限：退化为非精确闹钟，保证不崩且到点附近能提醒
            @Suppress("DEPRECATION")
            am.set(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pi)
        }
    }

    /** 取消已调度的闹钟。 */
    fun cancel(context: Context) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx))
    }

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
        }
        return PendingIntent.getBroadcast(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
