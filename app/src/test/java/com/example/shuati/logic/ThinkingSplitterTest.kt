package com.example.shuati.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ThinkingSplitter] 单元测试。
 *
 * 覆盖重点：流式场景最易出错的边界——标签跨 chunk 截断、标签变体、
 * 未闭合不回退、孤立 `<` 不误判、[ThinkingSplitter.flush] 契约、幂等与复用，
 * 以及「普通模型零回归」（无 think 标签时 answer 必须与输入逐字符相等）。
 */
class ThinkingSplitterTest {

    // ── 助手 ─────────────────────────────────────────────────────────

    /** 按 [chunkSize] 切分灌入全文，流结束时调用一次 flush（与生产用法一致）。 */
    private fun feed(
        splitter: ThinkingSplitter,
        text: String,
        chunkSize: Int
    ): ThinkingSplitter {
        require(chunkSize > 0)
        var i = 0
        while (i < text.length) {
            val end = minOf(i + chunkSize, text.length)
            splitter.pushContent(text.substring(i, end))
            i = end
        }
        splitter.flush()
        return splitter
    }

    /** 灌入全文后取出「答案 / 思考」二元组。 */
    private fun splitOf(text: String, chunkSize: Int): Pair<String, String> {
        val s = feed(ThinkingSplitter(), text, chunkSize)
        return s.answer to s.thinking
    }

    /**
     * 把 [text] 按 [sizes] 依次循环取块长度灌入（模拟真实网络分块的不规则性），
     * 结束后 flush。
     */
    private fun feedWithVariableChunks(text: String, sizes: List<Int>): ThinkingSplitter {
        val s = ThinkingSplitter()
        var i = 0
        var k = 0
        while (i < text.length) {
            val step = maxOf(1, sizes[k % sizes.size])
            val end = minOf(i + step, text.length)
            s.pushContent(text.substring(i, end))
            i = end
            k++
        }
        s.flush()
        return s
    }

    // ── 1. 普通模型零回归 ─────────────────────────────────────────────

    private val plainText = buildString {
        appendLine("## 正确答案")
        appendLine("选 **B**。这是一个「中文 + 换行 + Markdown + 代码块」的混合正文。")
        appendLine()
        appendLine("```kotlin")
        appendLine("val x = a < b && c > d   // 注意这里的 < 与 >")
        appendLine("```")
        appendLine()
        appendLine("## 解析")
        append("由题干可知：若 a < b，则 b - a > 0。")
        append("特殊字符：< > & \" ' \\ / 以及全角【】、……、—— ")
        append("制表符：\t行尾空格   ")
        append("Emoji：✅ ❌")
    }

    @Test
    fun plainText_chunked_answerIsByteIdenticalAndThinkingEmpty() {
        val s = feed(ThinkingSplitter(), plainText, 7)
        assertEquals("answer 必须与输入逐字符完全相等", plainText, s.answer)
        assertEquals("普通模型不得产生任何思考内容", "", s.thinking)
        assertFalse(s.inThinking)
        assertFalse(s.hasThinking)
    }

    @Test
    fun plainText_charByChar_answerIsByteIdenticalAndThinkingEmpty() {
        val s = feed(ThinkingSplitter(), plainText, 1)
        assertEquals(plainText, s.answer)
        assertEquals("", s.thinking)
    }

    @Test
    fun plainText_everyChunkSize_answerIsByteIdenticalAndThinkingEmpty() {
        for (size in 1..plainText.length) {
            val s = feed(ThinkingSplitter(), plainText, size)
            assertEquals("chunkSize=$size 时 answer 应逐字符相等", plainText, s.answer)
            assertEquals("chunkSize=$size 时 thinking 应为空", "", s.thinking)
        }
    }

    @Test
    fun plainText_irregularChunks_answerIsByteIdenticalAndThinkingEmpty() {
        val s = feedWithVariableChunks(plainText, listOf(1, 3, 2, 9, 4, 17, 5))
        assertEquals(plainText, s.answer)
        assertEquals("", s.thinking)
    }

    // ── 2. 标签跨 chunk 截断：遍历所有切点 ──────────────────────────────

