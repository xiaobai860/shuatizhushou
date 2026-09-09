# 刷题助手（Shuati） — Android 原生版

一款**刷题练习**应用，将微信小程序「刷题小程序」完整复刻为 Android 原生实现，并在底部导航四 Tab 架构基础上做了多项增强（AI 解析、随机刷题、智能推荐、备份还原等）。

---

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin 2.4.10（产物字节码固定 Java 17） |
| UI | Jetpack Compose + Material 3（BOM 2026.08.00），「柔光书桌」设计体系 |
| 架构 | 单 Activity + Compose Navigation（navigation-compose 2.9.8） |
| 存储 | DataStore Preferences 1.2.1（以 JSON 字符串持久化） |
| 序列化 | kotlinx-serialization 1.11.0 |
| Excel | 自研轻量实现 `logic/LightXlsx.kt`（xlsx = ZIP + XML，零依赖）；仅支持 `.xlsx` |
| 网络 | Retrofit 3.0.0 + OkHttp 5.5.0（AI 大模型 / 搜索引擎，OpenAI 兼容） |
| 加密 | androidx.security-crypto（AI 配置本地加密存 EncryptedSharedPreferences） |
| 构建 | **AGP 9.3.0 + Gradle 9.7.1**（运行 JDK 用 Android Studio 自带 **JBR 25**，产物字节码固定 Java 17） |
| SDK | minSdk 34 / **targetSdk 36** / **compileSdk 37** |

> **版本组合说明**：
> - `compileSdk 37` 要求 **AGP ≥ 9.1.1**（官方对照表），故采用 AGP 9.3.0 + Gradle 9.7.1；JDK 最低 **17**。升级 AGP / Gradle 时二者需同步查官方版本对照表。
> - `targetSdk` 保持 **36**（与 compileSdk 解耦，避免新平台行为变更影响运行时）。
> - AGP 9.0+ **已内置 Kotlin Android 支持**，`app` 模块无需 `org.jetbrains.kotlin.android` 插件；根目录仅保留该插件的版本声明（Kotlin 2.4.10）用于对齐。
> - **新 DSL 已就绪**：本项目零 legacy Variant API（无 `applicationVariants` / `variantFilter` / `registerJavaGeneratingTask` 等），已实测 `android.newDsl=true` 编译通过且无弃用警告，故**不声明**该 opt-out flag（它在 AGP 10.0 会被彻底移除）。
> - 未启用 multidex：minSdk 34 ≥ 21，原生多 dex，无需 `androidx.multidex`。

**版本号规则**（`versionName` 与 `versionCode` 解耦，两者均在 `app/build.gradle.kts` 的 `defaultConfig` 内手动维护）：
- `versionName`：展示用版本号 = 年月日 + 两位迭代版号（`YYYYMMDDII`，10 位纯数字；当天第一版 `01`，第二版 `02`……），如 `2026091001` 表示 2026-09-10 第 1 次迭代。「我的」页仅显示 `v$versionName`。
- `versionCode`：系统用的递增整数，每次发版 **+1**，与 `versionName` 无关。
- ⚠️ **发版清单（两处都要改）**：① `versionName` 按当天日期 + 迭代号；② `versionCode` 在上一版基础上 +1。`versionCode` **不可回退**。
- ⚠️ **序列重置说明**：2026-09-02 及之前曾用 `versionCode = versionName` 的绑定写法，`versionCode` 一度达到 `2026090204`（逼近 int 上限 `2147483647`）；2026-09-10 主动重置为小数值序列（当前 `50` = 当时仓库总提交数）。**该重置版已装机**。
  - 因重置值小于设备上当时的 `versionCode`（实测为 `104`），首次安装采用 `adb uninstall` 全新安装（本地数据已清空）。
  - **下次发版取 `51` 即可正常覆盖安装，无需再卸载。**
  - 查已装版本：`adb shell dumpsys package com.example.shuati | findstr versionCode`（装机版本以实测为准，勿凭记忆）

> **设计体系「柔光书桌」**（详见 `.impeccable.md` / `outputs/shuati-design-system.md`）：
> 主色陶土橙 `#C8551E`、次色柔紫 `#6F5FA8`、薄荷 `#3C8A75`；语义色锁定：正确 `#2E8B62` / 警告 `#A96C10` / 错误 `#C2403A` / 信息 `#3D74B8`；渐变仅限 Hero/进度/空状态/主 CTA；动效 120/200/320ms 减速、禁回弹；答错文案「再看一眼」。已移除 `AuroraBackground.kt`/`Glass.kt`。

---

## 功能总览（底部导航 4 Tab）

