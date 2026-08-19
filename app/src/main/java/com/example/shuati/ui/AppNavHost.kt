package com.example.shuati.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.shuati.ui.pages.*

/**
 * 全局导航图。等价小程序的 14 个页面。
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) { HomeScreen(navController) }
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
