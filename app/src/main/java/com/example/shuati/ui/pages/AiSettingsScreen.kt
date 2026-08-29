package com.example.shuati.ui.pages

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AiConfigStore
import com.example.shuati.logic.AiExplainService
import com.example.shuati.model.AiConfig
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.repository
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val initial = remember { AiConfigStore.load(ctx) }
    var baseUrl by remember { mutableStateOf(initial.llmBaseUrl) }
    var apiKey by remember { mutableStateOf(initial.llmApiKey) }
    var model by remember { mutableStateOf(initial.llmModel) }
    var searchProvider by remember { mutableStateOf(initial.searchProvider) }
    var searchKey by remember { mutableStateOf(initial.searchApiKey) }

    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }

    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun cfg() = AiConfig(
        llmBaseUrl = baseUrl.trim(),
        llmApiKey = apiKey.trim(),
        llmModel = model.trim(),
        searchProvider = searchProvider.trim(),
        searchApiKey = searchKey.trim()
    )

    val searchLabel = when (searchProvider) {
        "tavily" -> "Tavily（AI 搜索）"
        "exa" -> "Exa（AI 搜索）"
        else -> "（无，依赖模型内置搜索）"
    }
    val searchOptions = listOf(
        "" to "（无，依赖模型内置搜索）",
        "tavily" to "Tavily（AI 搜索）",
        "exa" to "Exa（AI 搜索）"
    )

    Scaffold(
        topBar = { ScreenTopBar("AI 大模型配置", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "配置你自己的大模型与联网搜索引擎。所有 Key 仅加密保存在本机，不会上传到任何服务器。",
                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 大模型 ────────────────────────────────────────────────
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("大模型地址（OpenAI 兼容）") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            // 模型选择（可拉取）
            ExposedDropdownMenuBox(
                expanded = modelExpanded, onExpandedChange = { modelExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("模型（可手动输入或拉取）") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded, onDismissRequest = { modelExpanded = false }
                ) {
                    models.forEach { m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = {
                            model = m; modelExpanded = false
                        })
                    }
                }
            }
            Button(
                onClick = {
                    if (baseUrl.isBlank() || apiKey.isBlank()) {
                        status = "请先填写大模型地址与 Key"; return@Button
                    }
                    scope.launch {
                        busy = true; status = "正在拉取模型…"
                        runCatching { AiExplainService.listModels(cfg()) }
                            .onSuccess { list ->
                                models = list
                                status = if (list.isEmpty()) "未返回模型，可手动填写模型名" else "已拉取 ${list.size} 个模型"
                                if (model.isBlank() && list.isNotEmpty()) model = list.first()
                            }
                            .onFailure { status = "拉取失败：${it.message ?: it}" }
                        busy = false
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) { Text("拉取可用模型") }

            HorizontalDivider()
            Text("联网搜索（可选）", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "配置后，AI 解析会先调用搜索引擎获取资料再汇总；若不配置，则依赖大模型自身是否具备联网能力。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = searchExpanded, onExpandedChange = { searchExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchLabel, onValueChange = {},
                    readOnly = true, label = { Text("搜索引擎") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = searchExpanded, onDismissRequest = { searchExpanded = false }) {
                    searchOptions.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            searchProvider = value; searchExpanded = false
                        })
                    }
                }
            }
            OutlinedTextField(
                value = searchKey, onValueChange = { searchKey = it },
                label = { Text("搜索引擎 API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                enabled = searchProvider.isNotBlank()
            )

            if (status.isNotBlank()) {
                Text(status, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { AiExplainService.listModels(cfg()) }
                                .onSuccess { status = "连接成功，可用模型 ${it.size} 个" }
                                .onFailure { status = "连接失败：${it.message ?: it}" }
                            busy = false
                        }
                    },
                    enabled = !busy, modifier = Modifier.weight(1f)
                ) { Text("测试连接") }
                Button(
                    onClick = {
                        AiConfigStore.save(ctx, cfg())
                        Toast.makeText(ctx, "已加密保存", Toast.LENGTH_SHORT).show()
                        status = "已保存"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存配置") }
            }

            val netStatus = when {
                AiConfigStore.searchEnabled(cfg()) -> "联网搜索：已启用（${searchProvider}）"
                baseUrl.isNotBlank() && apiKey.isNotBlank() -> "联网搜索：依赖模型内置（若支持）"
                else -> "尚未配置大模型"
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()
            ) {
                Text(netStatus, fontSize = 12.5.sp, modifier = Modifier.padding(10.dp))
            }
        }
    }
}
