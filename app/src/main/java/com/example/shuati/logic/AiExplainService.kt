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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
 *  - 生成过程以流式事件（Thinking / Generating / Delta / Done / Error）上报，
 *    便于 UI 展示准备进度与逐字内容。思考/推理过程不展示给用户，模型输出经净化后直接呈现最终解析。
 */
object AiExplainService {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    // 重新生成时复用的联网检索缓存（按「题干|搜索引擎」维度）；限容避免长会话无限增长。
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, SearchResult>()

    // 显式前缀缓存（prompt caching）仅对需要 cache_control 标记的 OpenAI 兼容服务开启；
    // 其余服务（含 DeepSeek 等服务端自动前缀缓存）保持原字符串 content，零回归风险。
    internal fun providerSupportsPromptCache(host: String): Boolean =
        host.contains("openai.com") || host.contains("openrouter.ai") ||
        host.contains("groq.com") || host.contains("together.ai") || host.contains("moonshot")

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
        先说明正确选项为何正确（关键推导或依据）；随后**逐选项**分析：对每一个错误选项，指出其具体的错误原因或混淆点（如概念偷换、张冠李戴、范围不符、计算错误、与题意相悖等），确保覆盖本题全部选项，帮助用户理解各干扰项的问题所在。
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
         * 约定：[Delta] 传「截至当前的完整文本快照」，
         * UI 侧直接整体替换显示即可，无需 append。服务端按快照推送，整体替换可避免重复。
         * 快照内容已经过净化（剥离思考块、控制字符等），可直接落盘。
         */
        data class Delta(val text: String) : AiStreamEvent
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
        val max_tokens: Int = 900,
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

    // ── Bocha 博查搜索（国内直连，中文优化，免费额度 + 低单价） ──────────
    @Serializable
    private data class BochaRequest(
        val query: String,
        val freshness: String = "noLimit",
        val summary: Boolean = true,
        val count: Int = 3
    )

    @Serializable
    private data class BochaResponse(val data: List<BochaResult> = emptyList())

    @Serializable
    internal data class BochaResult(val name: String = "", val url: String = "", val snippet: String = "")

    private interface BochaApi {
        @POST("v1/web-search")
        suspend fun search(@Header("Authorization") auth: String, @Body req: BochaRequest): BochaResponse
    }

