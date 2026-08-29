package com.example.shuati.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.shuati.logic.ExcelParser
import com.example.shuati.logic.ExcelWriter
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.components.StatTile
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
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            selectedUri = uri
            status = "parsing"
            scope.launch {
                try {
                    val res = ExcelParser.parseUri(context, uri, parsedName.ifEmpty { "导入题库" })
                    fileName = (context.contentResolver.query(uri, null, null, null, null)
                        ?.use { cursor ->
                            cursor.moveToFirst()
                            cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                        } ?: "unknown.xlsx")
                    imported = res.imported
                    totalRows = res.totalRows
                    skipped = res.skipped
                    parsedName = res.bank.name
                    bankName = repo.generateUniqueBankName(stripExt(fileName))
                    status = "done"
                } catch (e: Exception) {
                    message = "解析失败：${e.message}"
                    status = "error"
                }
            }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar("导入题库", onBack = { navController.popBackStack() }, subtitle = "Excel · 模板下载")
        },
        bottomBar = {
            if (status == "done") {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        SoftButton(
                            text = "开始导入 $imported 题",
                            icon = Icons.Filled.Upload,
                            enabled = bankName.isNotBlank() &&
                                !(repo.isBankNameTaken(bankName.trim()) && bankName.trim() != parsedName),
                            onClick = {
                                scope.launch {
                                    val res = ExcelParser.parseUri(context, selectedUri!!, bankName.trim())
                                    val r = repo.addBank(res.bank)
                                    if (r.success) {
                                        navController.popBackStack()
                                        navController.navigate("${Routes.EDIT_BANK}?bankId=${res.bank.id}")
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
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
                        Text("B 题干 · C 题型 · D 选项 · E 答案 · F 标签 · G 解析", fontSize = 11.sp,
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
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel"
                        ))
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
                            fileName.ifEmpty { "选择 Excel 文件" },
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                        )
                        Text(
                            if (fileName.isEmpty()) "支持 .xlsx / .xls" else "来自系统文件管理器",
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
                SectionTitle("解析预览", count = "共 $totalRows 行")
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
}

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
