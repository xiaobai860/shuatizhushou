package com.example.shuati.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.shuati.ShuatiApplication

/** 在 Composable 中获取全局 Repository */
@Composable
fun repository(): com.example.shuati.data.AppRepository {
    val app = LocalContext.current.applicationContext as ShuatiApplication
    return app.repository
}

/** 在非 Composable 环境（如 Activity）获取 */
fun Context.appRepository(): com.example.shuati.data.AppRepository {
    return (this.applicationContext as ShuatiApplication).repository
}

/** 导航路由常量 */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val IMPORT = "import"
    const val QUIZ = "quiz"
    const val RESULT = "result"
    const val WRONGS = "wrongs"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val BACKUP = "backup"
    const val EDIT_BANK = "edit_bank"
    const val SEARCH = "search"
    const val STATS = "stats"
    const val RECOMMEND = "recommend"
    const val STUDY_PLAN = "study_plan"
    const val DEDUP = "dedup"
    const val AI_SETTINGS = "ai_settings"
    // 底部导航顶层目的地
    const val TAB_LIBRARY = "tab_library"
    const val TAB_STUDY = "tab_study"
    const val TAB_MINE = "tab_mine"
}
