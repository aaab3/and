# 项目规格 v2（精简版）

本文档替代 overview-spec.md / development-order.md / delegation-prompts.md / acceptance-checklists.md / wiggly-soaring-quilt.md 的产品和实施部分。保留的参考文档是 core-interface-blueprint.md（作为接口清单）、database-sketch.md、ui-spec.md。

## 0. 产品定位

一个 Android 原生的、BYOK 的大模型聊天客户端，支持以 Markdown 技能文件增强模型能力，以 tool calling 让技能调用本地工具。

**不是**：插件商店、多人协作、可视化工作流编辑器、云同步。

## 1. 架构决策（与 v1 的差异）

| 条目 | v1 规划 | v2 决策 | 原因 |
|------|---------|---------|------|
| Provider 协议 | 4 种（OpenAI/Anthropic/Gemini/Custom） | 仅 OpenAI 兼容 | 用户可自行走网关转换 |
| 模型调用 SDK | 手写 JSON + OkHttp | `com.aallam.openai:openai-client` | 省数百行代码 |
| Skill 定义 | Markdown + workflow.yaml DSL | 仅 Markdown + frontmatter | 去掉 parser/validator/executor |
| Skill 执行 | 自研 WorkflowExecutor 步进式 | 模型 tool calling 循环 | 让模型自主编排 |
| Tool 数据流 | `call_tool` step | 原生 tool calling | 与所有 Provider 原生对齐 |
| RAG | 自研 chunker + embedding + index | P0 砍掉 | 没有业务必要性 |
| BindingScope | 4 级（GLOBAL/WORKSPACE/SKILL/CONVERSATION） | 2 级（default + skill override） | YAGNI |
| 模块数量 | 16 个 Gradle 模块 | 先删 3 个（knowledge/workflow-definition/workflow-execution），其余保持 | 物理合并不产生价值 |
| Permission | PermissionGate 独立层 | 简化为工具 manifest `requiresConfirmation` + 一次性弹窗 | 无需独立决策引擎 |
| Markdown 渲染 | 自研 MarkdownText | 换成 compose-markdown | 省维护成本 |

## 2. P0 功能清单（只有这些）

1. 多 Provider 配置（名称 / Base URL / Key / 默认 Model ID），支持添加删除切换
2. 会话列表 + 聊天 UI + 消息持久化
3. 发送消息、收到回复、深色模式、Markdown 渲染
4. 从本地文件系统导入 `.md` 技能文件
5. 聊天时 `@技能名` 调起技能
6. 技能可声明 tools，tool calling 循环自动执行
7. 三个内置工具：`http_fetch` / `current_time` / `read_text_file`

**P0 之外全部砍掉或延后**：RAG、流式、Anthropic/Gemini 原生 SDK、工作流 DSL、技能包 ZIP、执行日志 UI、导入导出。

## 3. 技能新定义

纯 Markdown 文件，带 YAML frontmatter，放在用户选择的文件夹里：

```markdown
---
name: translator
description: Translate text between languages
tools: [http_fetch]
model: gpt-4o-mini    # 可选，覆盖默认
---

You are a professional translator. Detect the source language
automatically. If the user provides a URL, call http_fetch first.
```

支持两种导入方式：
1. **单个 .md 文件**：frontmatter + body 就是全部
2. **文件夹**：包含 `SKILL.md` 主文件 + `references/` 子目录

文件夹结构：
```
skill-folder/
├── SKILL.md          ← 主文件（frontmatter + system prompt）
└── references/       ← 可选，所有 .md/.txt 自动拼接到 context
    ├── guide-1.md
    └── guide-2.md
```

**加载：** 读 SKILL.md 的 frontmatter + body，再把 references/ 下所有文件内容追加到 body 后面，一起作为 system prompt。  
**执行：** 用户发 `@translator 翻译这段` → 把完整 body（含 references）当 system prompt，tools 注入 tool declarations，进入 tool calling 循环。

## 4. Tool Calling 循环（核心机制）

```
用户消息
  ↓
构造 messages = [system(skill body), ...history, user]
构造 tools = skill.tools.map(toToolSpec)
  ↓
→ 调用 Provider.generate()
  ↓
response 有 tool_calls?
  ├─ 有 → 执行每个 tool → messages += [assistant(tool_calls), tool(result)*N]
  │      → 回到上一步
  └─ 无 → 存 assistant 消息，结束
```

**循环上限：8 次**，超过报错。

## 5. UI / 交互流程（重点）

### 5.1 导航骨架

