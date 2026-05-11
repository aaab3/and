# 投喂版 v1

下面这份是可以直接发给别的模型执行的任务文本骨架。目标是：
- 每一题范围收紧
- 依赖写清
- 非目标写死
- 验收方式明确
- 降低普通模型乱扩展概率

## Prompt 01
### 公共结果类型与错误模型

#### Task
Implement the shared result/error foundation used by all core modules.

#### Requirements
Must include:
- `AppResult<out T>`
- `AppError`
- success/failure modeling
- optional metadata support on errors
- a small, stable error-code convention

#### Constraints
- Do not add networking logic
- Do not add UI logic
- Do not add repository implementations
- Do not throw raw exceptions to upper layers
- Keep the design minimal and reusable

#### Suggested shape
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

#### Acceptance criteria
- Shared modules can depend on this without extra adapters
- Failures are represented structurally
- No raw exception contract leaks to callers

## Prompt 02
### Conversation / Message 数据层

#### Task
Implement the persistence layer for conversations and messages, plus a `ConversationStore` abstraction.

#### Requirements
Must include:
- conversation entity/model
- message entity/model
- conversation repository or DAO boundary
- message repository or DAO boundary
- `ConversationStore`
- create/get conversation
- append user message
- append assistant message
- load ordered message history

#### Constraints
- Do not implement provider calling
- Do not implement binding logic
- Do not implement UI
- Do not implement workflow logic
- Keep persistence separate from runtime orchestration

#### Acceptance criteria
- Conversations can be created and queried
- Ordered message history can be loaded
- User/assistant messages can be appended independently
- Failures return `AppResult.Failure(AppError(...))`

## Prompt 03
### Provider / Credential / Model / Binding 数据层

#### Task
Implement the local data-layer abstractions for provider definitions, credential references, model profiles, and model bindings.

#### Requirements
Must include:
- `ProviderDefinition`
- `CredentialRef`
- `ModelProfile`
- `ModelBinding`
- repository interfaces for each
- support for scope-based binding lookup inputs

#### Constraints
- Do not store real secrets in normal DB entities
- Do not implement provider HTTP calls
- Do not implement binding resolution yet
- Do not add speculative fields unrelated to current MVP

#### Acceptance criteria
- Provider/model/binding configuration can be stored and queried
- Secret values are not stored directly in these entities
- Repositories are narrow and runtime-safe

## Prompt 04
### SecretProvider

#### Task
Implement the secure-secret abstraction used to store and retrieve real API keys or tokens.

#### Requirements
Must include:
- `SecretProvider`
- `putSecret`
- `getSecret`
- `deleteSecret`
- secure-storage-facing abstraction
- integration boundary using `credentialRefId -> secret alias/value`

#### Constraints
- Do not implement provider calling
- Do not implement binding resolution
- Do not put secrets into Room/business entities
- Keep the API narrow

#### Acceptance criteria
- Secrets can be stored, read, and deleted through one abstraction
- Callers never need direct secure-storage details
- Missing secrets return structured failures

## Prompt 05
### BindingResolver

#### Task
Implement the binding-resolution layer that resolves the final model binding for a workspace/skill/conversation context.

#### Requirements
Must include:
- `BindingResolver`
- `DefaultBindingResolver`
- `ResolvedModelBinding`
- binding priority fallback
- provider/model/credential resolution
- secret retrieval through `SecretProvider`

#### Constraints
- Do not implement provider HTTP invocation
- Do not build chat requests
- Do not execute workflows
- Do not store real secrets in DB entities
- Return failures structurally

#### Acceptance criteria
- Fixed scope priority works correctly
- Fallback works correctly
- Missing binding/provider/model/credential/secret returns explicit failure
- Runtime receives a normalized resolved binding

## Prompt 06
### OpenAI-compatible ModelProvider

#### Task
Implement one fully working OpenAI-compatible model provider using the unified internal request/response shape.

#### Requirements
Must include:
- unified request model
- unified response model
- `ModelProvider`
- an OpenAI-compatible provider implementation
- mapping from internal request to provider payload
- mapping from provider response to internal result
- text-only non-streaming generation
- tool declaration sending
- tool call parsing

#### Constraints
- Do not implement chat runtime
- Do not implement streaming
- Do not implement retries/caching/event bus
- Do not execute tools
- Do not expose provider-specific objects to upper layers

#### Acceptance criteria
- Can accept `ResolvedModelBinding + ModelGenerateRequest`
- Can call an OpenAI-compatible endpoint
- Can parse text, finish reason, usage, and tool calls
- All failures use `AppResult.Failure(AppError(...))`

## Prompt 07
### ProviderRegistry + provider skeletons

#### Task
Implement `ProviderRegistry` and add Anthropic-like / Gemini-like provider skeletons or explicit not-implemented providers.

#### Requirements
Must include:
- `ProviderRegistry`
- `DefaultProviderRegistry`
- lookup by `providerType`
- one registered OpenAI-compatible provider
- Anthropic-like provider skeleton or explicit `NOT_IMPLEMENTED`
- Gemini-like provider skeleton or explicit `NOT_IMPLEMENTED`

#### Constraints
- Do not implement fallback provider switching
- Do not build chat runtime
- Do not add streaming
- Do not hide provider construction inside random runtime classes

#### Acceptance criteria
- Registry returns correct provider by type
- Unknown provider type returns failure at caller boundary
- Non-implemented providers fail explicitly and clearly

## Prompt 08
### ConversationRuntime / SendMessage

#### Task
Implement the minimal chat send-message loop.

#### Requirements
Must include:
- `ConversationRuntime`
- `sendMessage`
- `SendMessageRequest`
- `SendMessageResult`
- conversation existence check
- persist user message first
- load current history
- resolve binding
- resolve provider from registry
- call provider
- persist assistant message on success only

