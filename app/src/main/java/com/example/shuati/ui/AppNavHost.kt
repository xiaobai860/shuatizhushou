package com.example.shuati.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
/** 底部导航高度：顶层页内容底部需留出该空间，避免被叠加的 tab 遮挡。 */
private val bottomBarHeight = 72.dp

@Composable
private fun SoftBottomBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.height(bottomBarHeight)
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
fun AppNavHost(sharedImport: SharedImportIntent? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 系统分享面板进入：观察 holder 状态导航到导入页。
    // 用 holder（而非参数 sharedImport）是为了同时覆盖两条路径：
    //  - 冷启动分享：MainActivity.onCreate 解析 intent 并写入 holder
    //  - 应用已在前台时再次分享：MainActivity.onNewIntent 写入 holder
    // 若只用参数 sharedImport，LaunchedEffect 仅在首次组合时有值，前台二次分享会因 key 不变而无反应。
    val pendingShare = SharedImportIntent.holder.value
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) {
            navController.navigate(Routes.IMPORT)
        }
    }
    // 应用由 MainActivity.setContent 提供组合上下文，context 必为 Activity
    val activity = LocalContext.current as Activity

    // 分层布局：子页面（NavHost）在底层全屏，底部导航在上层叠加淡入淡出。
    // ⚠️ 不用 Scaffold(bottomBar=...)：那样 bottomBar 出现/消失会改变 content 的 padding，
    // 返回时会把子页面内容「向上顶出」。改为 Box 叠加后两者互不影响，tab 直接淡入覆盖。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // 页面转场：淡入淡出 + 轻微上移，避免子页面「瞬间跳变」的割裂感
            enterTransition = {
                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 24 }
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetY = { -it / 24 }
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            modifier = Modifier
                .fillMaxSize()
                // 顶层页底部留出导航栏空间，避免内容被叠加的 tab 遮挡；
                // 子页面不留（全屏显示，tab 隐藏时也不会有空白）
                .then(
                    if (currentRoute in topLevelRoutes) Modifier.padding(bottom = bottomBarHeight)
                    else Modifier
                )
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

        // 上层：底部导航叠加在子页面之上，淡入淡出（不与内容争空间，返回时不会顶出子页面按钮）
        AnimatedVisibility(
            visible = currentRoute in topLevelRoutes,
            // 用带缓动的时长，让 tab 的淡入淡出更平滑（默认瞬间切换显得生硬）
            enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SoftBottomBar(
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    navController.navigate(route) {
                        topLevelOptions()
                    }
                }
            )
        }
    }

    // 底部导航页（首页/学习/题库/我的）按返回键直接退出应用
    BackHandler(enabled = currentRoute in topLevelRoutes) {
        Log.d("AppNavHost", "back pressed on top-level route $currentRoute, finish activity")
        activity.finish()
    }
}

/**
 * 顶层 Tab 切换：清空返回栈，保证底部四页切换后栈中**只剩当前页一个条目**。
 *
 * ⚠️ 不要用官方推荐写法 `popUpTo(startId){ saveState=true } + restoreState=true`：
 * 那样切换后栈里是 [首页, 当前Tab] 两个条目，按返回键会先 pop 回「首页」（需按两次才退出），
 * 与「底部四页任意一页按一次返回直接退出」的预期不符。
 * popUpTo(0) 会清空整个返回栈，BackHandler 因此能稳定地在顶层页直接 finish。
 */
private fun NavOptionsBuilder.topLevelOptions() {
    popUpTo(0)
    launchSingleTop = true
}
