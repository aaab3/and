package com.runtime.android.ui.skills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSkillScreen(
    onSave: (name: String, description: String, body: String, tools: List<String>) -> Unit,
    availableTools: List<String>,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedTools by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建技能") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (name.isNotBlank() && description.isNotBlank() && body.isNotBlank()) {
                                onSave(name.trim(), description.trim(), body.trim(), selectedTools.toList())
                            }
                        },
                        enabled = name.isNotBlank() && description.isNotBlank() && body.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("技能名称") },
                placeholder = { Text("translator") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("在聊天中用 @${name.ifBlank { "名称" }} 调用") }
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("简介") },
                placeholder = { Text("翻译文本到目标语言") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Body (system prompt)
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("系统提示词") },
                placeholder = { Text("你是一个专业翻译，自动检测源语言...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                minLines = 8
            )

            // Tool selection
            if (availableTools.isNotEmpty()) {
                Text("启用工具", style = MaterialTheme.typography.titleSmall)
                Text(
                    "选中的工具可被模型在执行此技能时调用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                availableTools.forEach { tool ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tool, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = tool in selectedTools,
                            onCheckedChange = { checked ->
                                selectedTools = if (checked) selectedTools + tool
                                    else selectedTools - tool
                            }
                        )
                    }
                }
            }
        }
    }
}
