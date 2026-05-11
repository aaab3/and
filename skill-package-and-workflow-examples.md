# 技能包与 Workflow 示例 v1

这份文档的目标不是定义全部 DSL，而是给 P0 一个“能跑通全链路”的最小示例。

用途：
- 给普通模型看一个完整技能包长什么样
- 给 `SkillPackageLoader` 明确输入样子
- 给 `WorkflowParser / Validator / Executor` 明确最小工作流结构
- 给验收时准备样例资产

---

## 0. P0 示例目标

这个最小技能做的事很简单：
1. 接收用户问题
2. 用模板拼成最终 prompt
3. 可选检索知识库
4. 调模型回答
5. 返回结果

这个例子故意不包含：
- loop
- retry
- foreach
- arbitrary script
- parallel
- autonomous agent logic

---

## 1. 推荐目录结构

```text
summarize_with_knowledge/
├─ skill.md
├─ workflow.yaml
├─ prompts/
│  └─ answer_style.md
├─ assets/
│  └─ example-input.txt
├─ schemas/
│  ├─ input.schema.json
│  └─ output.schema.json
└─ icon.png
```

P0 必需：
- `skill.md`
- `workflow.yaml`

P0 可选：
- `prompts/`
- `assets/`
- `schemas/`
- `icon.png`

---

## 2. `skill.md` 示例

```md
---
name: summarize_with_knowledge
description: Answer a user question using optional retrieved local knowledge.
version: 0.1.0
inputs:
  user_question: string
defaults:
  answer_style: concise
model: default
tools: []
permissions: []
workflow: workflow.yaml
output:
  final_answer: string
---

# Summarize With Knowledge

This skill answers a user question.

It may retrieve local knowledge first, then sends a composed prompt to the model,
and returns the final answer.

## Notes
- Human-readable markdown body only
- This markdown body is not executable workflow
```

关键边界：
- frontmatter 是机器读的
- markdown body 是人类读的
- 不能把 markdown body 当可执行步骤

---

## 3. `workflow.yaml` 最小示例

```yaml
id: summarize_with_knowledge
name: Summarize With Knowledge
version: 0.1.0
steps:
  - id: build_query
    type: template
    template: |
      User question: {{inputs.user_question}}
    outputKey: query_text

  - id: retrieve_context
    type: retrieve_knowledge
    queryKey: query_text
    outputKey: retrieved_context

  - id: build_prompt
    type: template
    template: |
      You are a concise assistant.

      User question:
      {{inputs.user_question}}

      Retrieved context:
      {{retrieve_context}}

      Answer clearly and briefly.
    outputKey: final_prompt

  - id: call_model
    type: call_model
    inputKey: final_prompt
    outputKey: final_answer

  - id: return_result
    type: return
    outputKey: final_answer
```

说明：
- 这个版本只用了 P0 允许步骤：
  - `template`
  - `retrieve_knowledge`
  - `call_model`
  - `return`
- 没有 branch/tool 时更适合第一轮联调

---

## 4. 带 `call_tool` 和 `branch` 的扩展示例

如果你要测试更完整的 P0 executor，可以用下面这个变体。

```yaml
id: answer_with_optional_tool
name: Answer With Optional Tool
version: 0.1.0
steps:
  - id: build_prompt
    type: template
    template: |
      Decide whether a web fetch tool is needed.
      Question: {{inputs.user_question}}
    outputKey: decision_prompt

  - id: decide
    type: call_model
    inputKey: decision_prompt
    outputKey: decision_result

  - id: route
    type: branch
    inputKey: decision_result
    cases:
      - equals: USE_TOOL
        nextStepId: fetch_web
      - equals: DIRECT_ANSWER
        nextStepId: answer_directly
    defaultNextStepId: answer_directly

  - id: fetch_web
    type: call_tool
    toolName: web_fetch
    inputKey: decision_result
    outputKey: fetched_result

  - id: answer_directly
    type: template
    template: |
      User question: {{inputs.user_question}}
      Tool result: {{fetch_web}}
      Give the final answer.
    outputKey: final_prompt

  - id: final_call
    type: call_model
    inputKey: final_prompt
    outputKey: final_answer

  - id: return_result
    type: return
    outputKey: final_answer
```

注意：
- 这是执行器测试样例，不代表要先做自动工具自治循环
- `branch` 只做显式跳转，不做隐式递归

---

## 5. frontmatter 标准化结果示例