    @Test
    fun thinkTagSplitAtEveryCutPoint_twoChunks() {
        val full = "<think>abc</think>def"
        for (cut in 0..full.length) {
            val s = ThinkingSplitter()
            s.pushContent(full.substring(0, cut))
            s.pushContent(full.substring(cut))
            s.flush()
            assertEquals("切点=$cut 时 answer 应恒为 def", "def", s.answer)
            assertEquals("切点=$cut 时 thinking 应恒为 abc", "abc", s.thinking)
            assertFalse("切点=$cut 后不应仍处于思考态", s.inThinking)
        }
    }

    @Test
    fun thinkTagSplitAtEveryCutPoint_threeChunks() {
        val full = "<think>abc</think>def"
        for (i in 0..full.length) {
            for (j in i..full.length) {
                val s = ThinkingSplitter()
                s.pushContent(full.substring(0, i))
                s.pushContent(full.substring(i, j))
                s.pushContent(full.substring(j))
                s.flush()
                assertEquals("切点=($i,$j) 时 answer 应恒为 def", "def", s.answer)
                assertEquals("切点=($i,$j) 时 thinking 应恒为 abc", "abc", s.thinking)
            }
        }
    }

    @Test
    fun thinkingVariantTagSplitAtEveryCutPoint() {
        val full = "<thinking>abc</thinking>def"
        for (cut in 0..full.length) {
            val s = ThinkingSplitter()
            s.pushContent(full.substring(0, cut))
            s.pushContent(full.substring(cut))
            s.flush()
            assertEquals("切点=$cut 时 answer 应恒为 def", "def", s.answer)
            assertEquals("切点=$cut 时 thinking 应恒为 abc", "abc", s.thinking)
        }
    }

    // ── 3. 逐字符灌入 == 一次性灌入 ────────────────────────────────────

    @Test
    fun charByChar_matchesSinglePush() {
        val samples = listOf(
            "<think>abc</think>def",
            "<think>思考过程</think>最终答案",
            "前<think>中</think>后",
            "<think>T1 < T2</think>x < y",
            "<THINK>A B</THINK>C",
            "<thinking>多\n行\n思考</thinking>多\n行\n答案",
            "a < b 与 <div>块</div>",
            "<think>未闭合的思考",
            "A<thinking>B</thinking>C<thinking>D</thinking>E"
        )
        for (sample in samples) {
            val oneShot = splitOf(sample, sample.length.coerceAtLeast(1))
            val perChar = splitOf(sample, 1)
            assertEquals("样例「$sample」answer 不一致", oneShot.first, perChar.first)
            assertEquals("样例「$sample」thinking 不一致", oneShot.second, perChar.second)
        }
    }

    @Test
    fun everyChunkSize_matchesSinglePush() {
        val samples = listOf(
            "<think>abc</think>def",
            "<think>思考 < 过程</think>答案 < 正文",
            "< thinking >x</ thinking >y",
            "<think>abc",
            "<div>not a tag</div>"
        )
        for (sample in samples) {
            val oneShot = splitOf(sample, sample.length.coerceAtLeast(1))
            for (size in 1..sample.length) {
                val got = splitOf(sample, size)
                assertEquals("样例「$sample」chunkSize=$size answer 不一致", oneShot.first, got.first)
                assertEquals("样例「$sample」chunkSize=$size thinking 不一致", oneShot.second, got.second)
            }
        }
    }

    // ── 4. 标签变体 ───────────────────────────────────────────────────

    @Test
    fun tagVariants_areRecognized() {
        val variants = listOf(
            "<think>abc</think>def",
            "<THINK>abc</THINK>def",
            "<Think>abc</Think>def",
            "<ThInK>abc</ThInK>def",
            "<think >abc</think >def",
            "< thinking >abc</ thinking >def",
            "<thinking>abc</thinking>def",
            "<THINKING>abc</THINKING>def",
            "< think >abc</ think >def"
        )
        for (v in variants) {
            val (answer, thinking) = splitOf(v, 1)
            assertEquals("变体「$v」answer 应为 def", "def", answer)
            assertEquals("变体「$v」thinking 应为 abc", "abc", thinking)
        }
    }

