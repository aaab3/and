package com.runtime.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.runtime.android.db.AppDatabase
import com.runtime.android.db.ProviderEntity
import com.runtime.android.db.SkillEntity
import com.runtime.android.db.ScheduledTaskEntity
import com.runtime.android.db.RoomConversationRepository
import com.runtime.android.db.RoomMessageRepository
import com.runtime.android.security.AndroidSecretProvider
import com.runtime.android.ui.RuntimeApp
import com.runtime.android.ui.chat.ChatViewModel
import com.runtime.android.ui.chatlist.ConversationItem
import com.runtime.android.ui.settings.ProviderUiItem
import com.runtime.android.ui.skills.SkillUiItem
import com.runtime.binding.*
import com.runtime.conversation.ConversationRuntime
import com.runtime.conversation.DefaultConversationRuntime
import com.runtime.conversation.DefaultConversationStore
import com.runtime.core.AppResult
import com.runtime.model.DefaultProviderRegistry
import com.runtime.model.ModelToolSpec
import com.runtime.model.openai.OpenAiCompatibleModelProvider
import com.runtime.skill.DefaultSkillPackageLoader
import com.runtime.skill.SkillPackageSource
import com.runtime.tool.DefaultToolRegistry
import com.runtime.tool.builtin.CurrentTimeTool
import com.runtime.tool.builtin.HttpFetchTool
import com.runtime.tool.builtin.ReadTextFileTool
import com.runtime.tool.builtin.WebSearchTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private lateinit var secrets: AndroidSecretProvider
    private lateinit var store: DefaultConversationStore
    private val skillLoader = DefaultSkillPackageLoader()
    private val toolRegistry = DefaultToolRegistry()

    private val providersFlow = MutableStateFlow<List<ProviderUiItem>>(emptyList())
    private val conversationsFlow = MutableStateFlow<List<ConversationItem>>(emptyList())
    private val skillsFlow = MutableStateFlow<List<SkillUiItem>>(emptyList())
    private val promptTemplatesFlow = MutableStateFlow<List<com.runtime.android.ui.chat.PromptTemplate>>(emptyList())
    private val scheduledTasksFlow = MutableStateFlow<List<com.runtime.android.ui.tasks.ScheduledTaskUiItem>>(emptyList())
    private val skillBodies = mutableMapOf<String, String>()

    private var currentRuntime: ConversationRuntime? = null
    private lateinit var chatViewModel: ChatViewModel

    // File picker for single .md skill
    private val pickSkillFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importSkillFromUri(it, isFolder = false) } }

    // Folder picker for skill directory
    private val pickSkillFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { importSkillFromUri(it, isFolder = true) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        db = AppDatabase.get(this)
        secrets = AndroidSecretProvider(this)
        val convRepo = RoomConversationRepository(db)
        val msgRepo = RoomMessageRepository(db)
        store = DefaultConversationStore(convRepo, msgRepo)

        // Register built-in tools
        toolRegistry.register(CurrentTimeTool())
        toolRegistry.register(HttpFetchTool())
        toolRegistry.register(ReadTextFileTool())
        toolRegistry.register(WebSearchTool())

        chatViewModel = ChatViewModel(
            runtimeProvider = { currentRuntime },
            skillRuntimeProvider = { skillName -> buildSkillRuntime(skillName) },
            store = store,
            onTitleGenerated = { conversationId, userMsg, assistantMsg ->
                lifecycleScope.launch { generateTitle(conversationId, userMsg, assistantMsg) }
            },
            onMessageDeleted = { messageId ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.messageDao().deleteById(messageId) }
                }
            }
        )

        lifecycleScope.launch {
            refreshProviders()
            refreshConversations()
            refreshSkills()
            refreshTasks()
            refreshPromptTemplates()
            rebuildRuntime()

            val convs = conversationsFlow.value
            if (convs.isNotEmpty()) {
                chatViewModel.loadConversation(convs.first().id)
            } else {
                createNewChat()
            }
        }

        setContent {
            val providers by providersFlow.collectAsState()
            val conversations by conversationsFlow.collectAsState()
            val skills by skillsFlow.collectAsState()
            val templates by promptTemplatesFlow.collectAsState()
            val tasks by scheduledTasksFlow.collectAsState()

            RuntimeApp(
                chatViewModel = chatViewModel,
                conversations = conversations,
                providers = providers,
                skills = skills,
                promptTemplates = templates,
                scheduledTasks = tasks,
                availableTools = toolRegistry.listTools().map { it.name },
                onSelectConversation = { id -> chatViewModel.loadConversation(id) },
                onNewChat = { lifecycleScope.launch { createNewChat() } },
                onDeleteConversation = { id ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.conversationDao().delete(id) }
                        refreshConversations()
                    }
                },
                onAddProvider = { name, baseUrl, apiKey, modelId, systemPrompt ->
                    lifecycleScope.launch { addProvider(name, baseUrl, apiKey, modelId, systemPrompt) }
                },
                onEditProvider = { item ->
                    lifecycleScope.launch { updateProvider(item) }
                },
                onDeleteProvider = { id ->
                    lifecycleScope.launch { deleteProvider(id) }
                },
                onSetDefaultProvider = { id ->
                    lifecycleScope.launch { setDefaultProvider(id) }
                },
                onImportSkillFile = {
                    pickSkillFile.launch(arrayOf("text/*"))
                },
                onImportSkillFolder = {
                    pickSkillFolder.launch(null)
                },
                onDeleteSkill = { name ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.skillDao().delete(name) }
                        refreshSkills()
                    }
                },
                onCreateSkill = { name, desc, body, tools ->
                    lifecycleScope.launch { createSkillInApp(name, desc, body, tools) }
                },
                onAddTask = { name, skillName, prompt, schedule ->
                    lifecycleScope.launch { addScheduledTask(name, skillName, prompt, schedule) }
                },
                onToggleTask = { id, enabled ->
                    lifecycleScope.launch { toggleTask(id, enabled) }
                },
                onDeleteTask = { id ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.scheduledTaskDao().delete(id) }
                        refreshTasks()
                    }
                }
            )
        }
    }

    // --- Provider management ---

    private suspend fun addProvider(name: String, baseUrl: String, apiKey: String, modelId: String, systemPrompt: String) {
        val id = UUID.randomUUID().toString()
        val alias = "provider-key-$id"
        withContext(Dispatchers.IO) {
            secrets.putSecret(alias, apiKey)
            val isFirst = db.providerDao().getAll().isEmpty()
            db.providerDao().upsert(
                ProviderEntity(id, name, baseUrl.trimEnd('/'), alias, modelId, isFirst, systemPrompt, System.currentTimeMillis())
            )
        }
        refreshProviders()
        rebuildRuntime()
    }

    private suspend fun updateProvider(item: ProviderUiItem) {
        val alias = "provider-key-${item.id}"
        withContext(Dispatchers.IO) {
            secrets.putSecret(alias, item.apiKey)
            val existing = db.providerDao().getById(item.id) ?: return@withContext
            db.providerDao().upsert(existing.copy(
                name = item.name,
                baseUrl = item.baseUrl.trimEnd('/'),
                modelId = item.modelId,
                systemPrompt = item.systemPrompt
            ))
        }
        refreshProviders()
        rebuildRuntime()
    }

    private suspend fun deleteProvider(id: String) {
        withContext(Dispatchers.IO) {
            val entity = db.providerDao().getById(id) ?: return@withContext
            secrets.deleteSecret(entity.secretAlias)
            db.providerDao().delete(id)
            if (entity.isDefault) {
                db.providerDao().getAll().firstOrNull()?.let {
                    db.providerDao().upsert(it.copy(isDefault = true))
                }
            }
        }
        refreshProviders()
        rebuildRuntime()
    }

    private suspend fun setDefaultProvider(id: String) {
        withContext(Dispatchers.IO) {
            db.providerDao().clearDefault()
            db.providerDao().getById(id)?.let { db.providerDao().upsert(it.copy(isDefault = true)) }
        }
        refreshProviders()
        rebuildRuntime()
    }

    // --- Skill management ---

    private fun importSkillFromUri(uri: Uri, isFolder: Boolean) {
        lifecycleScope.launch {
            try {
                if (isFolder) {
                    importSkillFolder(uri)
                } else {
                    importSkillFile(uri)
                }
            } catch (e: Exception) {
                // TODO: show error snackbar
            }
        }
    }

    private suspend fun importSkillFile(uri: Uri) {
        val content = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }
        if (content == null) return

        // Write to internal storage, then load
        val tempFile = java.io.File(filesDir, "temp_skill_${System.currentTimeMillis()}.md")
        withContext(Dispatchers.IO) { tempFile.writeText(content) }

        val result = withContext(Dispatchers.IO) {
            skillLoader.load(SkillPackageSource.MarkdownFile(tempFile.absolutePath))
        }
        tempFile.delete()

        when (result) {
            is AppResult.Success -> persistSkill(result.value)
            is AppResult.Failure -> {} // TODO: show error
        }
    }

    private suspend fun importSkillFolder(treeUri: Uri) {
        val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri) ?: return

        // Create a temp directory and copy all files into it
        val tempDir = java.io.File(filesDir, "temp_skill_dir_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        copyDocumentTree(docFile, tempDir)

        val result = withContext(Dispatchers.IO) {
            skillLoader.load(SkillPackageSource.Directory(tempDir.absolutePath))
        }

        // Clean up temp dir
        tempDir.deleteRecursively()

        when (result) {
            is AppResult.Success -> persistSkill(result.value)
            is AppResult.Failure -> {} // TODO: show error
        }
    }

    private suspend fun copyDocumentTree(docFile: androidx.documentfile.provider.DocumentFile, destDir: java.io.File) {
        withContext(Dispatchers.IO) {
            for (child in docFile.listFiles()) {
                if (child.isDirectory) {
                    val subDir = java.io.File(destDir, child.name ?: "unknown")
                    subDir.mkdirs()
                    copyDocumentTree(child, subDir)
                } else if (child.isFile) {
                    val name = child.name ?: continue
                    val outFile = java.io.File(destDir, name)
                    contentResolver.openInputStream(child.uri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private suspend fun persistSkill(pkg: com.runtime.skill.LoadedSkillPackage) {
        val toolsJson = "[" + pkg.manifest.tools.joinToString(",") { "\"$it\"" } + "]"
        withContext(Dispatchers.IO) {
            db.skillDao().upsert(
                SkillEntity(
                    name = pkg.manifest.name,
                    description = pkg.manifest.description,
                    body = pkg.markdownBody,
                    toolsJson = toolsJson,
                    model = pkg.manifest.model,
                    sourcePath = pkg.sourcePath,
                    referenceCount = pkg.referenceFiles.size,
                    installedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        refreshSkills()
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        return try {
            contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            uri.path
        }
    }

    // --- Refresh helpers ---

    private suspend fun refreshProviders() {
        val entities = withContext(Dispatchers.IO) { db.providerDao().getAll() }
        providersFlow.value = entities.map { e ->
            val key = withContext(Dispatchers.IO) {
                when (val r = secrets.getSecret(e.secretAlias)) {
                    is AppResult.Success -> r.value
                    is AppResult.Failure -> ""
                }
            }
            ProviderUiItem(e.id, e.name, e.baseUrl, key, e.modelId, e.isDefault, e.systemPrompt)
        }
    }

    private suspend fun refreshConversations() {
        val all = withContext(Dispatchers.IO) { db.conversationDao().allForWorkspace("ws-1") }
        conversationsFlow.value = all.map { e ->
            ConversationItem(e.id, e.title ?: "新对话", "", e.updatedAtEpochMs)
        }.sortedByDescending { it.updatedAt }
    }

    private suspend fun refreshSkills() {
        val all = withContext(Dispatchers.IO) { db.skillDao().getAll() }
        skillBodies.clear()
        skillsFlow.value = all.map { e ->
            skillBodies[e.name] = e.body
            val tools = try {
                Json.decodeFromString<List<String>>(e.toolsJson)
            } catch (_: Exception) { emptyList() }
            SkillUiItem(e.name, e.description, tools, e.model, e.sourcePath, e.referenceCount)
        }
    }

    // --- Runtime ---

    private fun buildSkillRuntime(skillName: String): ConversationRuntime? {
        val skills = skillsFlow.value
        val skill = skills.find { it.name == skillName } ?: return null

        val body = skillBodies[skillName] ?: return null

        val defaultProvider = runBlocking { db.providerDao().getDefault() } ?: return null
        val secretValue = runBlocking {
            when (val r = secrets.getSecret(defaultProvider.secretAlias)) {
                is AppResult.Success -> r.value
                is AppResult.Failure -> null
            }
        } ?: return null

        // If skill declared no tools, make ALL registered tools available.
        // This handles Claude-style skills that mention tools in the prompt body
        // but don't declare them in frontmatter.
        val activeToolNames = if (skill.tools.isEmpty()) {
            toolRegistry.listTools().map { it.name }
        } else {
            skill.tools
        }

        // Build tool specs for the model
        val toolSpecs = activeToolNames.mapNotNull { toolName ->
            when (val t = toolRegistry.getTool(toolName)) {
                is AppResult.Success -> {
                    val m = t.value.manifest
                    ModelToolSpec(name = m.name, description = m.description, inputSchemaJson = m.inputSchemaJson)
                }
                is AppResult.Failure -> null
            }
        }

        // Augment system prompt with concise tool instructions
        val augmentedBody = buildString {
            append(body)
            if (toolSpecs.isNotEmpty()) {
                append("\n\n[Tools: ")
                append(toolSpecs.joinToString(", ") { it.name })
                append("]\nCall format: <tool_call><function=NAME><parameter=KEY>VALUE</parameter></function></tool_call>")
            }
        }

        val resolver = SimpleBindingResolver(defaultProvider, secretValue)
        val registry = DefaultProviderRegistry(listOf(OpenAiCompatibleModelProvider()))
        return DefaultConversationRuntime(
            conversationStore = store,
            bindingResolver = resolver,
            providerRegistry = registry,
            toolRegistry = toolRegistry,
            systemPrompt = augmentedBody,
            tools = toolSpecs
        )
    }

    private suspend fun rebuildRuntime() {
        val defaultProvider = withContext(Dispatchers.IO) { db.providerDao().getDefault() } ?: return
        val secretValue = withContext(Dispatchers.IO) {
            when (val r = secrets.getSecret(defaultProvider.secretAlias)) {
                is AppResult.Success -> r.value
                is AppResult.Failure -> null
            }
        } ?: return

        val resolver = SimpleBindingResolver(defaultProvider, secretValue)
        val registry = DefaultProviderRegistry(listOf(OpenAiCompatibleModelProvider()))
        val globalPrompt = defaultProvider.systemPrompt.takeIf { it.isNotBlank() }
        currentRuntime = DefaultConversationRuntime(
            conversationStore = store,
            bindingResolver = resolver,
            providerRegistry = registry,
            toolRegistry = toolRegistry,
            systemPrompt = globalPrompt
        )
    }

    private suspend fun createNewChat() {
        when (val result = withContext(Dispatchers.IO) { store.createConversation("ws-1", null) }) {
            is AppResult.Success -> {
                chatViewModel.loadConversation(result.value.id)
                refreshConversations()
            }
            is AppResult.Failure -> {}
        }
    }

    private suspend fun generateTitle(conversationId: String, userMsg: String, assistantMsg: String) {
        val title = userMsg.take(20).trim().ifEmpty { "新对话" }
        withContext(Dispatchers.IO) {
            val entity = db.conversationDao().getById(conversationId) ?: return@withContext
            db.conversationDao().update(entity.copy(title = title))
        }
        chatViewModel.updateTitle(title)
        refreshConversations()
    }

    // --- Create skill in-app ---

    private suspend fun createSkillInApp(name: String, description: String, body: String, tools: List<String>) {
        val toolsJson = "[" + tools.joinToString(",") { "\"$it\"" } + "]"
        withContext(Dispatchers.IO) {
            db.skillDao().upsert(
                SkillEntity(
                    name = name,
                    description = description,
                    body = body,
                    toolsJson = toolsJson,
                    model = null,
                    sourcePath = "app://created",
                    referenceCount = 0,
                    installedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        refreshSkills()
    }

    // --- Scheduled tasks ---

    private suspend fun addScheduledTask(name: String, skillName: String?, prompt: String, schedule: String) {
        val id = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
            db.scheduledTaskDao().upsert(
                ScheduledTaskEntity(
                    id = id,
                    name = name,
                    skillName = skillName,
                    prompt = prompt,
                    cronExpression = schedule,
                    enabled = true,
                    lastRunEpochMs = null,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        refreshTasks()
        // TODO: schedule with WorkManager
    }

    private suspend fun toggleTask(id: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val entity = db.scheduledTaskDao().getAll().find { it.id == id } ?: return@withContext
            db.scheduledTaskDao().upsert(entity.copy(enabled = enabled))
        }
        refreshTasks()
    }

    private suspend fun refreshTasks() {
        val all = withContext(Dispatchers.IO) { db.scheduledTaskDao().getAll() }
        scheduledTasksFlow.value = all.map { e ->
            com.runtime.android.ui.tasks.ScheduledTaskUiItem(
                id = e.id,
                name = e.name,
                skillName = e.skillName,
                prompt = e.prompt,
                schedule = e.cronExpression,
                enabled = e.enabled,
                lastRun = e.lastRunEpochMs?.let { "已运行" }
            )
        }
    }

    private suspend fun refreshPromptTemplates() {
        val all = withContext(Dispatchers.IO) { db.promptTemplateDao().getAll() }
        promptTemplatesFlow.value = all.map { e ->
            com.runtime.android.ui.chat.PromptTemplate(command = e.command, promptText = e.promptText)
        }
    }
}
