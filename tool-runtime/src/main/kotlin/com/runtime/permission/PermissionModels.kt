package com.runtime.permission

data class PermissionRequest(
    val toolName: String,
    val conversationId: String? = null,
    val skillId: String? = null,
    val workspaceId: String? = null,
    val requiresUserConfirmation: Boolean = false
)

sealed interface PermissionDecision {
    data object Allow : PermissionDecision
    data class Deny(val reason: String) : PermissionDecision
    data class RequiresUserConfirmation(val reason: String) : PermissionDecision
}
