package com.example.shuati.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.logic.ExcelWriter
import com.example.shuati.logic.QuestionFileImporter
import com.example.shuati.logic.Scoring
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.components.StatTile
import com.example.shuati.ui.components.TagChip
import com.example.shuati.ui.components.typeLabel
import com.example.shuati.ui.theme.ShuatiSpacing
import com.example.shuati.ui.theme.semantic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.shuati.ui.components.SectionTitle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(navController: NavController) {
    val repo = repository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("idle") }   // idle | parsing | done | error
    var message by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var parsedName by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf(0) }
    var totalRows by remember { mutableStateOf(0) }
    var skipped by remember { mutableStateOf(0) }
    var typeCounts by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var formatLabel by remember { mutableStateOf("") }
    var charsetLabel by remember { mutableStateOf<String?>(null) }
    /** 分享导入时为多个 Uri；普通选择时为单个 */
    var sharedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    /** 解析出的待导入题库；dupCount/suspectCount 在预览期由后台算好后回填 */
    var pendingBank by remember { mutableStateOf<QuestionBank?>(null) }
    /** 预览期后台检测状态：null=计算中，>=0=已算出 */
    var previewDupCount by remember { mutableStateOf<Int?>(null) }
    var previewSuspectCount by remember { mutableStateOf<Int?>(null) }

    /** 统一的解析流程：支持单文件与多文件（分享导入） */
    fun analyze(uris: List<Uri>) {
        if (uris.isEmpty()) return
        status = "parsing"
        sharedUris = uris
        scope.launch {
            try {
                val results = uris.map { QuestionFileImporter.importUri(context, it, "导入题库") }
                val bank = if (results.size == 1) {
                    results[0].bank
                } else {
                    QuestionFileImporter.mergeBanks(results.map { it.bank }, "导入题库")
                }
                val names = uris.map { QuestionFileImporter.displayName(context, it) ?: "未知文件" }
                fileName = names.joinToString("、")
                imported = bank.questions.size
                totalRows = results.sumOf { it.totalRows }
                skipped = bank.questions.count { it.incomplete || it.idProblem }
                parsedName = bank.name
                formatLabel = results.map { it.format.label }.distinct().joinToString("、")
                charsetLabel = results.mapNotNull { it.charsetLabel }.distinct().joinToString("、")
                    .takeIf { it.isNotEmpty() }
                typeCounts = bank.questions
                    .groupingBy { it.type }
                    .eachCount()
                    .map { (t, c) -> com.example.shuati.ui.components.typeLabel(t) to c }
                    .sortedByDescending { it.second }
                bankName = repo.generateUniqueBankName(
                    if (uris.size == 1) stripExt(names[0]) else "多文件导入${uris.size}个"
                )
                pendingBank = bank
                previewDupCount = null
                previewSuspectCount = null
                status = "done"
                // 预览期后台预计算查重 + 疑错检测（利用用户看预览/命名的时间，不阻塞导入）；
                // 结果随题库持久化，题库管理页徽章直接读取，进页面零计算。
                // 用户此后仅在增删改题目时使结果失效，进管理页补算。
                withContext(Dispatchers.Default) {
                    val groups = repo.findDuplicateGroups(bank, repo.getDedupThreshold())
                    val dupCount = groups.sumOf { it.size } - groups.size
                    val suspectCount = bank.questions.count { Scoring.checkIsSuspect(it) }
                    pendingBank = bank.copy(dupCount = dupCount, suspectCount = suspectCount)
                    previewDupCount = dupCount
                    previewSuspectCount = suspectCount
                }
            } catch (e: QuestionFileImporter.UnsupportedFormatException) {
                message = e.message ?: "不支持的文件格式"
                status = "error"
            } catch (e: SecurityException) {
                // 分享方未授予读权限（如直接给出 file:// 路径），无法读取
                message = "无法读取该文件：分享方未授予访问权限。请改用应用内的「选择题库文件」导入，或在文件管理器中分享。"
                status = "error"
            } catch (e: Exception) {
                message = when {
                    e.message?.contains("EACCES") == true ->
                        "无法读取该文件：没有访问权限。请改用应用内的「选择题库文件」导入。"
                    e.message?.contains("Permission denied") == true ->
                        "无法读取该文件：没有访问权限。请改用应用内的「选择题库文件」导入。"
                    else -> "解析失败：${e.message}"
                }
                status = "error"
            }
        }
    }

    // 系统分享面板进入：自动解析传入的文件。
    // 以 holder 为 key 观察状态变化——应用已在前台时再次收到分享（onNewIntent），
    // 若不观察 holder，LaunchedEffect(Unit) 只会在首次进入执行一次，导致后续分享被忽略。
    val shared = SharedImportIntent.holder.value
    LaunchedEffect(shared) {
        if (shared != null) {
            SharedImportIntent.holder.value = null   // 消费掉，避免重复触发
            analyze(shared.uris)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            selectedUri = uri
            analyze(listOf(uri))
        }
    }

    // 下载 Excel 模板
    var templateMsg by remember { mutableStateOf<String?>(null) }
    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelWriter.writeTemplate(out)
                        } != null
                    } catch (_: Exception) { false }
                }
                templateMsg = if (ok) "模板已保存，可打开填写后重新导入" else "模板保存失败"
            }
        }
    }

    val step = when (status) { "parsing", "done" -> 2; "error" -> 1; else -> 1 }

    // 分层布局：内容区（Scaffold）在底层，底部「开始导入」按钮叠加在上层淡入淡出。
    // ⚠️ 不用 Scaffold(bottomBar=...)：按钮随 status 显隐会改变 content 的 padding，
    // 滚动到底部时内容会被顶得跳动。改为 Box 叠加后两者互不影响。
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                ScreenTopBar("导入题库", onBack = { navController.popBackStack() }, subtitle = "Excel · 模板下载")
            }
        ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // 底部为叠加的导入按钮预留固定空间（不随 status 变化，避免跳动）
                .padding(bottom = importButtonAreaHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ShuatiSpacing.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 三步进度
            Spacer(Modifier.height(4.dp))
            StepIndicator(current = step)

            // 模板下载卡
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("下载 Excel 模板", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("题干 · 题型 · 选项 · 答案 · 解析 · 标签 · 难度", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { templateLauncher.launch("题库导入模板.xlsx") }) {
                        Text("下载", fontSize = 13.sp)
                    }
                }
            }
            templateMsg?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
            }

            // 选择文件
            SectionTitle("选择文件")
            CardBox(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, QuestionFileImporter.SUPPORTED_MIME_TYPES)
                    }
                    launcher.launch(intent)
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.semantic.infoContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.FileOpen, null, tint = MaterialTheme.semantic.info, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            fileName.ifEmpty { "选择题库文件" },
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 2
                        )
                        Text(
                            if (fileName.isEmpty()) "支持 .xlsx / .csv" else "来自系统文件管理器",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (status == "parsing") LinearProgressIndicator(Modifier.fillMaxWidth())

            if (status == "error") {
                Surface(
                    color = MaterialTheme.semantic.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(message, Modifier.padding(12.dp), fontSize = 13.sp,
                        color = MaterialTheme.semantic.onErrorContainer)
                }
            }

            if (status == "done") {
                // 解析预览
                SectionTitle("解析预览", count = formatLabel.ifEmpty { "共 $totalRows 行" })
                CardBox {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatTile("$totalRows", "检测到题目", Modifier.weight(1f))
                        StatTile("$skipped", "不完整", Modifier.weight(1f),
                            valueColor = MaterialTheme.semantic.warning)
                        StatTile("0", "ID 重复", Modifier.weight(1f),
                            valueColor = MaterialTheme.semantic.error)
                    }
                    if (skipped > 0) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.semantic.warningContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.semantic.warning, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("$skipped 道题不完整", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.semantic.onWarningContainer)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text("题干或答案为空的题目已跳过。导入后可在「疑似错题」中检查。",
                                    fontSize = 11.5.sp, lineHeight = 17.sp,
                                    color = MaterialTheme.semantic.onWarningContainer.copy(alpha = 0.85f))
                            }
                        }
                    }
                    // 编码提示（仅 CSV）
                    charsetLabel?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("文件编码：$it", fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 重复题 + 疑错预检（后台计算，完成后显示；结果随导入持久化，供题库管理页徽章直接读取）
                    Spacer(Modifier.height(8.dp))
                    val dupText = when (val d = previewDupCount) {
                        null -> "重复题检测中…"
                        else -> if (d > 0) "发现 $d 道疑似重复题（可导入后在「查重检测」中处理）"
                                else "未发现重复题"
                    }
                    Text(
                        dupText, fontSize = 11.5.sp,
                        color = if ((previewDupCount ?: 0) > 0) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val suspectText = when (val s = previewSuspectCount) {
                        null -> "疑似错题检测中…"
                        else -> if (s > 0) "发现 $s 道疑似错题（可在编辑页「疑似错题」页签修正）"
                                else "未发现疑似错题"
                    }
                    Text(
                        suspectText, fontSize = 11.5.sp,
                        color = if ((previewSuspectCount ?: 0) > 0) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 题型分布
                    if (typeCounts.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            typeCounts.forEach { (t, c) ->
                                TagChip("$t $c")
                            }
                        }
                    }
                }

                // 题库名称
                SectionTitle("导入设置")
                CardBox {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("题库名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = repo.isBankNameTaken(bankName.trim()) && bankName.trim() != parsedName
                    )
                    if (repo.isBankNameTaken(bankName.trim()) && bankName.trim() != parsedName) {
                        Text("题库名已存在", color = MaterialTheme.semantic.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("导入后可在「编辑题库」中修正题目", fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        }

        // 上层：底部「开始导入」按钮叠加在内容之上，淡入淡出（不占 padding，不会顶动内容）
        AnimatedVisibility(
            visible = status == "done",
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    SoftButton(
                        text = "开始导入 $imported 题",
                        icon = Icons.Filled.Upload,
                        enabled = bankName.isNotBlank() &&
                            !(repo.isBankNameTaken(bankName.trim()) && bankName.trim() != parsedName),
                        onClick = {
                            scope.launch {
                                val name = bankName.trim()
                                // 直接使用预览期解析好的题库（含后台算好的 dupCount），无需重新读文件
                                val bank = pendingBank?.copy(name = name) ?: return@launch
                                val r = repo.addBank(bank)
                                if (r.success) {
                                    navController.popBackStack()
                                    navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}")
                                } else {
                                    message = r.message ?: "导入失败"
                                    status = "error"
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 底部「开始导入」按钮区高度：内容区底部预留该固定空间，避免按钮显隐导致内容跳动。 */
private val importButtonAreaHeight = 76.dp

/** 三步进度指示：1 选择文件 → 2 预览校验 → 3 导入 */
@Composable
private fun StepIndicator(current: Int) {
    val labels = listOf("选择文件", "预览校验", "导入")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val active = i + 1 <= current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (active) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(13.dp))
                    } else {
                        Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < labels.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(
                            if (i + 1 < current) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                )
            }
        }
    }
}

private fun stripExt(name: String): String = name.replace(Regex("\\.[^.]+$"), "")
