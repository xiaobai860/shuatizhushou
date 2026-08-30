package com.example.shuati.logic

import com.example.shuati.data.AiConfigStore
import com.example.shuati.model.AiConfig
import com.example.shuati.model.AiExplanation
import com.example.shuati.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * AI 解析服务：编排「联网搜索 → 组装 prompt → 调大模型 → 流式生成 AiExplanation」。
 *
 * 设计要点：
 *  - 大模型走 OpenAI 兼容协议（chat/completions + /models），baseUrl/key/model 由用户在设置页配置。
 *  - 内置一份「题目解析 skill」（SYSTEM_PROMPT）：统一输出格式（正确答案/解析/关键知识点/易错点），
 *    约束大模型输出简洁、精准、可复习。该 skill 仅作为系统提示注入，无需任何服务端，纯客户端即可生效。
 *  - 搜索引擎可选（Tavily / Exa）：App 先搜、再把结果作为上下文喂给大模型；未配置则依赖模型内置联网。
 *  - 解析默认由调用方写入 Question.aiExplanation 落盘（见 QuizScreen / AppRepository）。
 *  - 生成过程以流式事件（Thinking / Generating / Delta / Reasoning / Done / Error）上报，
 *    便于 UI 展示准备进度、思考过程与逐字内容。思考过程仅用于展示，不落盘。
 *  - 思考内容与可见答案由 [ThinkingSplitter] 分离；模型是否为推理模型由 [isLikelyReasoningModel] 启发式判断
 *    （仅影响进度文案，实际展示与否取决于流里是否真有 reasoning 内容）。
 */
object AiExplainService {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.trim().removeSuffix("/") + "/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /**
     * 内置「题目解析 skill」：作为系统提示注入，约束大模型按统一、简洁、精准的规范输出。
     * 这是让结果更精准的核心——不依赖服务端，纯客户端 prompt 工程即可生效。
     */
    val SYSTEM_PROMPT = """
        你是一个严谨的考试题库解析助教。请严格按下列「解析规范」输出，确保简洁、精准、可靠、可复习。

        【角色】你是学科导师，目标是让用户真正理解考点并避免再错，而不是单纯给答案。
        【可靠性约束（必须遵守）】
        1. 以用户提供的「标准答案」为准来组织解析；除非该答案明显有误，否则不要否认它。若你认为标准答案有误，先按标准答案给出解析，再用一句「注：…」温和提示存疑点。
        2. 严禁编造任何来源、文献、书籍或网页链接。仅当用户提供了「联网搜索到的相关资料」时，才可引用其中的真实 URL，并明确标注；没有资料时不要写「来源」。
        3. 不要输出推理过程标记（如 <think> 之类）、代码围栏（```）、JSON 或多余的符号。直接输出最终解析文本。
        4. 只输出与本题相关的内容，不发散、不凑字数、不重复题干。
        【原则】简洁：每部分 1–3 句；精准：只讲直接相关的知识与推导；中文输出。
        【输出格式】严格使用以下四段（某段确实不适用可省略，但不要新增无关段落）：
        ## 正确答案
        一句话给出正确选项/答案。
        ## 解析
        说明为什么正确，给出关键推导或依据。
        ## 关键知识点
        列出 1–3 个本题涉及的核心考点。
        ## 易错点
        指出考生最容易理解错或混淆的地方（若有）。
        【附】若提供了「联网搜索到的相关资料」，以其为权威依据；若提供了用户补充提示（如考试类别），请贴合该类别的考法。
    """.trimIndent()

    /** 流式生成过程中的事件。 */
    sealed interface AiStreamEvent {
        /** 思考/准备阶段（搜索、组装 prompt 等），msg 为用户可见的进度文案。 */
        data class Thinking(val msg: String) : AiStreamEvent
        /** 思考结束，开始生成逐字内容。 */
        object Generating : AiStreamEvent
        /**
         * 可见答案的**全量快照**（注意不是增量）。
         *
         * 约定：[Delta] 与 [Reasoning] 一律传「截至当前的完整文本快照」，
         * UI 侧直接整体替换显示即可，无需 append。这样两个流各自独立、互不干扰。
         * 快照内容已经过净化（剥离思考块、控制字符等），可直接落盘。
         */
        data class Delta(val text: String) : AiStreamEvent
        /**
         * 模型思考过程的**全量快照**（不是增量，语义同 [Delta]）。
         *
         * 只有真的收到了思考内容才会发出（来自 `reasoning_content` / `reasoning` 字段，
         * 或正文里的 `<think>` 块），因此 UI 可以「收到即展示」而不必预判模型类型。
         * 思考过程**不落盘**，生成结束后即消失——它只是展示层。
         */
        data class Reasoning(val text: String) : AiStreamEvent
        /** 完成，携带最终 AiExplanation。 */
        data class Done(val ai: AiExplanation) : AiStreamEvent
        /** 出错。 */
        data class Error(val msg: String) : AiStreamEvent
    }

