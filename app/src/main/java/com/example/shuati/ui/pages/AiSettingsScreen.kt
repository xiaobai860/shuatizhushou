package com.example.shuati.ui.pages

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AiConfigStore
import com.example.shuati.logic.AiExplainService
import com.example.shuati.ui.components.ShuatiSwitch
import com.example.shuati.model.AiConfig
import com.example.shuati.model.AiProviderConfig
import com.example.shuati.ui.components.ScreenTopBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val initial = remember { AiConfigStore.loadAll(ctx) }
    var providers by remember { mutableStateOf(initial.providers) }
    var selectedProviderId by remember { mutableStateOf(initial.selectedProviderId) }
    var searchProvider by remember { mutableStateOf(initial.searchProvider) }
    var searchKey by remember { mutableStateOf(initial.searchApiKey) }
    var customPrompt by remember { mutableStateOf(initial.customPrompt.ifBlank { AiExplainService.SYSTEM_PROMPT }) }
    var reusePrev by remember { mutableStateOf(initial.reusePrevOnRegen) }

    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun activeCfg() = AiConfigStore.activeConfig(ctx)
    fun updProvider(p: AiProviderConfig) {
        providers = providers.map { if (it.id == p.id) p else it }
    }

    Scaffold(
        topBar = { ScreenTopBar("AI 大模型配置", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "配置你自己的大模型与联网搜索引擎。所有 Key 仅加密保存在本机，不会上传到任何服务器。",
                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 模块 A：大模型配置（多供应商） ──────────────────────────
            Text("大模型配置（多供应商）", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            ModuleCard {
                Text(
                    "可保存多家供应商，分别拉取模型列表后多选保存；解析页可随时切换供应商与模型，默认使用上次选中的模型。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                providers.forEach { p ->
                    ProviderCard(
                        provider = p,
                        isCurrent = p.id == selectedProviderId,
                        onUpdate = { updProvider(it) },
                        onDelete = { id ->
                            providers = providers.filter { it.id != id }
                            if (selectedProviderId == id) selectedProviderId = providers.firstOrNull()?.id ?: ""
                        },
                        onSetCurrent = { id -> selectedProviderId = id },
                        onTest = { prov ->
                            if (prov.baseUrl.isBlank() || prov.apiKey.isBlank()) {
                                status = "请先填写该供应商的地址与 Key"; return@ProviderCard
                            }
                            scope.launch {
                                runCatching { AiExplainService.listModels(AiConfig(prov.baseUrl, prov.apiKey, prov.selectedModel)) }
                                    .onSuccess { status = "「${prov.name}」连接成功，可用模型 ${it.size} 个" }
                                    .onFailure { status = "「${prov.name}」连接失败：${it.message ?: it}" }
                            }
                        },
                        scope = scope,
                        setStatus = { status = it }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = {
                        val np = AiProviderConfig(
                            id = AiConfigStore.newProviderId(),
                            name = "新供应商 ${providers.size + 1}"
                        )
                        providers = providers + np
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ 添加供应商") }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val target = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
                            if (target == null) { status = "请先添加供应商"; return@Button }
                            scope.launch {
                                runCatching { AiExplainService.listModels(AiConfig(target.baseUrl, target.apiKey, target.selectedModel)) }
                                    .onSuccess { status = "连接成功，可用模型 ${it.size} 个" }
                                    .onFailure { status = "连接失败：${it.message ?: it}" }
                            }
                        },
                        enabled = !busy, modifier = Modifier.weight(1f)
                    ) { Text("测试连接") }
                    Button(
                        onClick = {
                            val sel = if (providers.any { it.id == selectedProviderId }) selectedProviderId else providers.firstOrNull()?.id ?: ""
                            AiConfigStore.saveProviders(ctx, providers, sel)
                            selectedProviderId = sel
                            Toast.makeText(ctx, "已保存大模型配置", Toast.LENGTH_SHORT).show()
                            status = "大模型配置已保存（${providers.size} 家供应商）"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                }
            }

            // ── 模块 B：联网搜索配置 ───────────────────────────────────
            Text("联网搜索配置", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            ModuleCard {
                Text(
                    "配置后，AI 解析会先调用搜索引擎获取资料再汇总；若不配置，则依赖大模型自身是否具备联网能力。国内推荐博查（直连、免费额度）。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var searchExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = searchExpanded, onExpandedChange = { searchExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchLabel(searchProvider),
                        onValueChange = {}, readOnly = true, label = { Text("搜索引擎") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
                    placeholder = { Text(searchKeyHint(searchProvider), color = MaterialTheme.colorScheme.outline) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = searchProvider.isNotBlank()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (searchProvider.isBlank()) { status = "请先选择搜索引擎"; return@Button }
                            if (searchKey.isBlank()) { status = "请先填写搜索 Key"; return@Button }
                            scope.launch {
                                status = AiExplainService.testSearchConnection(
                                    AiConfig(searchProvider = searchProvider, searchApiKey = searchKey)
                                )
                            }
                        },
                        enabled = !busy, modifier = Modifier.weight(1f)
                    ) { Text("测试连接") }
                    Button(
                        onClick = {
                            AiConfigStore.saveSearch(ctx, searchProvider, searchKey)
                            Toast.makeText(ctx, "已保存联网搜索配置", Toast.LENGTH_SHORT).show()
                            status = "联网搜索配置已保存（${searchLabel(searchProvider)}）"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                }
            }

            // ── 模块 C：解析模板 ───────────────────────────────────────
            Text("解析模板（skill）", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            ModuleCard {
                Text(
                    "即内置的「题目解析 skill」系统提示。修改它可定制解析风格与结构（如更偏医学 / 法律 / 编程考法）；留空或点「恢复默认模板」即使用内置模板。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("重新生成携带上次解析", fontSize = 13.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(
                            "开启后，点「重新生成」会把上一次解析作为上下文供模型校正/补漏，并复用已检索的联网资料（更连贯、更省 token）。关闭则每次全新生成。",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ShuatiSwitch(
                        checked = reusePrev,
                        onCheckedChange = {
                            reusePrev = it
                            AiConfigStore.saveReusePrevOnRegen(ctx, it)
                        }
                    )
                }
                OutlinedTextField(
                    value = customPrompt, onValueChange = { customPrompt = it },
                    label = { Text("解析模板（系统提示）") },
                    placeholder = { Text("内置 skill 模板…", color = MaterialTheme.colorScheme.outline) },
                    singleLine = false, minLines = 6, modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { customPrompt = AiExplainService.SYSTEM_PROMPT }) {
                    Text("恢复默认模板", fontSize = 12.5.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            status = if (customPrompt.isBlank()) "模板为空：将使用内置默认模板（有效）"
                            else "模板已就绪（${customPrompt.length} 字），可被解析正常引用"
                        },
                        enabled = !busy, modifier = Modifier.weight(1f)
                    ) { Text("测试连接") }
                    Button(
                        onClick = {
                            AiConfigStore.savePrompt(ctx, customPrompt)
                            Toast.makeText(ctx, "已保存解析模板", Toast.LENGTH_SHORT).show()
                            status = "解析模板已保存"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                }
            }

            if (status.isNotBlank()) {
                Text(status, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
            }

            val net = activeCfg()
            val netStatus = when {
                AiConfigStore.searchEnabled(net) -> "联网搜索：已启用（${searchLabel(searchProvider)}）"
                net.llmBaseUrl.isNotBlank() && net.llmApiKey.isNotBlank() -> "联网搜索：依赖模型内置（若支持）"
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

@Composable
private fun ModuleCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ProviderCard(
    provider: AiProviderConfig,
    isCurrent: Boolean,
    onUpdate: (AiProviderConfig) -> Unit,
    onDelete: (String) -> Unit,
    onSetCurrent: (String) -> Unit,
    onTest: (AiProviderConfig) -> Unit,
    scope: CoroutineScope,
    setStatus: (String) -> Unit
) {
    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf(provider.models.toSet()) }
    var modelExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (name.isBlank()) "未命名供应商" else name,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.5.sp)
                Spacer(Modifier.weight(1f))
                if (isCurrent) {
                    AssistChip(onClick = { }, label = { Text("当前", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ))
                } else {
                    TextButton(onClick = { onSetCurrent(provider.id) }) { Text("设为当前", fontSize = 11.5.sp) }
                }
                IconButton(onClick = { onDelete(provider.id) }) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            OutlinedTextField(value = name, onValueChange = {
                name = it
                onUpdate(provider.copy(name = it))
            }, label = { Text("供应商名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = {
                baseUrl = it
                onUpdate(provider.copy(baseUrl = it.trim()))
            }, label = { Text("大模型地址（OpenAI 兼容）") }, placeholder = { Text("https://api.openai.com/v1", color = MaterialTheme.colorScheme.outline) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = apiKey, onValueChange = {
                apiKey = it
                onUpdate(provider.copy(apiKey = it.trim()))
            }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())

            Button(onClick = {
                if (baseUrl.isBlank() || apiKey.isBlank()) { setStatus("请先填写该供应商的地址与 Key"); return@Button }
                scope.launch {
                    runCatching { AiExplainService.listModels(AiConfig(baseUrl, apiKey, "")) }
                        .onSuccess { list ->
                            fetched = list
                            picked = if (provider.models.isNotEmpty()) provider.models.toSet() else list.toSet()
                            onUpdate(provider.copy(models = picked.toList(), selectedModel = provider.selectedModel.takeIf { it in picked } ?: picked.firstOrNull() ?: ""))
                            setStatus("已拉取 ${list.size} 个模型，可多选；勾选后即为该供应商保存的模型")
                        }
                        .onFailure { setStatus("拉取失败：${it.message ?: it}") }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("拉取可用模型") }

            val chipModels = fetched.ifEmpty { provider.models }
            if (chipModels.isNotEmpty()) {
                Text("已保存模型（可多选）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    chipModels.forEach { m ->
                        FilterChip(
                            selected = m in picked,
                            onClick = {
                                picked = picked.toMutableSet().apply { if (contains(m)) remove(m) else add(m) }
                                onUpdate(provider.copy(models = picked.toList(), selectedModel = provider.selectedModel.takeIf { it in picked } ?: picked.firstOrNull() ?: ""))
                            },
                            label = { Text(m, fontSize = 11.5.sp) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            if (provider.models.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = modelExpanded, onExpandedChange = { modelExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = provider.selectedModel, onValueChange = {},
                        readOnly = true, label = { Text("当前选中模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        provider.models.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onUpdate(provider.copy(selectedModel = m)); modelExpanded = false
                            })
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onTest(provider) }, modifier = Modifier.weight(1f)) { Text("测试连接", fontSize = 12.5.sp) }
            }
        }
    }
}

private val searchOptions = listOf(
    "" to "（无，依赖模型内置搜索）",
    "tavily" to "Tavily（AI 搜索，国际）",
    "exa" to "Exa（AI 搜索，国际）",
    "bocha" to "博查 Bocha（国内直连，推荐）"
)

private fun searchLabel(p: String) = when (p) {
    "tavily" -> "Tavily（AI 搜索）"
    "exa" -> "Exa（AI 搜索）"
    "bocha" -> "博查 Bocha（国内直连）"
    else -> "（无，依赖模型内置搜索）"
}

private fun searchKeyHint(p: String) = when (p) {
    "tavily" -> "如 tvly-xxxxxxxx"
    "exa" -> "如 exa-xxxxxxxx"
    "bocha" -> "如 sk-xxxxxxxx（博查开放平台）"
    else -> "选择搜索引擎后填写"
}