    @Test
    fun tagVariants_mixedOpenAndCloseStyles() {
        val (answer, thinking) = splitOf("<THINK >abc</ thinking>def", 1)
        assertEquals("def", answer)
        assertEquals("abc", thinking)
    }

    // ── 5. 未闭合 ─────────────────────────────────────────────────────

    @Test
    fun unclosedThinkBlock_keepsThinkingAndDoesNotRollback() {
        val s = ThinkingSplitter()
        s.pushContent("<think>abc")
        assertEquals("未闭合时思考内容应完整保留", "abc", s.thinking)
        assertEquals("未闭合时不应有可见答案", "", s.answer)
        assertTrue("未闭合时 inThinking 应为 true", s.inThinking)
        assertTrue(s.hasThinking)

        // 流结束：已吐出的内容不回退
        val tail = s.flush()
        assertEquals("flush 不应回退已吐出的思考内容", "abc", s.thinking)
        assertEquals("", s.answer)
        assertTrue(s.inThinking)
        assertTrue("未闭合且无未决尾部时 flush 应返回 Empty", tail.isEmpty)
    }

    @Test
    fun unclosedThinkBlock_thenMoreThinkingKeepsAppending() {
        val s = ThinkingSplitter()
        s.pushContent("<think>abc")
        s.pushContent("def")
        assertEquals("abcdef", s.thinking)
        assertEquals("", s.answer)
    }

    // ── 6. 多思考块 ───────────────────────────────────────────────────

    @Test
    fun multipleThinkBlocks() {
        val (answer, thinking) = splitOf("A<thinking>B</thinking>C<thinking>D</thinking>E", 1)
        assertEquals("ACE", answer)
        assertEquals("BD", thinking)
    }

    @Test
    fun multipleThinkBlocks_variantsMixed() {
        val (answer, thinking) = splitOf("A<think>B</think>C<THINK>D</THINK>E", 3)
        assertEquals("ACE", answer)
        assertEquals("BD", thinking)
    }

    // ── 7. 孤立 `<` 不误判 ────────────────────────────────────────────

    @Test
    fun isolatedLessThan_insideThinking_isPreserved() {
        val (answer, thinking) = splitOf("<think>a < b</think>ans", 1)
        assertEquals("a < b", thinking)
        assertEquals("ans", answer)
    }

    @Test
    fun isolatedLessThan_insideAnswer_isPreserved() {
        val text = "a < b 且 <div>块</div> c < d"
        val (answer, thinking) = splitOf(text, 1)
        assertEquals("孤立 < 不得丢字符或错位", text, answer)
        assertEquals("", thinking)
    }

    @Test
    fun isolatedLessThan_bothSides() {
        val (answer, thinking) = splitOf("<think>T1 < T2</think>x < y", 1)
        assertEquals("T1 < T2", thinking)
        assertEquals("x < y", answer)
    }

    @Test
    fun loneLessThanInsideText_doesNotSwallowFollowingTag() {
        // 正文里的孤立 `<` 之后紧跟着一个真正的 think 标签，标签必须仍被识别
        val (answer, thinking) = splitOf("a < b<think>隐藏</think>可见", 1)
        assertEquals("a < b可见", answer)
        assertEquals("隐藏", thinking)
    }

    // ── 8. 尾部孤立 `<` 与 flush() 契约 ────────────────────────────────

    @Test
    fun trailingLoneLessThan_isHeldUntilFlush() {
        val s = ThinkingSplitter()
        s.pushContent("abc<")
        assertFalse("未调用 flush 前，尾部孤立 < 不应出现在 answer", s.answer.contains("<"))
        assertEquals("abc", s.answer)

        val flushed = s.flush()
        assertEquals("flush 必须补吐未决尾部", "abc<", s.answer)
        assertEquals("flush 的增量应为被补吐的 <", "<", flushed.answerDelta)
        assertEquals("", flushed.thinkingDelta)
    }

    @Test
    fun trailingPartialTag_isHeldUntilFlush() {
        val s = ThinkingSplitter()
        s.pushContent("abc<thi")
        assertEquals("可能构成标签的残片不得当正文吐出", "abc", s.answer)
        s.flush()
        assertEquals("abc<thi", s.answer)
    }

