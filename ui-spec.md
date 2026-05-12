# 前端交互规格 v1

## 0. 现状问题

当前 UI 是一个调试级界面：
- 单个 Activity + XML LinearLayout
- 三个裸输入框（Base URL / API Key / Message）
- 一个发送按钮
- 一个 monospace TextView 当日志

问题：
1. 没有聊天气泡，消息混在一起
2. 没有会话列表，无法切换对话
3. 配置和聊天混在同一页面
4. 没有 loading 状态反馈
5. 没有错误提示 UI
6. 没有用 Jetpack Compose（架构文档要求）
7. 没有工作区/技能/工具等入口
8. 没有 Markdown 渲染

## 1. 目标

将 UI 从调试界面升级为可用的聊天产品界面，同时为后续技能/工具/RAG 功能预留入口。

### 1.1 本次范围（P0）
- Jetpack Compose 重写
- 聊天气泡界面（区分 user/assistant）
- 会话列表 + 新建/切换会话
- 独立设置页（Provider/Key 配置）
- 发送中 loading 状态
- 错误 Toast/Snackbar
- Markdown 基础渲染（代码块、加粗、列表）
- 深色/浅色主题

### 1.2 后续范围（P1，本次不实现但预留入口）
- 工作区切换
- 技能管理页
- 工具授权页
- 知识库管理页
- 流式输出
- 消息编辑/重新生成

## 2. 页面结构

```
App
├── ChatListScreen          // 会话列表（侧边栏或独立页）
├── ChatScreen              // 聊天主界面
│   ├── TopBar              // 标题 + 菜单
│   ├── MessageList         // 消息气泡列表
│   │   ├── UserBubble
│   │   └── AssistantBubble (with Markdown)
│   ├── TypingIndicator     // 等待回复时显示
│   └── InputBar           // 输入框 + 发送按钮
└── SettingsScreen          // 设置页
    ├── ProviderSection     // Provider 配置
    ├── ModelSection        // 模型选择
    └── AboutSection        // 关于
```

## 3. 交互流程

### 3.1 首次启动
1. 检测是否有已配置的 Provider
2. 如果没有 → 引导到 SettingsScreen 配置 Base URL + API Key
3. 如果有 → 进入 ChatScreen（最近一次会话或新建）

### 3.2 发送消息
1. 用户输入文本，点击发送（或回车）
2. 输入框清空，user bubble 立即出现
3. InputBar 禁用，显示 TypingIndicator
4. 收到回复 → assistant bubble 出现，InputBar 恢复
5. 出错 → Snackbar 显示错误信息，InputBar 恢复

### 3.3 会话管理
- 左滑或侧边栏打开会话列表
- 点击切换会话，加载历史消息
- 长按可删除会话
- FAB 或顶部按钮新建会话

### 3.4 设置
- 从 ChatScreen TopBar 菜单进入
- Provider 配置：Base URL + API Key + 模型名
- 保存后立即生效（下次发送使用新配置）

## 4. 组件设计

### 4.1 MessageBubble
```
UserBubble:
- 右对齐
- 主题色背景
- 白色文字
- 圆角矩形

AssistantBubble:
- 左对齐
- 浅灰背景（深色模式下深灰）
- 正常文字色
- 支持 Markdown 渲染
- 代码块有背景色 + 复制按钮
```

### 4.2 InputBar
```
- 圆角输入框，占满宽度减去发送按钮
- 发送按钮：圆形，主题色
- 输入为空时发送按钮灰色不可点
- 发送中时显示 loading spinner 替代发送图标
- 支持多行输入（最多展开 4 行）
```

### 4.3 TopBar
```
- 左侧：汉堡菜单（打开会话列表）或返回
- 中间：当前会话标题
- 右侧：设置图标
```

### 4.4 ChatList
```
- 每项显示：会话标题 + 最后一条消息预览 + 时间
- 顶部：新建会话按钮
- 滑动删除
```

## 5. 技术方案

### 5.1 依赖新增
```kotlin
// Jetpack Compose
implementation("androidx.activity:activity-compose:1.9.0")
implementation("androidx.compose.ui:ui:1.6.8")
implementation("androidx.compose.material3:material3:1.2.1")
implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
implementation("androidx.navigation:navigation-compose:2.7.7")

// Markdown 渲染
implementation("com.halilibo.compose-richtext:richtext-commonmark:1.0.0-alpha01")
// 或自行用 commonmark-java 解析 + Compose AnnotatedString
```

### 5.2 架构
```
ui/
├── theme/
│   ├── Theme.kt
│   ├── Color.kt
│   └── Type.kt
├── navigation/
│   └── AppNavigation.kt
├── chat/
│   ├── ChatScreen.kt
│   ├── ChatViewModel.kt
│   ├── MessageBubble.kt
│   ├── InputBar.kt
│   └── TypingIndicator.kt
├── chatlist/
│   ├── ChatListScreen.kt
│   └── ChatListViewModel.kt
└── settings/
    ├── SettingsScreen.kt
    └── SettingsViewModel.kt
```

### 5.3 ViewModel 职责
- `ChatViewModel`：持有当前会话消息列表、发送状态、调用 `ConversationRuntime`
- `ChatListViewModel`：持有会话列表、创建/删除会话
- `SettingsViewModel`：持有 Provider 配置、保存到 repository + SecretProvider

### 5.4 状态管理
```kotlin
// ChatViewModel 核心状态
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val conversationTitle: String = "New Chat"
)

data class UiMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long
)
```

## 6. 视觉规范

### 6.1 配色
- 主色：Material 3 动态取色或固定蓝色系
- User bubble：Primary color
- Assistant bubble：SurfaceVariant
- 背景：Surface
- 输入框：SurfaceContainer

### 6.2 间距
- 消息间距：8dp
- 气泡内边距：12dp horizontal, 8dp vertical
- 屏幕边距：16dp
- 气泡最大宽度：屏幕宽度 80%

### 6.3 字体
- 消息正文：Body Large (16sp)
- 代码块：Monospace 14sp
- 时间戳：Label Small (11sp)
- 会话标题：Title Medium

## 7. 实施顺序

1. 添加 Compose 依赖，创建 theme
2. 实现 ChatScreen + ChatViewModel（替代当前 Activity）
3. 实现 MessageBubble（user/assistant 区分）
4. 实现 InputBar + 发送逻辑
5. 实现 TypingIndicator + 错误提示
6. 实现 SettingsScreen（Provider 配置独立出来）
7. 实现 ChatListScreen（会话列表）
8. 接入 Navigation
9. 添加 Markdown 渲染
10. 深色主题适配

## 8. 验收标准

- [ ] 能正常发送消息并看到气泡式回复
- [ ] 消息区分 user（右）和 assistant（左）
- [ ] 发送中有明确的 loading 状态
- [ ] 错误有 Snackbar 提示
- [ ] 能切换/新建会话
- [ ] Provider 配置在独立设置页
- [ ] 支持深色模式
- [ ] 代码块有基础高亮和复制功能
- [ ] 历史消息加载正确
- [ ] 输入框支持多行
