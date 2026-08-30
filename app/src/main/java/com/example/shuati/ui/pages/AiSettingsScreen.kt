package com.example.shuati.ui.pages

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AiConfigStore
import com.example.shuati.logic.AiExplainService
import com.example.shuati.model.AiConfig
import com.example.shuati.model.AiProviderConfig
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.ShuatiSwitch
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

    var busy by remember { mutableStateOf(false) }
    // 各板块独立的结果提示（大模型 / 搜索 / 模板），分别显示在各自板块下方，不再统一堆到页面底部
    var llmStatus by remember { mutableStateOf("") }
    var searchStatus by remember { mutableStateOf("") }
    var promptStatus by remember { mutableStateOf("") }

    // 重命名 / 删除 弹窗状态
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf("") }
    var renameValue by remember { mutableStateOf("") }

    fun updProvider(p: AiProviderConfig) {
        providers = providers.map { if (it.id == p.id) p else it }
    }
    fun addProvider() {
        val np = AiProviderConfig(
            id = AiConfigStore.newProviderId(),
            name = "新供应商 ${providers.size + 1}"
        )
        providers = providers + np
        selectedProviderId = np.id
    }
    fun testCurrent() {
        val p = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
        if (p == null) { llmStatus = "请先添加供应商"; return }
        if (p.baseUrl.isBlank() || p.apiKey.isBlank()) { llmStatus = "请先填写该供应商的地址与 Key"; return }
        val modelsToTest = if (p.models.isNotEmpty()) p.models else {
            if (p.selectedModel.isNotBlank()) listOf(p.selectedModel) else emptyList()
        }
        if (modelsToTest.isEmpty()) { llmStatus = "当前供应商尚未配置模型，请先拉取/填写模型"; return }
        busy = true
        scope.launch {
            val cfg = AiConfig(p.baseUrl, p.apiKey, p.selectedModel)
            val ok = mutableListOf<String>()
            val fail = mutableListOf<String>()
            modelsToTest.forEach { m ->
                val err = AiExplainService.testModel(cfg, m)
                if (err.isEmpty()) ok.add(m) else fail.add("$m（${err}）")
            }
            llmStatus = buildString {
                append("「${p.name}」测试完成：")
                append(if (ok.isNotEmpty()) "可用 ${ok.size} 个（${ok.joinToString("、")}）" else "无可用模型")
                if (fail.isNotEmpty()) append("；失败 ${fail.size} 个（${fail.joinToString("；")}）")
            }
            busy = false
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "大模型配置（多供应商）", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                // 添加按钮：置于标题右侧
                TextButton(onClick = { addProvider() }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加", fontSize = 13.sp)
                }
            }
            ModuleCard {
                Text(
                    "可保存多家供应商，分别拉取模型列表后多选保存；解析页可随时切换供应商与模型，默认使用上次选中的模型。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val current = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()

                // 模型配置切换：点击输入框向下展开下拉列表（与搜索引擎同交互），非模态弹窗
                ProviderSelector(
                    providerName = current?.name ?: "（未配置供应商）",
                    providers = providers,
                    selectedId = selectedProviderId,
                    onSelect = { selectedProviderId = it }
                )

                // 操作按钮：重命名 / 删除 / 测试连接（单行不换行不裁切：收窄按钮内边距）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { renameTarget = current?.id ?: ""; renameValue = current?.name ?: ""; showRenameDialog = true },
                        enabled = current != null, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) { Text("重命名", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(
                        onClick = { if (current != null) showDeleteConfirm = true },
                        enabled = current != null, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) { Text("删除", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(
                        onClick = { testCurrent() },
                        enabled = current != null, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) { Text("测试连接", fontSize = 12.sp, maxLines = 1) }
                }

                // 当前选中供应商配置板块
                current?.let { p ->
                    ProviderConfigCard(
                        provider = p,
                        onUpdate = { updProvider(it) },
                        scope = scope,
                        setStatus = { llmStatus = it },
                        setBusy = { busy = it }
                    )
                } ?: Text("点击上方「添加」创建第一个供应商",
                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val sel = if (providers.any { it.id == selectedProviderId }) selectedProviderId else providers.firstOrNull()?.id ?: ""
                            AiConfigStore.saveProviders(ctx, providers, sel)
                            selectedProviderId = sel
                            Toast.makeText(ctx, "已保存大模型配置", Toast.LENGTH_SHORT).show()
                            llmStatus = "大模型配置已保存（${providers.size} 家供应商）"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                }
                // 大模型测试/拉取结果（显示在本板块下方）
                if (llmStatus.isNotBlank()) {
                    Text(llmStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // 重命名弹窗
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("重命名供应商") },
                    text = {
                        OutlinedTextField(
                            value = renameValue, onValueChange = { renameValue = it },
                            label = { Text("供应商名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (renameValue.isNotBlank()) {
                                providers = providers.map { if (it.id == renameTarget) it.copy(name = renameValue.trim()) else it }
                            }
                            showRenameDialog = false
                        }) { Text("确定") }
                    },
                    dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } }
                )
            }

            // 删除确认弹窗
            val deletingProvider = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("删除供应商") },
                    text = { Text("确定删除「${deletingProvider?.name ?: ""}」吗？此操作不可撤销。") },
                    confirmButton = {
                        TextButton(onClick = {
                            val id = deletingProvider?.id
                            if (id != null) {
                                providers = providers.filter { it.id != id }
                                if (selectedProviderId == id) selectedProviderId = providers.firstOrNull()?.id ?: ""
                            }
                            showDeleteConfirm = false
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
                )
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
                // 选择搜索引擎后，在 Key 文本框下方显示官网注册/申请链接，点击可直接浏览器打开
                if (searchProvider.isNotBlank()) {
                    val registerUrl = searchRegisterUrl(searchProvider)
                    if (registerUrl != null) {
                        TextButton(
                            onClick = {
                                runCatching {
                                    val i = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(registerUrl)
                                    )
                                    ctx.startActivity(i)
                                }.onFailure { searchStatus = "无法打开链接" }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("注册/申请 Key（${searchLabel(searchProvider)}）", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (searchProvider.isBlank()) { searchStatus = "请先选择搜索引擎"; return@Button }
                            if (searchKey.isBlank()) { searchStatus = "请先填写搜索 Key"; return@Button }
                            scope.launch {
                                searchStatus = "正在测试「${searchLabel(searchProvider)}」…"
                                searchStatus = AiExplainService.testSearchConnection(
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
                            searchStatus = "联网搜索配置已保存（${searchLabel(searchProvider)}）"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                }
                // 搜索测试结果（显示在本板块下方）
                if (searchStatus.isNotBlank()) {
                    Text(searchStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 模块 C：解析模板 ───────────────────────────────────────
            Text("解析模板（skill）", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            ModuleCard {
                Text(
                    "即内置的「题目解析 skill」系统提示。修改它可定制解析风格与结构（如更偏医学 / 法律 / 编程考法）；留空或点「恢复默认」即使用内置模板。",
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
                Box {
                    OutlinedTextField(
                        value = customPrompt, onValueChange = { customPrompt = it },
                        label = { Text("解析模板（系统提示）") },
                        placeholder = { Text("内置 skill 模板…", color = MaterialTheme.colorScheme.outline) },
                        singleLine = false, minLines = 6, modifier = Modifier.fillMaxWidth()
                    )
                    // 输入框最下方右下角：当前总字符数
                    Text(
                        "${customPrompt.length} 字",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp)
                    )
                }
                // 恢复默认 与 保存配置 并列
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { customPrompt = AiExplainService.SYSTEM_PROMPT },
                        modifier = Modifier.weight(1f)
                    ) { Text("恢复默认", fontSize = 12.5.sp, maxLines = 1) }
                    Button(
                        onClick = {
                            AiConfigStore.savePrompt(ctx, customPrompt)
                            Toast.makeText(ctx, "已保存解析模板", Toast.LENGTH_SHORT).show()
                            promptStatus = "解析模板已保存（${customPrompt.length} 字）"
                        },
                        enabled = !busy, modifier = Modifier.weight(1f)
                    ) { Text("保存配置", fontSize = 12.5.sp, maxLines = 1) }
                }
                if (promptStatus.isNotBlank()) {
                    Text(promptStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
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

/** 头部模型配置选择器：点击输入框向下展开下拉列表（与搜索引擎选择同交互）。 */
@Composable
private fun ProviderSelector(
    providerName: String,
    providers: List<AiProviderConfig>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (providers.isNotEmpty()) expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = providerName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("选择模型配置") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name.ifBlank { "未命名供应商" }, maxLines = 1) },
                    trailingIcon = {
                        if (p.id == selectedId) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                    },
                    onClick = {
                        onSelect(p.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 当前选中供应商的配置板块：供应商链接 / API 密钥 / 模型名称输入框 + 拉取模型按钮（多选弹窗）。
 * 支持手动输入模型名称，也可拉取供应商模型列表多选后自动填入。
 */
@Composable
private fun ProviderConfigCard(
    provider: AiProviderConfig,
    onUpdate: (AiProviderConfig) -> Unit,
    scope: CoroutineScope,
    setStatus: (String) -> Unit,
    setBusy: (Boolean) -> Unit
) {
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var apiKey by remember(provider.id) { mutableStateOf(provider.apiKey) }
    var modelName by remember(provider.id) { mutableStateOf(provider.selectedModel) }
    var fetched by remember(provider.id) { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember(provider.id) { mutableStateOf(provider.models.toSet()) }
    var menuExpanded by remember(provider.id) { mutableStateOf(false) }
    var fetching by remember(provider.id) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(provider.name.ifBlank { "未命名供应商" },
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.5.sp)

            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it; onUpdate(provider.copy(baseUrl = it.trim())) },
                label = { Text("供应商链接（OpenAI 兼容）") },
                placeholder = { Text("https://api.openai.com/v1", color = MaterialTheme.colorScheme.outline) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it; onUpdate(provider.copy(apiKey = it.trim())) },
                label = { Text("API 密钥") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            // 模型名称输入框：拉取按钮内嵌在输入框最右侧，点击向下展开模型列表（可多选）
            fun fetchModels(onDone: () -> Unit) {
                if (baseUrl.isBlank() || apiKey.isBlank()) {
                    setStatus("请先填写供应商链接与 API 密钥"); return
                }
                fetching = true
                scope.launch {
                    runCatching { AiExplainService.listModels(AiConfig(baseUrl.trim(), apiKey.trim(), "")) }
                        .onSuccess { list ->
                            fetched = list
                            picked = if (provider.models.isNotEmpty()) provider.models.toSet() else list.toSet()
                            setStatus("已拉取 ${list.size} 个模型，可多选保存")
                            onDone()
                        }
                        .onFailure { setStatus("拉取失败：${it.message ?: it}") }
                    fetching = false
                }
            }
            fun applyPicked() {
                val modelList = picked.toList().sorted()
                val first = modelList.firstOrNull() ?: ""
                onUpdate(provider.copy(models = modelList, selectedModel = modelName.trim().takeIf { it.isNotBlank() && modelList.contains(it.trim()) } ?: first))
                if (modelName.trim() !in modelList) modelName = first
            }

            Box {
                OutlinedTextField(
                    value = modelName, onValueChange = {
                        modelName = it
                        onUpdate(provider.copy(selectedModel = it.trim()))
                    },
                    label = { Text("模型名称") },
                    placeholder = { Text("如 gpt-4o / deepseek-chat", color = MaterialTheme.colorScheme.outline) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        // 输入框最右侧的拉取按钮：首次点击拉取模型列表，之后点击切换下拉展开
                        IconButton(onClick = {
                            if (menuExpanded) {
                                applyPicked(); menuExpanded = false
                            } else if (fetched.isNotEmpty()) {
                                menuExpanded = true
                            } else {
                                fetchModels { menuExpanded = true }
                            }
                        }) {
                            if (fetching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (menuExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.Download,
                                    contentDescription = "拉取模型列表",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { applyPicked(); menuExpanded = false },
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    val models = fetched.ifEmpty { provider.models }
                    if (models.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("未拉到可用模型，点此重新拉取", fontSize = 13.sp) },
                            onClick = { fetchModels { menuExpanded = true } }
                        )
                    } else {
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = m in picked, onCheckedChange = null)
                                        Text(m, fontSize = 13.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                    }
                                },
                                onClick = {
                                    picked = picked.toMutableSet().apply {
                                        if (contains(m)) remove(m) else add(m)
                                    }
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("完成（${picked.size} 个已选）", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { applyPicked(); menuExpanded = false }
                        )
                    }
                }
            }

            // 已选模型摘要
            if (picked.isNotEmpty()) {
                Text("已选模型（${picked.size}）：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(picked.sorted().joinToString("、"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val searchOptions = listOf(
    "" to "（无，依赖模型内置搜索）",
    "tavily" to "Tavily（AI 搜索，国际，免费额度）",
    "exa" to "Exa（AI 搜索，国际）",
    "bocha" to "博查 Bocha（国内直连，推荐）",
    "serper" to "Serper.dev（Google 搜索 API，国际）",
    "brave" to "Brave Search（隐私搜索，国际，免费额度）"
)

private fun searchLabel(p: String) = when (p) {
    "tavily" -> "Tavily（AI 搜索）"
    "exa" -> "Exa（AI 搜索）"
    "bocha" -> "博查 Bocha（国内直连）"
    "serper" -> "Serper.dev（Google 搜索）"
    "brave" -> "Brave Search"
    else -> "（无，依赖模型内置搜索）"
}

private fun searchKeyHint(p: String) = when (p) {
    "tavily" -> "如 tvly-xxxxxxxx"
    "exa" -> "如 exa-xxxxxxxx"
    "bocha" -> "如 sk-xxxxxxxx（博查开放平台）"
    "serper" -> "如 xxxxxxxx（Serper.dev 控制台）"
    "brave" -> "如 BSxxxxxxxx（Brave 开发者）"
    else -> "选择搜索引擎后填写"
}

/** 各搜索引擎的官网 Key 注册/申请链接（选择该引擎后在 Key 文本框下方展示）。 */
private fun searchRegisterUrl(p: String): String? = when (p) {
    "tavily" -> "https://app.tavily.com/"
    "exa" -> "https://dashboard.exa.ai/"
    "bocha" -> "https://open.bochaai.com/"
    "serper" -> "https://serper.dev/"
    "brave" -> "https://brave.com/search/api/"
    else -> null
}
