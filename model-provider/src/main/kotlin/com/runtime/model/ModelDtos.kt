package com.runtime.model

data class ModelMessage(
    val role: String,
    val content: String,
    val toolCallId: String? = null
)

data class ModelToolSpec(
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

data class ModelToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class ModelGenerateRequest(
    val messages: List<ModelMessage>,
    val tools: List<ModelToolSpec> = emptyList(),
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class ModelUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null
)

data class ModelGenerateResult(
    val text: String,
    val toolCalls: List<ModelToolCall> = emptyList(),
    val finishReason: String? = null,
    val usage: ModelUsage? = null,
    val rawProviderMessageId: String? = null
)