```
┌─────────────────────────────────┐
│ 首页 │ 学习 │ 题库 │ 我的        │  ← 底部导航栏
└─────────────────────────────────┘
```

- **首页**：数据总览与快速开始（含今日连续天数统计卡片、动态问候语）
- **学习**：智能推荐与学习记录汇总
- **题库**：题库管理与入口聚合
- **我的**：数据管理、储存空间真实占比、AI 配置入口、版本号

其余功能页（刷题、结果、编辑、导入、查重、错题、收藏、历史、统计、搜索、备份、计划、AI 设置等）均为**全屏详情页**，进入后隐藏底部导航栏。

---

## 各模块功能详解

### 首页（HomeScreen）
- **统计概览**：题库数、题目总数（动态，无题显示 0）、错题数、今日连续天数、进行中计划数、收藏数
- **快速开始**：全部题库（进入模式选择）、浏览模式、错题回顾、学习计划
- **最近题库**：显示进度条（已答 x/y）、"已刷完"状态；点击直接进入刷题

### 题库 Tab（LibraryTabScreen / LibraryScreen）
- 题库 / 题目总数统计；快捷入口：题库管理、导入题库、查重检测、全部题库
- **我的题库**：卡片显示「错题 N / 收藏 N」徽章、掌握进度条、重复题徽章；操作含刷题、浏览、只练错题、批量刷、导出 Excel、清除进度、单库查重、编辑、删除

### 学习 Tab（StudyTabScreen）
- **智能推荐**：一键进入推荐页；错题按遗忘曲线加权、已掌握降权、每题库软上限保证多样性
- **学习记录**：学习计划、错题回顾、收藏题目、刷题历史（带数量角标）、标签筛选

### 我的 Tab（MineTabScreen）
- 数据管理：统计分析、题目搜索、备份还原、AI 大模型配置
- **储存空间**：真实字节占比三段条（题库/记录/其他）+ 颜色图例
- 版本号「`v$versionName`」（仅外显版本号，不显示内部 build 号）

### 刷题（QuizScreen）⭐ 核心
- **模式选择页**：顺序 / 随机 / 浏览（含已答进度、上次浏览位置、清除进度）
  - 新增「题型筛选（可选）」FilterChip 行（全部/单选/多选/判断/填空/简答）
  - 「选项乱序」开关（选择类题型每题固定重排映射，判分按原始索引不受影响）
- **题型支持**：单选、多选、判断、填空（多空，支持"不限答案顺序" `|` 分隔多空、顺序无关贪心匹配）、简答
- **作答交互**：选项判分高亮、提交后显示答案与解析、双计时（本题 + 总用时）
- **浏览模式**：展示正确答案与解析、断点保存/恢复
- **收藏**：答题中一键收藏/取消
- **跳题浮层**：序号 + 题型标记 + 已答状态（图标按钮长按 Tooltip 显示语义）
- **自动下一题**：提交且开启后延迟自动跳题（最后一题不跳）
- **错题联动**：答错自动计入错题本；连对达标自动移出
- **过滤**：跳过残缺题、只刷未做过的题
- **退出确认**：进度自动保存，支持续练

### 结果页（ResultScreen）
- 个性化正确率标签、正确率进度条、总题数/答对/答错/总用时/均秒
- 重新刷题、错题回顾、返回首页

### 题库管理（LibraryScreen / EditBankScreen）
- 列表：搜索、新建、导入、批量删除、批量刷题
- 单库：浏览、顺序/随机刷、只练错题、导出 Excel、单库查重、清除进度、编辑、删除
- **编辑题库**：题目增删改、题型切换、选项动态增删（A–J，上限 10）、答案/解析/标签编辑；题库级「AI 默认提示语」
- **疑似错题 Tab**：识别缺选项/答案异常，一键修正缺失/重复 ID

### 题库导入 / 导出 ⭐（含系统分享入口）
- **支持格式**：`.xlsx`、`.csv`（UTF-8 / GBK 自动探测）
  - ⚠️ **已不支持 `.xls`**（Excel 97-2003）：随 Apache POI 一并移除，改用自研 `LightXlsx`（仅解析 xlsx = ZIP + XML）
  - 两种格式共用同一解析内核 `QuestionTableParser`，行为一致
  - CSV 编码探测：先按 UTF-8，出现替换字符则回退 GBK（国内 Excel 另存 CSV 默认为 GBK）
