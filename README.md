# 刷题助手（Shuati） — Android 原生版

一款**刷题练习**应用，将微信小程序「刷题小程序」完整复刻为 Android 原生实现，并在底部导航四 Tab 架构基础上做了多项增强（AI 解析、随机刷题、智能推荐、备份还原等）。

---

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin 2.3.21（JVM 17） |
| UI | Jetpack Compose + Material 3（BOM 2026.06.01），「柔光书桌」设计体系 |
| 架构 | 单 Activity + Compose Navigation（navigation-compose 2.9.8） |
| 存储 | DataStore Preferences（以 JSON 字符串持久化） |
| 序列化 | kotlinx-serialization 1.7.3 |
| Excel | Apache POI 5.3.0（导入解析 / 导出生成） |
| 网络 | Retrofit 2.11.0 + OkHttp 4.12.0（AI 大模型 / 搜索引擎，OpenAI 兼容） |
| 加密 | androidx.security-crypto（AI 配置本地加密存 EncryptedSharedPreferences） |
| SDK | minSdk 34 / targetSdk 36 / **compileSdk 36**（AGP 8.9.2 官方上限，勿升 37） |

> **版本红线（勿升）**：AGP 8.9.2 / Kotlin 2.3.21 / compileSdk 36 / BOM 2026.06.01。
> ⚠️ 升 BOM 2026.08.00 会让 Compose 1.12.0 要求 compileSdk ≥ 37，而 AGP 8.9.2 上限为 36，导致 `checkDebugAarMetadata` 编译失败 22 条。

**版本号规则**（显示「`v$versionName · build $versionCode`」）：
- `versionName`：外显版本号，格式 `YYYYMMDD` + 两位当日迭代（如 `2026083009`），手动修改。
- `versionCode`：内部累计构建计数器，从 1 自增、单调递增；由 `app/buildNumber.properties` 持久化，执行 assemble/bundle/install/build 时自动 +1（该文件须纳入 git）。
- 当前：`versionName=2026083009`。

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
- 「清理测试数据」按钮（仅当存在 `sample_` 题库时显示）

### 学习 Tab（StudyTabScreen）
- **智能推荐**：一键进入推荐页；错题按遗忘曲线加权、已掌握降权、每题库软上限保证多样性
- **学习记录**：学习计划、错题回顾、收藏题目、刷题历史（带数量角标）、标签筛选

### 我的 Tab（MineTabScreen）
- 数据管理：统计分析、题目搜索、备份还原、AI 大模型配置
- **储存空间**：真实字节占比三段条（题库/记录/其他）+ 颜色图例
- 版本号「`v$versionName · build $versionCode`」

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

### Excel 导入 / 导出（ImportScreen / ExcelParser / ExcelWriter）
- **模板**：题型中文化、选项默认 A–D（超 4 自动扩展 E/F…）、「AI解析」列
- **导入**：动态扫描「选项X」表头（任意字母）、中英文题型兼容、「AI解析」列回填
- **导出**：题库导出为 xlsx，含「AI解析」列；选项列按最大选项数动态扩展

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

## 示例（测试）题库

> 开发期用于让所有页面开箱可测，**发布前需移除**。

- `data/SampleBanks.kt`：`buildSampleBanks()` 返回 5 套（计算机基础/英语/生活常识/数学/综合知识 `sample_sci`），覆盖全 5 种题型共 30 题；`seedSampleData(repo)` 幂等播种，预置错题/收藏/历史/计划/掌握度
- 单一注入点：`ShuatiApplication.onCreate()` → `seedSampleData`（仅当无 `sample_` 题库时播种）；所有 id 带 `sample_` 前缀
- **移除**：A 代码层（删 `SampleBanks.kt` + `ShuatiApplication.kt:17` 调用与 import）；B 数据层用 `AppRepository.removeSampleData()`（按 `sample_` 前缀清理题库/错题/收藏/计划/历史/进度/正确率/掌握度/错答，无残留），`LibraryScreen` 顶栏「清理测试数据」按钮可直接触发

---

## 数据与核心逻辑

