package com.example.shuati.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.shuati.model.AiConfig
import com.example.shuati.model.AiProviderConfig
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

/**
 * AI 配置本地加密存储层。
 * - 多供应商：providers 列表（每家含 baseUrl/apiKey/已保存模型/选中模型），selectedProviderId 记忆"上次使用"。
 * - 全局模块：联网搜索配置（searchProvider/searchApiKey）、解析模板（customPrompt）各自独立存取。
 * 使用 Android Keystore 背书的 EncryptedSharedPreferences，Key 仅存于本机、不离开设备。
 */
object AiConfigStore {

    private const val FILE = "shuati_ai_secure"
    // 旧单配置键（用于首次启动时迁移到多供应商结构）
    private const val K_BASE = "llm_base_url"
    private const val K_KEY = "llm_api_key"
    private const val K_MODEL = "llm_model"
    // 新多供应商键
    private const val K_PROVIDERS = "providers_json"
    private const val K_SEL_PROVIDER = "selected_provider_id"
    // 全局模块键
    private const val K_SEARCH_PROVIDER = "search_provider"
    private const val K_SEARCH_KEY = "search_api_key"
    private const val K_PROMPT = "custom_prompt"
    // 行为开关：重新生成时是否携带上次解析作上下文（默认开）
    private const val K_REUSE_PREV = "reuse_prev_on_regen"

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx,
        FILE,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** 全部设置的聚合（设置页与组合运行时配置使用）。 */
    data class AiSettings(
        val providers: List<AiProviderConfig> = emptyList(),
        val selectedProviderId: String = "",
        val searchProvider: String = "",
        val searchApiKey: String = "",
        val customPrompt: String = "",
        val reusePrevOnRegen: Boolean = true
    )

    /** 读取全部设置；首次启动若存在旧单配置，自动迁移为单个供应商。 */
    fun loadAll(ctx: Context): AiSettings {
        val p = prefs(ctx)
        val providers = p.getString(K_PROVIDERS, "")?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<List<AiProviderConfig>>(raw) }.getOrElse { emptyList() }
        } ?: emptyList()
        val searchProvider = p.getString(K_SEARCH_PROVIDER, "") ?: ""
        val searchApiKey = p.getString(K_SEARCH_KEY, "") ?: ""
        val customPrompt = p.getString(K_PROMPT, "") ?: ""
        val reusePrevOnRegen = p.getBoolean(K_REUSE_PREV, true)

        // 迁移：旧单配置 -> 单个供应商
        if (providers.isEmpty()) {
            val oldBase = p.getString(K_BASE, "") ?: ""
            val oldKey = p.getString(K_KEY, "") ?: ""
            val oldModel = p.getString(K_MODEL, "") ?: ""
            if (oldBase.isNotBlank() || oldKey.isNotBlank() || oldModel.isNotBlank()) {
                val pid = newProviderId()
                val prov = AiProviderConfig(
                    id = pid,
                    name = defaultName(oldBase),
                    baseUrl = oldBase,
                    apiKey = oldKey,
                    models = if (oldModel.isNotBlank()) listOf(oldModel) else emptyList(),
                    selectedModel = oldModel
                )
                val migrated = AiSettings(listOf(prov), pid, searchProvider, searchApiKey, customPrompt, reusePrevOnRegen)
                saveProviders(ctx, migrated.providers, migrated.selectedProviderId)
                return migrated
            }
        }
        val sel = p.getString(K_SEL_PROVIDER, "") ?: ""
        return AiSettings(providers, sel, searchProvider, searchApiKey, customPrompt, reusePrevOnRegen)
    }

    /**
     * 组合出运行时 AiConfig：取"上次使用的供应商"（selectedProviderId，缺失则第一个）的
     * baseUrl/apiKey/选中模型，叠加全局搜索配置与解析模板。供 AiExplainService 直接使用。
     */
    fun activeConfig(ctx: Context): AiConfig {
        val s = loadAll(ctx)
        val p = s.providers.firstOrNull { it.id == s.selectedProviderId } ?: s.providers.firstOrNull()
        return AiConfig(
            llmBaseUrl = p?.baseUrl ?: "",
            llmApiKey = p?.apiKey ?: "",
            llmModel = p?.selectedModel ?: "",
            searchProvider = s.searchProvider,
            searchApiKey = s.searchApiKey,
            customPrompt = s.customPrompt
        )
    }

    /** 兼容旧调用：等价于 activeConfig。 */
    fun load(ctx: Context): AiConfig = activeConfig(ctx)

    fun saveProviders(ctx: Context, providers: List<AiProviderConfig>, selectedProviderId: String) {
        prefs(ctx).edit().apply {
            putString(K_PROVIDERS, json.encodeToString(ListSerializer(AiProviderConfig.serializer()), providers))
            putString(K_SEL_PROVIDER, selectedProviderId)
        }.apply()
    }

    fun saveSearch(ctx: Context, provider: String, key: String) {
        prefs(ctx).edit().apply {
            putString(K_SEARCH_PROVIDER, provider)
            putString(K_SEARCH_KEY, key)
        }.apply()
    }

    fun savePrompt(ctx: Context, prompt: String) {
        prefs(ctx).edit().apply { putString(K_PROMPT, prompt) }.apply()
    }

    /** 保存行为开关：重新生成时是否携带上次解析作上下文。 */
    fun saveReusePrevOnRegen(ctx: Context, value: Boolean) {
        prefs(ctx).edit().apply { putBoolean(K_REUSE_PREV, value) }.apply()
    }

    /** 是否已配置可联网搜索（搜索引擎 provider + key 齐全）。 */
    fun searchEnabled(cfg: AiConfig): Boolean =
        cfg.searchApiKey.isNotBlank() && cfg.searchProvider.isNotBlank()

    fun newProviderId(): String = "p_${UUID.randomUUID()}"

    /** 由端点推导默认供应商名称（取 host）。 */
    fun defaultName(baseUrl: String): String =
        if (baseUrl.isBlank()) "未命名供应商" else runCatching { URI(baseUrl).host ?: baseUrl }.getOrDefault(baseUrl)
}
