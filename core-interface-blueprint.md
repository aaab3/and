# Runtime 核心接口蓝图 v1

这份文档不是实现说明，而是给开发和模型生成使用的“接口与 DTO 骨架约束”。

目标只有两个：
1. 提前固定核心命名和依赖方向
2. 降低普通模型在多轮实现中把接口写漂移的概率

---

## 0. 统一约束

### 0.1 设计原则
- 先定义边界，再补实现
- 接口优先窄而稳定，不追求一次到位
- 所有跨模块失败统一返回 `AppResult.Failure(AppError(...))`
- 上层不依赖 provider-specific / storage-specific / parser-specific 细节对象
- 不在接口层混入 UI、Android 页面、Compose 状态对象

### 0.2 命名原则
- 读操作优先用 `get` / `list` / `load`
- 写操作优先用 `create` / `save` / `append` / `put` / `delete`
- 运行时入口优先用 `sendMessage` / `execute` / `resolve` / `generate`
- “默认实现”统一前缀 `Default`
- “请求 / 结果”统一后缀 `Request` / `Result`

### 0.3 Kotlin 风格约束
- interface 只声明能力，不携带实现细节
- DTO 用 `data class`
- 可穷举结果优先用 `sealed interface` / `sealed class`
- 非必要不要在 P0 引入继承层级很深的领域对象

---

## 1. Prompt 01 — 公共结果与错误模型

### 1.1 Core types
```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val code: String,
    val message: String,
    val cause: String? = null,
    val retryable: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)
```

### 1.2 Error code convention
```kotlin
object ErrorCodes {
    const val NOT_FOUND = "NOT_FOUND"
    const val INVALID_INPUT = "INVALID_INPUT"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val PROVIDER_ERROR = "PROVIDER_ERROR"
    const val STORAGE_ERROR = "STORAGE_ERROR"
    const val PARSE_ERROR = "PARSE_ERROR"
    const val EXECUTION_ERROR = "EXECUTION_ERROR"
}
```

---

## 2. Prompt 02 — Conversation / Message 数据层

### 2.1 Domain models
```kotlin
data class Conversation(
    val id: String,
    val workspaceId: String,
    val title: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class ConversationMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAtEpochMs: Long,
    val toolCallId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
```

### 2.2 Repository boundaries
```kotlin
interface ConversationRepository {
    suspend fun createConversation(conversation: Conversation): AppResult<Conversation>
    suspend fun getConversation(conversationId: String): AppResult<Conversation?>
    suspend fun updateConversation(conversation: Conversation): AppResult<Unit>
}

interface MessageRepository {
    suspend fun appendMessage(message: ConversationMessage): AppResult<ConversationMessage>
    suspend fun listMessages(conversationId: String): AppResult<List<ConversationMessage>>
}
```

### 2.3 Store boundary
```kotlin
interface ConversationStore {
    suspend fun createConversation(
        workspaceId: String,
        title: String?
    ): AppResult<Conversation>

    suspend fun getConversation(
        conversationId: String
    ): AppResult<Conversation?>

    suspend fun appendUserMessage(
        conversationId: String,
        content: String
    ): AppResult<ConversationMessage>

    suspend fun appendAssistantMessage(
        conversationId: String,
        content: String
    ): AppResult<ConversationMessage>

    suspend fun loadMessageHistory(
        conversationId: String
    ): AppResult<List<ConversationMessage>>
}
```

---

## 3. Prompt 03 — Provider / Credential / Model / Binding 数据层

### 3.1 Core models
```kotlin
enum class ProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC_LIKE,
    GEMINI_LIKE,
    CUSTOM
}

data class ProviderDefinition(
    val id: String,
    val providerType: ProviderType,
    val name: String,
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val capabilities: Set<String> = emptySet()
)

data class CredentialRef(
    val id: String,
    val providerId: String,
    val displayName: String,
    val secretAlias: String
)

data class ModelProfile(
    val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val capabilities: Set<String> = emptySet()
)

enum class BindingTargetType {
    GLOBAL,
    WORKSPACE,
    SKILL,
    CONVERSATION
}

data class ModelBinding(
    val id: String,
    val targetType: BindingTargetType,
    val targetId: String?,
    val providerId: String,
    val modelProfileId: String,
    val credentialRefId: String,
    val priority: Int = 0
)
```

