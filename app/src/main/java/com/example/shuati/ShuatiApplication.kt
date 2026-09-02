package com.example.shuati

import android.app.Application
import android.app.NotificationChannel
import android.content.Context
import android.app.NotificationManager
import android.os.Build
import com.example.shuati.data.AppRepository
import com.example.shuati.notification.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShuatiApplication : Application() {

    // 全局 Repository，等价复刻小程序 storage.ts 的同步存储语义
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        // 每日提醒通知渠道：Android 8+ 必须预先创建，否则通知不显示
        createReminderChannel()
        // 后台预热常用数据缓存：错题/收藏/历史/计划等首次读取发生在首帧之后的
        // 页面组合里，若不预热会以 runBlocking 阻塞主线程做全量 JSON 反序列化
        // （千题规模约数百毫秒，实测掉帧 30+）。预热后 UI 首读即内存命中。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.warmUpCache()
        }
    }

    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            ReminderReceiver.CHANNEL_ID,
            "每日提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每天定时提醒打卡刷题"
        }
        nm.createNotificationChannel(ch)
    }
}