    // ── OpenAI 兼容接口 ────────────────────────────────────────────────
    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.3,
        val stream: Boolean = false
    )

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: ChatMessage? = null)

    @Serializable
    private data class ModelsResponse(val data: List<ModelEntry> = emptyList())

    @Serializable
    private data class ModelEntry(val id: String = "")

    private interface OpenAiApi {
        @POST("chat/completions")
        suspend fun chat(@Header("Authorization") auth: String, @Body req: ChatRequest): ChatResponse

        @GET("models")
        suspend fun models(@Header("Authorization") auth: String): ModelsResponse
    }

    // ── Tavily 搜索 ───────────────────────────────────────────────────
    @Serializable
    private data class TavilyRequest(
        val api_key: String,
        val query: String,
        val max_results: Int = 5,
        val search_depth: String = "advanced"
    )

    @Serializable
    private data class TavilyResponse(val results: List<TavilyResult> = emptyList())

    @Serializable
    private data class TavilyResult(val title: String = "", val url: String = "", val content: String = "")

    private interface TavilyApi {
        @POST("search")
        suspend fun search(@Body req: TavilyRequest): TavilyResponse
    }

    // ── Exa 搜索 ─────────────────────────────────────────────────────
    @Serializable
    private data class ExaRequest(val query: String, val numResults: Int = 5, val type: String = "neural")

    @Serializable
    private data class ExaResponse(val results: List<ExaResult> = emptyList())

    @Serializable
    private data class ExaResult(val title: String = "", val url: String = "", val text: String = "")

    private interface ExaApi {
        @POST("search")
        suspend fun search(@Header("x-api-key") key: String, @Body req: ExaRequest): ExaResponse
    }

    private data class SearchResult(val text: String, val citations: List<String>)

    /**
     * 生成 AI 解析（一次性，兼容旧调用）。内部走流式实现，仅忽略事件。
     */
    suspend fun explain(cfg: AiConfig, q: Question, hint: String): AiExplanation =
        withContext(Dispatchers.IO) {
            var result: AiExplanation? = null
            var errMsg: String? = null
            explainStream(cfg, q, hint) { ev ->
                when (ev) {
                    is AiStreamEvent.Done -> result = ev.ai
                    is AiStreamEvent.Error -> errMsg = ev.msg
                    else -> {}
                }
            }
            if (errMsg != null) throw IllegalStateException(errMsg)
            result ?: throw IllegalStateException("模型未返回解析内容，请检查模型/Key 或重试")
        }

    /**
     * 流式生成 AI 解析（主入口）。通过 onEvent 上报思考进度与逐字内容。
     * cfg 由调用方从 AiConfigStore.load 取得后传入。
     */
    suspend fun explainStream(
        cfg: AiConfig,
        q: Question,
        hint: String,
        onEvent: (AiStreamEvent) -> Unit
    ): AiExplanation = withContext(Dispatchers.IO) {
        require(cfg.llmBaseUrl.isNotBlank() && cfg.llmApiKey.isNotBlank() && cfg.llmModel.isNotBlank()) {
            "请先在「设置 → AI 大模型配置」中填写大模型地址、Key 与模型"
        }
        // 进度文案只由真实事件驱动，不插入任何人造 delay（原 350ms + 300ms 仅为看清文案，并非服务端需要）。
        //  - 启用搜索时先上报「正在检索相关资料…」，对应真实的网络等待；
        //  - 随后上报「正在生成解析…」，对应真实的等待首 token（TTFT）。
        // 已删除「正在分析题目…」「已获取联网资料，正在组织解析…」「正在组织解析框架…」三条毫秒级本地拼接文案。
        val searchContext = if (AiConfigStore.searchEnabled(cfg)) {
            onEvent(AiStreamEvent.Thinking("正在检索相关资料…"))
            // 搜索加 5s 超时：引擎挂起时不让用户无限等待（否则最多 180s 且期间无法重试）。
            // 超时即放弃搜索、按「无联网资料」继续走生成流程，与搜索失败行为一致；withTimeoutOrNull 不会抛异常。
            withTimeoutOrNull(5000) { runCatching { doSearch(cfg, q.question) }.getOrNull() }
        } else null

        val prompt = buildPrompt(q, hint, searchContext?.text)
        // 仅用于进度文案：推理模型的思考阶段可能较长，提示语略有区别。
        // 注意：是否真的展示思考过程，一律以流里是否收到 reasoning 内容为准（真实信号优先）。
        val likelyReasoningModel = isLikelyReasoningModel(cfg.llmModel)
        onEvent(
            AiStreamEvent.Thinking(
                if (likelyReasoningModel) "正在生成解析…（推理模型思考较久，请稍候）"
                else "正在生成解析…（大模型思考中）"
            )
        )
        onEvent(AiStreamEvent.Generating)

        // 思考 / 答案两路分离：splitter.answer 只含可见答案（落盘），
        // splitter.thinking 只含思考过程（仅展示、不落盘）。
        val splitter = ThinkingSplitter()
        try {
            streamChat(cfg, prompt) { content, reasoning ->
                // 1) 独立思考字段（DeepSeek-R1 / 硅基流动 / QwQ 等）
                if (reasoning.isNotEmpty()) {
                    splitter.pushReasoning(reasoning)
                    val think = sanitizeThinkingText(splitter.thinking)
                    if (think.isNotEmpty()) onEvent(AiStreamEvent.Reasoning(think))
                }
                // 2) 正文（可能内嵌 <think>…</think> 块）
                if (content.isNotEmpty()) {
                    val split = splitter.pushContent(content)
                    if (split.thinkingDelta.isNotEmpty()) {
                        val think = sanitizeThinkingText(splitter.thinking)
                        if (think.isNotEmpty()) onEvent(AiStreamEvent.Reasoning(think))
                    }
                    if (split.answerDelta.isNotEmpty()) {
                        val clean = sanitizeAiText(splitter.answer)
                        if (clean.isNotEmpty()) onEvent(AiStreamEvent.Delta(clean))
                    }
                }
            }
            // 流结束：补吐未决尾部（如被截断的标签前缀）；未闭合的思考块保持有效，不回退
            val tail = splitter.flush()
            if (tail.thinkingDelta.isNotEmpty()) {
                val think = sanitizeThinkingText(splitter.thinking)
                if (think.isNotEmpty()) onEvent(AiStreamEvent.Reasoning(think))
            }
            if (tail.answerDelta.isNotEmpty()) {
                val clean = sanitizeAiText(splitter.answer)
                if (clean.isNotEmpty()) onEvent(AiStreamEvent.Delta(clean))
            }
        } catch (e: Exception) {
            onEvent(AiStreamEvent.Error(e.message ?: "生成失败"))
            throw e
        }

        // 落盘内容只含净化后的可见答案，思考过程不进 AiExplanation
        val text = sanitizeAiText(splitter.answer).trim()
        if (text.isBlank()) throw IllegalStateException("模型未返回解析内容，请检查模型/Key 或重试")

        val ai = AiExplanation(
            text = text,
            model = cfg.llmModel,
            provider = hostOf(cfg.llmBaseUrl),
            createdAt = System.currentTimeMillis(),
            hintUsed = hint.ifBlank { null },
            citations = searchContext?.citations ?: emptyList(),
            promptHash = promptHash(q, hint)
        )
        onEvent(AiStreamEvent.Done(ai))
        ai
    }

    /** 拉取可用模型列表（用户在设置页点「拉取模型」时调用）。 */
    suspend fun listModels(cfg: AiConfig): List<String> = withContext(Dispatchers.IO) {
        require(cfg.llmBaseUrl.isNotBlank() && cfg.llmApiKey.isNotBlank()) { "请先填写大模型地址与 Key" }
        val api = retrofit(cfg.llmBaseUrl).create(OpenAiApi::class.java)
        api.models("Bearer ${cfg.llmApiKey}").data.map { it.id }.filter { it.isNotBlank() }.sorted()
    }

    // ── 底层流式 HTTP（SSE，兼容非流式回退） ──────────────────────────
    /**
     * @param onDelta 参数1 = 正文 content 增量，参数2 = 思考内容增量（二者都可能为空串，互斥亦可并存）。
     */
    private fun streamChat(
        cfg: AiConfig,
        prompt: String,
        onDelta: (content: String, reasoning: String) -> Unit
    ) {
        val url = cfg.llmBaseUrl.trim().removeSuffix("/") + "/chat/completions"
        val bodyStr = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = cfg.llmModel,
                messages = listOf(
                    ChatMessage("system", cfg.customPrompt.ifBlank { SYSTEM_PROMPT }),
                    ChatMessage("user", prompt)
                ),
                temperature = 0.3,
                stream = true
            )
        )
        val body = bodyStr.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${cfg.llmApiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = okHttp.newCall(req)
        val resp = call.execute()
        try {
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(500) ?: "HTTP ${resp.code}"
                throw IllegalStateException("模型返回错误（${resp.code}）：$err")
            }
            val source = resp.body?.source()
                ?: throw IllegalStateException("模型未返回内容（空响应）")

            var sawData = false
            val raw = StringBuilder() // 用于非流式（单条 JSON）回退
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (line.startsWith("data:")) {
                    sawData = true
                    val data = line.substringAfter("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), data) }.getOrNull()
                        ?: continue
                    val delta = chunk.choices.firstOrNull()?.delta ?: continue
                    val content = delta.content.orEmpty()
                    // 不同兼容端点字段名不同：DeepSeek / 硅基流动 / 智谱等用 reasoning_content，
                    // 部分端点（含部分 OpenAI 兼容网关）用 reasoning；两者都读，缺字段时为 null。
                    val reasoning = delta.reasoning_content ?: delta.reasoning
                    if (content.isNotEmpty() || !reasoning.isNullOrEmpty()) {
                        onDelta(content, reasoning.orEmpty())
                    }
                } else if (!sawData) {
                    // 非 SSE：可能是单条 JSON 响应（部分兼容端点不支持 stream）
                    raw.appendLine(line)
                }
            }
            if (!sawData && raw.isNotBlank()) {
                val single = runCatching { json.decodeFromString(ChatResponse.serializer(), raw.toString()) }
                    .getOrNull()
                val text = single?.choices?.firstOrNull()?.message?.content?.trim()
                if (!text.isNullOrEmpty()) onDelta(text, "")
            }
        } finally {
            resp.close()
        }
    }

    @Serializable
    private data class StreamChunk(val choices: List<StreamChoice> = emptyList())

    @Serializable
    private data class StreamChoice(val delta: StreamDelta = StreamDelta())

    /**
     * SSE 增量字段。`reasoning_content` / `reasoning` 在普通模型上不存在，
     * json 已配 `ignoreUnknownKeys = true`，字段缺失安全。
     */
    @Serializable
    private data class StreamDelta(
        val content: String? = null,
        val reasoning_content: String? = null,
        val reasoning: String? = null
    )

    private suspend fun doSearch(cfg: AiConfig, query: String): SearchResult = withContext(Dispatchers.IO) {
        when (cfg.searchProvider.lowercase()) {
            "tavily" -> {
                val resp = retrofit("https://api.tavily.com/").create(TavilyApi::class.java)
                    .search(TavilyRequest(api_key = cfg.searchApiKey, query = query))
                val items = resp.results.take(5)
                SearchResult(
                    text = items.joinToString("\n\n") { "【${it.title}】\n${it.content}" },
                    citations = items.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
                )
            }
            "exa" -> {
                val resp = retrofit("https://api.exa.ai/").create(ExaApi::class.java)
                    .search(cfg.searchApiKey, ExaRequest(query = query))
                val items = resp.results.take(5)
                SearchResult(
                    text = items.joinToString("\n\n") { "【${it.title}】\n${it.text}" },
                    citations = items.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
                )
            }
            else -> throw IllegalStateException("不支持的搜索引擎：${cfg.searchProvider}")
        }
    }

    /**
     * 清洗模型输出：剥离推理块（<think>…</think>）、零宽空格、控制字符，
     * 合并多余空行，避免无意义字符与乱码进入解析结果。
     */
    private fun sanitizeAiText(raw: String): String {
        var s = raw
        // 去掉推理过程标记及其内容（可能跨多行、可能未闭合）
        s = s.replace(Regex("(?is)<think>.*?(</think>|$)"), "")
        // 去掉控制字符（保留 \n \t）
        s = s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), "")
        // 去掉零宽空格 / 软连字符 / BOM
        s = s.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF\\u00AD]"), "")
        // 合并 3 个及以上连续换行
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        // 行内多余空格折叠
        s = s.replace(Regex("[ \\t]{2,}"), " ")
        return s.trim()
    }

    /**
     * 清洗思考过程文本（**仅用于展示，不落盘**）。
     * 与 [sanitizeAiText] 的区别：不剥离 `<think>` 块（思考内容本身就是块里的东西），
     * 且只做 `trimStart`——流还在继续时尾部空格不应被吃掉，否则光标会贴字。
     */
    private fun sanitizeThinkingText(raw: String): String {
        var s = raw
        s = s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), "")
        s = s.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF\\u00AD]"), "")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        s = s.replace(Regex("[ \\t]{2,}"), " ")
        return s.trimStart()
    }

    /**
     * 启发式判断某个模型 id 是否**可能**是推理（思考型）模型。
     *
     * 覆盖 `o1` / `o3` / `o4` / `gpt-5` / `deepseek-r1` / `deepseek-reasoner` / `qwq` /
     * `glm-z1` / `kimi-thinking` / `*-thinking` / `*reasoner` / `r1` 等常见命名，
     * 并兼容 `-` `_` `:` `.` `/` 空格等分隔符变体与大小写差异。
     *
     * **重要**：本判定**只**用于进度文案与「思考中」占位提示。
     * 是否真的展示思考过程，一律以流里是否真的收到了 reasoning 内容为准（真实信号优先），
     * 这样即使误判（把普通模型认成推理模型、或漏判）也不会在界面上留下空白思考区。
     *
     * @param modelId 用户在设置页填写的模型 id，如 `deepseek-r1`、`Pro/deepseek-ai/DeepSeek-R1`
     * @return true 表示「有较高概率是推理模型」
     */
    fun isLikelyReasoningModel(modelId: String): Boolean {
        if (modelId.isBlank()) return false
        val normalized = modelId.lowercase()
            .replace(Regex("[_.:/\\s]+"), "-")
            .trim('-')
        if (normalized.isEmpty()) return false

        // 1) 按分隔符切段，逐段精确匹配（避免 "r1" 误命中 "llama-3-1-70b" 这类）
        val tokens = normalized.split('-')
        if (tokens.any { it in REASONING_MODEL_TOKENS }) return true

        // 2) 整串包含匹配（覆盖 gpt-5、deepseek-r1 这类多段词组）
        if (REASONING_MODEL_PHRASES.any { normalized.contains(it) }) return true

        // 3) 紧凑串兜底（兼容 deepseekr1 / DeepSeek_R1 之类无分隔符写法）
        val compact = normalized.replace("-", "")
        return REASONING_MODEL_PHRASES.any { compact.contains(it.replace("-", "")) }
    }

    /** [isLikelyReasoningModel] 的段级关键字（命中即判为推理模型）。 */
    private val REASONING_MODEL_TOKENS: Set<String> = setOf(
        // OpenAI o 系列 / gpt-oss 开放式推理模型
        "o1", "o3", "o4", "oss",
        // DeepSeek R1 系及同类命名
        "r1", "r2",
        // 智谱 GLM-Z1 / MiniMax-M1
        "z1", "m1",
        // 通义千问推理模型
        "qwq",
        // 显式带思考 / 推理后缀的命名
        "think", "thinking", "reasoner", "reasoning"
    )

    /** [isLikelyReasoningModel] 的词组级关键字（整串 / 紧凑串包含匹配）。 */
    private val REASONING_MODEL_PHRASES: List<String> = listOf(
        "gpt-5",
        "deepseek-r1",
        "deepseek-reasoner",
        "kimi-thinking",
        "qwen3-thinking",
        "glm-z1"
    )

    private fun buildPrompt(q: Question, hint: String, searchText: String?): String {
        val sb = StringBuilder()
        sb.appendLine("这是一道题目，请按解析规范解释为什么给出的标准答案是正确的。")
        if (hint.isNotBlank()) sb.appendLine("\n【用户补充提示】$hint")
        sb.appendLine("\n【题干】${q.question}")
        q.options?.takeIf { it.isNotEmpty() }?.let { opts ->
            sb.appendLine("【选项】")
            opts.forEachIndexed { i, o -> sb.appendLine("${'A' + i}. $o") }
        }
        sb.appendLine("\n【标准答案】${q.answer ?: "（无）"}")
        if (!q.explanation.isNullOrBlank()) sb.appendLine("\n【题库自带解析】${q.explanation}")
        if (!searchText.isNullOrBlank()) sb.appendLine("\n【联网搜索到的相关资料】（以下为权威依据，若引用其中的链接须保留真实 URL，禁止编造）\n$searchText")
        sb.appendLine("\n请严格按「正确答案 / 解析 / 关键知识点 / 易错点」四段输出，保持简洁精准；不要输出 <think> 标记、代码围栏或 JSON。")
        return sb.toString()
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host ?: url }.getOrDefault(url)

    private fun promptHash(q: Question, hint: String): String =
        ((q.question + "|" + (q.answer ?: "") + "|" + hint).hashCode()).toString()
}