- **系统分享面板入口**：在其他 App（微信/QQ/文件管理器/网盘）点分享 → 选择「刷题」→ 自动进入导入页并解析
  - 支持 `ACTION_SEND`（单文件）与 `ACTION_SEND_MULTIPLE`（多文件合并为一个题库）
  - 每种 MIME 单独声明 intent-filter（同一 filter 内多个 mimeType 会被合并导致 CSV 丢失）
  - 应用已在前台时再次分享也能响应（`onNewIntent` → 观察 holder 状态）
- **字段结构**（与模板 / 导出三方对齐）：
  `题目ID | 题型 | 题干 | 选项A…Z | 正确答案 | 解析 | AI解析 | 标签 | 难度 | 不限顺序`
  - 题型：单选/多选/判断/填空/简答（中英文均可）；不填则按选项数与答案自动推断
  - 选项列动态识别「选项X」（任意字母），最多 26 个
  - **标签**：承载行业来源 / 知识点分类，多值用 `,，、|｜` 分隔
  - **难度**：简单 / 中等 / 困难（或 easy/normal/hard、1/2/3）；留空为「未设置」，兼容历史数据
  - 判断类答案自动规整为「正确/错误」；填空支持 `|` 分隔多空 + 不限顺序
- **校验规则**：
  - 题干/答案缺失、选项不足 2 个 → 标记「疑似错题」，导入后可在编辑页修正
  - ID 缺失自动生成、重复自动加后缀（并标记）
  - 表格形态无法识别（无表头且解析不出任何题目）→ 明确报错，不静默导入空题库
  - 分享方未授予读权限 → 友好中文提示，不暴露原始路径错误
- **导入流程**（含确认步骤，避免跨应用误操作）：
  `分享/选择文件 → 预览（文件名·格式·编码·题数·异常数·题型分布）→ 命名题库 → 导入`
- **导出**：题库导出为 xlsx，含「AI解析」与「难度」列；选项列按最大选项数动态扩展

### 错题本（WrongsScreen）
- 按答错次数降序；失效记录（关联题库已删）单独展示、一键清除；全部/按筛选清空；重做进入错题练习

### 收藏（FavoritesScreen）
- 按题库筛选、题型标签、答案预览；批量/单题练习；取消收藏、失效收藏清除

### 刷题历史（HistoryScreen）
- 记录（题库/模式/成绩/用时）含正确率进度条；按题库筛选、清空（带确认）

### 学习计划（StudyPlanScreen）
- 创建：名称、题库（含"全部题库"）、模式、每日目标（coerceIn 1..题数）、开始/截止日期（真实周历 DatePicker）
- **打卡日历**：真实周历网格、打卡/今日/过期三态配色、预计完成日期
- 断点续练、归档/恢复/删除（同步清进度）、失效计划一键清除

### 智能推荐（RecommendScreen）
- 算法：错题（遗忘曲线 `forgettingFactor` 半衰期 72h × 错误频次 × 掌握度因子）、收藏、低正确率题库（薄弱度 + 练习可信度）、新题库、题型多样性兜底；连对临门一脚加成、已掌握降权
- 每条展示推荐理由标签；一键练习全部推荐

### 查重检测（DedupScreen）
- 单题库 / 分组 / 跨题库三种模式
- **算法 v3**：题干相似度 ≥ 阈值 **且**（选项集合 Jaccard ≥ 0.6 **或** 答案一致）才判重，降低同模板不同题误报
- 类型过滤（同类型才比对）、相似分组展示、批量删除

### 统计分析（StatsScreen）
- 总正确率、累计刷题、总用时、最高/平均正确率、平均每题用时
- 题库筛选 Tab、核心数据渐变卡片、详细指标卡片、近 7 次趋势、正确率分布、模式分布、按题库/题型分布

### 题目搜索（SearchScreen）
- 关键词 + 标签筛选（可单独按标签）；结果含题型/题目标签/答案预览，点击进编辑

### 备份与还原（BackupScreen）
- **格式**：ZIP = `app_data.json`（仅元数据，题目置空）+ `banks/<id>.xlsx`（每题库一份原生 Excel 重新编码）
- 导入仅接受 ZIP（按 PK 魔数判定）；覆盖旧单 JSON 通道
- 备份内容：题库/错题/收藏/历史/计划/进度/掌握目标/正确率/主题等
- 清空所有数据（带确认）

