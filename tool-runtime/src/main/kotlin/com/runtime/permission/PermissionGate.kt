package com.runtime.permission

import com.runtime.core.AppResult

interface PermissionGate {
    suspend fun evaluate(
        request: PermissionRequest
    ): AppResult<PermissionDecision>
}