    @Test
    fun flushOnFreshSplitter_returnsEmpty() {
        val s = ThinkingSplitter()
        assertSame(ThinkingSplitResult.Empty, s.flush())
        assertEquals("", s.answer)
        assertEquals("", s.thinking)
    }

    // ── 9. 空 chunk / 空输入 ──────────────────────────────────────────

    @Test
    fun emptyChunksProduceNoDelta() {
        val s = ThinkingSplitter()
        val c = s.pushContent("")
        val r = s.pushReasoning("")
        assertSame("pushContent(\"\") 应返回 Empty 单例", ThinkingSplitResult.Empty, c)
        assertSame("pushReasoning(\"\") 应返回 Empty 单例", ThinkingSplitResult.Empty, r)
        assertTrue(c.isEmpty)
        assertTrue(r.isEmpty)
        assertEquals("", c.answerDelta)
        assertEquals("", c.thinkingDelta)
        assertEquals("", s.answer)
        assertEquals("", s.thinking)
        assertEquals("", s.pendingText)
    }

    @Test
    fun emptyChunksInterleavedDoNotAffectResult() {
        val s = ThinkingSplitter()
        s.pushContent("")
        s.pushContent("<thi")
        s.pushContent("")
        s.pushContent("nk>abc</thi")
        s.pushContent("")
        s.pushContent("nk>def")
        s.flush()
        assertEquals("def", s.answer)
        assertEquals("abc", s.thinking)
    }

    @Test
    fun result_isEmptySemantics() {
        assertTrue(ThinkingSplitResult.Empty.isEmpty)
        assertFalse(ThinkingSplitResult(answerDelta = "x").isEmpty)
        assertFalse(ThinkingSplitResult(thinkingDelta = "x").isEmpty)
    }

    // ── 10. flush() 幂等 ──────────────────────────────────────────────

    @Test
    fun flushIsIdempotent() {
        val s = ThinkingSplitter()
        s.pushContent("abc<")
        val first = s.flush()
        assertEquals("<", first.answerDelta)

        val second = s.flush()
        assertSame("第二次 flush 应返回 Empty 单例", ThinkingSplitResult.Empty, second)
        assertTrue(second.isEmpty)
        assertEquals("重复 flush 不得重复追加内容", "abc<", s.answer)

        val third = s.flush()
        assertSame(ThinkingSplitResult.Empty, third)
        assertEquals("abc<", s.answer)
    }

    @Test
    fun flushIdempotent_whenInsideUnclosedThinkBlock() {
        val s = ThinkingSplitter()
        s.pushContent("<think>ab<")
        assertEquals("ab", s.thinking)
        val first = s.flush()
        assertEquals("<", first.thinkingDelta)
        assertEquals("ab<", s.thinking)
        val second = s.flush()
        assertTrue(second.isEmpty)
        assertEquals("ab<", s.thinking)
    }

    @Test
    fun flushClearsPending() {
        val s = ThinkingSplitter()
        s.pushContent("abc<")
        assertEquals("<", s.pendingText)
        s.flush()
        assertEquals("flush 后 pending 必须清空", "", s.pendingText)
    }

    // ── 11. reset() ───────────────────────────────────────────────────

    @Test
    fun resetClearsAllState() {
        val s = ThinkingSplitter()
        s.pushContent("<think>abc")
        s.pushReasoning("独立思考")
        s.pushContent("答案")
        assertTrue(s.hasThinking)

        s.reset()
        assertEquals("", s.answer)
        assertEquals("", s.thinking)
        assertEquals("", s.pendingText)
        assertFalse(s.inThinking)
        assertFalse(s.hasThinking)
        assertSame(ThinkingSplitResult.Empty, s.flush())
    }

    @Test
    fun resetAllowsReuse() {
        val s = ThinkingSplitter()
        s.pushContent("<think>A</think>B")
        s.flush()
        assertEquals("B", s.answer)
        assertEquals("A", s.thinking)

        s.reset()
        s.pushContent("<think>C</think>D")
        s.flush()
        assertEquals("复用后 answer 应只有新一轮内容", "D", s.answer)
        assertEquals("复用后 thinking 应只有新一轮内容", "C", s.thinking)
    }

