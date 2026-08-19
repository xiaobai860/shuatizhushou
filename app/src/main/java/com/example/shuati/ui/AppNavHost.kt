package com.example.shuati.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

/**
 * 全局导航图。顶部四页（首页/题库/学习/我的）挂底部导航栏，其余详情页全屏显示。
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // 避免与外层各页 TopAppBar 的状态栏 inset 叠加
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigate(Routes.HOME) { topLevelOptions(navController.graph.findStartDestination().id) } },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("首页") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.TAB_STUDY,
                        onClick = { navController.navigate(Routes.TAB_STUDY) { topLevelOptions(navController.graph.findStartDestination().id) } },
                        icon = { Icon(Icons.Filled.School, contentDescription = null) },
                        label = { Text("学习") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.TAB_LIBRARY,
                        onClick = { navController.navigate(Routes.TAB_LIBRARY) { topLevelOptions(navController.graph.findStartDestination().id) } },
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                        label = { Text("题库") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.TAB_MINE,
                        onClick = { navController.navigate(Routes.TAB_MINE) { topLevelOptions(navController.graph.findStartDestination().id) } },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("我的") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
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
            composable(Routes.SEARCH) { SearchScreen(navController) }
            composable(Routes.STATS) { StatsScreen(navController) }
            composable(Routes.RECOMMEND) { RecommendScreen(navController) }
            composable(Routes.STUDY_PLAN) { StudyPlanScreen(navController) }
            composable(
                route = "${Routes.DEDUP}?bankId={bankId}",
                arguments = listOf(
                    navArgument("bankId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStack ->
                DedupScreen(navController, initialBankId = backStack.arguments?.getString("bankId"))
            }

            composable(
                route = "${Routes.EDIT_BANK}?bankId={bankId}&editQuestionId={editQuestionId}",
                arguments = listOf(
                    navArgument("bankId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("editQuestionId") { type = NavType.StringType; defaultValue = "" }
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
}

/** 顶层 Tab 切换：保留各 Tab 状态，避免重复进栈 */
private fun NavOptionsBuilder.topLevelOptions(startId: Int) {
    popUpTo(startId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
