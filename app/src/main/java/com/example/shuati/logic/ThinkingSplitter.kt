package com.example.shuati.logic

/**
 * 思考内容切分器 —— 把大模型的输出增量流切成「可见答案」与「思考过程」两路。
 *
 * 纯 Kotlin 实现，不依赖 Android / Compose / 网络库，可被 JVM 单元测试直接调用。
 *
 * ## 背景：思考内容有两种形态
 * 1. **独立字段**：OpenAI 兼容端点的 delta 里带 `reasoning_content` / `reasoning`
 *    （DeepSeek-R1、硅基流动、QwQ、GLM-Z1 等推理模型常见）。这类由调用方用 [pushReasoning] 直接灌入。
 * 2. **内嵌标签**：正文 content 里包 `<think>…</think>`（部分兼容端与本地模型，亦有 `<thinking>` 变体）。
 *    这类由 [pushContent] 自动识别并剥离。
 *
 * ## 边界处理（流式场景的核心难点）
 * - **标签跨 chunk 截断**：chunk1 结尾是 `<thi`、chunk2 开头是 `nk>` 时，未决尾部会暂存进 pending，
 *   下个 chunk 到来后拼接再判断，绝不会把 `<thi` 当正文吐出去。
 * - **大小写与空白变体**：`<think>` / `<THINK>` / `<think >` / `< thinking >` / `<thinking>` 均可识别。
 * - **未闭合**：流结束时仍处于思考态（没有 `</think>`），已吐出的思考增量保持有效，不回退。
 * - **普通模型**：输出里根本没有 think 标签时，全部内容作为可见答案，行为与切分前完全一致。
 *
 * ## 使用约定
 * - 每个 chunk 只调用一次 [pushContent] / [pushReasoning]，按流顺序调用。
 * - **流结束时必须调用一次 [flush]**：它负责吐出 pending 里暂存的未决尾部
 *   （例如只有 `<` 或 `<thi` 的残片），否则最后几个字符会丢失。
 * - [answer] / [thinking] 是两条流的**累计全量快照**；每次 push 返回的
 *   [ThinkingSplitResult] 是**本次新增的增量**。
 *
 * ## 线程安全性
 * 非线程安全。调用方需保证顺序单线程调用（SSE 回调天然满足）。
 *
 * @see com.example.shuati.logic.AiExplainService.AiStreamEvent.Reasoning
 */
class ThinkingSplitter {

    private val answerBuf = StringBuilder()
    private val thinkBuf = StringBuilder()
    /** 未决尾部：可能是被截断的标签前缀，待下个 chunk 拼接后再判断。 */
    private val pending = StringBuilder()
    private var inThinkingBlock = false

    /** 累计的可见答案全量（已剥离思考内容）。最终落盘的就是它。 */
    val answer: String get() = answerBuf.toString()

    /** 累计的思考过程全量（已剥离可见答案）。仅用于展示，不落盘。 */
    val thinking: String get() = thinkBuf.toString()

    /** 当前是否处于一个尚未闭合的思考块内部。 */
    val inThinking: Boolean get() = inThinkingBlock

    /** 是否已经产出过思考内容（用于 UI 决定是否渲染思考区）。 */
    val hasThinking: Boolean get() = thinkBuf.isNotEmpty()

    /** 等待与下个 chunk 拼接的未决尾部，仅用于测试观测。 */
    val pendingText: String get() = pending.toString()

    /**
     * 灌入一段正文（content）增量，返回本次新增的答案增量与思考增量。
     *
     * 若增量里出现开标签 `<think>`，其后内容进入思考流；遇到闭标签 `</think>` 后回到答案流。
     * 可能构成标签前缀的尾部会被暂存，直到下一个 chunk 到来或 [flush] 被调用。
     */
    fun pushContent(chunk: String): ThinkingSplitResult {
        if (chunk.isEmpty()) return ThinkingSplitResult.Empty

        val buf = if (pending.isEmpty()) chunk else pending.toString() + chunk
        pending.setLength(0)

        val answerDelta = StringBuilder()
        val thinkingDelta = StringBuilder()
        var rest = buf

        while (rest.isNotEmpty()) {
            if (inThinkingBlock) {
                val close = CLOSE_TAG.find(rest)
                if (close != null) {
                    if (close.range.first > 0) thinkingDelta.append(rest, 0, close.range.first)
                    inThinkingBlock = false
                    rest = rest.substring(close.range.last + 1)
                } else {
                    val hold = pendingLengthOf(rest, forClose = true)
                    val emit = rest.length - hold
                    if (emit > 0) thinkingDelta.append(rest, 0, emit)
                    if (hold > 0) pending.append(rest, emit, rest.length)
                    rest = ""
                }
            } else {
                val open = OPEN_TAG.find(rest)
                if (open != null) {
                    if (open.range.first > 0) answerDelta.append(rest, 0, open.range.first)
                    inThinkingBlock = true
                    rest = rest.substring(open.range.last + 1)
                } else {
                    val hold = pendingLengthOf(rest, forClose = false)
                    val emit = rest.length - hold
                    if (emit > 0) answerDelta.append(rest, 0, emit)
                    if (hold > 0) pending.append(rest, emit, rest.length)
                    rest = ""
                }
            }
        }

        if (answerDelta.isNotEmpty()) answerBuf.append(answerDelta)
        if (thinkingDelta.isNotEmpty()) thinkBuf.append(thinkingDelta)
        return ThinkingSplitResult(answerDelta.toString(), thinkingDelta.toString())
    }