    // ── 12. 独立 reasoning 字段 ───────────────────────────────────────

    @Test
    fun pushReasoningGoesOnlyToThinking() {
        val s = ThinkingSplitter()
        val first = s.pushReasoning("思考一")
        assertEquals("思考一", first.thinkingDelta)
        assertEquals("", first.answerDelta)
        val second = s.pushReasoning("思考二")
        assertEquals("思考二", second.thinkingDelta)

        assertEquals("思考一思考二", s.thinking)
        assertEquals("pushReasoning 不得污染 answer", "", s.answer)
        assertTrue(s.hasThinking)
        assertFalse("pushReasoning 不应改变思考块状态", s.inThinking)
    }

    @Test
    fun pushReasoning_doesNotParseTags() {
        val s = ThinkingSplitter()
        s.pushReasoning("<think>原样保留</think>")
        assertEquals("独立思考字段不做标签解析", "<think>原样保留</think>", s.thinking)
        assertEquals("", s.answer)
    }

    @Test
    fun interleavedContentAndReasoning_accumulateIndependently() {
        val s = ThinkingSplitter()
        s.pushReasoning("R1")
        s.pushContent("A1")
        s.pushReasoning("R2")
        s.pushContent("A2")
        s.pushReasoning("R3")
        s.pushContent("A3")
        s.flush()

        assertEquals("A1A2A3", s.answer)
        assertEquals("R1R2R3", s.thinking)
    }

    @Test
    fun interleavedContentWithThinkTags_andReasoning() {
        val s = ThinkingSplitter()
        s.pushReasoning("先思考")
        s.pushContent("可见1<think>内嵌思考</think>可见2")
        s.pushReasoning("后思考")
        s.pushContent("可见3")
        s.flush()

        assertEquals("可见1可见2可见3", s.answer)
        assertEquals("先思考内嵌思考后思考", s.thinking)
    }

    // ── 13. 全量快照语义（服务端 Delta / Reasoning 契约）────────────────

    @Test
    fun answerAndThinkingAreCumulativeSnapshots() {
        val s = ThinkingSplitter()
        s.pushContent("<think>思")
        assertEquals("answer 应是累计全量", "", s.answer)
        assertEquals("thinking 应是累计全量", "思", s.thinking)

        s.pushContent("考")
        assertEquals("thinking 应随每个 chunk 累加", "思考", s.thinking)
        assertEquals("", s.answer)

        s.pushContent("</think>答")
        assertEquals("思考", s.thinking)
        assertEquals("答", s.answer)

        s.pushContent("案")
        assertEquals("闭合后 thinking 不再增长", "思考", s.thinking)
        assertEquals("answer 应随每个 chunk 累加", "答案", s.answer)
    }

    @Test
    fun deltasSumUpToFinalSnapshot() {
        val stream = listOf(
            "<thi", "nk>深", "度思", "考", "</thi", "nk>", "最终答", "案内容"
        )
        val s = ThinkingSplitter()
        var answerAcc = ""
        var thinkingAcc = ""
        for (chunk in stream) {
            val r = s.pushContent(chunk)
            answerAcc += r.answerDelta
            thinkingAcc += r.thinkingDelta
        }
        val tail = s.flush()
        answerAcc += tail.answerDelta
        thinkingAcc += tail.thinkingDelta

        assertEquals("增量累加应等于 answer 全量快照", s.answer, answerAcc)
        assertEquals("增量累加应等于 thinking 全量快照", s.thinking, thinkingAcc)
        // 流：思考块「深度思考」+ 闭标签后的正文「最终答」「案内容」
        assertEquals("最终答案内容", s.answer)
        assertEquals("深度思考", s.thinking)
    }

    // ── 14. isLikelyReasoningModel ────────────────────────────────────

    @Test
    fun isLikelyReasoningModel_positiveCases() {
        val positives = listOf(
            "o1",
            "o3-mini",
            "o4-mini",
            "gpt-5",
            "deepseek-r1",
            "DeepSeek-R1-Distill-Qwen-32B",
            "deepseek_reasoner",
            "Pro/deepseek-ai/DeepSeek-R1",
            "QwQ-32B",
            "glm-z1-9b",
            "kimi-thinking"
        )
        for (id in positives) {
            assertTrue("「$id」应被判定为推理模型", AiExplainService.isLikelyReasoningModel(id))
        }
    }

