# 刷题 (Shuati) — Android 原生版

将微信小程序「刷题小程序」复刻为 Android 原生应用。

## 技术栈
- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **架构**：单 Activity + Compose Navigation
- **存储**：DataStore (Preferences)，以 JSON 持久化（等价复刻小程序 `storage.ts` 同步存储语义）
- **序列化**：kotlinx.serialization
- **Excel 导入**：Apache POI (XSSF)
- **最小/目标/编译 SDK**：minSdk 34 / targetSdk 36 / compileSdk 37

## 功能对照（小程序 14 个页面）
| 小程序页面 | Android 页面 |
|---|---|
| home | `ui.pages.HomeScreen` |
| library | `ui.pages.LibraryScreen` |
| import | `ui.pages.ImportScreen` |
| quiz | `ui.pages.QuizScreen` |
| result | `ui.pages.ResultScreen` |
| wrongs | `ui.pages.WrongsScreen` |
| favorites | `ui.pages.FavoritesScreen` |
| history | `ui.pages.HistoryScreen` |
| backup | `ui.pages.BackupScreen` |
| edit-bank | `ui.pages.EditBankScreen` |
| search | `ui.pages.SearchScreen` |
| stats | `ui.pages.StatsScreen` |
| recommend | `ui.pages.RecommendScreen` |
| study-plan | `ui.pages.StudyPlanScreen` |
| dedup | `ui.pages.DedupScreen` |

## 核心逻辑（等价复刻）
- 判分与查重：`logic/Scoring.kt`（Dice 相似度、宽松/严格判定、`judge()` 解析答案）
- Excel 解析：`logic/ExcelParser.kt`（B/C 列格式，D 选项、E 解析、F 标签、G 题型）
- 数据层：`data/AppRepository.kt`（题库/错题/收藏/进度/历史/计划/备份/查重阈值/掌握度）

## 构建与运行
1. 用 **Android Studio** (Hedgehog 或更新，含 Android SDK Platform 37) 打开本目录 `e:\Android\shuati`。
2. 等待 Gradle 同步（会自动下载 Gradle 8.11.1 与依赖）。
3. 连接设备或启动模拟器（API 34+）。
4. 点击 ▶ Run，或直接 `./gradlew assembleDebug`。

> 注意：本项目在创建时所在环境无 Java / Android SDK / Gradle，未执行本地编译。
> 请在具备 Android 构建环境的机器上首次打开并执行 Gradle 同步后再运行。

## 目录结构
```
app/src/main/java/com/example/shuati/
├── MainActivity.kt / ShuatiApplication.kt
├── model/QuizModels.kt          # 数据模型
├── logic/Scoring.kt             # 判分 / 相似度
├── logic/ExcelParser.kt         # Excel 解析
├── data/AppRepository.kt        # 数据仓库（等价 storage.ts）
└── ui/
    ├── AppNavHost.kt            # 导航图
    ├── Routes.kt                # 路由与 repository 获取
    ├── theme/                   # 主题
    ├── components/Common.kt     # 通用组件
    └── pages/                   # 14 个页面
```
