# 数据库草图 v1

这份文档的目标不是直接生成 Room 代码，而是把 P0 阶段最容易漂移的数据骨架先定住。

原则：
- 先服务 MVP 主链，不提前为远期能力预埋大量空字段
- 业务库不保存真实 secret 明文
- 聊天主链、技能定义链、技能执行链分别有清晰数据边界
- 普通模型实现时优先贴近这里的表和字段命名

---

## 0. 设计规则

### 0.1 主键规则
- 所有主实体统一使用 `String` 类型 `id`
- ID 生成策略先不在本稿强绑定，可由应用层生成 UUID
- 不要求 P0 先做自增主键体系

### 0.2 时间字段规则
- 时间统一使用 `createdAtEpochMs` / `updatedAtEpochMs`
- 如实体天然不会更新，可只保留 `createdAtEpochMs`

### 0.3 secret 规则
- `CredentialRef` 只保存 `secretAlias`
- 真正 API key / token 只存在 `SecretProvider` 对接的安全存储
- Room / SQLite 中不保存 secret 明文、副本、缓存影子字段

### 0.4 JSON 字段规则
P0 允许少量 JSON 文本字段，避免前期表爆炸：
- headers
- capabilities
- metadata
- schemas
- inputs/defaults/output

但不要把本该是主关系的数据全塞进 JSON。

---

## 1. 聊天主链数据

### 1.1 conversations
```text
conversations
- id: String (PK)
- workspaceId: String
- title: String?
- createdAtEpochMs: Long
- updatedAtEpochMs: Long
```

用途：
- 会话列表
- 会话打开/恢复
- 会话级绑定覆盖入口

### 1.2 messages
```text
messages
- id: String (PK)
- conversationId: String (FK -> conversations.id)
- role: String
- content: String
- toolCallId: String?
- createdAtEpochMs: Long
- metadataJson: String
```

约束：
- `role` 只允许 P0 已定义角色：`USER` / `ASSISTANT` / `SYSTEM` / `TOOL`
- 查询历史时按 `createdAtEpochMs ASC` 排序

索引建议：
- `(conversationId, createdAtEpochMs)`

---

## 2. Provider / Model / Binding 数据

### 2.1 provider_definitions
```text
provider_definitions
- id: String (PK)
- providerType: String
- name: String
- baseUrl: String
- defaultHeadersJson: String
- capabilitiesJson: String
- createdAtEpochMs: Long
- updatedAtEpochMs: Long
```

说明：
- `providerType` 典型值：`OPENAI_COMPATIBLE` / `ANTHROPIC_LIKE` / `GEMINI_LIKE` / `CUSTOM`
- `capabilitiesJson` 存能力标签集合，不在 P0 过度规范化

### 2.2 credential_refs
```text
credential_refs
- id: String (PK)
- providerId: String (FK -> provider_definitions.id)
- displayName: String
- secretAlias: String
- createdAtEpochMs: Long
- updatedAtEpochMs: Long
```

说明：
- `secretAlias` 是安全存储中的定位名
- 不新增 `secretValue` 字段

### 2.3 model_profiles
```text
model_profiles
- id: String (PK)
- providerId: String (FK -> provider_definitions.id)
- modelId: String
- displayName: String
- contextWindow: Int?
- capabilitiesJson: String
- createdAtEpochMs: Long
- updatedAtEpochMs: Long
```

### 2.4 model_bindings
```text
model_bindings
- id: String (PK)
- targetType: String
- targetId: String?
- providerId: String (FK -> provider_definitions.id)
- modelProfileId: String (FK -> model_profiles.id)
- credentialRefId: String (FK -> credential_refs.id)
- priority: Int
- createdAtEpochMs: Long
- updatedAtEpochMs: Long
```

说明：
- `targetType`: `GLOBAL` / `WORKSPACE` / `SKILL` / `CONVERSATION`
- `targetId`：
  - GLOBAL 时可为 `null`
  - 其他 scope 时必须对应实体 id
- `priority` 给同 scope 内排序兜底，但真正解析顺序仍以 `BindingResolver` 规则为准

索引建议：
- `(targetType, targetId)`

---

## 3. 技能定义链数据

### 3.1 installed_skills
```text
installed_skills
- id: String (PK)
- name: String
- description: String
- version: String
- sourceType: String
- sourceRef: String
- workflowEntry: String
- manifestJson: String
- markdownBody: String
- installedAtEpochMs: Long
- updatedAtEpochMs: Long
```

说明：
- `sourceType`: `DIRECTORY` / `ZIP`
- `sourceRef`：原始导入来源路径或导入后内部定位值
- `manifestJson` 存 frontmatter 标准化结果
- `markdownBody` 存人类可读正文，不把它当执行 DSL

### 3.2 workflow_definitions
```text
workflow_definitions
- id: String (PK)
- skillId: String (FK -> installed_skills.id)
- version: String
- yamlContent: String
- normalizedJson: String
- createdAtEpochMs: Long
- updatedAtEpochMs: Long
```

