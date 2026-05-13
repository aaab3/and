package com.runtime.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

data class ProviderUiItem(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val isDefault: Boolean,
    val systemPrompt: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    providers: List<ProviderUiItem>,
    onAddProvider: (name: String, baseUrl: String, apiKey: String, modelId: String, systemPrompt: String) -> Unit,
    onEditProvider: (ProviderUiItem) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onFetchModels: suspend (baseUrl: String, apiKey: String) -> List<String> = { _, _ -> emptyList() },
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderUiItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Provider section header
            Text(
                text = "模型服务",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            if (providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "还没有配置模型",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "点击 + 添加一个",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                providers.forEach { provider ->
                    ProviderRow(
                        provider = provider,
                        onEdit = { editingProvider = provider },
                        onDelete = { onDeleteProvider(provider.id) },
                        onSetDefault = { onSetDefault(provider.id) }
                    )
                }
            }

            // Future sections
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = "即将推出",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            ListItem(
                headlineContent = { Text("技能") },
                supportingContent = { Text("导入 Markdown 技能文件") },
                leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text("工具") },
                supportingContent = { Text("内置工具配置") },
                leadingContent = { Icon(Icons.Default.Build, contentDescription = null) }
            )
        }
    }

    // Add dialog
    if (showAddDialog) {
        ProviderEditorDialog(
            title = "添加模型服务",
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, url, key, model, prompt ->
                onAddProvider(name, url, key, model, prompt)
                showAddDialog = false
            },
            onFetchModels = onFetchModels
        )
    }

    // Edit dialog
    editingProvider?.let { provider ->
        ProviderEditorDialog(
            title = "编辑模型服务",
            initial = provider,
            onDismiss = { editingProvider = null },
            onSave = { name, url, key, model, prompt ->
                onEditProvider(provider.copy(name = name, baseUrl = url, apiKey = key, modelId = model, systemPrompt = prompt))
                editingProvider = null
            },
            onFetchModels = onFetchModels
        )
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderUiItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.name)
                if (provider.isDefault) {
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("默认", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        },
        supportingContent = {
            Text("${provider.modelId} · ${provider.baseUrl.take(40)}")
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { showMenu = false; onEdit() }
                    )
                    if (!provider.isDefault) {
                        DropdownMenuItem(
                            text = { Text("设为默认") },
                            onClick = { showMenu = false; onSetDefault() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onEdit)
    )
}

@Composable
private fun ProviderEditorDialog(
    title: String,
    initial: ProviderUiItem?,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, apiKey: String, modelId: String, systemPrompt: String) -> Unit,
    onFetchModels: suspend (baseUrl: String, apiKey: String) -> List<String> = { _, _ -> emptyList() }
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var modelId by remember { mutableStateOf(initial?.modelId ?: "gpt-4o-mini") }
    var systemPrompt by remember { mutableStateOf(initial?.systemPrompt ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("我的模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
                // Model ID with refresh button
                Box {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("gpt-4o-mini") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (isLoadingModels) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = {
                                    if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                                        isLoadingModels = true
                                        scope.launch {
                                            modelList = onFetchModels(baseUrl.trim(), apiKey.trim())
                                            isLoadingModels = false
                                            if (modelList.isNotEmpty()) showModelDropdown = true
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "获取模型列表")
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false }
                    ) {
                        modelList.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, style = MaterialTheme.typography.bodySmall) },
                                onClick = { modelId = model; showModelDropdown = false }
                            )
                        }
                    }
                }
                // System Prompt
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词（可选）") },
                    placeholder = { Text("你是一个简洁的助手，回答用中文") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), baseUrl.trim(), apiKey.trim(), modelId.trim(), systemPrompt.trim()) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank() && modelId.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
