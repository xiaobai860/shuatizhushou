# 刷题助手（Shuati）— Android 原生版

> 本地优先的 Android 原生刷题练习应用，复刻微信小程序「刷题小程序」并增强（AI 解析、智能推荐、学习计划、备份还原等）。数据全部存于本机，不上传云端。
>
> 本文档分两部分：**一、软件介绍**（功能 / 用途 / 特点）；**二、开发指南**（架构 / 构建 / 当前状态，供后期维护与转平台参考）。

---

## 一、软件介绍

### 软件特点

- **本地优先 · 隐私安全**：题库 / 错题 / 进度全部存于本机，无后台进程、无广告；AI 配置密钥本地加密。
- **零依赖表格引擎**：自带轻量 Excel 读写，**支持 `.xlsx` 与 `.csv` 导入导出**（CSV 自动识别 UTF-8 / GBK），不依赖 Office 或任何重型库。
- **AI 智能解析**：多家大模型供应商 + 联网搜索（含国内博查 Bocha），逐题生成「正确答案 / 解析 / 关键知识点 / 易错点」。
- **柔光书桌设计**：Material 3 设计体系，陶土橙主色，长时间刷题不刺眼。
- **题型齐全 · 判分严谨**：单选 / 多选 / 判断 / 填空（多空顺序无关）/ 简答全覆盖；支持选项乱序、题型筛选。

### 功能总览

底部四 Tab：**首页**（数据总览 / 快速开始）、**学习**（智能推荐 / 学习记录）、**题库**（题库管理 / 导入 / 查重）、**我的**（数据管理 / 储存空间 / AI 配置 / 版本号）。其余为全屏详情页（刷题、结果、编辑、导入、查重、错题、收藏、历史、统计、搜索、备份、计划、AI 设置等）。

### 核心功能

- **刷题**（核心）：顺序 / 随机 / 浏览模式；题型筛选、选项乱序；双计时、收藏、跳题浮层、错题联动、进度续练。
- **题库管理**：新建 / 编辑 / 批量操作；单库查重、导出 Excel、疑似错题修正。
- **导入导出**：`.xlsx` / `.csv` 导入，系统分享直达导入；字段含题型 / 选项 / 答案 / 解析 / 标签 / 难度等；导出 `.xlsx`。
- **AI 解析**：提交判分后一键生成结构化解析，流式逐字渲染，结果自动保存。
- **智能推荐**：按遗忘曲线 + 错题 + 薄弱度加权推荐，保证题型多样性。
- **查重检测**：题干相似 **且**（选项高度重合 **或** 答案一致）才判为重题，降低同模板不同题误报。
- **学习计划**：每日目标、打卡日历、本地通知提醒。
- **备份还原**：ZIP 整体导出 / 还原；一键清空数据。
- **错题 / 收藏 / 历史 / 搜索 / 统计**：标准练习辅助功能。

### 版本历史

| 版本 | 说明 |
|---|---|
| 2026090204 | 移除测试题库；每日提醒本地通知（无后台进程）；版本号解耦为两处手动维护；清理垃圾文件与构建产物 |
| 2026090203 | 修复：取消时间选择器后无法再次打开 |
| 2026090202 | 修复：开启每日提醒闪退、提醒时间行补图标 |
| 2026090201 | 每日提醒设置项；版本号规则统一 |
| 2026083009 | 随机刷题系统（题型筛选 / 选项乱序 / 多空顺序无关） |
| 2026083008 | Excel 模板对齐、题型中文化、动态选项列、AI 解析导出回填 |
| 2026083007 | 重生成携带上次解析开关 + 真实 JVM 单测落地 |
| 2026083006 | 重生成带上下文 + prompt caching 降本 |
| 2026083005 | AI 配置页改造：多模型管理 + 三模块独立操作 |
| 2026083004 | 流式打字机修复 + 代际守卫防重生成覆盖 |
| 2026083002/3 | 移除思考展示、逐选项分析、版本号规则稳定 |
| 2026082903 | 储存空间真实化、开关统一、版本号显示规则 |
| 1.0.x | 备份 ZIP、示例题库、首页 / 学习页修复、UI 规范（早期） |
| 1.0.0 | 与小程序功能对齐，补齐全部缺失功能 |

