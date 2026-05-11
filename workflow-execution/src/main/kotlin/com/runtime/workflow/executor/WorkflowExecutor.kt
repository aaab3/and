package com.runtime.workflow.executor

import com.runtime.core.AppResult

interface WorkflowExecutor {
    suspend fun execute(
        request: WorkflowExecutionRequest
    ): AppResult<WorkflowExecutionResult>
}
