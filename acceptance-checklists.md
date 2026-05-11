# 验收版 v1

这份文档把各 Prompt 的 acceptance criteria 改写成更适合人工或模型验收的 checklist。

**勾选说明（2026-05-11）：** 对照当前仓库实现与 `.\gradlew.bat test` 全绿结果逐条勾选。Prompt 09 中「workflow 文件」在技能包加载阶段做路径安全、存在性与非空校验；工作流 **语义**（P0 步骤、引用、terminal return 等）由 Parser + Validator（Prompt 10/11）在定义链中完成。

## Prompt 01 验收单
- [x] 提供 `AppResult<out T>`
- [x] 提供 `AppError`
- [x] 成功与失败路径结构化表达
- [x] 上层不依赖裸异常协议
- [x] 错误结构支持 code/message

## Prompt 02 验收单
- [x] conversation 可创建 / 查询
- [x] message 可按顺序读取
- [x] user / assistant message 可分别追加
- [x] `ConversationStore` 边界存在
- [x] 持久化层未混入 provider / workflow 逻辑

## Prompt 03 验收单
- [x] 存在 `ProviderDefinition`
- [x] 存在 `CredentialRef`
- [x] 存在 `ModelProfile`
- [x] 存在 `ModelBinding`
- [x] 没有在普通业务库中明文保存真实 secret
- [x] repository 接口职责收敛

## Prompt 04 验收单
- [x] 存在 `SecretProvider`
- [x] 支持 `putSecret`
- [x] 支持 `getSecret`
- [x] 支持 `deleteSecret`
- [x] 丢失 secret 返回结构化失败
- [x] 业务层不直接操作底层安全存储细节

## Prompt 05 验收单
- [x] 存在 `BindingResolver`
- [x] 支持固定 scope 优先级解析
- [x] 支持 fallback
- [x] provider / model / credential / secret 能被完整解析
- [x] 缺失 binding/provider/model/credential/secret 时有明确错误
- [x] 返回统一 `ResolvedModelBinding`

## Prompt 06 验收单
- [x] 存在统一 request/response 模型
- [x] 存在 `ModelProvider`
- [x] OpenAI-compatible provider 可实际调用
- [x] 支持 text-only non-streaming
- [x] 能发送 tool declarations
- [x] 能解析 tool calls
- [x] 能解析 usage / finishReason
- [x] 所有错误结构化返回

## Prompt 07 验收单
- [x] 存在 `ProviderRegistry`
- [x] 能按 `providerType` 查找 provider
- [x] OpenAI-compatible provider 已注册
- [x] Anthropic-like provider 有 skeleton 或明确 not implemented
- [x] Gemini-like provider 有 skeleton 或明确 not implemented
- [x] 未知 provider type 不会静默失败

## Prompt 08 验收单
- [x] 存在 `ConversationRuntime`
- [x] 存在 `sendMessage`
- [x] 发送前检查 conversation 存在
- [x] user message 在 provider 调用前已落库
- [x] 当前新消息被包含进历史上下文
- [x] provider 调用成功后才写 assistant message
- [x] provider 调用失败时 user message 仍然保留
- [x] 没有混入 workflow / RAG / tool loop / streaming

## Prompt 09 验收单
- [x] `SkillPackageLoader` 存在
- [x] 支持目录导入
- [x] 支持 ZIP 导入
- [x] `skill.md` 必需
- [x] frontmatter 被解析
- [x] `workflow.yaml` 被定位并校验
- [x] markdown body 未被当成 executable workflow
- [x] 非法包明确失败

## Prompt 10 验收单
- [x] `WorkflowParser` 存在
- [x] 合法 YAML 可转成内部 workflow 结构
- [x] 仅支持 P0 step types
- [x] 不支持的 step type 明确失败
- [x] parsing 阶段没有执行副作用

## Prompt 11 验收单
- [x] `WorkflowValidator` 存在
- [x] step 必填字段校验存在
- [x] duplicate id 检测存在
- [x] reference 校验存在
- [x] branch 结构校验存在
- [x] 缺失 terminal return 明确失败

## Prompt 12 验收单
- [x] `ToolManifest` 存在
- [x] `ToolHandler` 存在
- [x] `ToolRegistry` 存在
- [x] 工具能按名称注册和查找
- [x] 没有绕过 registry 的执行路径
- [x] 没有引入通用 shell/script 后门工具

## Prompt 13 验收单
- [x] `PermissionGate` 存在
- [x] permission request / decision 模型存在
- [x] allow / deny 结果明确
- [x] “requires user confirmation” 信息可表达
- [x] UI 审批逻辑没有硬耦合进 core
- [x] tool 无法自授权

## Prompt 14 验收单
- [x] `DocumentImporter` 存在
- [x] `Chunker` 存在
- [x] `EmbeddingProvider` 存在
- [x] `VectorIndex` 存在
- [x] `KnowledgeRetriever` 存在
- [x] 至少支持 `txt` / `md`
- [x] retrieval 结果保留 source metadata
- [x] 未自动耦合到 chat runtime

## Prompt 15 验收单
- [x] `WorkflowExecutor` 存在
- [x] 只执行已验证 workflow
- [x] 使用显式 context map 传递 step outputs
- [x] 支持 `template`
- [x] 支持 `call_model`
- [x] 支持 `call_tool`
- [x] 支持 `retrieve_knowledge`
- [x] 支持 `branch`
- [x] 支持 `return`
- [x] step 失败会中止执行并返回结构化失败
- [x] 没有原始 YAML 解析逻辑混入 executor
- [x] 没有任意 loop / parallel / autonomous agent 扩展

## 总体验收
- [x] 聊天主链能独立闭合
- [x] 技能导入链能独立闭合
- [x] workflow 定义链能独立闭合
- [x] workflow 执行链能独立闭合
- [x] tool 调用统一走 registry + permission
- [x] RAG 边界独立存在且未提前污染聊天主链
- [x] 普通模型可按 Prompt 01~15 顺序逐步实现
