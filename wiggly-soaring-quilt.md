# Context
用户要做一个“功能很多、主要在手机上使用”的大模型应用，首发以 Android 为主。核心诉求不是先做极简聊天壳，而是做一个后续可以不断加功能的平台型产品，但用户当前倾向于先以“一体化应用”落地，再在内部把扩展点设计好，避免过早平台化导致首版过重。

已明确的关键要求：
- 首发平台：Android 优先
- 客户端技术栈：Kotlin 原生优先
- 产品形态：一体化应用优先，但架构必须保留强扩展性
- API：需要支持不同技能/工作区/用户绑定不同 key / 模型（密钥隔离）
- 技能：技能 MD 不只是提示词，而是要能表达工作流
- 技能工作流首发能力：条件分支、循环/重试、多模型串联、自动调工具
- 技能分发：目录/ZIP 技能包导入
- 首发额外能力：本地知识库 / RAG
- 后续方向：未来会继续加很多新功能，因此要求模块边界清晰、扩展能力强

这已经足够支撑一套明确的初版架构：不是做“聊天 App + 几个配置页”，而是做“面向技能执行的大模型客户端内核”。

# Recommended approach
建议按“单体产品 + 内核分层 + 可插拔能力”的方式设计，而不是一开始就做完整插件市场。这样能兼顾首版交付效率和长期扩展性。

## 1. 总体架构
采用六层结构：

1. **App Shell（Android UI 层）**
   - 使用 **Kotlin + Jetpack Compose**。
   - 负责聊天界面、技能管理、API 配置、知识库入口、工具授权、会话历史、工作区切换。
   - UI 只消费 ViewModel / UseCase，不直接碰模型 SDK、技能解析器、工具实现。

2. **Application Layer（应用编排层）**
   - 负责 use case 编排，如：发送消息、运行技能、导入技能包、绑定模型、执行检索增强问答。
   - 对 UI 暴露稳定的业务入口。

3. **Conversation Runtime（会话运行时）**
   - 统一处理消息流、上下文拼装、模型调用、技能执行、工具调用、RAG 注入、记忆注入。
   - 所有聊天、技能、工作流都必须经过统一 runtime。
   - 关键接口建议固定为：
     - `ModelProvider`
     - `ProviderRegistry`
     - `SkillPackageLoader`
     - `WorkflowExecutor`
     - `ToolRegistry`
     - `KnowledgeRetriever`
     - `SecretProvider`
     - `ConversationStore`

4. **Capability Modules（能力模块层）**
   - 每个主要能力独立成模块，但首版全部内置发布：
     - 聊天模块
     - API / 模型配置模块
     - 技能包模块
     - 工作流模块
     - 工具调用模块
     - 本地知识库 / RAG 模块
     - 历史与记忆模块
   - 这样首版仍是一体化产品，但内部已经具备持续加功能的能力。

5. **Infrastructure Adapters（基础设施适配层）**
   - 模型供应商适配器
   - 本地文件系统适配器
   - 向量索引/检索适配器
   - 网络与下载适配器
   - Android 系统能力适配器（文件选择、分享、通知等）

6. **Persistence & Security（持久化与安全层）**
   - Room/SQLite 保存：会话、消息、技能、工具配置、模型绑定、知识库索引元数据、执行日志。
   - API key、token 放系统安全存储，不直接明文进业务库。

## 2. Android 技术栈建议
建议技术组合：
- **语言/UI**：Kotlin + Jetpack Compose
- **架构**：Clean-ish modular architecture + MVVM
- **DI**：Hilt / Koin（二选一，Hilt 更主流）
- **本地数据库**：Room
- **序列化**：kotlinx.serialization
- **网络**：Ktor 或 Retrofit（若多 provider 协议差异明显，Ktor 更灵活）
- **后台任务**：WorkManager
- **安全存储**：Android Keystore + EncryptedSharedPreferences / 等价封装
- **文档解析**：Markdown parser + YAML frontmatter parser

原因：你的产品后续会大量触达 Android 系统能力和本地资源，原生栈更稳。

## 3. API 接入设计
因为你需要“密钥隔离 + 多模型串联 + 自动调工具”，不要把模型配置设计成单全局配置。

建议核心实体：
- `ProviderDefinition`
  - 协议类型（OpenAI-compatible / Anthropic-like / Gemini-like / custom）
  - base URL
  - 默认 headers
  - 能力声明（streaming / tool calling / image / json mode 等）
- `CredentialRef`
  - 指向安全存储中的 key/token
- `ModelProfile`
  - 模型名、上下文窗口、能力标签、计费标签（可选）
- `BindingScope`
  - 全局默认
  - 工作区默认
  - 技能默认
  - 会话覆盖

运行时由 `ModelProvider` 根据 binding scope 决定具体走哪个 provider/model/credential。

这样后面你要支持：
- 一个技能固定用某模型
- 同一工作区内多个技能用不同 key
- 多模型工作流串联
都不需要重构数据层。

## 4. 技能包格式设计
既然技能不只是单个 md，而是 **目录/ZIP 技能包**，建议首版就定义一个可扩展包结构：

- `skill.md`：主说明文档，含 frontmatter
- `prompts/`：模板片段
- `assets/`：示例、静态资源
- `schemas/`：输入输出 schema
- `tools.json`：可选，声明技能依赖的工具
- `icon.png`：可选

建议 `skill.md` 采用：
- **Markdown 正文**：说明、提示词模板、示例、人类可读文档
- **YAML frontmatter**：机器可执行元数据

frontmatter 最少支持：
- `name`
- `description`
- `version`
- `inputs`
- `defaults`
- `model`
- `context`
- `tools`
- `permissions`
- `workflow`
- `output`