### 3.2 Repository boundaries
```kotlin
interface ProviderDefinitionRepository {
    suspend fun getProvider(providerId: String): AppResult<ProviderDefinition?>
    suspend fun listProviders(): AppResult<List<ProviderDefinition>>
}

interface CredentialRefRepository {
    suspend fun getCredentialRef(credentialRefId: String): AppResult<CredentialRef?>
    suspend fun listCredentialRefs(providerId: String): AppResult<List<CredentialRef>>
}

interface ModelProfileRepository {
    suspend fun getModelProfile(modelProfileId: String): AppResult<ModelProfile?>
    suspend fun listModelProfiles(providerId: String): AppResult<List<ModelProfile>>
}

interface ModelBindingRepository {
    suspend fun findBindings(
        targetType: BindingTargetType,
        targetId: String?
    ): AppResult<List<ModelBinding>>
}
```

---

## 4. Prompt 04 — SecretProvider

### 4.1 Secret model
```kotlin
data class SecretValue(
    val alias: String,
    val value: String
)
```

### 4.2 Boundary
```kotlin
interface SecretProvider {
    suspend fun putSecret(
        alias: String,
        value: String
    ): AppResult<Unit>

    suspend fun getSecret(
        alias: String
    ): AppResult<String>

    suspend fun deleteSecret(
        alias: String
    ): AppResult<Unit>
}
```

---

## 5. Prompt 05 — BindingResolver

### 5.1 Request context
```kotlin
data class BindingResolveRequest(
    val workspaceId: String,
    val skillId: String? = null,
    val conversationId: String? = null
)
```

### 5.2 Resolved binding
```kotlin
data class ResolvedModelBinding(
    val binding: ModelBinding,
    val provider: ProviderDefinition,
    val model: ModelProfile,
    val credentialRef: CredentialRef,
    val secretValue: String
)
```

### 5.3 Boundary
```kotlin
interface BindingResolver {
    suspend fun resolve(
        request: BindingResolveRequest
    ): AppResult<ResolvedModelBinding>
}
```

### 5.4 Recommended default implementation deps
```kotlin
class DefaultBindingResolver(
    private val bindingRepository: ModelBindingRepository,
    private val providerRepository: ProviderDefinitionRepository,
    private val modelRepository: ModelProfileRepository,
    private val credentialRepository: CredentialRefRepository,
    private val secretProvider: SecretProvider
) : BindingResolver
```

---

## 6. Prompt 06 / 07 — ModelProvider / ProviderRegistry

### 6.1 Unified model request/response
```kotlin
data class ModelMessage(
    val role: String,
    val content: String,
    val toolCallId: String? = null
)

data class ModelToolSpec(
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

data class ModelToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class ModelGenerateRequest(
    val messages: List<ModelMessage>,
    val tools: List<ModelToolSpec> = emptyList(),
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class ModelUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null
)

data class ModelGenerateResult(
    val text: String,
    val toolCalls: List<ModelToolCall> = emptyList(),
    val finishReason: String? = null,
    val usage: ModelUsage? = null,
    val rawProviderMessageId: String? = null
)
```

### 6.2 Provider boundary
```kotlin
interface ModelProvider {
    val providerType: ProviderType

    suspend fun generate(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): AppResult<ModelGenerateResult>
}
```

### 6.3 Registry boundary
```kotlin
interface ProviderRegistry {
    fun getProvider(providerType: ProviderType): AppResult<ModelProvider>
    fun listProviders(): List<ModelProvider>
}
```

### 6.4 Suggested skeleton providers
```kotlin
class OpenAiCompatibleModelProvider : ModelProvider
class AnthropicLikeModelProvider : ModelProvider
class GeminiLikeModelProvider : ModelProvider
```

---

## 7. Prompt 08 — ConversationRuntime / SendMessage

### 7.1 Request/result
```kotlin
data class SendMessageRequest(
    val workspaceId: String,
    val conversationId: String,
    val userMessage: String,
    val skillId: String? = null
)

data class SendMessageResult(
    val conversation: Conversation,
    val userMessage: ConversationMessage,
    val assistantMessage: ConversationMessage,
    val providerResult: ModelGenerateResult
)
```

### 7.2 Boundary
```kotlin
interface ConversationRuntime {
    suspend fun sendMessage(
        request: SendMessageRequest
    ): AppResult<SendMessageResult>
}
```

