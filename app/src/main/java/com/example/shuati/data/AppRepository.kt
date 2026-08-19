package com.example.shuati.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.shuati.logic.Scoring
import com.example.shuati.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.UUID

/**
 * 全局数据仓库，等价复刻小程序 utils/storage.ts 的同步存储语义。
 * 底层用 DataStore(Preferences) 以 JSON 字符串持久化；对外提供同步/挂起双接口，
 * 以匹配原小程序「同步读写」的使用习惯（页面内用 runBlocking 读取，写入用 edit）。
 */
class AppRepository(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "shuati_store")

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
    }

    // ─── 通用读写（类型安全，避免泛型擦除） ──────────────────────
    private inline fun <reified T> readList(key: androidx.datastore.preferences.core.Preferences.Key<String>): List<T> =
        runBlocking {
            val raw = context.dataStore.data.first()[key] ?: return@runBlocking emptyList<T>()
            try {
                json.decodeFromString(serializer(), raw)
            } catch (_: Exception) { emptyList() }
        }

    private inline fun <reified T> writeList(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: List<T>) =
        runBlocking { context.dataStore.edit { it[key] = json.encodeToString(serializer(), value) } }

    private fun readString(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: String): String =
        runBlocking { context.dataStore.data.first()[key] ?: default }
    private fun writeString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) =
        runBlocking { context.dataStore.edit { it[key] = value } }

    // ─── 题库 ────────────────────────────────────────────────────
    fun getBanks(): List<QuestionBank> = readList(BANKS_KEY)
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
        val v = readString(MASTERY_TARGET_KEY, "3")
        return v.toIntOrNull()?.takeIf { it > 0 } ?: 3
    }
    fun saveMasteryTarget(n: Int) = writeString(MASTERY_TARGET_KEY, n.toString())

    fun getMasteryStreaks(): Map<String, Int> {
        val raw = readString(MASTERY_STREAKS_KEY, "{}")
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyMap() }
    }
    private fun saveMasteryStreaks(map: Map<String, Int>) = writeString(MASTERY_STREAKS_KEY, json.encodeToString(map))
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
    fun buildBackupData(): BackupData {
        return BackupData(
            version = 5,
            exportedAt = System.currentTimeMillis(),
            banks = getBanks(),
            wrongRecords = getWrongRecords(),
            favoriteRecords = getFavoriteRecords(),
            progress = getAllProgress(),
            history = getHistoryRecords(),
            studyPlans = getStudyPlans(),
            planProgress = getAllPlanProgress(),
            settings = AppSettings(
                masteryTarget = getMasteryTarget(),
                masteryStreaks = getMasteryStreaks(),
                dedupThreshold = getDedupThreshold()
            )
        )
    }

    fun exportBackupJson(): String = json.encodeToString(buildBackupData())

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

        // 偏好设置
        if (data.settings != null) {
            if (overwriteAll) {
                saveMasteryTarget(data.settings.masteryTarget)
                saveMasteryStreaks(data.settings.masteryStreaks)
                saveDedupThreshold(data.settings.dedupThreshold)
            } else {
                if (readString(MASTERY_TARGET_KEY, "") == "") saveMasteryTarget(data.settings.masteryTarget)
                val localStreaks = getMasteryStreaks()
                val merged = data.settings.masteryStreaks + localStreaks  // 本地优先
                saveMasteryStreaks(merged)
                if (readString(DEDUP_THRESHOLD_KEY, "") == "") saveDedupThreshold(data.settings.dedupThreshold)
            }
        }

        return RestoreResult(imported, skipped)
    }

    fun clearAllData() {
        runBlocking {
            context.dataStore.edit { it.clear() }
        }
    }

    /**
     * 本地存储占用（字节）：DataStore 持久化文件大小总和。
     * 等价小程序 Taro.getStorageInfoSync().currentSize 的展示用途。
     */
    fun storageUsedBytes(): Long {
        val dir = java.io.File(context.filesDir, "datastore")
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** 查重：在指定题库中找出相似度 ≥ 阈值的题目对。等价 dedup 页逻辑。 */
    fun findDuplicates(bank: QuestionBank, threshold: Double): List<Triple<Question, Question, Double>> {
        val result = mutableListOf<Triple<Question, Question, Double>>()
        val qs = bank.questions
        for (i in qs.indices) {
            for (j in i + 1 until qs.size) {
                val s = Scoring.similarity(qs[i].question, qs[j].question)
                if (s >= threshold) result.add(Triple(qs[i], qs[j], s))
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
            if (Scoring.similarity(qs[i].question, qs[j].question) >= threshold) union(i, j)
        }
        val groups = mutableMapOf<Int, MutableList<String>>()
        qs.forEachIndexed { idx, q -> groups.getOrPut(find(idx)) { mutableListOf() }.add(q.id) }
        return groups.values.filter { it.size >= 2 }.toList()
    }

    /** 跨题库查重：在多个题库间聚合重复簇，返回每簇的 (bankId, questionId) 列表 */
    fun findDuplicateGroupsAcrossBanks(banks: List<QuestionBank>, threshold: Double): List<List<Pair<String, String>>> {
        val all = banks.flatMap { b -> b.questions.map { Triple(b.id, it.id, it.question) } }
        val parent = IntArray(all.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        for (i in all.indices) for (j in i + 1 until all.size) {
            if (Scoring.similarity(all[i].third, all[j].third) >= threshold) union(i, j)
        }
        val groups = mutableMapOf<Int, MutableList<Pair<String, String>>>()
        all.forEachIndexed { idx, t -> groups.getOrPut(find(idx)) { mutableListOf() }.add(t.first to t.second) }
        return groups.values.filter { it.size >= 2 }.toList()
    }

    /** 批量删除某题库的题目（按 id） */
    fun deleteQuestions(bankId: String, ids: Set<String>) {
        val bank = getBankById(bankId) ?: return
        if (ids.isEmpty()) return
        val updated = bank.copy(questions = bank.questions.filter { it.id !in ids }, updatedAt = System.currentTimeMillis())
        updateBank(updated)
    }
}

data class BooleanResult(val success: Boolean, val message: String? = null)
data class RestoreResult(val imported: Int, val skipped: Int, val message: String? = null)
