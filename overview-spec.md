# 完整开发规格目录 v1

这份目录的目标不是继续发散，而是把前面已经定下的内容收成一套能直接指导开发和拆任务的规格骨架。

## 0. 文档使用原则

这套规格用于指导一个：

- Android-first
- Kotlin 原生
- 一体化产品形态
- 内核分层清晰
- 技能 / 工作流 / 工具 / RAG 可持续扩展

的大模型应用 MVP。

### 核心原则
1. 先做单体产品，不先做完整插件生态。
2. 先做统一 runtime 内核，不做分散特判。
3. 先做有限 DSL，不做任意脚本系统。
4. 先做显式边界，不做魔法式自动扩展。
5. 所有实现任务都必须适配中等能力模型可稳定推进的要求。

## 1. 总体规格结构

### 1.1 架构与范围
1. 产品总体架构
2. MVP 范围与版本边界
3. 模块划分与依赖方向
4. 开发顺序与里程碑

### 1.2 核心数据与运行时
5. 数据模型与数据库草图
6. Provider / Credential / Model Binding
7. SecretProvider
8. BindingResolver
9. ModelProvider / ProviderRegistry
10. ConversationRuntime / SendMessage

### 1.3 技能系统
11. SkillPackageLoader / InstalledSkill
12. Workflow DSL Parser / Validator
13. WorkflowExecutor

### 1.4 工具与知识
14. ToolRegistry + PermissionGate
15. Knowledge Base / RAG minimal boundary

### 1.5 增强项
16. Execution log / debug trace
17. Import / export / backup
18. Settings / admin / management contracts
19. Testing / acceptance pack
20. Delegation prompt pack

## 2. Prompt 08~15 正式目录

### Spec 08. BindingResolver
#### Objective
Resolve the final model binding for a given conversation or skill context.

#### Scope
- binding priority resolution
- provider definition lookup
- model profile lookup
- credential reference lookup
- secret retrieval via `SecretProvider`
- unified `ResolvedModelBinding` output

#### Non-goals
- provider invocation
- chat request construction
- workflow execution
- tool execution
- UI logic
- secret persistence in business DB

#### Required deliverables
- `BindingResolver`
- `DefaultBindingResolver`
- `ResolvedModelBinding`
- binding/provider/model/credential repository interfaces
- structured failure handling
- fallback tests

#### Acceptance criteria
- fixed scope priority works correctly
- fallback works correctly
- real secret only comes from `SecretProvider`
- explicit failure for missing dependency
- no persistence-entity leakage to upper layers

### Spec 09. ModelProvider / ProviderRegistry
#### Objective
Implement the unified model invocation abstraction layer.

#### Scope
- `ModelProvider`
- `ProviderRegistry`
- unified request/response models
- one real OpenAI-compatible provider
- Anthropic-like / Gemini-like skeletons or explicit not-implemented
- text-only non-streaming generation
- tool declaration sending + tool call parsing

#### Non-goals
- chat runtime
- workflow execution
- tool execution
- streaming
- retries/caching/event bus
- multimodal

#### Required deliverables
- `ModelProvider`
- `ProviderRegistry`
- `DefaultProviderRegistry`
- `ModelGenerateRequest`
- `ModelGenerateResult`
- `ModelMessage`
- `ModelToolSpec`
- `ModelToolCall`
- one complete OpenAI-compatible provider
- provider lookup / parsing tests

#### Acceptance criteria
- unified internal request shape
- provider-specific payload translation
- unified response normalization
- tool call parsing retained
- structured failures only

### Spec 10. ConversationRuntime / SendMessage
#### Objective
Implement the minimal chat send-message execution loop.

#### Scope
- persist user message
- load history
- resolve binding
- resolve provider
- call provider
- persist assistant message
- return send result

#### Non-goals
- workflow execution
- tool loop
- RAG injection
- streaming
- auto retry
- giant orchestration runtime

#### Required deliverables
- `ConversationRuntime`
- `DefaultConversationRuntime` or `SendMessageUseCase`
- `SendMessageRequest`
- `SendMessageResult`
- integration with `ConversationStore`
- integration with `BindingResolver`
- integration with `ProviderRegistry`
- success/failure tests

#### Acceptance criteria
- conversation existence checked
- user message written before provider call
- current message included in model history
- assistant message only written after success
- user message preserved on downstream failure

### Spec 11. SkillPackageLoader / InstalledSkill
#### Objective
Implement skill package loading and installation for directory and ZIP sources.

#### Scope
- package structure definition
- `skill.md` parsing
- frontmatter parsing
- `workflow.yaml` location/validation
- normalized package model
- install persistence boundary

#### Non-goals
- workflow execution
- provider invocation
- tool execution
- RAG execution
- plugin code execution
- remote marketplace

#### Required deliverables
- `SkillPackageLoader`
- `DefaultSkillPackageLoader`
- `SkillPackageSource`
- `LoadedSkillPackage`
- `SkillManifest`
- install persistence input model
- installed skill/workflow repositories
- package validation tests

