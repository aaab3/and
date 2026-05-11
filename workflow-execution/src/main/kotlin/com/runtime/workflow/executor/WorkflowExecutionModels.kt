package com.runtime.workflow.executor

import com.runtime.workflow.ParsedWorkflowDefinition

data class WorkflowExecutionRequest(
    val workflow: ParsedWorkflowDefinition,
    val workspaceId: String,
    val conversationId: String? = null,
    val skillId: String? = null,
    val inputs: Map<String, String> = emptyMap()
)

data class WorkflowExecutionResult(
    val output: String,
    val context: Map<String, String>
)
