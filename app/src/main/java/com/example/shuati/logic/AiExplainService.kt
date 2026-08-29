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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * AI 解析服务：编排「联网搜索 → 组装 prompt → 调大模型 → 生成 AiExplanation」。
 *
 * 设计要点：
 *  - 大模型走 OpenAI 兼容协议（chat/completions + /models），baseUrl/key/model 由用户在设置页配置。
 *  - 搜索引擎可选（Tavily / Exa），App 先搜、再把结果作为上下文喂给大模型；
 *    若未配置搜索引擎，则直接调大模型（依赖该模型是否自带联网能力）。
 *  - 生成的解析默认由调用方写入 Question.aiExplanation 落盘（见 QuizScreen / AppRepository）。
 */
object AiExplainService {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.trim().removeSuffix("/") + "/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val SYSTEM_PROMPT =
        "你是专业的考试题库解析助手。用户会给你一道题、标准答案以及（可选的）联网搜索资料，" +
        "你需要解释为什么该答案是正确的，帮助用户真正理解考点。回答要准确、简洁、有条理，使用中文。"

    // ── OpenAI 兼容接口 ────────────────────────────────────────────────
    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.3
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

    /** 生成 AI 解析（主入口）。cfg 由调用方从 AiConfigStore.load 取得后传入。 */
    suspend fun explain(cfg: AiConfig, q: Question, hint: String): AiExplanation = withContext(Dispatchers.IO) {
        require(cfg.llmBaseUrl.isNotBlank() && cfg.llmApiKey.isNotBlank() && cfg.llmModel.isNotBlank()) {
            "请先在「设置 → AI 大模型配置」中填写大模型地址、Key 与模型"
        }
        val searchContext = if (AiConfigStore.searchEnabled(cfg)) {
            runCatching { doSearch(cfg, q.question) }.getOrElse { null }
        } else null

        val prompt = buildPrompt(q, hint, searchContext?.text)
        val api = retrofit(cfg.llmBaseUrl).create(OpenAiApi::class.java)
        val resp = api.chat(
            "Bearer ${cfg.llmApiKey}",
            ChatRequest(
                model = cfg.llmModel,
                messages = listOf(
                    ChatMessage("system", SYSTEM_PROMPT),
                    ChatMessage("user", prompt)
                )
            )
        )
        val text = resp.choices.firstOrNull()?.message?.content?.trim()
            ?: throw IllegalStateException("模型未返回解析内容，请检查模型/Key 或重试")
        AiExplanation(
            text = text,
            model = cfg.llmModel,
            provider = hostOf(cfg.llmBaseUrl),
            createdAt = System.currentTimeMillis(),
            hintUsed = hint.ifBlank { null },
            citations = searchContext?.citations ?: emptyList(),
            promptHash = promptHash(q, hint)
        )
    }

    /** 拉取可用模型列表（用户在设置页点「拉取模型」时调用）。 */
    suspend fun listModels(cfg: AiConfig): List<String> = withContext(Dispatchers.IO) {
        require(cfg.llmBaseUrl.isNotBlank() && cfg.llmApiKey.isNotBlank()) { "请先填写大模型地址与 Key" }
        val api = retrofit(cfg.llmBaseUrl).create(OpenAiApi::class.java)
        api.models("Bearer ${cfg.llmApiKey}").data.map { it.id }.filter { it.isNotBlank() }.sorted()
    }

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
        sb.appendLine("这是一道题目，请解释为什么给出的标准答案是正确的，帮助用户理解考点。")
        if (hint.isNotBlank()) sb.appendLine("\n【用户补充提示】$hint")
        sb.appendLine("\n【题干】${q.question}")
        q.options?.takeIf { it.isNotEmpty() }?.let { opts ->
            sb.appendLine("【选项】")
            opts.forEachIndexed { i, o -> sb.appendLine("${'A' + i}. $o") }
        }
        sb.appendLine("\n【标准答案】${q.answer ?: "（无）"}")
        if (!q.explanation.isNullOrBlank()) sb.appendLine("\n【题库自带解析】${q.explanation}")
        if (!searchText.isNullOrBlank()) sb.appendLine("\n【联网搜索到的相关资料】\n$searchText")
        sb.appendLine("\n请输出清晰、有条理的解析，说明正确答案的推导/依据，必要时指出常见误区。用中文回答。")
        return sb.toString()
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host ?: url }.getOrDefault(url)

    private fun promptHash(q: Question, hint: String): String =
        ((q.question + "|" + (q.answer ?: "") + "|" + hint).hashCode()).toString()
}