```
RootApp
├── ChatScreen (主界面)
│   ├── TopBar: [≡菜单] 会话标题 [⋮操作]
│   ├── MessageList (滚动)
│   │   ├── UserBubble
│   │   ├── AssistantBubble (Markdown 渲染 + 长按复制)
│   │   └── ToolCallBubble (可折叠显示 tool call + result)
│   └── InputBar
│       ├── [+] 附件按钮 (P1，先占位)
│       ├── TextField (多行 + 自动感应 @)
│       └── [▶] 发送按钮 (发送中替换为 loading)
│
├── DrawerContent (左滑出，会话列表)
│   ├── [新建会话] 按钮
│   ├── 会话项 (长按弹删除/重命名)
│   └── 底部: Settings / Skills 两个入口
│
├── SettingsScreen (Provider 管理)
│   ├── Providers section (列表 + 添加)
│   │   └── ProviderEditorDialog (名称/BaseURL/Key/默认Model)
│   ├── DefaultBinding section (全局默认 Provider/Model)
│   └── About
│
└── SkillsScreen (技能管理)
    ├── [导入] 按钮 (选择文件夹或单个 md)
    ├── 技能列表 (name + description)
    └── 点击 → SkillDetailScreen (显示 body preview + tools)
```

### 5.2 关键交互流程

#### 流程 A：首次启动
1. 启动 → 检测数据库有无 Provider → **无**
2. 自动跳转到 SettingsScreen，顶部横幅提示："Add a provider to start"
3. 用户点击 [添加 Provider] → 弹出 ProviderEditorDialog
4. 填完保存 → 回到 ChatScreen，默认绑定自动设为该 provider
5. 自动新建第一个会话

#### 流程 B：发送消息（无技能）
1. 输入文字 → 点击发送
2. 输入框立即清空，user bubble 立刻追加（乐观更新）
3. InputBar 的发送按钮变 loading，TextField 禁用
4. 消息列表底部出现三点跳动的 TypingIndicator
5. 请求成功 → 追加 assistant bubble，TypingIndicator 移除
6. 请求失败 → 底部 Snackbar 显示错误，user 消息保留，TypingIndicator 移除
7. 用户可重试（长按 user bubble → 弹出菜单 → Resend）

#### 流程 C：@技能 调用
1. 用户在 TextField 输入 `@` 触发技能建议 Popup
2. Popup 显示匹配的技能（按 name 前缀）
3. 用户选中 → TextField 插入 `@skill_name `
4. 继续输入内容 → 发送
5. ChatViewModel 识别 `@skill_name` 前缀 → 用该 skill 的 body 作为 system prompt
6. 如果 skill 声明了 tools → 进入 tool calling 循环
7. 循环中每次 tool call → 消息列表追加 ToolCallBubble（默认折叠，显示 `🔧 http_fetch`，展开看参数和结果）
8. 最终 assistant 回复追加到底部

#### 流程 D：Tool 需要用户确认
1. Tool manifest 里 `requiresConfirmation=true` 的工具（比如 http_fetch）
2. 循环中执行到该 tool → 先暂停，显示 ConfirmDialog（展示工具名 + 参数 + 接受/拒绝）
3. 用户接受 → 继续执行
4. 用户拒绝 → 把"user denied"注入 messages 作为 tool result，循环继续

#### 流程 E：Provider 切换
1. ChatScreen TopBar 右上角点击 ⋮ → 弹出菜单
2. 菜单顶部显示当前 Provider + Model，点击展开下拉
3. 选择其他 Provider → 该会话的 binding 覆盖为所选
4. 不影响其他会话

#### 流程 F：导入技能
1. SkillsScreen → [导入] → 系统文件选择器
2. 支持选择单个 .md 文件或一个文件夹
3. 后台解析：读 frontmatter → 验证 name/description 存在 → 落库
4. 解析失败 → Snackbar 提示具体原因（缺少字段/YAML 错误）
5. 成功 → 技能列表刷新，顶部 Snackbar "Imported N skill(s)"

### 5.3 视觉细节（不要再改）

- **消息气泡**：user 主题色右对齐；assistant surfaceVariant 左对齐，不占满行
- **ToolCall 气泡**：中央对齐，边框而非填充色，左侧小图标（🔧），默认折叠
- **输入框**：圆角 24dp，多行最多 4 行，超出滚动
- **发送按钮**：48dp FilledIconButton，禁用时降低到 38% 透明度
- **会话列表项**：标题 + 最后消息预览 + 相对时间（"5m" / "2h" / "01/05"）
- **深色模式**：Material3 动态取色（Android 12+），否则 fallback 到固定蓝色
- **字号**：bodyLarge (16sp) 消息正文；labelSmall (11sp) 时间戳；代码块 Monospace 13sp

### 5.4 错误处理规范

| 场景 | UI 表现 |
|------|---------|
| 无 Provider | SettingsScreen 顶部横幅，ChatScreen 发送按钮禁用 |
| API Key 错 | Snackbar "Invalid API key"，引导到设置 |
| 网络失败 | Snackbar "Network error"，提供重试按钮 |
| Provider 返回 4xx/5xx | Snackbar 显示 provider 的错误消息（前 200 字符） |
| Tool 执行失败 | ToolCallBubble 内标红显示错误，循环继续（注入 error 到 messages） |
| Skill 解析失败 | 导入时 Dialog 显示具体字段错误 |

