package com.example.shuati

import android.app.Application
import com.example.shuati.data.AppRepository
import com.example.shuati.data.seedSampleData

class ShuatiApplication : Application() {

    // 全局 Repository，等价复刻小程序 storage.ts 的同步存储语义
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        // 开发期示例题库：仅当无 sample_ 题库时播种，幂等。移除示例数据请删 SampleBanks.kt 与本行。
        seedSampleData(repository)
    }
}