### AI 解析（AiSettingsScreen + QuizScreen.AnswerPanel）⭐ 增强
- **多供应商管理**：`AiConfigStore.AiSettings` 支持多 LLM 供应商增删改、模型下拉拉取、上次使用记忆；首次启动自动迁移旧单配置
- **独立模块操作**：大模型配置 / 联网搜索 / 解析模板 三模块各自「测试连接 + 保存」；设置存 EncryptedSharedPreferences
- **联网搜索**：支持 Tavily / Exa / **博查 Bocha**（国内首选，注册领免费额度）；结果 5→3 条、单条截断 500 字、`max_tokens=900`
- **内置解析 skill**：`SYSTEM_PROMPT` 结构化四段（正确答案/解析/关键知识点/易错点）+ 逐选项分析错误项；可自定义模板（默认内置，允许覆盖）
- **交互**：提交判分后出现「AI 解析」入口；生成/重新生成/加定向提示（预填题库级 aiHint）；流式打字机逐字渲染 + 进度反馈；结果默认落盘 `Question.aiExplanation`，下次直接展示
- **降本**：prompt caching（openai/openrouter/groq 等标记 `cache_control`）、重生成携带上次解析作上下文（带开关，默认开）且不复搜、引用长度限制
- **联网标识**：模型名后「联网」= 已配搜索 OR `modelHasBuiltinSearch` allowlist 命中

---

## 数据与核心逻辑

```
app/src/main/java/com/example/shuati/
├── MainActivity.kt / ShuatiApplication.kt   # 入口与全局单例（启动预热缓存 + 建通知渠道）
├── model/QuizModels.kt                      # 数据模型 + AiExplanation + 配置类
├── data/AppRepository.kt                    # 数据仓库：DataStore+JSON（题库/错收/进度/历史/计划/备份/查重）
├── data/AiConfigStore.kt                    # AI 多供应商配置（加密存储 + 迁移）
├── logic/Scoring.kt                         # 判分、相似度 v3、查重、疑似错题
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

- **数据层**：`AppRepository` 全局单例（Application 持有），DataStore 顶层委托保证唯一实例；页面经 `repository()` 获取
- **判分**：`Scoring.judge()` 支持单选/多选/判断/填空（多空顺序无关）/简答
- **查重**：`Scoring.compareForDedup()` v3
- **推荐**：`Recommend.buildRecommendations()` 多维加权

---

## 构建与运行

> **环境要点（Windows 本机）**
> - **JDK 推荐用 Android Studio 自带的 JBR 25**（`C:/Program Files/Android/Android Studio/jbr`），零配置、已实测构建成功。
>   - 官方兼容：Gradle 9.7.1 支持运行 JDK **17~26**；Java 25 需 Gradle ≥ 9.1.0（满足）。AGP 9.3 最低 JDK 17。
>   - ⚠️ 历史坑已失效：旧笔记「勿用 JBR 25」是 **Gradle 8.11 时代**的限制（8.11 仅支持到 Java 23）。**Gradle 9.7.1 下 JBR 25 可用，不必再另装 JDK 17。**
>   - 产物不受影响：`compileOptions`/`kotlin.jvmTarget` 已固定 **Java 17**，运行 Gradle 的 JDK 版本与产物字节码版本无关。
> - 需安装 **Android SDK Platform 37** 与 **build-tools 37.0.0**（本机已具备）。
> - **切勿设置 `GRADLE_USER_HOME`**：Git Bash 下 JVM 会误解析成 `\c\Users\...` 触发 `*.lck 拒绝访问`。
> - 国内镜像已配置（长期保留，无需还原）：`settings.gradle.kts` 依赖仓库用阿里云；wrapper `distributionUrl` 用**腾讯云**镜像（Gradle 9.7.1 已验证可下载）。
>   - ⚠️ 中科大镜像 `mirrors.ustc.edu.cn/gradle/` 会 HEAD 假 200、实际下载 404；阿里云 macports 路径 403。**只用腾讯云或华为云**。
> - 偶发 `*.zip.lck 拒绝访问` / `native-platform.dll 加载失败`：删 `~/.gradle/wrapper/dists/**/*.lck` 孤儿锁 + 清 `~/.gradle/native`、`~/.gradle/daemon`，或 `--stop --no-daemon` 复跑。

```powershell
# 编译（PowerShell）
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"   # JBR 25，推荐
$env:PATH      = "$env:JAVA_HOME/bin;$env:PATH"
cd D:/Android/shuati
./gradlew.bat assembleDebug --no-daemon
# 产物 app/build/outputs/apk/debug/app-debug.apk

# 跑单测（不含 assemble，不改动版本号）
./gradlew.bat :app:testDebugUnitTest

