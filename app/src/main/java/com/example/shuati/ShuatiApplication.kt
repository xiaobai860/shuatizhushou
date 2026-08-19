package com.example.shuati

import android.app.Application
import com.example.shuati.data.AppRepository

class ShuatiApplication : Application() {

    // 全局 Repository，等价复刻小程序 storage.ts 的同步存储语义
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
    }
}
