package com.runtime.workflow

import com.runtime.core.AppResult

interface WorkflowValidator {
    suspend fun validate(
        workflow: ParsedWorkflowDefinition
    ): AppResult<Unit>
}