`SkillPackageLoader` 输出的 `SkillManifest` 可以近似长这样：

```kotlin
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
```

对应这个例子时，标准化结果可以视为：

```json
{
  "name": "summarize_with_knowledge",
  "description": "Answer a user question using optional retrieved local knowledge.",
  "version": "0.1.0",
  "workflow": "workflow.yaml",
  "inputs": {
    "user_question": "string"
  },
  "defaults": {
    "answer_style": "concise"
  },
  "tools": [],
  "permissions": [],
  "model": "default",
  "output": {
    "final_answer": "string"
  }
}
```

---

## 6. Parsed workflow 标准结构示意

`WorkflowParser` 不必保留 YAML 细节给上层，而是输出标准内部结构。

例如：

```kotlin
data class ParsedWorkflowDefinition(
    val id: String,
    val name: String,
    val version: String,
    val steps: List<WorkflowStep>
)
```

对最小示例而言，可以解析为概念上这样的结构：

```text
ParsedWorkflowDefinition(
  id = "summarize_with_knowledge",
  name = "Summarize With Knowledge",
  version = "0.1.0",
  steps = [
    TemplateStep(id = "build_query", outputKey = "query_text"),
    RetrieveKnowledgeStep(id = "retrieve_context", outputKey = "retrieved_context"),
    TemplateStep(id = "build_prompt", outputKey = "final_prompt"),
    CallModelStep(id = "call_model", outputKey = "final_answer"),
    ReturnStep(id = "return_result", outputKey = "final_answer")
  ]
)
```

---

## 7. 运行时上下文示例

建议 `WorkflowExecutor` 采用显式 context map。

初始输入：

```json
{
  "inputs.user_question": "What changed in this note?"
}
```

执行后中间上下文可能像这样：

```json
{
  "inputs.user_question": "What changed in this note?",
  "build_query": "User question: What changed in this note?",
  "retrieve_context": "Relevant local note excerpt...",
  "build_prompt": "You are a concise assistant...",
  "call_model": "The note changed in these ways..."
}
```

最终 `return` 输出：

```json
{
  "output": "The note changed in these ways..."
}
```

关键原则：
- step output 存显式 key
- 不依赖隐藏魔法状态
- 不允许 executor 靠隐式副作用串联步骤

---

## 8. 最小执行链路

### 8.1 导入阶段
```text
ZIP / Directory
-> SkillPackageLoader.load(...)
-> 解析 skill.md frontmatter
-> 定位 workflow.yaml
-> 输出 LoadedSkillPackage
-> 持久化 installed_skill + workflow_definition
```

### 8.2 定义阶段
```text
workflow.yaml
-> WorkflowParser.parse(...)
-> ParsedWorkflowDefinition
-> WorkflowValidator.validate(...)
-> validated workflow
```

### 8.3 执行阶段
```text
WorkflowExecutor.execute(...)
-> TemplateStep
-> RetrieveKnowledgeStep
-> CallModelStep
-> ReturnStep
-> WorkflowExecutionResult
```

---

## 9. 非法包示例

以下情况必须明确失败：

### 9.1 缺少 `skill.md`
```text
invalid_skill_package/
└─ workflow.yaml
```

应返回：
- `AppResult.Failure(AppError(code = "NOT_FOUND", ...))`
  或
- `AppResult.Failure(AppError(code = "VALIDATION_FAILED", ...))`

### 9.2 frontmatter 缺少 workflow 字段
```md
---
name: bad_skill
version: 0.1.0
---
```

### 9.3 workflow 文件不存在
`skill.md` 写了：
```yaml
workflow: missing.yaml
```

### 9.4 不支持的 step type
```yaml
- id: hack
  type: script
```

应明确失败，不允许静默跳过。

---

## 10. 用这份示例做联调的推荐顺序

1. 先用最小技能包验证 `SkillPackageLoader`
2. 再用最小 `workflow.yaml` 验证 `WorkflowParser`
3. 再用非法样例验证 `WorkflowValidator`
4. 再用无 tool 的最小流验证 `WorkflowExecutor`
5. 最后再加 `branch` / `call_tool` 变体

---

## 11. P0 非目标

这个示例明确不覆盖：
- markdown 正文内嵌执行脚本
- JavaScript / Python hook
- provider 自动切换链
- loop / retry / foreach 真实语义
- 多人协作技能包
- 在线技能市场元数据