### 7.3 Recommended default implementation deps
```kotlin
class DefaultConversationRuntime(
    private val conversationStore: ConversationStore,
    private val bindingResolver: BindingResolver,
    private val providerRegistry: ProviderRegistry
) : ConversationRuntime
```

---

## 8. Prompt 09 — SkillPackageLoader

### 8.1 Models
```kotlin
sealed interface SkillPackageSource {
    data class Directory(val path: String) : SkillPackageSource
    data class ZipFile(val path: String) : SkillPackageSource
}

data class SkillManifest(
    val name: String,
    val description: String,
    val version: String,
    val workflow: String,
    val inputs: Map<String, String> = emptyMap(),
    val defaults: Map<String, String> = emptyMap(),
    val tools: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val model: String? = null,
    val output: Map<String, String> = emptyMap()
)

data class LoadedSkillPackage(
    val manifest: SkillManifest,
    val markdownBody: String,
    val workflowFilePath: String,
    val assetPaths: List<String> = emptyList()
)
```

### 8.2 Boundary
```kotlin
interface SkillPackageLoader {
    suspend fun load(
        source: SkillPackageSource
    ): AppResult<LoadedSkillPackage>
}
```

---

## 9. Prompt 10 / 11 — WorkflowParser / WorkflowValidator

### 9.1 Step types
```kotlin
enum class WorkflowStepType {
    TEMPLATE,
    CALL_MODEL,
    CALL_TOOL,
    RETRIEVE_KNOWLEDGE,
    BRANCH,
    RETURN
}
```

### 9.2 Parsed workflow model
```kotlin
data class ParsedWorkflowDefinition(
    val id: String,
    val name: String,
    val version: String,
    val steps: List<WorkflowStep>
)

sealed interface WorkflowStep {
    val id: String
    val type: WorkflowStepType
}

data class TemplateStep(
    override val id: String,
    val template: String,
    val outputKey: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.TEMPLATE
}

data class CallModelStep(
    override val id: String,
    val inputKey: String,
    val outputKey: String,
    val bindingRef: String? = null,
    val toolNames: List<String> = emptyList()
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.CALL_MODEL
}

data class CallToolStep(
    override val id: String,
    val toolName: String,
    val inputKey: String,
    val outputKey: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.CALL_TOOL
}

data class RetrieveKnowledgeStep(
    override val id: String,
    val queryKey: String,
    val outputKey: String,
    val knowledgeScopeId: String? = null
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.RETRIEVE_KNOWLEDGE
}

data class BranchCase(
    val equals: String,
    val nextStepId: String
)

data class BranchStep(
    override val id: String,
    val inputKey: String,
    val cases: List<BranchCase>,
    val defaultNextStepId: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.BRANCH
}

data class ReturnStep(
    override val id: String,
    val outputKey: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.RETURN
}
```

### 9.3 Boundaries
```kotlin
interface WorkflowParser {
    suspend fun parse(
        yamlContent: String
    ): AppResult<ParsedWorkflowDefinition>
}

interface WorkflowValidator {
    suspend fun validate(
        workflow: ParsedWorkflowDefinition
    ): AppResult<Unit>
}
```

---

## 10. Prompt 12 / 13 — ToolRegistry / PermissionGate

### 10.1 Tool models
```kotlin
data class ToolManifest(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
    val outputSchemaJson: String,
    val requiresUserConfirmation: Boolean = false,
    val timeoutMs: Long = 30_000
)

data class ToolExecutionRequest(
    val toolName: String,
    val argumentsJson: String,
    val conversationId: String? = null,
    val skillId: String? = null,
    val workspaceId: String? = null
)

data class ToolExecutionResult(
    val outputJson: String,
    val metadata: Map<String, String> = emptyMap()
)
```

### 10.2 Handler boundary
```kotlin
interface ToolHandler {
    val manifest: ToolManifest

    suspend fun execute(
        request: ToolExecutionRequest
    ): AppResult<ToolExecutionResult>
}
```

### 10.3 Registry boundary
```kotlin
interface ToolRegistry {
    fun register(handler: ToolHandler): AppResult<Unit>
    fun getTool(name: String): AppResult<ToolHandler>
    fun listTools(): List<ToolManifest>
}
```

### 10.4 Permission models
```kotlin
data class PermissionRequest(
    val toolName: String,
    val conversationId: String? = null,
    val skillId: String? = null,
    val workspaceId: String? = null,
    val requiresUserConfirmation: Boolean = false
)

sealed interface PermissionDecision {
    data object Allow : PermissionDecision
    data class Deny(val reason: String) : PermissionDecision
    data class RequiresUserConfirmation(val reason: String) : PermissionDecision
}
```

