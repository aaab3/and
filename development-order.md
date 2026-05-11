# 开发顺序版 v1

这份不是再解释架构，而是把前面的规格转成真正可开工的工程顺序。目标只有一个：让你用普通模型也能稳步往前写，不在前期把系统写散。

## 0. 总原则

### 规则 1：先闭合主链，再做扩展
先把“能发一条消息、能收到回复”做出来，再接技能、工具、RAG。

### 规则 2：先做抽象边界，再做实现丰满
先让接口和依赖方向稳定，不要一开始把每个模块都做满。

### 规则 3：优先做低耦合基础件
例如：
- result/error 类型
- store 接口
- binding resolver
- provider registry

### 规则 4：凡是普通模型容易发散的点，都延后
例如：
- streaming
- workflow loop
- parallel
- complex RAG
- visual editor
- cloud sync

## 1. 最佳实施阶段划分

### 阶段 A：聊天最小闭环
本阶段完成后，你应该得到：
- 可以创建/打开一个 conversation
- 可以发送一条 user message
- 可以调用 provider
- 可以得到 assistant reply
- 可以把消息落库并回显

本阶段包含：
1. 公共结果类型与错误模型
2. 会话/消息存储
3. Provider / Credential / Model / Binding 数据层
4. SecretProvider
5. BindingResolver
6. ModelProvider / ProviderRegistry
7. ConversationRuntime / SendMessage

### 阶段 B：技能导入与定义边界
本阶段完成后，你应该得到：
- 可导入目录/ZIP 技能包
- `skill.md` 可解析
- `workflow.yaml` 可解析并校验
- 系统内部存在 installed skill + parsed workflow definition

本阶段包含：
1. SkillPackageLoader
2. Workflow Parser / Validator

### 阶段 C：技能执行主链
本阶段完成后，你应该得到：
- executor 能顺序执行 workflow
- `call_model` 能走统一 provider 抽象
- `call_tool` 有统一边界
- `retrieve_knowledge` 有统一边界
- `return` 能给出最终结果

本阶段包含：
1. WorkflowExecutor
2. ToolRegistry + PermissionGate
3. Knowledge base / RAG minimal boundary

### 阶段 D：增强与可运维性
本阶段完成后，你应该得到：
- execution log
- debug trace
- import/export
- test/acceptance pack
- 更完整 settings/admin 合同

## 2. 绝对推荐的具体实现顺序

### Step 1. 公共基础类型
先做：
- `AppResult`
- `AppError`
- 通用错误码组织方式
- 基础 domain model 命名规则

### Step 2. Conversation / Message 数据层
先把：
- conversation entity
- message entity
- DAO / repository / ConversationStore

做出来。

### Step 3. Provider / Credential / Model / Binding 数据层
把：
- `ProviderDefinition`
- `CredentialRef`
- `ModelProfile`
- `ModelBinding`

以及对应 repository 做出来。

### Step 4. SecretProvider
把：
- secure storage 接口
- `putSecret/getSecret/deleteSecret`

做出来。

### Step 5. BindingResolver
本步完成标志：给一个 workspace/skill 上下文，能稳定得到：
- provider
- model
- credential ref
- secret
- unified `ResolvedModelBinding`

### Step 6. ModelProvider / ProviderRegistry
推荐真实顺序：
1. 先统一 request/response 模型
2. 再做 `ProviderRegistry`
3. 再完整实现一个 OpenAI-compatible provider
4. Anthropic-like / Gemini-like 只做 skeleton / not-implemented

### Step 7. ConversationRuntime / SendMessage
系统第一次可以：
- 写 user message
- 查历史
- resolve binding
- 调 provider
- 写 assistant message

到这里，聊天主链闭合。

## 3. 为什么这里必须停一下

做到 Step 7 后，建议先停一下，做一次小验收。

这次验收只看 3 件事：
1. 主链有没有真正跑通
2. 分层有没有被污染
3. 普通模型写出来的代码有没有开始变胖

## 4. 第二阶段的正确顺序

### Step 8. SkillPackageLoader
你能从目录 / ZIP 稳定导入：
- `skill.md`
- `workflow.yaml`

并转成内部标准对象。

### Step 9. Workflow Parser / Validator
任意合法 `workflow.yaml` 都能得到稳定 `ParsedWorkflowDefinition`，非法输入能明确失败。

到这里，技能定义链闭合。

## 5. 第三阶段的正确顺序

### Step 10. ToolRegistry + PermissionGate
先定 `ToolRegistry -> PermissionGate -> ToolHandler` 这条线，后面 executor 才不会乱。

### Step 11. Knowledge base / RAG minimal boundary
先把：
- importer
- chunker
- embedder
- index
- retriever

边界定住，executor 只调用抽象。

### Step 12. WorkflowExecutor
它依赖：
- parsed workflow
- binding resolver
- provider registry
- tool registry
- permission gate
- knowledge retriever

本步完成标志：已安装技能第一次能从 workflow 跑出结果。

到这里，技能执行主链闭合。

## 6. 哪些可以并行，哪些不要并行

### 可以并行的
- Step 2 会话/消息数据层
- Step 3 provider/binding 数据层

- Step 10 ToolRegistry + PermissionGate
- Step 11 Knowledge base / RAG boundary

### 不建议并行的
- BindingResolver / ModelProvider
- Workflow Parser / WorkflowExecutor
- ConversationRuntime / Skill execution runtime

## 7. 哪些必须先 stub，哪些必须先做真实现

### 必须先做真实现的
1. `AppResult` / `AppError`
2. `ConversationStore`
3. `BindingResolver`
4. 一个真正可用的 OpenAI-compatible `ModelProvider`
5. `ConversationRuntime.sendMessage`
6. `SkillPackageLoader`
7. `Workflow Parser / Validator`

### 可以先 stub 的
1. Anthropic-like provider
2. Gemini-like provider
3. 部分高级 tool handlers
4. pdf/docx importer
5. 高级 retrieval ranking
6. execution log UI
7. debug console

## 8. 每个阶段的停点

### 阶段 A 停点
验收：
- 一次完整聊天发送
- 历史消息正确持久化
- binding fallback 正确
- provider 返回正确解析
- user message 在失败时仍保留

### 阶段 B 停点
验收：
- 目录技能包导入成功
- ZIP 技能包导入成功
- 非法 workflow 被拒绝
- `skill.md` 与 `workflow.yaml` 边界没有混

### 阶段 C 停点
验收：
- 一个最小技能 workflow 成功跑完
- 一个 tool step 成功走 registry + permission
- 一个 retrieval step 成功返回 source metadata
- executor 没有变成 God object

## 9. 最适合直接交给普通模型的任务块
1. 公共 result/error 类型
2. conversation/message 数据层
3. provider/binding 数据层
4. `SecretProvider`
5. `BindingResolver`
6. OpenAI-compatible `ModelProvider`
7. `ProviderRegistry`
8. `ConversationRuntime.sendMessage`
9. `SkillPackageLoader`
10. `Workflow Parser`
11. `Workflow Validator`
12. `ToolRegistry`
13. `PermissionGate`
14. `KnowledgeRetriever` 相关抽象
15. `WorkflowExecutor`

## 10. 一句话版真实顺序

```text
基础类型
-> 会话存储
-> provider/binding 存储
-> SecretProvider
-> BindingResolver
-> OpenAI-compatible ModelProvider
-> ProviderRegistry
-> ConversationRuntime
-> SkillPackageLoader
-> Workflow Parser / Validator
-> ToolRegistry + PermissionGate
-> Knowledge base / RAG boundary
-> WorkflowExecutor
```
