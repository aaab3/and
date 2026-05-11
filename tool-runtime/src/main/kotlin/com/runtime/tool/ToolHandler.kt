package com.runtime.tool

import com.runtime.core.AppResult

interface ToolHandler {
    val manifest: ToolManifest

    suspend fun execute(
        request: ToolExecutionRequest
    ): AppResult<ToolExecutionResult>
}