---

## 二、开发指南

> 面向后续维护 / 移植（转平台）人员。含技术栈、架构、构建、测试、当前状态与关键约定。

### 2.1 技术栈与版本组合

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin 2.4.10（产物字节码固定 **Java 17**） |
| UI | Jetpack Compose + Material 3（BOM 2026.08.00），「柔光书桌」设计体系 |
| 架构 | 单 Activity + Compose Navigation（navigation-compose 2.9.8） |
| 存储 | DataStore Preferences 1.2.1（以 JSON 字符串持久化） |
| 序列化 | kotlinx-serialization 1.11.0 |
| 表格 | 自研 `logic/LightXlsx.kt`（xlsx = ZIP + XML，零依赖）+ `CsvParser.kt`；**支持 xlsx 与 csv** |
| 网络 | Retrofit 3.0.0 + OkHttp 5.5.0（AI 大模型 / 搜索引擎，OpenAI 兼容） |
| 加密 | androidx.security-crypto（AI 配置本地加密存 EncryptedSharedPreferences） |
| 构建 | **AGP 9.3.0 + Gradle 9.7.1**（运行 JDK 用 Android Studio 自带 **JBR 25**，产物字节码固定 Java 17） |
| SDK | minSdk 34 / **targetSdk 36** / **compileSdk 37** |

**版本组合说明**
- `compileSdk 37` 要求 **AGP ≥ 9.1.1**（官方对照表），故采用 AGP 9.3.0 + Gradle 9.7.1；JDK 最低 **17**。
- **`targetSdk` 刻意保持 36**（与 `compileSdk` 解耦，避免新平台行为变更影响运行时）。详见 2.5 / 2.7。
- AGP 9.0+ **已内置 Kotlin Android 支持**，`app` 模块无需 `org.jetbrains.kotlin.android` 插件；根目录仅保留该插件的版本声明用于对齐。
- **新 DSL 已就绪**：零 legacy Variant API（`applicationVariants` / `variantFilter` / `registerJavaGeneratingTask` 等），`android.newDsl=true` 实测编译通过且无弃用警告，故不声明 opt-out flag。
- 未启用 multidex：`minSdk 34 ≥ 21`，原生多 dex。

**设计体系「柔光书桌」**（详见 `.impeccable.md` / `outputs/shuati-design-system.md`）：主色陶土橙 `#C8551E`、次色柔紫 `#6F5FA8`、薄荷 `#3C8A75`；语义色：正确 `#2E8B62` / 警告 `#A96C10` / 错误 `#C2403A` / 信息 `#3D74B8`；渐变仅限 Hero / 进度 / 空状态 / 主 CTA；动效 120 / 200 / 320ms 减速、禁回弹；答错文案「再看一眼」。已移除 `AuroraBackground.kt` / `Glass.kt`。

### 2.2 项目架构

```
app/src/main/java/com/example/shuati/
├── MainActivity.kt / ShuatiApplication.kt   # 入口与全局单例（预热缓存 + 通知渠道）
├── model/QuizModels.kt                      # 数据模型 + AiExplanation + 配置类
├── data/AppRepository.kt                    # 数据仓库：DataStore+JSON（题库/错收/进度/历史/计划/备份/查重）
├── data/AiConfigStore.kt                    # AI 多供应商配置（加密存储 + 迁移）
├── logic/Scoring.kt                         # 判分、相似度、查重、疑似错题
├── logic/QuestionTableParser.kt             # 表格解析内核（表头模式 + 固定列兼容）
├── logic/LightXlsx.kt                       # 轻量 xlsx 读写（ZIP+XML，零依赖，替代 POI）
├── logic/ExcelParser.kt / ExcelWriter.kt    # Excel 导入 / 导出（动态列 + AI解析列）
├── logic/QuestionFileImporter.kt / CsvParser.kt  # 文件导入编排 / CSV 解析
├── logic/AiExplainService.kt                # AI 流式解析（SSE）、搜索编排、prompt caching
├── logic/Recommend.kt                       # 智能推荐算法
├── notification/ReminderScheduler.kt        # 每日提醒调度（AlarmManager）
├── notification/ReminderReceiver.kt         # 每日提醒广播（含 BOOT_COMPLETED 恢复）
└── ui/
    ├── AppNavHost.kt / Routes.kt           # 导航图 + 底部导航 + 返回键处理
    ├── theme/                              # Material3 主题
    ├── components/ShuatiSwitch.kt          # 统一开关组件（勿用 M3 Switch）
    └── pages/                              # 全部页面（约 16 个）
```