## 6. 实施路线图（7 天）

### Day 1：清理与精简（不写业务代码）
- [x] 删除 `knowledge/` 模块（P0 砍掉）
- [x] 删除 `workflow-definition/` 模块
- [x] 删除 `workflow-execution/` 模块
- [x] 更新 `settings.gradle.kts` 去掉删除的模块
- [x] 删除 `AnthropicLikeModelProvider.kt` 和 `GeminiLikeModelProvider.kt`（skeleton）
- [x] 删除 `ProviderType` 枚举里的 ANTHROPIC_LIKE / GEMINI_LIKE / CUSTOM
- [x] 删除 skill-package 里的 workflow.yaml 相关代码

### Day 2：换 SDK
- [x] `model-provider/build.gradle.kts` 添加 `com.aallam.openai:openai-client:4.1.0`
- [x] 重写 `OpenAiCompatibleModelProvider` 用 SDK
- [x] 删除 `JsonElementCodec`（SDK 自带）
- [x] 更新测试

### Day 3：多 Provider 存储 + 设置 UI
- [x] Provider 表改为可多条，前端显示列表 + 添加 + 编辑 + 删除
- [x] DefaultBinding 改为单例配置（存全局默认 providerId + modelId）
- [x] SettingsScreen 重写：Provider 列表 + ProviderEditorDialog + DefaultBinding 切换

### Day 4：Skill 加载 + 导入 UI
- [x] 简化 `SkillPackageLoader` → 支持单 .md 和文件夹（SKILL.md + references/）
- [x] 定义 `InstalledSkill` Room 表（name / description / body / tools / model / sourcePath / referenceCount）
- [x] SkillsScreen：列表 + 文件选择器导入（单文件 / 文件夹）+ 详情 BottomSheet
- [x] ChatListScreen 底部加 Skills / Settings 导航入口

### Day 5：Tool 系统 + 内置工具
- [x] 实现 `HttpFetchTool`（OkHttp，截断 8000 字符，requiresConfirmation=true）
- [x] 实现 `CurrentTimeTool`（支持时区参数）
- [x] 实现 `ReadTextFileTool`（读本地文件，截断 8000 字符，requiresConfirmation=true）
- [x] 在 MainActivity 装配时 register 到 ToolRegistry

### Day 6：Tool Calling 循环（核心）
- [x] `ConversationRuntime.sendMessage` 改为支持循环
- [x] 每次 tool call → 执行 → result 注入 → 再调模型
- [x] 循环上限 8 次
- [x] `@skill_name` 前缀解析 + skill runtime 构建
- [ ] ToolCallBubble UI 组件（延后到 Day 7 打磨）

### Day 7：@技能 + 打磨
- [x] TextField 输入 `@` 触发 Popup
- [x] 前缀匹配技能列表
- [x] 发送时解析 `@skill_name` 前缀
- [x] 替换 MarkdownText 为 compose-markdown 库
- [ ] ToolCallBubble UI（P1 延后，当前 tool 结果直接包含在 assistant 回复中）
- [ ] 整体交互测试 + 修 bug（需要实际编译运行）

## 7. 不做的事（明确拒绝清单）

- ❌ 自研 workflow DSL（template/branch/loop/return）
- ❌ 自研 embedding / chunker / vector index
- ❌ Anthropic 原生 protocol
- ❌ Gemini 原生 protocol
- ❌ 插件市场 / 云同步
- ❌ 可视化 workflow 编辑器
- ❌ 多人协作
- ❌ 账号系统
- ❌ 执行日志页面（日志写到 db 就行，不做 UI）
- ❌ PDF / DOCX 导入（P0 不做 RAG）
- ❌ 流式输出（P1 再说）
- ❌ ZIP 技能包（P1 再说）

## 8. 技术栈最终形态

```
Kotlin 2.0.21
AGP 8.7.2
Jetpack Compose (Material 3)
Room 2.6.1
OkHttp 4.12 (被 openai-kotlin 间接使用)
com.aallam.openai:openai-client:3.8.2   ← 新增
dev.jeziellago:compose-markdown:0.5.7    ← 新增
kotlinx.serialization 1.7.3
kotlinx.coroutines 1.9
```

## 9. 投喂模型的纪律

- 改一个函数而不是重写一个文件（用 str_replace）
- 不要让模型加 TODO 注释——要做就做，不做就删
- 错误信息一律简短：`AppError("NOT_FOUND", "conversation")` 就够了
- 不要加"防御性"的 edge case（NullPointerException 让它抛，顶层 catch）
- 接口变更先在本 SPEC 写清楚再改代码
- 禁止模型主动扩展 scope