#### Acceptance criteria
- directory and ZIP both supported
- `skill.md` required
- workflow entry required and validated
- markdown body is human-readable content only
- normalized package object produced before persistence
- invalid package rejected cleanly

### Spec 12. Workflow DSL Parser / Validator
#### Objective
Parse and validate `workflow.yaml` into a safe normalized internal workflow model.

#### Scope
- P0 DSL structure
- step parsing
- step validation
- supported step-type enforcement
- id/reference validation
- branch validation
- terminal structure validation

#### Non-goals
- execution
- provider invocation
- arbitrary scripting
- unrestricted loops
- custom expression engine

#### Required deliverables
- `WorkflowParser`
- `WorkflowValidator`
- normalized parsed workflow model
- step models
- parse/validation errors
- parser tests

#### Acceptance criteria
- only allowed step types accepted
- unsupported types rejected
- required fields validated
- duplicate ids rejected
- invalid references rejected
- no execution side effects during parse/validate

### Spec 13. WorkflowExecutor
#### Objective
Execute validated workflows using explicit runtime dependencies and deterministic context passing.

#### Scope
- workflow execution entrypoint
- explicit execution context
- step-by-step execution
- `template`
- `call_model`
- `call_tool`
- `retrieve_knowledge`
- `branch`
- `return`

#### Non-goals
- raw YAML parsing
- arbitrary loops
- arbitrary parallelism
- autonomous recursive agent loops
- hidden global state

#### Required deliverables
- `WorkflowExecutor`
- `DefaultWorkflowExecutor`
- `WorkflowExecutionRequest`
- `WorkflowExecutionResult`
- execution context model
- step execution handlers
- execution tests

#### Acceptance criteria
- executes only validated workflows
- deterministic step behavior
- explicit context map
- step failures stop execution
- final output returned through `AppResult`

### Spec 14. ToolRegistry + PermissionGate
#### Objective
Implement the unified tool registration and permission-check boundary.

#### Scope
- tool manifest
- `ToolHandler`
- `ToolRegistry`
- `PermissionGate`
- permission evaluation
- approved tool execution path

#### Non-goals
- remote plugin ecosystems
- arbitrary shell/script tools
- permission bypass
- UI permission dialog implementation unless explicitly requested

#### Required deliverables
- `ToolManifest`
- `ToolHandler`
- `ToolRegistry`
- `DefaultToolRegistry`
- `PermissionGate`
- tool execution request/result
- permission decision model
- tool lookup/allow/deny/failure tests

#### Acceptance criteria
- all tool execution goes through registry
- permission evaluated before execution
- deny blocks execution
- missing tool is explicit failure
- execution failure returned structurally

### Spec 15. Knowledge Base / RAG minimal boundary
#### Objective
Implement the minimal local knowledge ingestion and retrieval boundary.

#### Scope
- document import
- chunking
- embedding abstraction
- vector index abstraction
- retrieval abstraction
- source metadata preservation
- retrieval result return

#### Non-goals
- cloud sync
- distributed retrieval
- automatic chat injection in this prompt
- advanced reranking
- hardwiring one embedding source into everything

#### Required deliverables
- `DocumentImporter`
- `Chunker`
- `EmbeddingProvider`
- `VectorIndex`
- `KnowledgeRetriever`
- document/chunk/result models
- repository abstractions
- import/retrieval tests

#### Acceptance criteria
- limited P0 file types supported
- document -> chunks -> embeddings -> index path works
- retrieval returns source metadata
- retrieval path separated from chat runtime
- structured failures only

## 3. 规格依赖顺序

### 第一阶段：模型调用主链
1. Spec 08 — BindingResolver
2. Spec 09 — ModelProvider / ProviderRegistry
3. Spec 10 — ConversationRuntime / SendMessage

### 第二阶段：技能装载与 DSL
4. Spec 11 — SkillPackageLoader
5. Spec 12 — Workflow Parser / Validator

### 第三阶段：技能执行主链
6. Spec 13 — WorkflowExecutor
7. Spec 14 — ToolRegistry + PermissionGate
8. Spec 15 — Knowledge Base / RAG

## 4. 推荐实现顺序（给普通模型时的真正顺序）

### Track A：聊天主链
1. 数据模型与公共结果类型
2. SecretProvider
3. BindingResolver
4. ModelProvider / ProviderRegistry
5. ConversationRuntime

### Track B：技能主链
6. SkillPackageLoader
7. Workflow Parser / Validator
8. WorkflowExecutor

### Track C：能力扩展
9. ToolRegistry + PermissionGate
10. Knowledge Base / RAG

## 5. 每份规格的统一模板

后面继续扩文档时，每份都固定保持这 6 段：
1. Objective
2. Scope
3. Non-goals
4. Required deliverables
5. Acceptance criteria
6. Common failure modes
