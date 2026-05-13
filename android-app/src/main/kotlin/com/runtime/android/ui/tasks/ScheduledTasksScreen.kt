package com.runtime.android.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ScheduledTaskUiItem(
    val id: String,
    val name: String,
    val skillName: String?,
    val prompt: String,
    val schedule: String,
    val enabled: Boolean,
    val lastRun: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    tasks: List<ScheduledTaskUiItem>,
    skills: List<String>,
    onAdd: (name: String, skillName: String?, prompt: String, schedule: String) -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定时任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加任务")
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("还没有定时任务", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击 + 创建一个", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.name) },
                        supportingContent = {
                            Column {
                                Text("${task.schedule}${task.skillName?.let { " · @$it" } ?: ""}")
                                Text(task.prompt.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                task.lastRun?.let {
                                    Text("上次运行: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        trailingContent = {
                            Row {
                                Switch(checked = task.enabled, onCheckedChange = { onToggle(task.id, it) })
                                IconButton(onClick = { onDelete(task.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            skills = skills,
            onDismiss = { showAddDialog = false },
            onSave = { name, skill, prompt, schedule ->
                onAdd(name, skill, prompt, schedule)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddTaskDialog(
    skills: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, skillName: String?, prompt: String, schedule: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var selectedSkill by remember { mutableStateOf<String?>(null) }
    var scheduleType by remember { mutableStateOf("每天") }
    var scheduleTime by remember { mutableStateOf("09:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建定时任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称") },
                    placeholder = { Text("每日新闻摘要") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("执行内容") },
                    placeholder = { Text("搜索今天的科技新闻并总结") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                // Schedule
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("每天", "每周一", "每周五").forEach { option ->
                        FilterChip(
                            selected = scheduleType == option,
                            onClick = { scheduleType = option },
                            label = { Text(option) }
                        )
                    }
                }
                OutlinedTextField(
                    value = scheduleTime,
                    onValueChange = { scheduleTime = it },
                    label = { Text("时间") },
                    placeholder = { Text("09:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Skill selection (optional)
                if (skills.isNotEmpty()) {
                    Text("使用技能（可选）", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedSkill == null,
                            onClick = { selectedSkill = null },
                            label = { Text("无") }
                        )
                        skills.take(4).forEach { skill ->
                            FilterChip(
                                selected = selectedSkill == skill,
                                onClick = { selectedSkill = skill },
                                label = { Text("@$skill") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val schedule = "${scheduleType}_$scheduleTime"
                    onSave(name.trim(), selectedSkill, prompt.trim(), schedule)
                },
                enabled = name.isNotBlank() && prompt.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