# 装真机（release 已自动签名，可直装）
adb install -r app/build/outputs/apk/release/app-release.apk
# 查看设备已装版本，确认能否覆盖安装（versionCode 不可回退）
adb shell dumpsys package com.example.shuati | findstr versionCode
```

环境要求：Android Studio Panda 3 (2025.3.3 Patch 1)+（匹配 AGP 9.x，自带 JBR 25 即可）、Android SDK Platform 37、设备 API 34+。

> Android Studio 中确认：`Settings → Build Tools → Gradle → Gradle JDK`，选 **`jbr-25`**（或 "Android Studio default JDK"）。

---

## 测试

- `app/src/test`（JVM 单测，JUnit4），共 **29 例全绿**：

| 测试类 | 用例数 | 覆盖范围 |
|---|---|---|
| `AiExplainServiceTest` | 12 | `modelHasBuiltinSearch` / `providerSupportsPromptCache` allowlist、`buildPrompt` 注入、SSE 累积解析、`bochaResultsToSearch` 截断 |
| `QuestionFileImporterTest` | 11 | xlsx/csv 两格式字段与题型推断一致性、CSV 的 GBK/UTF-8 编码探测、引号内逗号与换行、难度列解析、多文件 ID 冲突合并、非法输入与无法识别表格的报错 |
| `TemplateNoteRowTest` | 4 | 模板说明行机制的 4 个回归场景（保留说明行 / 删除后写题 / ID 含标记字样 / 题干含标记字样） |
| `ExcelRoundTripTest` | 2 | Excel 多选项动态列 + AI 解析往返 + 模板对齐 |

- 跑法：`:app:testDebugUnitTest --no-daemon`（不含 assemble，不改动版本号）

## 编译警告

全量编译仅剩 **2 条**，均为 `AiExplainService.kt` 的提示级警告：

- `AiExplainService.kt:405 / :408 Unnecessary safe call on a non-null receiver of type 'ResponseBody'` —— 对已非空的 `ResponseBody` 用了多余的 `?.`，不影响行为，可清理。

> 已清理（2026-09-10）：`Opt-in requirement marker 'androidx.compose.material.ExperimentalMaterialApi' is unresolved` —— 根因是 `build.gradle.kts` 的 `freeCompilerArgs` 里声明了对 `androidx.compose.material`（material 2）的 opt-in，但项目只依赖 `material-icons-extended`、不含该核心库，指向了不存在的注解；**已删除该行**，警告消失。

> 已清理项（2026-08-30）：冗余 `!!` 与多余安全调用（Dedup/EditBank/Recommend/Wrongs/StudyPlan）、恒真条件（AppRepository settings 判空 / QuizScreen answerText / TabScreens versionCode 回退分支）、废弃图标（TrendingUp / HelpOutline → `Icons.AutoMirrored.*`）。
>
> 其中 **StudyPlanScreen 剩余天数计算**为实质修复：`SimpleDateFormat#parse` 返回平台类型（Kotlin 视为 `Date?`），原写法取值失败会静默不显示剩余天数；现为安全取值 + 局部 val 传递非空日期。

---

## 版本历史

| 版本 | 说明 |
|---|---|
| 2026090204 | 移除测试题库（`SampleBanks.kt` 及播种/清理入口）；每日提醒本地通知（AlarmManager + BroadcastReceiver，无后台进程）；版本号解耦为两处手动维护；清理垃圾文件与构建产物 |
| 2026090203 | 修复：取消时间选择器后无法再次打开（`DisposableEffect` + `setOnDismissListener` 复位） |
| 2026090202 | 修复：开启每日提醒闪退（`canScheduleExactAlarms` 兜底 + 运算符优先级 bug）、提醒时间行补图标 |
| 2026090201 | 每日提醒设置项；版本号规则统一；使用帮助内容同步 |
| 2026083009 | 随机刷题系统（题型筛选/选项乱序/多空顺序无关）+ 示例题库扩至 30 题 + 一键清理测试数据（后于 2026090204 移除） |
| 2026083008 | Excel 模板对齐、题型中文化、动态选项列、AI 解析导出/导入回填 |
| 2026083007 | 重生成携带上次解析开关（默认开）+ 真实 JVM 单测落地 |
| 2026083006 | 第3项（重生成带上下文） + prompt caching 降本 |
| 2026083005 | AI 配置页改造：多模型管理 + 三模块独立操作 + 降本（Bocha/截断/max_tokens） |
| 2026083004 | 流式打字机修复（累积 `+=`）+ 代际守卫防重生成覆盖 |
| 2026083002/3 | 移除思考展示、逐选项分析、版本号规则稳定 |
| 2026082903 | 储存空间真实化、开关统一 ShuatiSwitch、版本号显示规则 |
| 1.0.x | 备份 ZIP、示例题库、首页/学习页修复、UI 规范（早期） |
| 1.0.0 | 与小程序功能对齐，补齐全部缺失功能 |