说明：
- `yamlContent` 保留原始工作流定义
- `normalizedJson` 保存 parser 输出后的标准结构快照
- validator 不应依赖运行时副作用填充这里的数据

索引建议：
- `(skillId)`

---

## 4. 工具与权限数据

P0 可以先把“工具注册”主要留在代码层，但若要本地配置化，推荐最小两张表。

### 4.1 tool_configs
```text
tool_configs
- id: String (PK)
- toolName: String
- enabled: Boolean
- configJson: String
- updatedAtEpochMs: Long
```

说明：
- 仅用于持久化工具开关或少量配置
- 不是执行入口
- 不替代 `ToolRegistry`

### 4.2 permission_policies
```text
permission_policies
- id: String (PK)
- scopeType: String
- scopeId: String?
- toolName: String
- policyType: String
- createdAtEpochMs: Long
- updatedAtEpochMs: Long
```

说明：
- `policyType`: `ALLOW` / `DENY` / `ASK`
- scope 可对应 workspace / skill / conversation
- 具体评估逻辑仍归 `PermissionGate`

---

## 5. 知识库 / RAG 数据

### 5.1 knowledge_documents
```text
knowledge_documents
- id: String (PK)
- workspaceId: String?
- skillId: String?
- sourcePath: String
- title: String
- mimeType: String
- contentHash: String?
- importedAtEpochMs: Long
- metadataJson: String
```

说明：
- 允许绑定到 workspace 或 skill
- P0 不强制做全局去重，但 `contentHash` 预留给后续增量导入

### 5.2 knowledge_chunks
```text
knowledge_chunks
- id: String (PK)
- documentId: String (FK -> knowledge_documents.id)
- chunkIndex: Int
- text: String
- metadataJson: String
```

索引建议：
- `(documentId, chunkIndex)`

### 5.3 chunk_embeddings
```text
chunk_embeddings
- chunkId: String (PK, FK -> knowledge_chunks.id)
- vectorBlob: BLOB / TEXT
- dimension: Int
- updatedAtEpochMs: Long
```

说明：
- 这里是抽象草图，不强行规定最终 SQLite 存储格式
- 若后续改为外部向量索引，实现层可以替换，但上层接口不变

---

## 6. 执行与调试数据

P0 非必需，但建议先留最小日志表，否则后面排错会很痛。

### 6.1 execution_logs
```text
execution_logs
- id: String (PK)
- executionType: String
- conversationId: String?
- skillId: String?
- workflowId: String?
- status: String
- startedAtEpochMs: Long
- finishedAtEpochMs: Long?
- summary: String?
- errorCode: String?
- errorMessage: String?
```

说明：
- `executionType`: `CHAT_SEND` / `WORKFLOW_RUN` / `TOOL_CALL` / `KNOWLEDGE_IMPORT`
- 先做摘要级日志，不要求 P0 就做完整 trace graph

---

## 7. 关系总图

```text
conversations 1 --- N messages

provider_definitions 1 --- N credential_refs
provider_definitions 1 --- N model_profiles
provider_definitions 1 --- N model_bindings
credential_refs      1 --- N model_bindings
model_profiles       1 --- N model_bindings

installed_skills     1 --- N workflow_definitions

knowledge_documents  1 --- N knowledge_chunks
knowledge_chunks     1 --- 1 chunk_embeddings
```

补充说明：
- `model_bindings.targetId` 是逻辑引用，不强制全做 FK，因为它可能指向不同 scope 实体
- `messages` 不直接引用 provider/model/binding，避免聊天记录与配置快照强耦合
- `execution_logs` 可以弱引用 conversation / skill / workflow

---

## 8. P0 非目标

以下内容现在不要进数据库设计：
- 在线技能商店实体
- 用户账号 / 云同步表
- 多人协作权限体系
- 自治 agent 长循环状态机
- 复杂 AB test / billing / quota 表
- provider 原始响应大对象归档系统

---

## 9. 最适合普通模型的实现顺序

1. `conversations`
2. `messages`
3. `provider_definitions`
4. `credential_refs`
5. `model_profiles`
6. `model_bindings`
7. `installed_skills`
8. `workflow_definitions`
9. `knowledge_documents`
10. `knowledge_chunks`
11. `execution_logs`

---

## 10. 与接口蓝图的对应关系

- `conversations` / `messages`
  -> `ConversationRepository` / `MessageRepository` / `ConversationStore`
- `provider_definitions` / `credential_refs` / `model_profiles` / `model_bindings`
  -> BindingResolver 相关 repository
- `installed_skills` / `workflow_definitions`
  -> `SkillPackageLoader` / `WorkflowParser` / `WorkflowValidator`
- `knowledge_documents` / `knowledge_chunks` / `chunk_embeddings`
  -> `DocumentImporter` / `Chunker` / `VectorIndex` / `KnowledgeRetriever`
- `execution_logs`
  -> 后续 execution log / debug trace
