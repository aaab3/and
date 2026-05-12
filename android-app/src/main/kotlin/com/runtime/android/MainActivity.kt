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

            RuntimeApp(
                chatViewModel = chatViewModel,
                conversations = conversations,
                providers = providers,
                skills = skills,
                onSelectConversation = { id -> chatViewModel.loadConversation(id) },
                onNewChat = { lifecycleScope.launch { createNewChat() } },
                onDeleteConversation = { id ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.conversationDao().delete(id) }
                        refreshConversations()
                    }
                },
                onAddProvider = { name, baseUrl, apiKey, modelId ->
                    lifecycleScope.launch { addProvider(name, baseUrl, apiKey, modelId) }
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
                }
            )
        }
    }

    // --- Provider management ---

    private suspend fun addProvider(name: String, baseUrl: String, apiKey: String, modelId: String) {
        val id = UUID.randomUUID().toString()
        val alias = "provider-key-$id"
        withContext(Dispatchers.IO) {
            secrets.putSecret(alias, apiKey)
            val isFirst = db.providerDao().getAll().isEmpty()
            db.providerDao().upsert(
                ProviderEntity(id, name, baseUrl.trimEnd('/'), alias, modelId, isFirst, System.currentTimeMillis())
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
            db.providerDao().upsert(existing.copy(name = item.name, baseUrl = item.baseUrl.trimEnd('/'), modelId = item.modelId))
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
            ProviderUiItem(e.id, e.name, e.baseUrl, key, e.modelId, e.isDefault)
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

        // Get the full body from DB (skillsFlow only has UI items, need the body from Room)
        // For simplicity, we cache the body in a map when refreshing skills
        val body = skillBodies[skillName] ?: return null

        val defaultProvider = runBlocking { db.providerDao().getDefault() } ?: return null
        val secretValue = runBlocking {
            when (val r = secrets.getSecret(defaultProvider.secretAlias)) {
                is AppResult.Success -> r.value
                is AppResult.Failure -> null
            }
        } ?: return null

        // Build tool specs from skill's declared tools
        val toolSpecs = skill.tools.mapNotNull { toolName ->
            when (val t = toolRegistry.getTool(toolName)) {
                is AppResult.Success -> {
                    val m = t.value.manifest
                    ModelToolSpec(name = m.name, description = m.description, inputSchemaJson = m.inputSchemaJson)
                }
                is AppResult.Failure -> null
            }
        }

        val resolver = SimpleBindingResolver(defaultProvider, secretValue)
        val registry = DefaultProviderRegistry(listOf(OpenAiCompatibleModelProvider()))
        return DefaultConversationRuntime(
            conversationStore = store,
            bindingResolver = resolver,
            providerRegistry = registry,
            toolRegistry = toolRegistry,
            systemPrompt = body,
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
        currentRuntime = DefaultConversationRuntime(
            conversationStore = store,
            bindingResolver = resolver,
            providerRegistry = registry,
            toolRegistry = toolRegistry
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
        // Simple approach: use first 20 chars of user message as title
        // A more advanced approach would call the model, but that costs tokens
        val title = userMsg.take(20).trim().ifEmpty { "新对话" }
        withContext(Dispatchers.IO) {
            val entity = db.conversationDao().getById(conversationId) ?: return@withContext
            db.conversationDao().update(entity.copy(title = title))
        }
        chatViewModel.updateTitle(title)
        refreshConversations()
    }
}
