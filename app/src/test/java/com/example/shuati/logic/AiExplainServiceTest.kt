package com.example.shuati.logic

import com.example.shuati.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AiExplainService 纯函数单测（JVM，无网络、无 Android 依赖）。
 * 覆盖：内置联网判定、prompt caching 命中、buildPrompt 注入、SSE 增量解析累积、博查截断。
 */
class AiExplainServiceTest {

    // ── modelHasBuiltinSearch：allowlist 子串匹配 ──────────────────────
    @Test
    fun modelHasBuiltinSearch_hitsAllowlist() {
        val hits = listOf(
            "deepseek-chat",
            "qwen-max",
            "qwen3-32b",
            "glm-4-plus",
            "kimi-k2",
            "moonshot-v1-8k",
            "chatgpt-4o",
            "gpt-4.1-mini",
            "ernie-4.0",
            "doubao-pro-32k",
            "hunyuan-turbo",
            "spark-4.0",
            "perplexity-sonar",
            "tongyi",
            "abab6.5",
            "tiangong"
        )
        hits.forEach { assertTrue("应为内置联网模型: $it", AiExplainService.modelHasBuiltinSearch(it)) }
    }

    @Test
    fun modelHasBuiltinSearch_missesNonListed() {
        val misses = listOf(
            "llama-3-70b",
            "gpt-3.5-turbo",
            "claude-3-opus",
            "gemini-1.5-pro",
            "mixtral-8x7b",
            "baichuan-53b",
            ""
        )
        misses.forEach { assertFalse("不应判为内置联网模型: $it", AiExplainService.modelHasBuiltinSearch(it)) }
    }

    // ── providerSupportsPromptCache：仅对需 cache_control 的 OpenAI 兼容服务开启 ──
    @Test
    fun providerSupportsPromptCache_hitsKnownHosts() {
        val hits = listOf(
            "https://api.openai.com/v1",
            "https://openrouter.ai/api/v1",
            "https://api.groq.com/openai/v1",
            "https://api.together.ai/v1",
            "https://api.moonshot.cn/v1"
        )
        hits.forEach { assertTrue("应支持前缀缓存: $it", AiExplainService.providerSupportsPromptCache(it)) }
    }

    @Test
    fun providerSupportsPromptCache_missesOthers() {
        val misses = listOf(
            "https://api.deepseek.com",   // 服务端自动前缀缓存，无需标记
            "https://api.bochaai.com",
            "https://api.tavily.com",
            "http://localhost:11434/v1",
            "https://api.anthropic.com",
            ""
        )
        misses.forEach { assertFalse("不应发 cache_control: $it", AiExplainService.providerSupportsPromptCache(it)) }
    }

    // ── buildPrompt：各段注入 ──────────────────────────────────────────
    private val sampleQ = Question(
        id = "q1",
        question = "1+1=?",
        options = listOf("2", "3"),
        answer = "2"
    )

    @Test
    fun buildPrompt_withoutPrevAnswer_hasNoPrevBlock() {
        val p = AiExplainService.buildPrompt(sampleQ, "", null, null)
        assertTrue(p.contains("【题干】"))
        assertTrue(p.contains("【选项】"))
        assertTrue(p.contains("【标准答案】"))
        assertFalse("无 prevAnswer 不应出现「上一次解析」段", p.contains("【上一次解析"))
    }

    @Test
    fun buildPrompt_withHint_injectsHintBlock() {
        val p = AiExplainService.buildPrompt(sampleQ, "注册会计师考试", null, null)
        assertTrue(p.contains("【用户补充提示】"))
        assertTrue(p.contains("注册会计师考试"))
    }

    @Test
    fun buildPrompt_withSearchText_injectsSearchBlock() {
        val p = AiExplainService.buildPrompt(sampleQ, "", "搜索资料 xyz", null)
        assertTrue(p.contains("【联网搜索到的相关资料】"))
        assertTrue(p.contains("搜索资料 xyz"))
    }

    @Test
    fun buildPrompt_withPrevAnswer_injectsPrevBlock() {
        val prev = "上次答案认为是 2，因为 1+1=2。"
        val p = AiExplainService.buildPrompt(sampleQ, "", null, prev)
        assertTrue(p.contains("【上一次解析"))
        assertTrue(p.contains(prev))
        // 明确要求不照抄、冲突以标准答案为准
        assertTrue(p.contains("请勿照抄"))
    }

    // ── parseSseContent：SSE 增量解析（对应 answer += content 的取数路径） ──
    @Test
    fun parseSseContent_validDeltasReturnContent() {
        assertEquals("你", AiExplainService.parseSseContent("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}"))
        assertEquals("", AiExplainService.parseSseContent("data: {\"choices\":[{\"delta\":{}}]}")) // 空 content → 空串
    }

    @Test
    fun parseSseContent_nonDataOrDoneOrBad_returnNull() {
        assertNull(AiExplainService.parseSseContent(""))              // 空行
        assertNull(AiExplainService.parseSseContent("event: ping"))  // 非 data 行
        assertNull(AiExplainService.parseSseContent("data: [DONE]")) // 结束标记
        assertNull(AiExplainService.parseSseContent("data: {bad json")) // 解析失败
    }

    @Test
    fun parseSseContent_accumulatesLikeTyping() {
        // 模拟流式分块：每个 Delta 仅带新增字符，拼接即完整文本（对应 answer += content）。
        val lines = listOf(
            "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"世\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"界\"}}]}",
            "data: [DONE]"
        )
        val answer = StringBuilder()
        lines.forEach { line ->
            val c = AiExplainService.parseSseContent(line)
            if (!c.isNullOrEmpty()) answer.append(c)
        }
        assertEquals("你好世界", answer.toString())
    }

    // ── bochaResultsToSearch：取前 3 条 + 摘要截断 500 + URL 引用 ──────
    @Test
    fun bochaResultsToSearch_truncatesAndCites() {
        val items = listOf(
            AiExplainService.BochaResult(name = "A", url = "https://a.com", snippet = "x".repeat(600)),
            AiExplainService.BochaResult(name = "B", url = "", snippet = "短摘要"),
            AiExplainService.BochaResult(name = "C", url = "https://c.com", snippet = "y".repeat(50))
        )
        val r = AiExplainService.bochaResultsToSearch(items)
        // 摘要超 500 字被截断（保留 500 个 x，但不含 501 个）
        assertTrue(r.text.contains("x".repeat(500)))
        assertFalse(r.text.contains("x".repeat(501)))
        // 仅非空 URL 作为引用，B 的空 url 被过滤
        assertEquals(listOf("https://a.com", "https://c.com"), r.citations)
        assertNotNull(r)
    }
}
