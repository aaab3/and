package com.runtime.binding

data class BindingResolveRequest(
    val workspaceId: String,
    val skillId: String? = null,
    val conversationId: String? = null
)