```
app/src/main/java/com/example/shuati/
├── MainActivity.kt / ShuatiApplication.kt   # 入口与全局单例（含 sample 播种）
├── model/QuizModels.kt                      # 数据模型 + AiExplanation + 配置类
├── data/AppRepository.kt                    # 数据仓库：DataStore+JSON（题库/错收/进度/历史/计划/备份/查重/示例清理）
├── data/AiConfigStore.kt                   # AI 多供应商配置（加密存储 + 迁移）
├── logic/Scoring.kt                        # 判分、相似度 v3、查重、疑似错题
├── logic/ExcelParser.kt / ExcelWriter.kt   # Excel 导入 / 导出（动态列 + AI解析列）
├── logic/AiExplainService.kt               # AI 流式解析（SSE）、搜索编排、prompt caching
├── logic/Recommend.kt                      # 智能推荐算法
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
> - JDK 必须显式指定 **JDK 17**，勿用 Android Studio 自带 JBR 25（Gradle 8.11 不支持 Java 23+）。
>   `JAVA_HOME="C:/Users/XX/.jdks/ms-17.0.20.1"`
> - **切勿设置 `GRADLE_USER_HOME`**：Git Bash 下 JVM 会误解析成 `\c\Users\...` 触发 `*.lck 拒绝访问`。
> - 国内镜像已配置（长期保留，无需还原）：`settings.gradle.kts` 用阿里云仓库、wrapper 用腾讯云镜像。
> - 偶发 `gradle-8.11.1-bin.zip.lck 拒绝访问` / `native-platform.dll 加载失败`：删孤儿 lock + 清 `~/.gradle/native`、`~/.gradle/daemon`，或 `--stop --no-daemon` 复跑。

```bash
# 显式 JDK 后编译（PowerShell）
$env:JAVA_HOME = "C:/Users/XX/.jdks/ms-17.0.20.1"
cd D:/Android/shuati
./gradlew assembleDebug          # 产物 app/build/outputs/apk/debug/app-debug.apk

# 跑单测（不触发 versionCode 自增）
./gradlew :app:testDebugUnitTest

# 装真机（设备 c733003）
adb -s c733003 install -r app/build/outputs/apk/debug/app-debug.apk
```

环境要求：Android Studio（含 SDK Platform 36）、JDK 17、Android 设备 API 34+。

---

## 测试

- `app/src/test`（JVM 单测，JUnit4）：`AiExplainServiceTest`（12 例）、`ExcelRoundTripTest`（2 例），共 **14 例全绿**
- 跑法：`:app:testDebugUnitTest --no-daemon`（不含 assemble，不触发 buildNumber 自增）
- 覆盖：`modelHasBuiltinSearch` / `providerSupportsPromptCache` allowlist、`buildPrompt` 注入、SSE 累积解析、`bochaResultsToSearch` 截断、Excel 多选项动态列 + AI 解析往返 + 模板对齐

---

## 版本历史

| 版本 | 说明 |
|---|---|
| 2026083009 | 随机刷题系统（题型筛选/选项乱序/多空顺序无关）+ 示例题库扩至 30 题 + 一键清理测试数据 |
| 2026083008 | Excel 模板对齐、题型中文化、动态选项列、AI 解析导出/导入回填 |
| 2026083007 | 重生成携带上次解析开关（默认开）+ 真实 JVM 单测落地 |
| 2026083006 | 第3项（重生成带上下文） + prompt caching 降本 |
| 2026083005 | AI 配置页改造：多模型管理 + 三模块独立操作 + 降本（Bocha/截断/max_tokens） |
| 2026083004 | 流式打字机修复（累积 `+=`）+ 代际守卫防重生成覆盖 |
| 2026083002/3 | 移除思考展示、逐选项分析、版本号规则稳定 |
| 2026082903 | 储存空间真实化、开关统一 ShuatiSwitch、版本号显示规则 |
| 1.0.x | 备份 ZIP、示例题库、首页/学习页修复、UI 规范（早期） |
| 1.0.0 | 与小程序功能对齐，补齐全部缺失功能 |
