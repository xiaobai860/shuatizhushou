package com.example.shuati.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.shuati.model.AiConfig

/**
 * AI 配置（大模型地址/Key/模型 + 搜索引擎/Key）的本地加密存储层。
 * 使用 Android Keystore 背书的 EncryptedSharedPreferences，Key 仅存于本机、不离开设备。
 */
object AiConfigStore {

    private const val FILE = "shuati_ai_secure"
    private const val K_BASE = "llm_base_url"
    private const val K_KEY = "llm_api_key"
    private const val K_MODEL = "llm_model"
    private const val K_SEARCH_PROVIDER = "search_provider"
    private const val K_SEARCH_KEY = "search_api_key"
    private const val K_PROMPT = "custom_prompt"

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx,
        FILE,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(ctx: Context): AiConfig {
        val p = prefs(ctx)
        return AiConfig(
            llmBaseUrl = p.getString(K_BASE, "") ?: "",
            llmApiKey = p.getString(K_KEY, "") ?: "",
            llmModel = p.getString(K_MODEL, "") ?: "",
            searchProvider = p.getString(K_SEARCH_PROVIDER, "") ?: "",
            searchApiKey = p.getString(K_SEARCH_KEY, "") ?: "",
            customPrompt = p.getString(K_PROMPT, "") ?: ""
        )
    }

    fun save(ctx: Context, cfg: AiConfig) {
        prefs(ctx).edit().apply {
            putString(K_BASE, cfg.llmBaseUrl)
            putString(K_KEY, cfg.llmApiKey)
            putString(K_MODEL, cfg.llmModel)
            putString(K_SEARCH_PROVIDER, cfg.searchProvider)
            putString(K_SEARCH_KEY, cfg.searchApiKey)
            putString(K_PROMPT, cfg.customPrompt)
        }.apply()
    }

    /** 是否已配置可联网搜索（搜索引擎 provider + key 齐全） */
    fun searchEnabled(cfg: AiConfig): Boolean =
        cfg.searchApiKey.isNotBlank() && cfg.searchProvider.isNotBlank()
}
