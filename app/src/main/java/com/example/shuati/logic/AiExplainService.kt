package com.example.shuati.logic

import com.example.shuati.data.AiConfigStore
import com.example.shuati.model.AiConfig
import com.example.shuati.model.AiExplanation
import com.example.shuati.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 *  - 生成过程以流式事件（Thinking / Generating / Delta / Done / Error）上报，便于 UI 展示思考与逐字进度。
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
    private val SYSTEM_PROMPT = """
        你是一个专业的考试题库解析助教，擅长把任何题型的题目拆解清楚。请严格按下面的「解析规范」输出，确保简洁、精准、可复习。

        【角色】你是学科导师，目标不是「给答案」，而是让用户真正理解考点并避免再错。
        【原则】
        1. 简洁：每部分 1–3 句，不啰嗦，不重复题干。
        2. 精准：只讲与本题直接相关的知识与推导，不发散、不凑字数。
        3. 中文输出，使用 Markdown 小标题。

        【输出格式】请严格使用以下四段（某段确实不适用可省略，但不要新增无关段落）：
        ## 正确答案
        一句话给出正确选项/答案。
        ## 解析
        说明为什么正确，给出关键推导或依据（ concise）。
        ## 关键知识点
        列出 1–3 个本题涉及的核心考点。
        ## 易错点
        指出考生最容易理解错或混淆的地方（若有）。

        【附】若提供了联网搜索资料，以其为权威依据；若提供了用户补充提示（如考试类别），请贴合该类别的考法。
    """.trimIndent()

    /** 流式生成过程中的事件。 */
    sealed interface AiStreamEvent {
        /** 思考/准备阶段（搜索、组装 prompt 等），msg 为用户可见的进度文案。 */
        data class Thinking(val msg: String) : AiStreamEvent
        /** 思考结束，开始生成逐字内容。 */
        object Generating : AiStreamEvent
        /** 流式增量文本（逐字/逐词）。 */
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
        onEvent(AiStreamEvent.Thinking("正在分析题目并准备资料…"))

        val searchContext = if (AiConfigStore.searchEnabled(cfg)) {
            runCatching { doSearch(cfg, q.question) }.getOrElse { null }
        } else null

        onEvent(
            AiStreamEvent.Thinking(
                if (searchContext != null) "已获取联网资料，正在生成解析…"
                else "正在生成解析…"
            )
        )

        val prompt = buildPrompt(q, hint, searchContext?.text)
        onEvent(AiStreamEvent.Generating)

        val full = StringBuilder()
        try {
            streamChat(cfg, prompt) { delta ->
                full.append(delta)
                onEvent(AiStreamEvent.Delta(delta))
            }
        } catch (e: Exception) {
            onEvent(AiStreamEvent.Error(e.message ?: "生成失败"))
            throw e
        }

        val text = full.toString().trim()
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
    private fun streamChat(cfg: AiConfig, prompt: String, onDelta: (String) -> Unit) {
        val url = cfg.llmBaseUrl.trim().removeSuffix("/") + "/chat/completions"
        val bodyStr = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = cfg.llmModel,
                messages = listOf(
                    ChatMessage("system", SYSTEM_PROMPT),
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

            val full = StringBuilder()
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
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) {
                        full.append(delta)
                        onDelta(delta)
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

    @Serializable
    private data class StreamDelta(val content: String? = null)

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
        if (!searchText.isNullOrBlank()) sb.appendLine("\n【联网搜索到的相关资料】\n$searchText")
        sb.appendLine("\n请严格按「正确答案 / 解析 / 关键知识点 / 易错点」四段输出，保持简洁精准。")
        return sb.toString()
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host ?: url }.getOrDefault(url)

    private fun promptHash(q: Question, hint: String): String =
        ((q.question + "|" + (q.answer ?: "") + "|" + hint).hashCode()).toString()
}
