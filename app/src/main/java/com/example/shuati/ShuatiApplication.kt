package com.example.shuati

import android.app.Application
import com.example.shuati.data.AppRepository
import com.example.shuati.data.seedSampleData
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
        // 开发期示例题库：仅当无 sample_ 题库时播种，幂等。移除示例数据请删 SampleBanks.kt 与本行。
        seedSampleData(repository)
        // 后台预热常用数据缓存：错题/收藏/历史/计划等首次读取发生在首帧之后的
        // 页面组合里，若不预热会以 runBlocking 阻塞主线程做全量 JSON 反序列化
        // （千题规模约数百毫秒，实测掉帧 30+）。预热后 UI 首读即内存命中。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.warmUpCache()
        }
    }
}
