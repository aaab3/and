package com.runtime.android.ui.skills

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SkillUiItem(
    val name: String,
    val description: String,
    val tools: List<String>,
    val model: String?,
    val sourcePath: String,
    val referenceCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    skills: List<SkillUiItem>,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit = {},
    onBack: () -> Unit
) {
    var selectedSkill by remember { mutableStateOf<SkillUiItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("技能") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                FloatingActionButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "导入技能")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("创建新技能") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onCreate() }
                    )
                    DropdownMenuItem(
                        text = { Text("导入单个文件") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        onClick = { showMenu = false; onImportFile() }
                    )
                    DropdownMenuItem(
                        text = { Text("导入文件夹") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        onClick = { showMenu = false; onImportFolder() }
                    )
                }
            }
        }
    ) { padding ->
        if (skills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "还没有安装技能",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "点击 + 导入技能文件或文件夹",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(skills, key = { it.name }) { skill ->
                    SkillRow(
                        skill = skill,
                        onClick = { selectedSkill = skill },
                        onDelete = { onDelete(skill.name) }
                    )
                }
            }
        }
    }

    // Detail bottom sheet
    selectedSkill?.let { skill ->
        SkillDetailSheet(
            skill = skill,
            onDismiss = { selectedSkill = null }
        )
    }
}

@Composable
private fun SkillRow(
    skill: SkillUiItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(skill.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(skill.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(
    skill: SkillUiItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(skill.name, style = MaterialTheme.typography.headlineSmall)
            Text(skill.description, style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider()

            if (skill.tools.isNotEmpty()) {
                Text("工具", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    skill.tools.forEach { tool ->
                        SuggestionChip(onClick = {}, label = { Text(tool) })
                    }
                }
            }

            skill.model?.let {
                Text("模型覆盖", style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Text("来源", style = MaterialTheme.typography.labelLarge)
            Text(
                skill.sourcePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (skill.referenceCount > 0) {
                Text(
                    "已加载 ${skill.referenceCount} 个参考文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("使用方法", style = MaterialTheme.typography.labelLarge)
            Text(
                "在聊天中输入 @${skill.name} 调用此技能",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