    @Test
    fun isLikelyReasoningModel_negativeCases() {
        val negatives = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "deepseek-v3",
            "deepseek-chat",
            "qwen-plus",
            "claude-sonnet-4-5",
            "grok-4",
            "glm-4-plus",
            ""
        )
        for (id in negatives) {
            assertFalse("「$id」不应被判定为推理模型", AiExplainService.isLikelyReasoningModel(id))
        }
    }

    @Test
    fun isLikelyReasoningModel_deepseekNonReasoningNotMisjudged() {
        // 特别验证：含 "deepseek" 子串的非推理模型不得被宽松匹配误判
        assertFalse("deepseek-chat 不得被误判为推理模型", AiExplainService.isLikelyReasoningModel("deepseek-chat"))
        assertFalse("deepseek-v3 不得被误判为推理模型", AiExplainService.isLikelyReasoningModel("deepseek-v3"))
        assertFalse("deepseek-coder 不得被误判为推理模型", AiExplainService.isLikelyReasoningModel("deepseek-coder"))
        assertFalse("DeepSeek-V3 不得被误判为推理模型", AiExplainService.isLikelyReasoningModel("DeepSeek-V3"))
        assertFalse("deepseek_chat 不得被误判为推理模型", AiExplainService.isLikelyReasoningModel("deepseek_chat"))
        assertFalse("Pro/deepseek-ai/DeepSeek-V3 不得被误判", AiExplainService.isLikelyReasoningModel("Pro/deepseek-ai/DeepSeek-V3"))
    }

    @Test
    fun isLikelyReasoningModel_blankAndSeparatorOnly() {
        assertFalse(AiExplainService.isLikelyReasoningModel(""))
        assertFalse(AiExplainService.isLikelyReasoningModel("   "))
        assertFalse(AiExplainService.isLikelyReasoningModel("___"))
        assertFalse(AiExplainService.isLikelyReasoningModel("///"))
    }

    @Test
    fun isLikelyReasoningModel_reasoningVariantsAreDetected() {
        assertTrue(AiExplainService.isLikelyReasoningModel("DeepSeek-R1"))
        assertTrue(AiExplainService.isLikelyReasoningModel("deepseek-reasoner"))
        assertTrue(AiExplainService.isLikelyReasoningModel("deepseek_ai/DeepSeek_R1"))
        assertTrue(AiExplainService.isLikelyReasoningModel("qwen3-thinking"))
        assertTrue(AiExplainService.isLikelyReasoningModel("gpt-oss:120b"))
        assertTrue(AiExplainService.isLikelyReasoningModel("some-model-thinking"))
        assertTrue(AiExplainService.isLikelyReasoningModel("o1-mini"))
    }

    // ── 15. 端到端组合场景 ────────────────────────────────────────────

    @Test
    fun realisticStream_reasoningFieldThenContentWithInlineThink() {
        val s = ThinkingSplitter()
        // 模拟 DeepSeek-R1：先吐 reasoning 字段，再吐正文（正文里可能还内嵌 think 块）
        s.pushReasoning("先确认题干要求：")
        s.pushReasoning("比较 A 与 B 的差异。")
        s.pushContent("<think>")
        s.pushContent("综合来看，")
        s.pushContent("B 更符合。")
        s.pushContent("</think>")
        s.pushContent("## 正确答案\n选 B。")
        s.pushContent("因为 a < b。")
        s.flush()

        assertEquals("先确认题干要求：比较 A 与 B 的差异。综合来看，B 更符合。", s.thinking)
        assertEquals("## 正确答案\n选 B。因为 a < b。", s.answer)
        assertFalse(s.inThinking)
    }

    @Test
    fun realisticStream_unclosedThinkThenFlushStillSeparates() {
        val s = ThinkingSplitter()
        s.pushContent("<think>只有思考没有答案")
        s.flush()
        assertEquals("只有思考没有答案", s.thinking)
        assertEquals("未闭合时落盘答案为空", "", s.answer)
    }
}