## 5. 工作流 DSL 设计
你希望首发就支持：条件分支、循环/重试、多模型串联、自动调工具。
所以不要把 DSL 设计成“只有一段 prompt”。

但首版仍然建议 **有限 DSL**，不要直接做任意脚本执行。

推荐 workflow 节点类型：
- `collect_input`
- `template`
- `retrieve_knowledge`
- `call_model`
- `call_tool`
- `branch`
- `retry`
- `foreach`（可选，若你确实需要批量任务）
- `transform`
- `return`

推荐控制原则：
- 循环只允许有限次数
- 重试必须有最大次数
- 分支条件只支持有限表达式或结构化判断结果
- 多模型串联通过显式 step 配置，不允许隐式递归调度
- 工具自动调用必须经过工具权限策略检查

也就是说：
- **可编排**，但不 **任意脚本化**
- **强扩展**，但不 **失控**

## 6. 工具调用系统设计
工具系统必须从第一天就有统一协议和权限模型。

建议工具分三级：
1. **内置纯软件工具**
   - 文本处理
   - 知识库检索
   - 网页抓取
   - 文件内容提取
2. **受限设备工具**
   - 文件选择
   - 相册/图片
   - 分享
   - 录音
   - 通知
3. **外部连接工具**
   - HTTP API
   - webhook
   - 未来 MCP / 第三方扩展

每个工具统一 manifest：
- `name`
- `description`
- `inputSchema`
- `outputSchema`
- `permissionLevel`
- `requiresUserConfirmation`
- `timeoutMs`
- `supportedScopes`
- `idempotencyHint`

执行路径统一为：
`WorkflowExecutor -> ToolRegistry -> PermissionGate -> ToolAdapter`

后续无论你加本地能力、联网能力还是 MCP，都走这条链路。

## 7. 本地知识库 / RAG 设计
你希望首发就做本地知识库 / RAG，这会成为核心能力之一。

建议首版范围：
- 支持导入 txt / md / pdf / docx（可逐步扩）
- 本地切块、索引、检索
- 技能或聊天可声明是否注入检索上下文
- 检索源可绑定到工作区或技能

建议抽象接口：
- `DocumentImporter`
- `Chunker`
- `EmbeddingProvider`
- `VectorIndex`
- `KnowledgeRetriever`

注意：
- 如果 embedding 也走外部 API，需支持独立 credential/model
- 检索结果要保留 source metadata，便于回答时引用
- 首版先做本地单设备索引，不必先做云同步

## 8. 扩展性落地规则
为了保证后续加功能不把系统拖垮，建议从一开始执行这些硬规则：
- UI 不直接调模型 SDK，只调应用层用例
- 技能包不直接控制页面逻辑，只描述 runtime 行为
- 工具全部走 ToolRegistry，不允许散落调用
- provider 走 adapter，不在业务代码里写分散的 if/else
- 会话、技能执行、工具执行、检索执行全部记 execution log
- 新功能优先以模块接入，而不是往聊天页面继续堆逻辑

建议内部采用“模块注册”机制，而不是首版就开放第三方插件安装：
- 模块注册自己的路由、设置、服务、工具、技能来源
- 首发全部内置
- 二期再开放外部技能仓库、远程工具源或插件生态

## 9. 推荐 MVP 范围
建议 MVP 控制在这 7 块：
1. 多会话聊天
2. 多 provider / 多模型 / 多 key 绑定
3. 技能包（目录/ZIP）导入、解析、安装
4. 基础工作流执行器（含分支、重试、多模型串联、自动调工具）
5. 工具调用框架 + 2~4 个内置工具
6. 本地知识库导入与检索增强问答
7. 历史记录与本地存储

MVP 不建议首发就做：
- 在线技能商店
- 账号体系与云同步
- 多人协作
- 可视化工作流编辑器
- 完整自治 agent 循环

这些可以建立在首版 runtime 之上继续迭代。

## 10. 建议先产出的设计物
开始开发前，建议先定 6 份核心设计：
1. 系统模块图
2. Runtime 核心接口文档
3. 技能包规范 v0
4. 工作流 DSL 规范 v0
5. Tool manifest 与权限模型 v0
6. 数据库 ER 图

## 11. 建议开发顺序
推荐开发顺序：
1. 先搭 Android 工程骨架与模块边界
2. 先实现 Provider / Credential / Model Binding
3. 再实现基础聊天 runtime
4. 再实现技能包解析与安装
5. 再实现 workflow executor
6. 再接入 tool registry 与权限门
7. 再做本地知识库 / RAG
8. 最后补执行日志、调试页、导入导出能力

这样顺序最稳，因为后面的技能、工具、RAG 都依赖前面的 runtime 和 binding 设计。

# Open questions
现在还剩两类关键未定项，决定实现细节：
1. 自定义 API 首发要兼容哪些协议：
   - OpenAI-compatible
   - Anthropic-like
   - Gemini-like
   - 完全自定义 HTTP
2. 本地知识库 / RAG 首发对文件类型与 embedding 方案的要求：
   - 只做文本/Markdown
   - 还是直接支持 PDF / Office
   - embedding 走本地模型还是远程 API

# Verification
下一步如果继续细化，建议直接输出这 4 份内容：
1. **系统架构图**：模块关系、依赖方向、数据流
2. **MVP 功能清单**：按版本拆分 P0 / P1 / P2
3. **数据库草图**：provider、credential、skill package、workflow、conversation、knowledge base 等表结构
4. **技能包与 DSL 示例**：给出一个完整 ZIP 技能包示例和一次执行链路

如果你愿意，我下一步可以直接继续给你其中一种：
- **方案一：产品 + 技术总体架构图**
- **方案二：MVP 功能拆解 + 开发顺序**
- **方案三：技能包 MD/ZIP 规范初稿**
- **方案四：数据库表结构和核心类设计**