    /**
     * 灌入一段独立思考字段的增量（`reasoning_content` / `reasoning`）。
     * 这类内容天然就是思考过程，不做任何标签解析，直接进思考流。
     */
    fun pushReasoning(chunk: String): ThinkingSplitResult {
        if (chunk.isEmpty()) return ThinkingSplitResult.Empty
        thinkBuf.append(chunk)
        return ThinkingSplitResult(answerDelta = "", thinkingDelta = chunk)
    }

    /**
     * 流结束时调用：吐出 pending 里暂存的未决尾部。
     *
     * 若此刻仍处于思考态（未闭合），尾部归入思考流；否则归入答案流。
     * 已经吐出的内容不会被回退。
     *
     * @return 本次补吐的增量；没有未决内容时返回 [ThinkingSplitResult.Empty]。
     */
    fun flush(): ThinkingSplitResult {
        if (pending.isEmpty()) return ThinkingSplitResult.Empty
        val tail = pending.toString()
        pending.setLength(0)
        return if (inThinkingBlock) {
            thinkBuf.append(tail)
            ThinkingSplitResult(answerDelta = "", thinkingDelta = tail)
        } else {
            answerBuf.append(tail)
            ThinkingSplitResult(answerDelta = tail, thinkingDelta = "")
        }
    }

    /** 清空全部状态，复用本实例开始新一轮生成。 */
    fun reset() {
        answerBuf.setLength(0)
        thinkBuf.setLength(0)
        pending.setLength(0)
        inThinkingBlock = false
    }

    /**
     * 计算 [buf] 末尾需要暂存的长度：即「最长的、仍有可能长成完整标签的后缀」的长度。
     *
     * 例：`abc<thi` → 4（`<thi` 可能是 `<think>` 的前缀）；`abc` → 0；`a < b` → 3。
     * 扫描长度上限 [MAX_TAG_SCAN]，避免正文里出现孤立 `<` 时退化成 O(n²)。
     */
    private fun pendingLengthOf(buf: String, forClose: Boolean): Int {
        val re = if (forClose) CLOSE_PARTIAL else OPEN_PARTIAL
        val lt = buf.lastIndexOf('<')
        if (lt < 0) return 0
        val tail = buf.substring(lt)
        val gt = tail.indexOf('>')
        val limit = if (gt >= 0) minOf(gt + 1, MAX_TAG_SCAN, tail.length)
        else minOf(MAX_TAG_SCAN, tail.length)
        for (len in limit downTo 1) {
            if (re.matches(tail.substring(0, len))) return len
        }
        return 0
    }

    companion object {
        /** 标签前缀扫描上限：正常标签远短于此，仅用于兜底防退化。 */
        const val MAX_TAG_SCAN: Int = 64

        /** 完整的开标签：`<think>` / `<THINK>` / `<think >` / `< thinking >` / `<thinking>`。 */
        private val OPEN_TAG = Regex("<\\s*(?:think|thinking)\\s*>", RegexOption.IGNORE_CASE)

        /** 完整的闭标签：`</think>` / `</THINK>` / `</think >` / `</ thinking >` / `</thinking>`。 */
        private val CLOSE_TAG = Regex("<\\s*/\\s*(?:think|thinking)\\s*>", RegexOption.IGNORE_CASE)

        /** 「可能是开标签的前缀」：`<` / `<t` / `<thi` / `<think ` / `<think>` … */
        private val OPEN_PARTIAL = Regex(
            "^<\\s*(?:t|th|thi|thin|think|thinki|thinkin|thinking)?\\s*>?$",
            RegexOption.IGNORE_CASE
        )

        /**
         * 「可能是闭标签的前缀」：`</` / `</t` / `</thi` / `</think ` / `</think>` …
         *
         * 注意 `/` 必须是**可选**的：逐字符到达时，处于思考态的流先收到一个光秃秃的 `<`，
         * 若此时不把它当作闭标签的前缀暂存，它就会被当思考正文吐出去，
         * 后面的 `/think>` 也随之全部变成正文，闭标签将永远等不到。
         */
        private val CLOSE_PARTIAL = Regex(
            "^<\\s*/?\\s*(?:t|th|thi|thin|think|thinki|thinkin|thinking)?\\s*>?$",
            RegexOption.IGNORE_CASE
        )
    }
}

/**
 * 一次 push / flush 的切分结果。
 *
 * @property answerDelta 本次新增的**可见答案**增量。
 * @property thinkingDelta 本次新增的**思考过程**增量。
 */
data class ThinkingSplitResult(
    val answerDelta: String = "",
    val thinkingDelta: String = ""
) {
    /** 本次没有任何产出，可用于跳过无意义的上报。 */
    val isEmpty: Boolean get() = answerDelta.isEmpty() && thinkingDelta.isEmpty()

    companion object {
        /** 空结果单例。 */
        val Empty: ThinkingSplitResult = ThinkingSplitResult()
    }
}
