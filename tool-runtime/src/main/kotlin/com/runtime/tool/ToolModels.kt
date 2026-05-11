package com.runtime.tool

data class ToolManifest(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
    val outputSchemaJson: String,
    val requiresUserConfirmation: Boolean = false,
    val timeoutMs: Long = 30_000
)

data class ToolExecutionRequest(
    val toolName: String,
    val argumentsJson: String,
    val conversationId: String? = null,
    val skillId: String? = null,
    val workspaceId: String? = null
)

data class ToolExecutionResult(
    val outputJson: String,
    val metadata: Map<String, String> = emptyMap()
)
