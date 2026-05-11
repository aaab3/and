package com.runtime.binding

enum class ProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC_LIKE,
    GEMINI_LIKE,
    CUSTOM
}

data class ProviderDefinition(
    val id: String,
    val providerType: ProviderType,
    val name: String,
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val capabilities: Set<String> = emptySet()
)

data class CredentialRef(
    val id: String,
    val providerId: String,
    val displayName: String,
    val secretAlias: String
)

data class ModelProfile(
    val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val capabilities: Set<String> = emptySet()
)

enum class BindingTargetType {
    GLOBAL,
    WORKSPACE,
    SKILL,
    CONVERSATION
}

data class ModelBinding(
    val id: String,
    val targetType: BindingTargetType,
    val targetId: String?,
    val providerId: String,
    val modelProfileId: String,
    val credentialRefId: String,
    val priority: Int = 0
)
