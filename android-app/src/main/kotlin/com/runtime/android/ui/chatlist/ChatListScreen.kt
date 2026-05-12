package com.runtime.android.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

data class ConversationItem(
    val id: String,
    val title: String,
    val lastMessage: String,
    val updatedAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    conversations: List<ConversationItem>,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSkills: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "对话",
                style = MaterialTheme.typography.headlineSmall
            )
            FilledTonalIconButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = "新建对话")
            }
        }

        // Conversation list
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有对话\n点击 + 开始",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { conv ->
                    ConversationRow(
                        item = conv,
                        onClick = { onSelect(conv.id) },
                        onDelete = { onDelete(conv.id) }
                    )
                }
            }
        }

        // Bottom navigation entries
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("技能") },
            leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenSkills)
        )
        ListItem(
            headlineContent = { Text("设置") },
            leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenSettings)
        )
    }
}

@Composable
private fun ConversationRow(
    item: ConversationItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
            text = if (item.title.isBlank()) "未命名" else item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            if (item.lastMessage.isNotBlank()) {
                Text(
                    text = item.lastMessage,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatTime(item.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除对话？") },
            text = { Text("此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun formatTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(epochMs))
    }
}