- **数据层**：`AppRepository` 全局单例（Application 持有），DataStore 顶层委托保证唯一实例；页面经 `repository()` 获取。核心数据模型定义于 `model/QuizModels.kt`（题库 / 题目 / 答案 / 解析 / 标签 / 难度 / 掌握目标等），**转平台时以此为准**。
- **判分**：`Scoring.judge()` 支持单选 / 多选 / 判断 / 填空（多空顺序无关）/ 简答。
- **查重**：`Scoring.compareForDedup()`（题干相似度 + 选项 Jaccard + 答案一致）。
- **推荐**：`Recommend.buildRecommendations()` 多维加权（遗忘曲线 / 错题 / 薄弱度 / 多样性）。
- **表格解析**：`QuestionTableParser` 为内核（表头模式 + 固定列兼容），新增格式只需实现 `Table` 接口；`LightXlsx` 仅处理 xlsx，`CsvParser` 处理 csv，二者共用该内核。

### 2.3 构建与运行

> **环境要点（Windows 本机）**
> - **JDK 推荐用 Android Studio 自带的 JBR 25**（`C:/Program Files/Android/Android Studio/jbr`），零配置、已实测构建成功。Gradle 9.7.1 支持运行 JDK **17~26**；产物字节码固定 Java 17，与运行 Gradle 的 JDK 版本无关。
> - 需安装 **Android SDK Platform 37** 与 **build-tools 37.0.0**。
> - **切勿设置 `GRADLE_USER_HOME`**：Git Bash 下 JVM 会误解析成 `\c\Users\...` 触发 `*.lck 拒绝访问`。临时换盘可用 `D:\Android\gradle-cache`（构建完清掉，勿持久化）。
> - 国内镜像已配置（长期保留）：`settings.gradle.kts` 用阿里云；`gradle-wrapper.properties` 的 `distributionUrl` 用**腾讯云**镜像。⚠️ 中科大镜像 HEAD 假 200 实际 404、阿里云 macports 403，**只用腾讯云 / 华为云**。
> - 偶发 `*.zip.lck 拒绝访问` / `native-platform.dll 加载失败`：删 `~/.gradle/wrapper/dists/**/*.lck` 孤儿锁 + 清 `~/.gradle/native`、`~/.gradle/daemon`，或 `--stop --no-daemon` 复跑。

```powershell
# 编译（PowerShell）
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"   # JBR 25
$env:PATH      = "$env:JAVA_HOME/bin;$env:PATH"
cd D:/Android/shuati
./gradlew.bat assembleDebug --no-daemon
# 产物 app/build/outputs/apk/debug/app-debug.apk

# 跑单测（不触发版本号变更）
./gradlew.bat :app:testDebugUnitTest

# 装真机（release 已自动签名，可直装）
adb install -r app/build/outputs/apk/release/app-release.apk
# 查看设备已装版本，确认能否覆盖安装（versionCode 不可回退）
adb shell dumpsys package com.example.shuati | findstr versionCode
```

环境要求：Android Studio（匹配 AGP 9.x，自带 JBR 25 即可）、Android SDK Platform 37、设备 API 34+。

### 2.4 测试

`app/src/test`（JVM 单测，JUnit4），共 **29 例全绿**：

| 测试类 | 用例数 | 覆盖范围 |
|---|---|---|
| `AiExplainServiceTest` | 12 | `modelHasBuiltinSearch` / `providerSupportsPromptCache` allowlist、`buildPrompt` 注入、SSE 累积解析、`bochaResultsToSearch` 截断 |
| `QuestionFileImporterTest` | 11 | xlsx/csv 两格式字段与题型推断一致性、CSV 的 GBK/UTF-8 编码探测、引号内逗号与换行、难度列解析、多文件 ID 冲突合并、非法输入报错 |
| `TemplateNoteRowTest` | 4 | 模板说明行机制的 4 个回归场景 |
| `ExcelRoundTripTest` | 2 | Excel 多选项动态列 + AI 解析往返 + 模板对齐 |

