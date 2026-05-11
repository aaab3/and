package com.runtime.workflow

import com.runtime.core.AppResult

interface WorkflowParser {
    suspend fun parse(
        yamlContent: String
    ): AppResult<ParsedWorkflowDefinition>
}
