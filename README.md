# 项目文档导航 v2

本项目在 v2 做了大幅精简。v1 的规划文档（overview-spec / development-order / delegation-prompts / acceptance-checklists / implementation-start-guide / wiggly-soaring-quilt / skill-package-and-workflow-examples）已全部删除。

## 当前文档清单

- [`SPEC.md`](SPEC.md) — **主规格文档**。产品范围、架构决策、UI 交互流程、7 天路线图、明确砍掉的功能
- [`ui-spec.md`](ui-spec.md) — 前端页面结构与视觉规范（配合 SPEC §5 看）
- [`core-interface-blueprint.md`](core-interface-blueprint.md) — 接口清单参考（v1 遗留，供查阅，已部分废弃）
- [`database-sketch.md`](database-sketch.md) — 数据库草图（v1 遗留）

## v2 与 v1 的差异（简要）

| 方面 | v1 | v2 |
|------|-----|-----|
| 规划阶段 | A/B/C/D 四阶段 × 15 个 Prompt | 7 天单路线图 |
| Provider 协议 | 4 种 | 仅 OpenAI 兼容 |
| Skill 定义 | Markdown + workflow.yaml DSL | 纯 Markdown + frontmatter |
| Skill 执行 | 自研 WorkflowExecutor | 模型 tool calling 循环 |
| RAG | 自研 embedding + chunker + vector index | P0 砍掉 |
| Gradle 模块 | 16 个 | 13 个（删了 knowledge / workflow-definition / workflow-execution） |

## 工程模块

```
core-result                数据层公共 AppResult / AppError
conversation-data          Conversation / Message model + repository 接口
conversation-data-sqlite-jvm  JVM 测试用 SQLite 实现
conversation-store-core    DefaultConversationStore
conversation-runtime       DefaultConversationRuntime (sendMessage)
binding-data               Provider / Credential / Model / Binding 数据模型
binding-data-sqlite-jvm    JVM 测试用实现
binding-runtime            DefaultBindingResolver
secret-api                 SecretProvider 接口
secret-sqlite-jvm          JVM 测试用实现
model-provider             OpenAI-compatible provider + registry
skill-package              Skill Markdown 加载器
tool-runtime               ToolRegistry + PermissionGate
android-app                Android App（Compose UI + Room）
```

## 开始开发

阅读 [`SPEC.md`](SPEC.md)，按第 6 节的 7 天路线图推进。