### 10.5 Permission boundary
```kotlin
interface PermissionGate {
    suspend fun evaluate(
        request: PermissionRequest
    ): AppResult<PermissionDecision>
}
```

---

## 11. Prompt 14 — Knowledge base / RAG minimal boundary

### 11.1 Models
```kotlin
data class ImportedDocument(
    val id: String,
    val sourcePath: String,
    val title: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap()
)

data class DocumentChunk(
    val id: String,
    val documentId: String,
    val text: String,
    val index: Int,
    val metadata: Map<String, String> = emptyMap()
)

data class EmbeddedChunk(
    val chunk: DocumentChunk,
    val vector: List<Float>
)

data class RetrievedChunk(
    val chunkId: String,
    val documentId: String,
    val text: String,
    val score: Double,
    val sourcePath: String,
    val metadata: Map<String, String> = emptyMap()
)
```

### 11.2 Boundaries
```kotlin
interface DocumentImporter {
    suspend fun importDocument(path: String): AppResult<ImportedDocument>
}

interface Chunker {
    suspend fun chunk(document: ImportedDocument): AppResult<List<DocumentChunk>>
}

interface EmbeddingProvider {
    suspend fun embed(texts: List<String>): AppResult<List<List<Float>>>
}

interface VectorIndex {
    suspend fun upsert(chunks: List<EmbeddedChunk>): AppResult<Unit>
    suspend fun search(queryVector: List<Float>, topK: Int): AppResult<List<RetrievedChunk>>
}

interface KnowledgeRetriever {
    suspend fun retrieve(
        query: String,
        topK: Int = 5
    ): AppResult<List<RetrievedChunk>>
}
```

---

## 12. Prompt 15 — WorkflowExecutor

### 12.1 Execution models
```kotlin
data class WorkflowExecutionRequest(
    val workflow: ParsedWorkflowDefinition,
    val workspaceId: String,
    val conversationId: String? = null,
    val skillId: String? = null,
    val inputs: Map<String, String> = emptyMap()
)

data class WorkflowExecutionResult(
    val output: String,
    val context: Map<String, String>
)
```

### 12.2 Boundary
```kotlin
interface WorkflowExecutor {
    suspend fun execute(
        request: WorkflowExecutionRequest
    ): AppResult<WorkflowExecutionResult>
}
```

### 12.3 Recommended default implementation deps
```kotlin
class DefaultWorkflowExecutor(
    private val workflowValidator: WorkflowValidator,
    private val bindingResolver: BindingResolver,
    private val providerRegistry: ProviderRegistry,
    private val toolRegistry: ToolRegistry,
    private val permissionGate: PermissionGate,
    private val knowledgeRetriever: KnowledgeRetriever
) : WorkflowExecutor
```

---

## 13. 依赖方向总表

### 13.1 聊天主链
```text
ConversationRuntime
-> ConversationStore
-> BindingResolver
-> ProviderRegistry
-> ModelProvider
```

### 13.2 技能定义链
```text
SkillPackageLoader
-> WorkflowParser
-> WorkflowValidator
```

### 13.3 技能执行链
```text
WorkflowExecutor
-> WorkflowValidator
-> BindingResolver
-> ProviderRegistry
-> ToolRegistry
-> PermissionGate
-> KnowledgeRetriever
```

### 13.4 必须避免的反向依赖
- `ConversationStore` 不依赖 `ModelProvider`
- `WorkflowParser` 不依赖 `WorkflowExecutor`
- `ToolHandler` 不依赖 `PermissionGate` 做自授权
- `ModelProvider` 不依赖 UI / ViewModel
- `SecretProvider` 不暴露底层安全存储 SDK 细节

---

## 14. 给普通模型的使用方式

建议实际开发时严格按下面顺序使用这份蓝图：

1. 先让模型只实现 Prompt 01，并要求名字严格对齐本文档
2. 再实现 Prompt 02~05，禁止自行扩字段
3. 再实现 Prompt 06~08，禁止加入 streaming / retry / cache
4. 再实现 Prompt 09~11，禁止 parser/validator/executor 混写
5. 再实现 Prompt 12~15，禁止新增 shell/script/loop 扩展

如果模型产出和这里的接口名明显漂移，优先回滚命名，不要继续叠实现。
