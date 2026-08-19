package com.example.shuati.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.logic.ExcelParser
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

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
                        ?.use { cursor -> cursor.moveToFirst().let { cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) } }
                        ?: "unknown.xlsx")
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

    Scaffold(topBar = { ScreenTopBar("导入题库", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "支持 Excel (.xlsx) 导入。格式：B 列题干、C 列答案、D 列选项（| 分隔）、E 列解析、F 列标签（| 分隔）、G 列题型。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
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
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择 Excel 文件")
            }

            if (status == "parsing") LinearProgressIndicator(Modifier.fillMaxWidth())

            if (status == "error") {
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            if (status == "done") {
                CardBox {
                    Text("解析完成", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("文件：$fileName", fontSize = 13.sp)
                    Text("总行数：$totalRows　成功导入：$imported　跳过：$skipped", fontSize = 13.sp)
                    if (skipped > 0) Text("跳过原因：题干或答案为空", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("题库名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = repo.isBankNameTaken(bankName.trim()) && bankName.trim() != parsedName
                )
                if (repo.isBankNameTaken(bankName.trim()) && bankName.trim() != parsedName) {
                    Text("题库名已存在", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Button(
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
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = bankName.isNotBlank()
                ) { Text("保存并查看题库（${imported} 题）") }
            }
        }
    }
}

private fun stripExt(name: String): String = name.replace(Regex("\\.[^.]+$"), "")
