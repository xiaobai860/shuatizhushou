package com.example.shuati.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.shuati.ui.pages.*

/** 底部导航的顶层目的地 */
private val topLevelRoutes = setOf(
    Routes.HOME, Routes.TAB_LIBRARY, Routes.TAB_STUDY, Routes.TAB_MINE
)

private data class NavItemSpec(
    val route: String,
    val icon: ImageVector,
    val label: String
)

private val navItems = listOf(
    NavItemSpec(Routes.HOME, Icons.Filled.Home, "首页"),
    NavItemSpec(Routes.TAB_STUDY, Icons.Filled.School, "学习"),
    NavItemSpec(Routes.TAB_LIBRARY, Icons.AutoMirrored.Filled.LibraryBooks, "题库"),
    NavItemSpec(Routes.TAB_MINE, Icons.Filled.Person, "我的")
)

/**
 * 底部导航（2026 版）：surface 底 + secondaryContainer 胶囊指示器。
 * 高 72dp，图标 22dp，标签 11sp；仅顶层四页显示，其余详情页全屏隐藏。
 */
@Composable
private fun SoftBottomBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.height(72.dp)
    ) {
        navItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(item.route) },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

/**
 * 全局导航图。顶部四页挂底部导航，其余详情页全屏显示。
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 应用由 MainActivity.setContent 提供组合上下文，context 必为 Activity
    val activity = LocalContext.current as Activity

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 顶层导航壳：不自己加系统栏 padding（各页面顶部栏自行 statusBarsPadding，
        // 底部导航 NavigationBar 自行处理导航栏 inset），避免状态栏高度被算两次导致标题下移
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                SoftBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            topLevelOptions(navController.graph.findStartDestination().id)
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // 顶部四页
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.TAB_LIBRARY) { LibraryTabScreen(navController) }
            composable(Routes.TAB_STUDY) { StudyTabScreen(navController) }
            composable(Routes.TAB_MINE) { MineTabScreen(navController) }

            // 详情页
            composable(Routes.LIBRARY) { LibraryScreen(navController) }
            composable(Routes.IMPORT) { ImportScreen(navController) }
            composable(Routes.WRONGS) { WrongsScreen(navController) }
            composable(Routes.FAVORITES) { FavoritesScreen(navController) }
            composable(Routes.HISTORY) { HistoryScreen(navController) }
            composable(Routes.BACKUP) { BackupScreen(navController) }
            composable(Routes.AI_SETTINGS) { AiSettingsScreen(navController) }
            composable(Routes.SEARCH) { SearchScreen(navController) }
            composable(Routes.STATS) { StatsScreen(navController) }
            composable(Routes.RECOMMEND) { RecommendScreen(navController) }
            composable(Routes.STUDY_PLAN) { StudyPlanScreen(navController) }

            composable(
                route = "${Routes.DEDUP}?bankId={bankId}",
                arguments = listOf(
                    navArgument("bankId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStack ->
                DedupScreen(navController, initialBankId = backStack.arguments?.getString("bankId"))
            }

            composable(
                route = "${Routes.EDIT_BANK}?bankId={bankId}&editQuestionId={editQuestionId}",
                arguments = listOf(
                    navArgument("bankId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("editQuestionId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStack ->
                EditBankScreen(
                    navController,
                    backStack.arguments?.getString("bankId"),
                    editQuestionId = backStack.arguments?.getString("editQuestionId") ?: ""
                )
            }

            composable(
                route = "${Routes.QUIZ}?bankId={bankId}&mode={mode}&source={source}&planId={planId}&filterUnanswered={filterUnanswered}&questionIds={questionIds}&showModeSelect={showModeSelect}",
                arguments = listOf(
                    navArgument("bankId") { type = NavType.StringType; nullable = true },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "sequence" },
                    navArgument("source") { type = NavType.StringType; defaultValue = "bank" },
                    navArgument("planId") { type = NavType.StringType; nullable = true },
                    navArgument("filterUnanswered") { type = NavType.BoolType; defaultValue = false },
                    navArgument("questionIds") { type = NavType.StringType; defaultValue = "" },
                    navArgument("showModeSelect") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStack ->
                QuizScreen(
                    navController,
                    bankId = backStack.arguments?.getString("bankId"),
                    mode = backStack.arguments?.getString("mode") ?: "sequence",
                    source = backStack.arguments?.getString("source") ?: "bank",
                    planId = backStack.arguments?.getString("planId"),
                    filterUnanswered = backStack.arguments?.getBoolean("filterUnanswered") ?: false,
                    questionIds = backStack.arguments?.getString("questionIds") ?: "",
                    showModeSelect = backStack.arguments?.getBoolean("showModeSelect") ?: false
                )
            }

            composable(
                route = "${Routes.RESULT}?bankId={bankId}&mode={mode}&source={source}&planId={planId}&correct={correct}&wrong={wrong}&total={total}&duration={duration}",
                arguments = listOf(
                    navArgument("bankId") { type = NavType.StringType; nullable = true },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "sequence" },
                    navArgument("source") { type = NavType.StringType; defaultValue = "bank" },
                    navArgument("planId") { type = NavType.StringType; nullable = true },
                    navArgument("correct") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("wrong") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("total") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("duration") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { backStack ->
                ResultScreen(
                    navController,
                    bankId = backStack.arguments?.getString("bankId"),
                    mode = backStack.arguments?.getString("mode") ?: "sequence",
                    source = backStack.arguments?.getString("source") ?: "bank",
                    planId = backStack.arguments?.getString("planId"),
                    correct = backStack.arguments?.getInt("correct") ?: 0,
                    wrong = backStack.arguments?.getInt("wrong") ?: 0,
                    total = backStack.arguments?.getInt("total") ?: 0,
                    durationSec = backStack.arguments?.getInt("duration") ?: 0
                )
            }
        }
    }

    // 底部导航页（首页/学习/题库/我的）按返回键直接退出应用
    BackHandler(enabled = currentRoute in topLevelRoutes) {
        Log.d("AppNavHost", "back pressed on top-level route $currentRoute, finish activity")
        activity.finish()
    }
}

/** 顶层 Tab 切换：保留各 Tab 状态，避免重复进栈 */
private fun NavOptionsBuilder.topLevelOptions(startId: Int) {
    popUpTo(startId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
