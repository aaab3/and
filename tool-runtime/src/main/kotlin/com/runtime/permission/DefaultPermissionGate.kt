package com.runtime.permission

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Policy-based gate with no UI. Callers (e.g. workflow executor) must not run tools when decision is not [PermissionDecision.Allow].
 *
 * Precedence: explicit deny list → [PermissionRequest.requiresUserConfirmation] or confirmation-required set → allow.
 */
class DefaultPermissionGate(
    private val deniedToolNames: Set<String> = emptySet(),
    private val confirmationRequiredToolNames: Set<String> = emptySet()
) : PermissionGate {

    private val deniedNormalized = deniedToolNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    private val confirmNormalized = confirmationRequiredToolNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    override suspend fun evaluate(request: PermissionRequest): AppResult<PermissionDecision> = withContext(Dispatchers.Default) {
        val name = request.toolName.trim()
        if (name.isEmpty()) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "toolName must not be blank")
            )
        }

        if (name in deniedNormalized) {
            return@withContext AppResult.Success(
                PermissionDecision.Deny("Tool is denied by policy")
            )
        }

        if (request.requiresUserConfirmation || name in confirmNormalized) {
            return@withContext AppResult.Success(
                PermissionDecision.RequiresUserConfirmation(
                    "Tool execution requires user confirmation before proceeding"
                )
            )
        }

        AppResult.Success(PermissionDecision.Allow)
    }
}