跑法：`:app:testDebugUnitTest --no-daemon`（不含 assemble，不改动版本号）。

### 2.5 当前项目状态

- **质量**：单测 29 例全绿；Release APK 约 **4 MB**（POI 移除 + 图标具体导入 + R8 shrinkResources）。
- **已知 Lint 警告（有意保留）**：`OldTargetApi`（`targetSdk 36 < compileSdk 37`）—— **不升级也不抑制**，留作 Android 17 适配提醒。核心路径（通知 + AlarmManager、SAF 导入导出、AI 网络、查重）在 36 下行为稳定。
- **已修复的告警**：`AiExplainService.kt` 的 `UNNECESSARY_SAFE_CALL`（2026-09-10 修复，保留 `?.` + `@Suppress` 防 204/304 响应 NPE）；`Opt-in requirement marker` 未解析（删除对不存在 material 2 注解的 opt-in）。
- **依赖决策**（升级前务必看）：
  - `datastore-preferences 1.1.1 → 1.2.1`：跳过 IDE 建议的 1.1.4（1.1.4/1.1.5 有 Gradle 元数据损坏连锁问题）；**1.2.0 修复「R8 优化下 DataStore 抛 UnsatisfiedLinkError」**——本项目开 R8，正中痛点。
  - `retrofit / converter 2.11.0 → 3.0.0`：官方唯一变更是内置 OkHttp 3.14→4.12，且被项目显式声明的 **OkHttp 5.5.0 覆盖**，运行时收益为零，仅为版本新鲜。
  - `okhttp 5.5.0`：Kotlin API 重写，**有 breaking change**（如 `Response.body` 等），已适配。
  - `kotlinx-serialization-json 1.10.0 → 1.11.0`：基于 Kotlin 2.3.20（项目 2.4.10），风险低。
- **已移除**：测试题库（`SampleBanks.kt`，2026-09-02）；Apache POI（换自研 `LightXlsx`，曾让 APK 达 69 MB）。

### 2.6 版本号机制

`versionName` 与 `versionCode` **解耦**，两者均在 `app/build.gradle.kts` 的 `defaultConfig` 内**手动维护**：

- `versionName`：展示用版本号 = 年月日 + 两位迭代版号（`YYYYMMDDII`，10 位纯数字；当天第一版 `01`…），如 `2026091001`。「我的」页仅显示 `v$versionName`。
- `versionCode`：系统用的递增整数，每次发版 **+1**，与 `versionName` 无关，**不可回退**。
- **发版清单（两处都要改）**：① `versionName` 按当天日期 + 迭代号；② `versionCode` 在上一版基础上 +1。
- **序列重置**：2026-09-02 前曾用 `versionCode = versionName` 绑定写法，一度达 `2026090204`（逼近 int 上限）；2026-09-10 主动重置为小数值序列（当前 `50` = 当时仓库总提交数），**该重置版已装机**。下次发版取 `51` 即可覆盖安装。
- 查已装版本：`adb shell dumpsys package com.example.shuati | findstr versionCode`（以实测为准，勿凭记忆）。

### 2.7 关键约定与踩坑

- **`targetSdk 36` 刻意勿改**：与 `compileSdk 37` 解耦，避免新平台行为变更；`OldTargetApi` 警告保留作提醒。
- **勿引入 Apache POI**：曾使 APK 达 69 MB；用自研 `LightXlsx`（xlsx=ZIP+XML 零依赖）。
- **`build.gradle.kts` 的 `import` 必须在文件最顶部、`plugins {}` 之前**：放在 `plugins {}` 之后时 Gradle 命令行仍能编译，但 **IDE 会报 "Unresolved reference 'import'"**（这正是曾见「19 个问题」的真凶）。
- **构建产物 `app/build/` 已被 gitignore**：勿提交；清理与否由人工决定。
- **升级依赖先查官方 release notes**：IDE 的更新提示只给同 minor 线最新版，未必最优（如 DataStore 应跳 1.1.4 直上 1.2.1）。
- **权限**：仅 `INTERNET`；零短信 / 存储权限、零运行时申请；文件读写全走 SAF + contentResolver。