#### Constraints
- Do not implement workflow execution
- Do not implement tool loop
- Do not implement RAG injection
- Do not implement streaming
- Do not auto-retry
- Do not create a giant orchestration class

#### Acceptance criteria
- User message is persisted before model invocation
- Current message appears in model history
- Assistant message is written only on success
- User message remains persisted if provider call fails
- All failures are structured

## Prompt 09
### SkillPackageLoader

#### Task
Implement skill package loading and installation from directory and ZIP sources.

#### Requirements
Must include:
- `SkillPackageLoader`
- `SkillPackageSource`
- directory source support
- ZIP source support
- `skill.md` parsing
- frontmatter parsing
- `workflow.yaml` lookup
- normalized `LoadedSkillPackage`
- install persistence boundary

#### Constraints
- Do not execute workflows
- Do not invoke providers
- Do not execute tools
- Do not allow arbitrary scripts/hooks/code execution
- Do not treat markdown body as executable workflow

#### Acceptance criteria
- Directory and ZIP both work
- `skill.md` is required
- workflow reference is validated
- invalid packages fail clearly
- normalized package object is produced before persistence

## Prompt 10
### Workflow Parser

#### Task
Implement parsing of `workflow.yaml` into a normalized internal workflow definition.

#### Requirements
Must include:
- `WorkflowParser`
- parsed workflow model
- step parsing for supported step types
- conversion from YAML to internal step objects

#### Constraints
- Do not execute workflows
- Do not validate via execution side effects
- Do not support arbitrary scripts or expression engines
- Support only explicit P0 step types

#### Acceptance criteria
- Valid YAML becomes normalized workflow structure
- Unsupported step types fail structurally
- No execution happens during parsing

## Prompt 11
### Workflow Validator

#### Task
Implement validation for parsed workflow definitions.

#### Requirements
Must include:
- `WorkflowValidator`
- required field checks
- duplicate id detection
- reference validation
- branch validation
- terminal/return validation

#### Constraints
- Do not execute workflows
- Do not add loops/parallel/script support
- Keep validator separate from executor

#### Acceptance criteria
- Invalid structure fails before execution
- Duplicate ids are rejected
- Missing required fields are rejected
- Invalid references are rejected
- Missing terminal return is rejected

## Prompt 12
### ToolRegistry

#### Task
Implement the unified tool-registration layer.

#### Requirements
Must include:
- `ToolManifest`
- `ToolHandler`
- `ToolRegistry`
- `DefaultToolRegistry`
- lookup by tool name
- list registered tools

#### Constraints
- Do not execute tools outside registry path
- Do not add arbitrary shell/script tools
- Do not mix permission logic into every handler manually

#### Acceptance criteria
- Tools can be registered and looked up cleanly
- Registry is the single tool-entry boundary

## Prompt 13
### PermissionGate

#### Task
Implement the permission evaluation layer used before tool execution.

#### Requirements
Must include:
- `PermissionGate`
- permission request model
- permission decision model
- allow / deny semantics
- support for “requires user confirmation” metadata

#### Constraints
- Do not implement UI dialog flow here
- Do not let tools self-authorize
- Do not bypass permission checks

#### Acceptance criteria
- Permission decisions are explicit and structured
- Denied tools cannot execute through normal path
- Core permission logic stays separate from UI

## Prompt 14
### Knowledge base / RAG minimal boundary

#### Task
Implement the minimal local knowledge ingestion and retrieval abstractions.

#### Requirements
Must include:
- `DocumentImporter`
- `Chunker`
- `EmbeddingProvider`
- `VectorIndex`
- `KnowledgeRetriever`
- source-preserving retrieval result models
- support at least `txt` and `md`

#### Constraints
- Do not auto-inject retrieval into chat runtime
- Do not hardwire one embedding backend everywhere
- Do not add cloud sync/collab/distributed indexing
- Keep importer/chunker/embedder/index/retriever separated

#### Acceptance criteria
- Documents can be imported and chunked
- Chunks can be embedded and indexed
- Retrieval returns chunks with source metadata
- Failures are structured

## Prompt 15
### WorkflowExecutor

#### Task
Implement the minimal workflow execution engine for validated workflows.

#### Requirements
Must include:
- `WorkflowExecutor`
- execution request/result models
- explicit execution context map
- support for:
  - `template`
  - `call_model`
  - `call_tool`
  - `retrieve_knowledge`
  - `branch`
  - `return`
- provider usage through `BindingResolver + ProviderRegistry`
- tool usage through `ToolRegistry + PermissionGate`
- retrieval usage through `KnowledgeRetriever`

#### Constraints
- Do not parse raw YAML here
- Do not add arbitrary loops
- Do not add parallel execution
- Do not turn executor into autonomous agent runtime
- Do not hardcode provider/tool branching directly

#### Acceptance criteria
- Executes validated workflows deterministically
- Step outputs are stored in explicit context
- Step failures stop execution
- Final output is returned structurally
- No hidden magic state is required

## 推荐投喂顺序
1. Prompt 01
2. Prompt 02
3. Prompt 03
4. Prompt 04
5. Prompt 05
6. Prompt 06
7. Prompt 07
8. Prompt 08
9. Prompt 09
10. Prompt 10
11. Prompt 11
12. Prompt 12
13. Prompt 13
14. Prompt 14
15. Prompt 15

## 最重要的投喂原则
1. 一次只喂一个 Prompt
2. 每次都保留 Constraints
3. 每次生成后都按 Acceptance criteria 对照验收
4. 允许 stub，但只允许规格明确允许的 stub
