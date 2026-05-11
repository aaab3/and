# 实施启动手册 v1

这份文档不是规格补充，而是“你现在就要开做时怎么用前面这些文档”。

目标：
- 把文档包串成一个真正可执行的启动流程
- 降低你一开工就乱序实现的概率
- 让普通模型参与时也能沿同一套边界推进

---

## 0. 先认清现在已有的文档

当前建议你直接使用的文档：

1. `wiggly-soaring-quilt.md`
   - 总体方向与架构原则
2. `overview-spec.md`
   - 正式规格目录
3. `development-order.md`
   - 实际开发顺序
4. `delegation-prompts.md`
   - Prompt 01~15 投喂文本
5. `acceptance-checklists.md`
   - Prompt 01~15 验收清单
6. `core-interface-blueprint.md`
   - 核心接口与 DTO 蓝图
7. `database-sketch.md`
   - 数据骨架草图
8. `skill-package-and-workflow-examples.md`
   - 技能包与 DSL 最小样例

如果你现在要开始做，真正高频看的其实只有 5 份：
- `development-order.md`
- `delegation-prompts.md`
- `acceptance-checklists.md`
- `core-interface-blueprint.md`
- `database-sketch.md`

---

## 1. 正确启动顺序

### 阶段 A：先闭合聊天主链
严格顺序：

1. Prompt 01 — 结果与错误模型
2. Prompt 02 — Conversation / Message 数据层
3. Prompt 03 — Provider / Credential / Model / Binding 数据层
4. Prompt 04 — SecretProvider
5. Prompt 05 — BindingResolver
6. Prompt 06 — OpenAI-compatible ModelProvider
7. Prompt 07 — ProviderRegistry
8. Prompt 08 — ConversationRuntime / SendMessage

### 阶段 B：再闭合技能定义链
9. Prompt 09 — SkillPackageLoader
10. Prompt 10 — WorkflowParser
11. Prompt 11 — WorkflowValidator

### 阶段 C：最后闭合技能执行链
12. Prompt 12 — ToolRegistry
13. Prompt 13 — PermissionGate
14. Prompt 14 — Knowledge base / RAG boundary
15. Prompt 15 — WorkflowExecutor

不要换顺序。

---

## 2. 每做一题时要同时看什么

### Prompt 01
看：
- `delegation-prompts.md`
- `acceptance-checklists.md`
- `core-interface-blueprint.md`

### Prompt 02
看：
- 上面 3 份
- `database-sketch.md`

### Prompt 03~05
看：
- `core-interface-blueprint.md`
- `database-sketch.md`
- `acceptance-checklists.md`

### Prompt 06~08
看：
- `core-interface-blueprint.md`
- `acceptance-checklists.md`
- `development-order.md`

### Prompt 09~11
看：
- `skill-package-and-workflow-examples.md`
- `core-interface-blueprint.md`
- `acceptance-checklists.md`

### Prompt 12~15
看：
- `core-interface-blueprint.md`
- `skill-package-and-workflow-examples.md`
- `acceptance-checklists.md`
- `development-order.md`

---

## 3. 每题的标准执行动作

每一题都建议按这个固定动作走：

### Step 1. 先投喂任务文本
直接从 `delegation-prompts.md` 拿对应 Prompt。

### Step 2. 再加两个硬约束
每次都补这两句：

```text
Use the exact interface and DTO names from core-interface-blueprint.md unless there is a clear contradiction.
Do not add features, fields, or abstractions beyond the acceptance criteria.
```

如果是数据层题，再补一句：

```text
Align entity/repository fields with database-sketch.md unless there is a clear reason not to.
```

### Step 3. 产出后立刻验收
按 `acceptance-checklists.md` 对照。

### Step 4. 不通过就原地返工
不要先继续下一题。

---

## 4. 每个阶段的停点

### 阶段 A 停点
必须验证：
- conversation 可创建
- user message 可落库
- history 可读取
- binding 可 resolve
- OpenAI-compatible provider 可调用
- assistant reply 成功落库
- provider 失败时 user message 仍保留

### 阶段 B 停点
必须验证：
- 目录技能包导入成功
- ZIP 技能包导入成功
- `skill.md` frontmatter 可解析
- `workflow.yaml` 可解析
- 非法 workflow 被拒绝

### 阶段 C 停点
必须验证：
- 最小 workflow 能执行完成
- `call_tool` 走 registry + permission
- `retrieve_knowledge` 返回 source metadata
- executor 不是 God object

---

## 5. 最容易失控的点

### 5.1 Prompt 06 失控点
常见错误：
- 顺手把 chat runtime 也写了
- 加 streaming
- 加 retry/cache
- 暴露 provider-specific DTO 到上层

处理原则：
- 只保留 unified request/response
- 只做 text non-streaming
- tools 只做声明发送 + tool call 解析

### 5.2 Prompt 08 失控点
常见错误：
- 把 workflow/tool/RAG 注入全塞进去
- 把 sendMessage 做成大而全 runtime

处理原则：
- 只做 user message -> history -> resolve -> provider -> assistant message

### 5.3 Prompt 10~11 失控点
常见错误：
- parser 里偷做执行
- validator 依赖运行副作用
- 支持 script/loop/parallel

处理原则：
- parser 只 parse
- validator 只 validate
- 只支持 P0 step types

### 5.4 Prompt 15 失控点
常见错误：
- executor 直接解析 raw YAML
- executor 隐式自己做 provider/tool 分发特判
- executor 变成长自治 agent runtime

处理原则：
- 只执行 validated workflow
- 只依赖抽象边界
- 失败即停

---

## 6. 你开工时最该先建的工程骨架

建议先有这些模块/包位：

```text
core-result
conversation-data
binding-data
secret
binding-runtime
model-provider
conversation-runtime
skill-package
workflow-definition
tool-runtime
knowledge
workflow-execution
```

如果你不想一开始就拆多 module，至少逻辑包边界也要按这个切。

---

## 7. 最小可运行里程碑

### Milestone 1
完成 Prompt 01~08。

产出标志：
- 你第一次能真正发一条消息并得到回复

### Milestone 2
完成 Prompt 09~11。

产出标志：
- 你第一次能导入技能包并拿到 validated workflow

### Milestone 3
完成 Prompt 12~15。

产出标志：
- 你第一次能跑通一个最小技能 workflow

---

## 8. 不要现在做的事

你现在启动时，不要做这些：
- 视觉编辑器
- 云同步
- 多人协作
- 完整插件生态
- provider fallback chain
- agent loop
- loop/retry/parallel 真执行语义
- 复杂 RAG reranking
- execution log UI

这些都属于后续增强项。

---

## 9. 最适合普通模型的投喂模板

每次可以直接这样发：

```text
Implement Prompt XX only.

Follow the task, requirements, constraints, and acceptance criteria from delegation-prompts.md.
Use the exact interface and DTO names from core-interface-blueprint.md unless there is a clear contradiction.
Align persistence-layer field names with database-sketch.md when this task touches stored entities.
Do not add extra features, speculative abstractions, or non-requested error-handling layers.
Return structured failures through AppResult.Failure(AppError(...)).
```

如果是技能相关题，再补：

```text
Use skill-package-and-workflow-examples.md as the shape reference for package and workflow structures.
```

---

## 10. 真正开做时的一句话纪律

```text
一次只做一题，做完立刻验收，不通过绝不进入下一题。
```
