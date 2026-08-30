package com.example.shuati.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.shuati.logic.Scoring
import com.example.shuati.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.UUID

/** 全局唯一 DataStore（顶层委托保证单例，避免多实例指向同一文件触发崩溃） */
private val Context.dataStore by preferencesDataStore(name = "shuati_store")

/**
 * 全局数据仓库，等价复刻小程序 utils/storage.ts 的同步存储语义。
 * 底层用 DataStore(Preferences) 以 JSON 字符串持久化；对外提供同步/挂起双接口，
 * 以匹配原小程序「同步读写」的使用习惯（页面内用 runBlocking 读取，写入用 edit）。
 */
class AppRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    companion object {
        private val BANKS_KEY = stringPreferencesKey("question_banks")
        private val WRONGS_KEY = stringPreferencesKey("wrong_records")
        private val FAVORITES_KEY = stringPreferencesKey("favorite_records")
        private val PROGRESS_KEY = stringPreferencesKey("quiz_progress")
        private val HISTORY_KEY = stringPreferencesKey("quiz_history")
        private val STUDY_PLANS_KEY = stringPreferencesKey("study_plans")
        private val PLAN_PROGRESS_KEY = stringPreferencesKey("plan_progress_v1")
        private val MASTERY_TARGET_KEY = stringPreferencesKey("wrong_mastery_target")
        private val MASTERY_STREAKS_KEY = stringPreferencesKey("wrong_mastery_streaks")
        private val DEDUP_THRESHOLD_KEY = stringPreferencesKey("dedup_threshold")
        private val DYNAMIC_COLOR_KEY = stringPreferencesKey("dynamic_color")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val QUESTION_STATS_KEY = stringPreferencesKey("question_stats")
        private val WRONG_ANSWERS_KEY = stringPreferencesKey("wrong_answers")
        private val BACKUP_HISTORY_KEY = stringPreferencesKey("backup_history")
    }

    // ─── 内存缓存 ────────────────────────────────────────────────
    //
    // 背景：页面在组合体内频繁调用 getBanks() 等同步接口（14 个页面共 49 处），
    // 每次调用都会 runBlocking 读 DataStore + JSON 反序列化全部题目（1030 题约 300ms），
    // 重组时反复触发 → 主线程卡顿（实测掉帧 36%、90 百分位 300ms）。
    //
    // 方案：读取结果按 key 缓存。写入时「更新缓存 + 落盘」，而不是简单失效缓存，
    // 避免下次读取又触发全量反序列化。

    /** 缓存容器：key → 已反序列化的列表。 */
    private val listCache = java.util.concurrent.ConcurrentHashMap<String, List<*>>()
    /** 缓存容器：key → 已反序列化的 Map（仅对已知高频热点 key 使用，如 MASTERY_STREAKS_KEY）。 */
    private val stringCache = java.util.concurrent.ConcurrentHashMap<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> readListCached(
        key: androidx.datastore.preferences.core.Preferences.Key<String>
    ): List<T> {
        listCache[key.name]?.let { return it as List<T> }
        val loaded: List<T> = readListUncached(key)
        listCache[key.name] = loaded
        return loaded
    }

    private inline fun <reified T> readListUncached(
        key: androidx.datastore.preferences.core.Preferences.Key<String>
    ): List<T> =
        runBlocking(Dispatchers.IO) {
            val raw = context.dataStore.data.first()[key] ?: return@runBlocking emptyList<T>()
            try {
                json.decodeFromString(serializer(), raw)
            } catch (_: Exception) { emptyList() }
        }

    // ─── 通用读写（类型安全，避免泛型擦除） ──────────────────────
    //
    // 55 处写入调用全部经由这两个方法，因此把「缓存更新」内置在此处，
    // 保证任何写入路径都会同步缓存，无需逐个调用点修改，也杜绝漏改导致的数据不一致。
    private inline fun <reified T> readList(key: androidx.datastore.preferences.core.Preferences.Key<String>): List<T> =
        readListCached(key)

    private inline fun <reified T> writeList(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: List<T>) {
        listCache[key.name] = value                 // 先更新缓存，后续读取零成本
        runBlocking(Dispatchers.IO) {                // 再落盘（IO 线程）
            context.dataStore.edit { it[key] = json.encodeToString(serializer(), value) }
        }
    }

    /** 清空缓存（仅在清空全部数据 / 还原备份后调用，避免读到旧数据）。 */
    private fun clearCache() { listCache.clear(); stringCache.clear() }

    private fun readString(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: String): String =
        runBlocking { context.dataStore.data.first()[key] ?: default }
    private fun writeString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) =
        runBlocking { context.dataStore.edit { it[key] = value } }

    // ─── 题库 ────────────────────────────────────────────────────
    fun getBanks(): List<QuestionBank> = readList(BANKS_KEY)

    /**
     * 预热缓存：把常用列表提前读入内存。
     * 由 Application 启动时在 IO 线程调用，使 UI 首次读取即命中缓存，
     * 避免首帧后在主线程做全量 JSON 反序列化（千题规模约数百毫秒）。
     */
    fun warmUpCache() {
        getBanks()
        getWrongRecords()
        getFavoriteRecords()
        getHistoryRecords()
        getStudyPlans()
    }
    fun getBankById(bankId: String): QuestionBank? = getBanks().find { it.id == bankId }
    fun isBankNameTaken(name: String, excludeId: String? = null): Boolean =
        getBanks().any { it.name == name && it.id != excludeId }
    fun generateUniqueBankName(baseName: String): String {
        if (!isBankNameTaken(baseName)) return baseName
        var i = 1
        while (isBankNameTaken("$baseName($i)")) i++
        return "$baseName($i)"
    }

    fun addBank(bank: QuestionBank): BooleanResult {
        if (getBanks().any { it.name == bank.name }) return BooleanResult(false, "题库名称已存在")
        val banks = getBanks() + bank
        writeList(BANKS_KEY, banks)
        return BooleanResult(true)
    }

    /** 记录题库被打开（更新 lastOpenedAt），用于首页「最近题库」按打开时间排序。 */
    fun markBankOpened(bankId: String) {
        val bank = getBanks().firstOrNull { it.id == bankId } ?: return
        updateBank(bank.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    /** 首页「最近题库」：按最后打开时间由近到远，取前 limit 个（未打开过的排在最后）。 */
    fun getRecentBanks(limit: Int = 3): List<QuestionBank> =
        getBanks().sortedByDescending { it.lastOpenedAt }.take(limit)

    fun updateBank(bank: QuestionBank): Boolean {
        val banks = getBanks().toMutableList()
        val idx = banks.indexOfFirst { it.id == bank.id }
        if (idx < 0) return false
        val oldName = banks[idx].name
        banks[idx] = bank
        writeList(BANKS_KEY, banks)
        if (oldName != bank.name) syncBankNameInRecords(bank.id, bank.name)
        return true
    }

    fun deleteBank(bankId: String): Boolean {
        writeList(BANKS_KEY, getBanks().filter { it.id != bankId })
        return true
    }

    /**
     * 计算并持久化题库的重复题数（查重 v3）与疑似错题数。
     * 导入预览期 / 题库管理页补算时由后台线程调用，结果随题库落盘；
     * 管理页徽章直接读 QuestionBank.dupCount / suspectCount，进页面零计算。
     */
    fun computeAndStoreCounts(bankId: String): Pair<Int, Int>? {
        val bank = getBanks().firstOrNull { it.id == bankId } ?: return null
        val groups = findDuplicateGroups(bank, getDedupThreshold())
        val dupCount = groups.sumOf { it.size } - groups.size
        val suspectCount = bank.questions.count { com.example.shuati.logic.Scoring.checkIsSuspect(it) }
        updateBank(bank.copy(dupCount = dupCount, suspectCount = suspectCount))
        return dupCount to suspectCount
    }

    /** 保存某题的 AI 解析（默认保存，覆盖旧值）。 */
    fun saveAiExplanation(bankId: String, questionId: String, ai: AiExplanation): Boolean {
        val bank = getBanks().firstOrNull { it.id == bankId } ?: return false
        val qs = bank.questions.toMutableList()
        val qi = qs.indexOfFirst { it.id == questionId }
        if (qi < 0) return false
        qs[qi] = qs[qi].copy(aiExplanation = ai)
        return updateBank(bank.copy(questions = qs))
    }

    /** 设置题库级 AI 默认提示语（空串则清除）。 */
    fun saveBankAiHint(bankId: String, hint: String?) {
        val bank = getBanks().firstOrNull { it.id == bankId } ?: return
        updateBank(bank.copy(aiHint = hint?.takeIf { it.isNotBlank() }))
    }

    private fun syncBankNameInRecords(bankId: String, newName: String) {
        val wrongs = getWrongRecords().map { if (it.bankId == bankId) it.copy(bankName = newName) else it }
        writeList(WRONGS_KEY, wrongs)
        val favs = getFavoriteRecords().map { if (it.bankId == bankId) it.copy(bankName = newName) else it }
        writeList(FAVORITES_KEY, favs)
        val history = getHistoryRecords().map { if (it.bankId == bankId) it.copy(bankName = newName) else it }
        writeList(HISTORY_KEY, history)
        val plans = getStudyPlans().map { if (it.bankId == bankId) it.copy(bankName = newName) else it }
        writeList(STUDY_PLANS_KEY, plans)
    }

    fun getAllTags(): List<String> {
        val set = LinkedHashSet<String>()
        getBanks().forEach { bank -> bank.questions.forEach { q -> q.tags.forEach { set.add(it) } } }
        return set.sorted()
    }

    // ─── 错题记录 ────────────────────────────────────────────────
    fun getWrongRecords(): List<WrongRecord> = readList(WRONGS_KEY)
    fun addWrongRecord(record: WrongRecord) {
        val records = getWrongRecords().toMutableList()
        var existing = records.find { r -> r.bankId == record.bankId && r.questionId == record.questionId }
        if (existing == null && record.bankId != "__all__") {
            existing = records.find { r ->
                r.bankId == "__all__" && r.questionId == record.questionId &&
                        r.question?.question == record.question?.question
            }
        }
        val filtered = records.filter { it !== existing }.toMutableList()
        filtered.add(0,
            record.copy(
                wrongCount = (existing?.wrongCount ?: 0) + 1,
                lastWrongAt = System.currentTimeMillis(),
                recordedAt = existing?.recordedAt ?: record.recordedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            )
        )
        writeList(WRONGS_KEY, filtered)
    }
    fun clearWrongRecords() = writeList(WRONGS_KEY, emptyList<WrongRecord>())
    fun removeWrongRecord(bankId: String, questionId: String) =
        writeList(WRONGS_KEY, getWrongRecords().filter { !(it.bankId == bankId && it.questionId == questionId) })

    // ─── 收藏记录 ────────────────────────────────────────────────
    fun getFavoriteRecords(): List<FavoriteRecord> = readList(FAVORITES_KEY)
    fun isFavorite(bankId: String, questionId: String): Boolean =
        getFavoriteRecords().any { it.bankId == bankId && it.questionId == questionId }
    fun toggleFavorite(record: FavoriteRecord): Boolean {
        val records = getFavoriteRecords().toMutableList()
        val idx = records.indexOfFirst { it.bankId == record.bankId && it.questionId == record.questionId }
        return if (idx >= 0) {
            records.removeAt(idx)
            writeList(FAVORITES_KEY, records)
            false
        } else {
            records.add(0, record)
            writeList(FAVORITES_KEY, records)
            true
        }
    }
    fun removeFavorite(bankId: String, questionId: String) =
        writeList(FAVORITES_KEY, getFavoriteRecords().filter { !(it.bankId == bankId && it.questionId == questionId) })
    fun removeFavoriteByQuestionId(questionId: String) =
        writeList(FAVORITES_KEY, getFavoriteRecords().filter { it.questionId != questionId })
    fun removeWrongRecordByQuestionId(questionId: String) =
        writeList(WRONGS_KEY, getWrongRecords().filter { it.questionId != questionId })

    // ─── 掌握度 streak ───────────────────────────────────────────
    fun getMasteryTarget(): Int {
        stringCache[MASTERY_TARGET_KEY.name]?.let { return it as Int }
        val v = readString(MASTERY_TARGET_KEY, "3")
        val n = v.toIntOrNull()?.takeIf { it > 0 } ?: 3
        stringCache[MASTERY_TARGET_KEY.name] = n
        return n
    }
    fun saveMasteryTarget(n: Int) {
        writeString(MASTERY_TARGET_KEY, n.toString())
        stringCache[MASTERY_TARGET_KEY.name] = n
    }

    // ─── 每题正确率统计（key = "bankId::questionId"，value = "correct,total"） ──
    @Suppress("UNCHECKED_CAST")
    fun getQuestionStats(): Map<String, String> {
        stringCache[QUESTION_STATS_KEY.name]?.let { return it as Map<String, String> }
        val raw = readString(QUESTION_STATS_KEY, "")
        val map = if (raw.isBlank()) emptyMap()
            else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        stringCache[QUESTION_STATS_KEY.name] = map
        return map
    }
    fun recordQuestionResult(bankId: String, questionId: String, correct: Boolean) {
        val key = "$bankId::$questionId"
        val stats = getQuestionStats().toMutableMap()
        val (c, t) = stats[key]?.split(",")?.let {
            (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        } ?: (0 to 0)
        stats[key] = "${if (correct) c + 1 else c},${t + 1}"
        writeString(QUESTION_STATS_KEY, json.encodeToString(stats))
        stringCache[QUESTION_STATS_KEY.name] = stats
    }

    // ─── 错误历史（key = "bankId::questionId"，value = 用户答错的选择列表） ──
    fun getWrongAnswers(bankId: String, questionId: String): List<String> =
        getAllWrongAnswers()["$bankId::$questionId"] ?: emptyList()
    fun recordWrongAnswer(bankId: String, questionId: String, userAnswer: String) {
        if (userAnswer.isBlank()) return
        val map = getAllWrongAnswers().toMutableMap()
        val key = "$bankId::$questionId"
        val list = (map[key] ?: emptyList()) + userAnswer
        map[key] = list.takeLast(8) // 最多保留 8 条历史
        writeString(WRONG_ANSWERS_KEY, json.encodeToString(map))
        stringCache[WRONG_ANSWERS_KEY.name] = map
    }

    // ─── 备份历史（最近导出时间戳列表） ──────────────────────────
    fun getBackupHistory(): List<Long> {
        val raw = readString(BACKUP_HISTORY_KEY, "")
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Long>>(raw) }.getOrDefault(emptyList())
    }
    fun recordBackup(ts: Long) {
        val list = (getBackupHistory() + ts).takeLast(5)
        writeString(BACKUP_HISTORY_KEY, json.encodeToString(list))
    }

    // ─── 偏好：动态取色（Compose 状态，开关即时生效） ─────────────
    var dynamicColor: Boolean by mutableStateOf(
        runBlocking { context.dataStore.data.first()[DYNAMIC_COLOR_KEY] ?: "1" == "1" }
    )
        private set
    fun saveDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        writeString(DYNAMIC_COLOR_KEY, if (enabled) "1" else "0")
    }

    // ─── 偏好：主题模式（system/light/dark） ────────────────────────
    var themeMode: String by mutableStateOf(
        runBlocking { context.dataStore.data.first()[THEME_MODE_KEY] ?: "system" }
    )
        private set
    fun saveThemeMode(mode: String) {
        themeMode = mode
        writeString(THEME_MODE_KEY, mode)
    }

    /**
     * 返回当前掌握度 streak map。结果来自 [stringCache]，避免每次调用都读磁盘 + JSON 解析。
     * 写入时必须同步调用 [updateMasteryStreaksCache] 以保持缓存一致。
     */
    fun getMasteryStreaks(): Map<String, Int> {
        stringCache[MASTERY_STREAKS_KEY.name]?.let {
            @Suppress("UNCHECKED_CAST")
            return it as Map<String, Int>
        }
        val raw = readString(MASTERY_STREAKS_KEY, "{}")
        val map = try { json.decodeFromString<Map<String, Int>>(raw) } catch (_: Exception) { emptyMap() }
        stringCache[MASTERY_STREAKS_KEY.name] = map
        return map
    }

    /** 写入 streaks map 后同步更新缓存，保证后续 [getMasteryStreaks] 命中。 */
    private fun saveMasteryStreaks(map: Map<String, Int>) {
        writeString(MASTERY_STREAKS_KEY, json.encodeToString(map))
        stringCache[MASTERY_STREAKS_KEY.name] = map
    }
    fun getStreak(bankId: String, questionId: String): Int = getMasteryStreaks()["$bankId::$questionId"] ?: 0
    fun incrementStreak(bankId: String, questionId: String): Int {
        val key = "$bankId::$questionId"
        val map = getMasteryStreaks().toMutableMap()
        map[key] = (map[key] ?: 0) + 1
        saveMasteryStreaks(map)
        return map[key]!!
    }
    fun resetStreak(bankId: String, questionId: String) {
        val key = "$bankId::$questionId"
        val map = getMasteryStreaks().toMutableMap()
        if (map.containsKey(key)) {
            map[key] = 0
            saveMasteryStreaks(map)
        }
    }

    /**
     * 今日连续：统计 history 中从当前日期向前连续有答题记录的天数
     * （等价小程序首页「今日连续」逻辑）
     */
    fun getTodayStreak(): Int {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        var streak = 0
        while (true) {
            val day = fmt.format(cal.time)
            val hasRecord = getHistoryRecords().any { h ->
                val ts = if (h.finishedAt > 0) h.finishedAt else h.createdAt
                ts > 0 && fmt.format(java.util.Date(ts)) == day
            }
            if (hasRecord) {
                streak++
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            } else break
        }
        return streak
    }

    // ─── 查重阈值 ────────────────────────────────────────────────
    fun getDedupThreshold(): Double {
        val v = readString(DEDUP_THRESHOLD_KEY, "0.45")
        val d = v.toDoubleOrNull() ?: 0.45
        return if (d in 0.1..1.0) d else 0.45
    }
    fun saveDedupThreshold(v: Double) =
        writeString(DEDUP_THRESHOLD_KEY, (v.coerceIn(0.1, 1.0)).toString())

    // ─── 刷题进度 ────────────────────────────────────────────────
    fun getAllProgress(): List<QuizProgress> = readList(PROGRESS_KEY)
    fun getProgress(bankId: String): QuizProgress? = getAllProgress().find { it.bankId == bankId }
    fun saveProgress(bankId: String, answeredIds: List<String>, markCompleted: Boolean = false) {
        val all = getAllProgress().toMutableList()
        val idx = all.indexOfFirst { it.bankId == bankId }
        val prev = if (idx >= 0) all[idx] else null
        val entry = (prev ?: QuizProgress(bankId = bankId)).copy(
            bankId = bankId,
            answeredIds = answeredIds,
            updatedAt = System.currentTimeMillis(),
            everCompleted = markCompleted || (prev?.everCompleted ?: false)
        )
        if (idx >= 0) all[idx] = entry else all.add(entry)
        writeList(PROGRESS_KEY, all)
    }
    fun clearProgress(bankId: String) =
        writeList(PROGRESS_KEY, getAllProgress().filter { it.bankId != bankId })
    fun clearSessionProgress(bankId: String) {
        val all = getAllProgress().toMutableList()
        val idx = all.indexOfFirst { it.bankId == bankId }
        if (idx < 0) return
        all[idx] = all[idx].copy(answeredIds = emptyList(), browseIndex = null, updatedAt = System.currentTimeMillis())
        writeList(PROGRESS_KEY, all)
    }
    fun getBrowseProgress(bankId: String): Int? = getProgress(bankId)?.browseIndex
    fun saveBrowseProgress(bankId: String, browseIndex: Int) {
        val all = getAllProgress().toMutableList()
        val idx = all.indexOfFirst { it.bankId == bankId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(browseIndex = browseIndex, updatedAt = System.currentTimeMillis())
        } else {
            all.add(QuizProgress(bankId = bankId, browseIndex = browseIndex, updatedAt = System.currentTimeMillis()))
        }
        writeList(PROGRESS_KEY, all)
    }

    // ─── 刷题历史 ────────────────────────────────────────────────
    fun getHistoryRecords(): List<QuizHistory> = readList(HISTORY_KEY)
    fun addHistoryRecord(record: QuizHistory) {
        val records = (getHistoryRecords() + record).take(200)
        writeList(HISTORY_KEY, records)
    }
    fun clearHistoryRecords() = writeList(HISTORY_KEY, emptyList<QuizHistory>())

    // ─── 学习计划 ────────────────────────────────────────────────
    fun getStudyPlans(): List<StudyPlan> = readList(STUDY_PLANS_KEY)
    fun saveStudyPlan(plan: StudyPlan): Boolean {
        val plans = getStudyPlans().toMutableList()
        val idx = plans.indexOfFirst { it.id == plan.id }
        if (idx >= 0) plans[idx] = plan else plans.add(0, plan)
        writeList(STUDY_PLANS_KEY, plans)
        return true
    }
    fun deleteStudyPlan(planId: String): Boolean {
        writeList(STUDY_PLANS_KEY, getStudyPlans().filter { it.id != planId })
        clearPlanProgress(planId)
        return true
    }
    fun archiveStudyPlan(planId: String): Boolean {
        val plans = getStudyPlans().toMutableList()
        val idx = plans.indexOfFirst { it.id == planId }
        if (idx < 0) return false
        plans[idx] = plans[idx].copy(archived = true)
        writeList(STUDY_PLANS_KEY, plans)
        return true
    }
    fun unarchiveStudyPlan(planId: String): Boolean {
        val plans = getStudyPlans().toMutableList()
        val idx = plans.indexOfFirst { it.id == planId }
        if (idx < 0) return false
        plans[idx] = plans[idx].copy(archived = false)
        writeList(STUDY_PLANS_KEY, plans)
        return true
    }

    // ─── 学习计划进度 ────────────────────────────────────────────
    fun getAllPlanProgress(): List<PlanProgress> = readList(PLAN_PROGRESS_KEY)
    fun getPlanProgress(planId: String): PlanProgress? = getAllPlanProgress().find { it.planId == planId }

    fun initPlanSession(planId: String, sessionQuestionIds: List<String>, sessionDate: String, existingAnsweredIds: List<String> = emptyList()) {
        val all = getAllPlanProgress().toMutableList()
        val idx = all.indexOfFirst { it.planId == planId }
        val entry = PlanProgress(
            planId = planId,
            answeredIds = existingAnsweredIds,
            sessionDate = sessionDate,
            sessionQuestionIds = sessionQuestionIds,
            sessionAnsweredCount = 0,
            updatedAt = System.currentTimeMillis()
        )
        if (idx >= 0) all[idx] = entry else all.add(entry)
        writeList(PLAN_PROGRESS_KEY, all)
    }

    fun savePlanAnswer(planId: String, questionId: String) {
        val all = getAllPlanProgress().toMutableList()
        val idx = all.indexOfFirst { it.planId == planId }
        if (idx < 0) return
        val prev = all[idx]
        val newCount = minOf(prev.sessionAnsweredCount + 1, prev.sessionQuestionIds.size)
        val sessionComplete = newCount >= prev.sessionQuestionIds.size
        val today = todayStr()
        val punchInDates = if (sessionComplete && !prev.punchInDates.contains(today))
            prev.punchInDates + today else prev.punchInDates
        all[idx] = prev.copy(
            answeredIds = (prev.answeredIds + questionId).toSet().toList(),
            sessionAnsweredCount = newCount,
            punchInDates = punchInDates,
            updatedAt = System.currentTimeMillis()
        )
        writeList(PLAN_PROGRESS_KEY, all)
    }

    fun savePlanBrowseStep(planId: String) {
        val all = getAllPlanProgress().toMutableList()
        val idx = all.indexOfFirst { it.planId == planId }
        if (idx < 0) return
        val prev = all[idx]
        val newIdx = minOf((prev.sessionBrowseIndex) + 1, prev.sessionQuestionIds.size)
        val sessionComplete = newIdx >= prev.sessionQuestionIds.size
        val today = todayStr()
        val punchInDates = if (sessionComplete && !prev.punchInDates.contains(today))
            prev.punchInDates + today else prev.punchInDates
        all[idx] = prev.copy(sessionBrowseIndex = newIdx, punchInDates = punchInDates, updatedAt = System.currentTimeMillis())
        writeList(PLAN_PROGRESS_KEY, all)
    }

    fun clearPlanProgress(planId: String) =
        writeList(PLAN_PROGRESS_KEY, getAllPlanProgress().filter { it.planId != planId })

    /** 直接设置浏览进度索引（跳题/续刷定位用） */
    fun setPlanBrowseIndex(planId: String, index: Int) {
        val all = getAllPlanProgress().toMutableList()
        val idx = all.indexOfFirst { it.planId == planId }
        if (idx < 0) return
        val prev = all[idx]
        all[idx] = prev.copy(sessionBrowseIndex = index.coerceAtLeast(0), updatedAt = System.currentTimeMillis())
        writeList(PLAN_PROGRESS_KEY, all)
    }

    private fun todayStr(): String {
        val d = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(d.get(java.util.Calendar.YEAR), d.get(java.util.Calendar.MONTH) + 1, d.get(java.util.Calendar.DAY_OF_MONTH))
    }

    // ─── 备份 / 还原 ─────────────────────────────────────────────
    /** 读取完整错答历史 Map（key="bankId::questionId"）。结果走 [stringCache]，写入方须同步更新缓存。 */
    @Suppress("UNCHECKED_CAST")
    fun getAllWrongAnswers(): Map<String, List<String>> {
        stringCache[WRONG_ANSWERS_KEY.name]?.let { return it as Map<String, List<String>> }
        val raw = readString(WRONG_ANSWERS_KEY, "")
        val map = if (raw.isBlank()) emptyMap()
            else runCatching { json.decodeFromString<Map<String, List<String>>>(raw) }.getOrDefault(emptyMap())
        stringCache[WRONG_ANSWERS_KEY.name] = map
        return map
    }

    fun buildBackupData(): BackupData {
        return BackupData(
            version = 6,
            exportedAt = System.currentTimeMillis(),
            banks = getBanks(),
            wrongRecords = getWrongRecords(),
            favoriteRecords = getFavoriteRecords(),
            progress = getAllProgress(),
            history = getHistoryRecords(),
            studyPlans = getStudyPlans(),
            planProgress = getAllPlanProgress(),
            questionStats = getQuestionStats(),
            wrongAnswers = getAllWrongAnswers(),
            settings = AppSettings(
                masteryTarget = getMasteryTarget(),
                masteryStreaks = getMasteryStreaks(),
                dedupThreshold = getDedupThreshold(),
                dynamicColor = dynamicColor,
                themeMode = themeMode
            )
        )
    }

    fun restoreBackup(data: BackupData, overwriteAll: Boolean): RestoreResult {
        if (data.banks.isEmpty()) return RestoreResult(0, 0, "备份文件格式错误")

        val existing = getBanks().associateByTo(LinkedHashMap()) { it.name }
        var imported = 0
        var skipped = 0
        for (bank in data.banks) {
            if (existing.containsKey(bank.name)) {
                if (overwriteAll) { existing[bank.name] = bank; imported++ } else skipped++
            } else { existing[bank.name] = bank; imported++ }
        }
        writeList(BANKS_KEY, existing.values.toList())

        if (data.wrongRecords.isNotEmpty()) {
            if (overwriteAll) writeList(WRONGS_KEY, data.wrongRecords)
            else {
                val exist = getWrongRecords()
                val set = exist.map { "${it.bankId}_${it.questionId}" }.toSet()
                val toAdd = data.wrongRecords.filter { !set.contains("${it.bankId}_${it.questionId}") }
                if (toAdd.isNotEmpty()) writeList(WRONGS_KEY, exist + toAdd)
            }
        }
        if (data.favoriteRecords.isNotEmpty()) {
            if (overwriteAll) writeList(FAVORITES_KEY, data.favoriteRecords)
            else {
                val exist = getFavoriteRecords()
                val set = exist.map { "${it.bankId}_${it.questionId}" }.toSet()
                val toAdd = data.favoriteRecords.filter { !set.contains("${it.bankId}_${it.questionId}") }
                if (toAdd.isNotEmpty()) writeList(FAVORITES_KEY, exist + toAdd)
            }
        }
        if (data.progress.isNotEmpty()) {
            if (overwriteAll) writeList(PROGRESS_KEY, data.progress)
            else {
                val exist = getAllProgress().associateByTo(LinkedHashMap()) { it.bankId }
                for (p in data.progress) {
                    val local = exist[p.bankId]
                    if (local == null) exist[p.bankId] = p
                    else exist[p.bankId] = local.copy(everCompleted = local.everCompleted || p.everCompleted)
                }
                writeList(PROGRESS_KEY, exist.values.toList())
            }
        }
        if (data.history.isNotEmpty()) {
            if (overwriteAll) writeList(HISTORY_KEY, data.history)
            else {
                val exist = getHistoryRecords()
                val set = exist.map { it.id }.toSet()
                val toAdd = data.history.filter { !set.contains(it.id) }
                if (toAdd.isNotEmpty()) writeList(HISTORY_KEY, exist + toAdd)
            }
        }
        if (data.studyPlans.isNotEmpty()) {
            if (overwriteAll) writeList(STUDY_PLANS_KEY, data.studyPlans)
            else {
                val exist = getStudyPlans()
                val set = exist.map { it.id }.toSet()
                val toAdd = data.studyPlans.filter { !set.contains(it.id) }
                if (toAdd.isNotEmpty()) writeList(STUDY_PLANS_KEY, exist + toAdd)
            }
        }
        if (data.planProgress.isNotEmpty()) {
            if (overwriteAll) writeList(PLAN_PROGRESS_KEY, data.planProgress)
            else {
                val exist = getAllPlanProgress().associateByTo(LinkedHashMap()) { it.planId }
                for (pp in data.planProgress) {
                    val local = exist[pp.planId]
                    if (local == null) exist[pp.planId] = pp
                    else {
                        val mergedPunch = (local.punchInDates + pp.punchInDates).toSet().toList()
                        exist[pp.planId] = local.copy(punchInDates = mergedPunch)
                    }
                }
                writeList(PLAN_PROGRESS_KEY, exist.values.toList())
            }
        }

        // 偏好设置（BackupData.settings 为非空的 AppSettings，无需判空）
        if (overwriteAll) {
            saveMasteryTarget(data.settings.masteryTarget)
            saveMasteryStreaks(data.settings.masteryStreaks)
            saveDedupThreshold(data.settings.dedupThreshold)
            saveDynamicColor(data.settings.dynamicColor)
            saveThemeMode(data.settings.themeMode)
        } else {
            if (readString(MASTERY_TARGET_KEY, "") == "") saveMasteryTarget(data.settings.masteryTarget)
            val localStreaks = getMasteryStreaks()
            val merged = data.settings.masteryStreaks + localStreaks  // 本地优先
            saveMasteryStreaks(merged)
            if (readString(DEDUP_THRESHOLD_KEY, "") == "") saveDedupThreshold(data.settings.dedupThreshold)
            if (readString(DYNAMIC_COLOR_KEY, "") == "") saveDynamicColor(data.settings.dynamicColor)
            if (readString(THEME_MODE_KEY, "") == "") saveThemeMode(data.settings.themeMode)
        }

        // 每题正确率统计（key="bankId::questionId"）
        if (data.questionStats.isNotEmpty()) {
            if (overwriteAll) writeString(QUESTION_STATS_KEY, json.encodeToString(data.questionStats))
            else if (readString(QUESTION_STATS_KEY, "").isBlank()) writeString(QUESTION_STATS_KEY, json.encodeToString(data.questionStats))
        }
        // 每题错答历史
        if (data.wrongAnswers.isNotEmpty()) {
            if (overwriteAll) writeString(WRONG_ANSWERS_KEY, json.encodeToString(data.wrongAnswers))
            else if (readString(WRONG_ANSWERS_KEY, "").isBlank()) writeString(WRONG_ANSWERS_KEY, json.encodeToString(data.wrongAnswers))
        }

        // 还原涉及多条未走 writeList 的写入（如 questionStats / wrongAnswers / 偏好设置），
        // 统一清一次缓存，确保后续读取拿到的是还原后的数据而非旧缓存。
        clearCache()

        return RestoreResult(imported, skipped)
    }

    /**
     * 导出为 ZIP：app_data.json（仅题库元数据，题目置空）+ banks/<id>.xlsx（每题库一份 Excel）。
     * 题目以原生 Excel 存放，不混入 JSON；还原时由 ExcelParser 回填题目。
     */
    fun exportBackupZip(): ByteArray {
        val data = buildBackupData()
        val baos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(baos)
        try {
            // 应用数据 JSON（题库题目置空，避免与 Excel 重复）
            val meta = data.copy(banks = data.banks.map { it.copy(questions = emptyList()) })
            zos.putNextEntry(java.util.zip.ZipEntry("app_data.json"))
            zos.write(json.encodeToString(meta).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            // 每题库一份 Excel
            data.banks.forEach { bank ->
                if (bank.questions.isNotEmpty()) {
                    zos.putNextEntry(java.util.zip.ZipEntry("banks/${safeZipName(bank.id)}.xlsx"))
                    zos.write(com.example.shuati.logic.ExcelWriter.writeBankToBytes(bank))
                    zos.closeEntry()
                }
            }
        } finally {
            zos.finish()
            zos.close()
        }
        return baos.toByteArray()
    }

    /**
     * 从 ZIP 还原：读取 app_data.json（含题库元数据），将 banks/<id>.xlsx 题目回填，
     * 再走统一 restoreBackup 逻辑。仅支持新版 ZIP 备份格式。
     */
    fun restoreBackupFromZip(bytes: ByteArray, overwriteAll: Boolean): RestoreResult {
        val entries = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }
        val jsonBytes = entries["app_data.json"] ?: return RestoreResult(0, 0, "备份文件格式错误：缺少 app_data.json")
        val meta = try {
            json.decodeFromString<BackupData>(jsonBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return RestoreResult(0, 0, "备份解析失败：${e.message}")
        }
        val banks = meta.banks.map { b ->
            val xlsx = entries["banks/${safeZipName(b.id)}.xlsx"]
            if (xlsx != null) {
                try {
                    val parsed = com.example.shuati.logic.ExcelParser.parseStream(java.io.ByteArrayInputStream(xlsx), b.name)
                    // questions 被 Excel 重新编码替换，查重/疑错缓存不可信，置失效由管理页补算
                    b.copy(questions = parsed.bank.questions, dupCount = -1, suspectCount = -1)
                } catch (_: Exception) { b }
            } else b
        }
        return restoreBackup(meta.copy(banks = banks), overwriteAll)
    }

    /** 题库 id 转为安全文件名（去除非法字符，保持两侧一致） */
    private fun safeZipName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9_\\-]"), "_")

    fun clearAllData() {
        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { it.clear() }
        }
        clearCache()   // 数据已全清，缓存必须同步清空，否则会读到旧数据
    }

    /**
     * 一键清理全部「示例/测试」数据（id 以 sample_ 开头）。
     * 仅删除测试数据，不影响用户自建题库与记录。返回删除的题库数量。
     * 与 SampleBanks.kt 配套：调用本方法即移除测试模块的全部落库数据（无残留）。
     */
    fun removeSampleData(): Int {
        val prefix = "sample_"
        val removedCount = getBanks().count { it.id.startsWith(prefix) }
        // 1) 题库
        writeList(BANKS_KEY, getBanks().filter { !it.id.startsWith(prefix) })
        // 2) 错题 / 收藏
        writeList(WRONGS_KEY, getWrongRecords().filter { !it.bankId.startsWith(prefix) })
        writeList(FAVORITES_KEY, getFavoriteRecords().filter { !it.bankId.startsWith(prefix) })
        // 3) 学习计划 + 计划进度
        val planIds = getStudyPlans()
            .filter { it.id.startsWith(prefix) || it.bankId.startsWith(prefix) }
            .map { it.id }
        writeList(STUDY_PLANS_KEY, getStudyPlans()
            .filter { !it.id.startsWith(prefix) && !it.bankId.startsWith(prefix) })
        writeList(PLAN_PROGRESS_KEY, getAllPlanProgress().filter { it.planId !in planIds })
        // 4) 历史
        writeList(HISTORY_KEY, getHistoryRecords().filter { !it.bankId.startsWith(prefix) })
        // 5) 刷题进度（逐题库 + __all__ 中 sample_:: 条目）
        val progress = getAllProgress().mapNotNull { p ->
            if (p.bankId.startsWith(prefix)) null
            else if (p.bankId == "__all__") {
                val kept = p.answeredIds.filter { !it.startsWith("$prefix::") }
                if (kept.isEmpty()) null else p.copy(answeredIds = kept, everCompleted = false)
            } else p
        }
        writeList(PROGRESS_KEY, progress)
        // 6) 每题正确率统计 / 掌握度 / 错答历史（key 形如 sample_::qid）
        val keptStats = getQuestionStats().filterKeys { !it.startsWith("$prefix::") }
        writeString(QUESTION_STATS_KEY, json.encodeToString(keptStats))
        stringCache[QUESTION_STATS_KEY.name] = keptStats
        saveMasteryStreaks(getMasteryStreaks().filterKeys { !it.startsWith("$prefix::") })
        val keptWrong = getAllWrongAnswers().filterKeys { !it.startsWith("$prefix::") }
        writeString(WRONG_ANSWERS_KEY, json.encodeToString(keptWrong))
        stringCache[WRONG_ANSWERS_KEY.name] = keptWrong
        return removedCount
    }

    /**
     * 本地存储占用（字节）：DataStore 持久化文件大小总和。
     * 等价小程序 Taro.getStorageInfoSync().currentSize 的展示用途。
     */
    fun storageUsedBytes(): Long {
        val dir = java.io.File(context.filesDir, "datastore")
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * 存储占用明细（字节）：题库 / 记录（错题+收藏+历史+计划）/ 其他（设置等开销）。
     * 三段之和恒等于 [storageUsedBytes] 的真实文件大小，随数据增长而真实变化，
     * 不再使用「每题估算字节」这类会与总占用脱节的假值。
     */
    data class StorageBreakdown(val banks: Long, val records: Long, val other: Long)

    fun storageBreakdown(): StorageBreakdown {
        val banks = getBanks()
        val bankBytes = safeJsonBytes(banks)
        val recBytes = safeJsonBytes(getWrongRecords()) +
            safeJsonBytes(getFavoriteRecords()) +
            safeJsonBytes(getHistoryRecords()) +
            safeJsonBytes(getStudyPlans())
        val total = storageUsedBytes().coerceAtLeast(1)
        val dataBytes = (bankBytes + recBytes).coerceAtLeast(1)
        val bankPart = total * bankBytes / dataBytes
        val recPart = total * recBytes / dataBytes
        val other = (total - bankPart - recPart).coerceAtLeast(0)
        return StorageBreakdown(bankPart, recPart, other)
    }

    private inline fun <reified T> safeJsonBytes(value: T): Long =
        runCatching { json.encodeToString(value).toByteArray().size.toLong() }.getOrDefault(0L)

    /** 查重：在指定题库中找出"题干相似且选项/答案一致"的重复题目对。等价 dedup 页逻辑。 */
    fun findDuplicates(bank: QuestionBank, threshold: Double): List<Triple<Question, Question, Double>> {
        val result = mutableListOf<Triple<Question, Question, Double>>()
        val qs = bank.questions
        for (i in qs.indices) {
            for (j in i + 1 until qs.size) {
                // 仅比对同类型（单选/多选同组），对齐小程序 superType
                if (superType(qs[i].type) != superType(qs[j].type)) continue
                val m = Scoring.compareForDedup(qs[i], qs[j], threshold)
                if (m.duplicate) result.add(Triple(qs[i], qs[j], m.score))
            }
        }
        return result.sortedByDescending { it.third }
    }

    /**
     * 分组查重：将相似度 ≥ threshold 的题目聚合为簇（并查集），
     * 返回每簇的题目 id 列表。供 UI 展示"第 N 组（k 题）"并支持批量删除。
     */
    fun findDuplicateGroups(bank: QuestionBank, threshold: Double): List<List<String>> {
        val qs = bank.questions
        val parent = IntArray(qs.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        for (i in qs.indices) for (j in i + 1 until qs.size) {
            // 仅比对同类型（单选/多选同组），对齐小程序 superType
            if (superType(qs[i].type) != superType(qs[j].type)) continue
            if (Scoring.compareForDedup(qs[i], qs[j], threshold).duplicate) union(i, j)
        }
        val groups = mutableMapOf<Int, MutableList<String>>()
        qs.forEachIndexed { idx, q -> groups.getOrPut(find(idx)) { mutableListOf() }.add(q.id) }
        return groups.values.filter { it.size >= 2 }.toList()
    }

    /** 跨题库查重：在多个题库间聚合重复簇，返回每簇的 (bankId, questionId) 列表 */
    private data class DedupItem(val bankId: String, val questionId: String, val type: QuestionType, val text: String, val options: List<String>?, val answer: String?)

    fun findDuplicateGroupsAcrossBanks(banks: List<QuestionBank>, threshold: Double): List<List<Pair<String, String>>> {
        val all = banks.flatMap { b -> b.questions.map { DedupItem(b.id, it.id, it.type, it.question, it.options, it.answer) } }
        val parent = IntArray(all.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        for (i in all.indices) for (j in i + 1 until all.size) {
            // 仅比对同类型（单选/多选同组），对齐小程序 superType
            if (superType(all[i].type) != superType(all[j].type)) continue
            if (Scoring.compareForDedup(all[i].text, all[i].options, all[i].answer, all[j].text, all[j].options, all[j].answer, threshold).duplicate) union(i, j)
        }
        val groups = mutableMapOf<Int, MutableList<Pair<String, String>>>()
        all.forEachIndexed { idx, t -> groups.getOrPut(find(idx)) { mutableListOf() }.add(t.bankId to t.questionId) }
        return groups.values.filter { it.size >= 2 }.toList()
    }

    /** 题型大类：单选/多选视为同组，其余各自独立。等价 similarity.ts superType。 */
    private fun superType(t: QuestionType): String = when (t) {
        QuestionType.single, QuestionType.multiple -> "choice"
        else -> t.name
    }

    /** 批量删除某题库的题目（按 id） */
    fun deleteQuestions(bankId: String, ids: Set<String>) {
        val bank = getBankById(bankId) ?: return
        if (ids.isEmpty()) return
        val updated = bank.copy(
            questions = bank.questions.filter { it.id !in ids },
            updatedAt = System.currentTimeMillis(),
            // 题目集合已变：查重与疑错缓存失效
            dupCount = -1,
            suspectCount = -1
        )
        updateBank(updated)
    }
}

data class BooleanResult(val success: Boolean, val message: String? = null)
data class RestoreResult(val imported: Int, val skipped: Int, val message: String? = null)
