package com.example.shuati

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.shuati.ui.AppNavHost
import com.example.shuati.ui.SharedImportIntent
import com.example.shuati.ui.theme.ShuatiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 冷启动经系统分享进入：解析 intent 写入 holder（AppNavHost 观察 holder 导航到导入页）
        SharedImportIntent.from(intent)?.let { SharedImportIntent.holder.value = it }
        setContent {
            val repo = (application as ShuatiApplication).repository
            // 主题模式："system" 跟随系统 / "light" 浅色 / "dark" 深色
            val darkTheme = when (repo.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            ShuatiTheme(darkTheme = darkTheme, dynamicColor = repo.dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 系统分享面板进入时，AppNavHost 观察 SharedImportIntent.holder 自动导航到导入确认页
                    AppNavHost()
                }
            }
        }
    }

    /**
     * 应用已在前台时收到新的分享 intent（未走 onCreate），需要把新 Uri 推给 UI 层。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedImportIntent.from(intent)?.let { SharedImportIntent.holder.value = it }
    }
}