    internal data class SearchResult(val text: String, val citations: List<String>)

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
        prevAnswer: String? = null,
        onEvent: (AiStreamEvent) -> Unit
    ): AiExplanation = withContext(Dispatchers.IO) {
        require(cfg.llmBaseUrl.isNotBlank() && cfg.llmApiKey.isNotBlank() && cfg.llmModel.isNotBlank()) {
            "请先在「设置 → AI 大模型配置」中填写大模型地址、Key 与模型"
        }
        // 进度文案只由真实事件驱动，不插入任何人造 delay（原 350ms + 300ms 仅为看清文案，并非服务端需要）。
        //  - 启用搜索时先上报「正在检索相关资料…」，对应真实的网络等待；
        //  - 随后上报「正在生成解析…」，对应真实的等待首 token（TTFT）。
        // 已删除「正在分析题目…」「已获取联网资料，正在组织解析…」「正在组织解析框架…」三条毫秒级本地拼接文案。
        // 重新生成（prevAnswer 非空）时复用首次检索到的联网资料，跳过二次搜索：省 token、降延迟、避免重复检索。
        val isRegen = !prevAnswer.isNullOrBlank()
        val searchKey = q.question + "|" + cfg.searchProvider
        val searchContext = if (isRegen && searchCache[searchKey] != null) {
            searchCache[searchKey]
        } else if (AiConfigStore.searchEnabled(cfg)) {
            onEvent(AiStreamEvent.Thinking("正在检索相关资料…"))
            // 搜索加 5s 超时：引擎挂起时不让用户无限等待（否则最多 180s 且期间无法重试）。
            // 超时即放弃搜索、按「无联网资料」继续走生成流程，与搜索失败行为一致；withTimeoutOrNull 不会抛异常。
            withTimeoutOrNull(5000) { runCatching { doSearch(cfg, q.question) }.getOrNull() }
                ?.also {
                    searchCache[searchKey] = it
                    if (searchCache.size > 200) searchCache.clear()
                }
        } else null

        // 重新生成时把上一次解析压缩为要点注入，作为校正参考（不照抄）；首生成不注入。
        val prevSummary = prevAnswer?.take(700)
        val prompt = buildPrompt(q, hint, searchContext?.text, prevSummary)
        onEvent(AiStreamEvent.Thinking("正在生成解析…"))
        onEvent(AiStreamEvent.Generating)

        // 累计可见答案：SSE 的 delta.content 是「增量分块」（每次只带新增的几个字符），
        // 这里必须 += 累加，不能用 = 覆盖——否则每次 Delta 只含最新一小块，
        // 流式期间文本框只会闪烁末块、流结束后 answer 仅剩最后一块（句末标点），解析内容全丢。
        // 思考/推理内容不展示、不落盘，最终由 sanitizeAiText 净化后直接呈现。
        // 每个 Delta 上报「截至当前的完整文本快照」（整体替换而非 append，由上游保证不重复），
        // 这样 UI 侧直接整体替换显示即可，天然呈现逐字累积的打字机效果。
        var answer = ""
        try {
            streamChat(cfg, prompt) { content ->
                if (content.isNotEmpty()) {
                    answer += content
                    val clean = sanitizeAiText(answer)
                    if (clean.isNotEmpty()) onEvent(AiStreamEvent.Delta(clean))
                }
            }
        } catch (e: Exception) {
            onEvent(AiStreamEvent.Error(e.message ?: "生成失败"))
            throw e
        }

        // 落盘内容只含净化后的可见答案，思考过程不进 AiExplanation
        val text = sanitizeAiText(answer).trim()
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
     * @param onDelta 正文 content 增量回调（可能为空串，调用方需自行忽略）。
     *   思考/推理内容不展示给用户，故此处只回传可见正文。
     */
    private fun streamChat(
        cfg: AiConfig,
        prompt: String,
        onDelta: (content: String) -> Unit
    ) {
        val url = cfg.llmBaseUrl.trim().removeSuffix("/") + "/chat/completions"
        val sysPrompt = cfg.customPrompt.ifBlank { SYSTEM_PROMPT }
        // 手写请求体，便于按 provider 条件注入 prompt caching 标记；其余服务保持原 content 字符串形态。
        val reqJson = buildJsonObject {
            put("model", cfg.llmModel)
            putJsonArray("messages") {
                if (providerSupportsPromptCache(hostOf(cfg.llmBaseUrl))) {
                    // OpenAI 兼容前缀缓存：把系统提示标记为可缓存前缀，省去每题重复输入（约 40% 输入 token）。
                    add(buildJsonObject {
                        put("role", "system")
                        putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", sysPrompt)
                                putJsonObject("cache_control") { put("type", "ephemeral") }
                            })
                        }
                    })
                } else {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", sysPrompt)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("temperature", 0.3)
            put("max_tokens", 900)
            put("stream", true)
        }
        val bodyStr = reqJson.toString()
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
                    // 只回传可见正文；思考/推理字段（reasoning_content / reasoning）不展示，忽略。
                    val content = parseSseContent(line)
                    if (!content.isNullOrEmpty()) onDelta(content)
                } else if (!sawData) {
                    // 非 SSE：可能是单条 JSON 响应（部分兼容端点不支持 stream）
                    raw.appendLine(line)
                }
            }
            if (!sawData && raw.isNotBlank()) {
                val single = runCatching { json.decodeFromString(ChatResponse.serializer(), raw.toString()) }
                    .getOrNull()
                val text = single?.choices?.firstOrNull()?.message?.content?.trim()
                if (!text.isNullOrEmpty()) onDelta(text)
            }
        } finally {
            resp.close()
        }
    }

    @Serializable
    private data class StreamChunk(val choices: List<StreamChoice> = emptyList())

    @Serializable
    private data class StreamChoice(val delta: StreamDelta = StreamDelta())

    /** SSE 增量字段：只取可见正文 `content`；思考/推理字段不展示，由服务端忽略。 */
    @Serializable
    private data class StreamDelta(
        val content: String? = null
    )

    /** 博查检索结果格式化：取前 3 条，摘要截断 500 字，提取非空 URL 作为引用。供 [doSearch] 与单测复用。 */
    internal fun bochaResultsToSearch(items: List<BochaResult>): SearchResult {
        val top = items.take(3)
        return SearchResult(
            text = top.joinToString("\n\n") { "【${it.name}】\n${it.snippet.take(500)}" },
            citations = top.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
        )
    }

    private suspend fun doSearch(cfg: AiConfig, query: String): SearchResult = withContext(Dispatchers.IO) {
        when (cfg.searchProvider.lowercase()) {
            "tavily" -> {
                val resp = retrofit("https://api.tavily.com/").create(TavilyApi::class.java)
                    .search(TavilyRequest(api_key = cfg.searchApiKey, query = query, max_results = 3))
                val items = resp.results.take(3)
                SearchResult(
                    text = items.joinToString("\n\n") { "【${it.title}】\n${it.content.take(500)}" },
                    citations = items.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
                )
            }
            "exa" -> {
                val resp = retrofit("https://api.exa.ai/").create(ExaApi::class.java)
                    .search(cfg.searchApiKey, ExaRequest(query = query, numResults = 3))
                val items = resp.results.take(3)
                SearchResult(
                    text = items.joinToString("\n\n") { "【${it.title}】\n${it.text.take(500)}" },
                    citations = items.mapNotNull { it.url.takeIf { u -> u.isNotBlank() } }
                )
            }
            "bocha" -> {
                val resp = retrofit("https://api.bochaai.com/").create(BochaApi::class.java)
                    .search("Bearer ${cfg.searchApiKey}", BochaRequest(query = query))
                bochaResultsToSearch(resp.data)
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
     * 启发式判断某个模型 id 是否**可能**是推理（思考型）模型。
     *
     * 覆盖 `o1` / `o3` / `o4` / `gpt-5` / `deepseek-r1` / `deepseek-reasoner` / `qwq` /
     * `glm-z1` / `kimi-thinking` / `*-thinking` / `*reasoner` / `r1` 等常见命名，
     * 并兼容 `-` `_` `:` `.` `/` 空格等分隔符变体与大小写差异。
     *
     * 当前 UI 不再展示思考过程，本函数保留供日志/后续扩展使用，不影响解析结果。
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

    internal fun buildPrompt(q: Question, hint: String, searchText: String?, prevAnswer: String? = null): String {
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
        // 重新生成时注入上一次解析要点，供模型校正/补漏（明确要求不照抄、冲突以标准答案为准）。
        if (!prevAnswer.isNullOrBlank()) sb.appendLine("\n【上一次解析（仅供参考与校正，请勿照抄；若与标准答案冲突，以标准答案为准）】\n$prevAnswer")
        sb.appendLine("\n请严格按「正确答案 / 解析 / 关键知识点 / 易错点」四段输出，并在「解析」中逐选项分析每个错误选项的具体错误原因、确保覆盖全部选项；保持简洁精准；不要输出 <think> 标记、代码围栏或 JSON。")
        return sb.toString()
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host ?: url }.getOrDefault(url)

    /**
     * 解析单行 SSE：`data:` 前缀 + JSON 增量。返回可见正文 content（可能为空串），
     * 非 data 行 / `[DONE]` / 空行 / 解析失败均返回 null。供 [streamChat] 与单测复用。
     */
    internal fun parseSseContent(line: String): String? {
        if (line.isBlank()) return null
        if (!line.startsWith("data:")) return null
        val data = line.substringAfter("data:").trim()
        if (data == "[DONE]") return null
        val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), data) }.getOrNull() ?: return null
        val delta = chunk.choices.firstOrNull()?.delta ?: return null
        return delta.content.orEmpty()
    }

    /**
     * 测试"联网搜索配置"是否可用（独立模块测试用）。不消耗大模型配额，仅跑一次真实搜索。
     */
    suspend fun testSearchConnection(cfg: AiConfig): String = withContext(Dispatchers.IO) {
        if (!AiConfigStore.searchEnabled(cfg)) return@withContext "未配置搜索引擎或 Key"
        runCatching { doSearch(cfg, "今天有哪些值得关注的科技新闻") }
            .fold(
                onSuccess = { "联网搜索正常，返回 ${it.citations.size} 条来源" },
                onFailure = { "联网搜索失败：${it.message ?: it}" }
            )
    }

    /**
     * 判定某模型是否内置联网搜索能力（供 UI 在模型名后展示联网标识）。
     * 采用已知模型 id 子串 allowlist（大小写不敏感），覆盖主流自带联网的国内/国际模型。
     * 注意：allowlist 可能随厂商更新而滞后，UI 侧仍应以"应用层已配置搜索"为主标识、
     * 内置联网作辅助提示（不阻断任何模型的使用）。
     */
    fun modelHasBuiltinSearch(modelId: String): Boolean {
        val m = modelId.lowercase()
        if (m.isBlank()) return false
        return BUILTIN_SEARCH_HINTS.any { it in m }
    }

    private val BUILTIN_SEARCH_HINTS = listOf(
        "perplexity", "sonar",           // Perplexity 系列（原生联网）
        "deepseek",                      // DeepSeek 默认开启联网
        "qwen", "tongyi", "qwen3",       // 通义千问（enable_search）
        "glm",                           // 智谱 GLM（web_search 工具）
        "doubao", "skylark",             // 豆包 / 火山方舟（内置联网）
        "ernie", "wenxin",               // 文心一言（内置联网）
        "kimi", "moonshot",              // Kimi（内置联网）
        "abab", "tiangong",              // MiniMax / 天工（内置联网）
        "hunyuan",                       // 腾讯混元（内置联网）
        "spark",                         // 讯飞星火（内置联网）
        "chatgpt", "gpt-4o", "gpt-4.1"   // OpenAI 带 web search 的模型
    )

    private fun promptHash(q: Question, hint: String): String =
        ((q.question + "|" + (q.answer ?: "") + "|" + hint).hashCode()).toString()
}
