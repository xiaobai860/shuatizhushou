package com.example.shuati.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf

/**
 * 系统分享面板传入的题库文件。
 *
 * MainActivity 在 onCreate / onNewIntent 中解析 intent 得到本对象，
 * 导航层据此跳转到导入确认页；确权完成后调用 [consume] 清除，避免旋转屏幕或
 * 再次进入时重复触发。
 */
data class SharedImportIntent(val uris: List<Uri>) {

    companion object {
        /** Compose 侧观察的持有者；为 null 表示当前没有待处理的分享。 */
        val holder = mutableStateOf<SharedImportIntent?>(null)

        fun from(intent: Intent?): SharedImportIntent? {
            if (intent == null) return null
            val uris: List<Uri> = when (intent.action) {
                Intent.ACTION_SEND -> {
                    @Suppress("DEPRECATION")
                    listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
                }
                else -> emptyList()
            }
            return if (uris.isEmpty()) null else SharedImportIntent(uris)
        }
    }
}
