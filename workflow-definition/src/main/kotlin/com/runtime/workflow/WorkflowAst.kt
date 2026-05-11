package com.runtime.workflow

enum class WorkflowStepType {
    TEMPLATE,
    CALL_MODEL,
    CALL_TOOL,
    RETRIEVE_KNOWLEDGE,
    BRANCH,
    RETURN
}

data class ParsedWorkflowDefinition(
    val id: String,
    val name: String,
    val version: String,
    val steps: List<WorkflowStep>
)

sealed interface WorkflowStep {
    val id: String
    val type: WorkflowStepType
}

data class TemplateStep(
    override val id: String,
    val template: String,
    val outputKey: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.TEMPLATE
}

data class CallModelStep(
    override val id: String,
    val inputKey: String,
    val outputKey: String,
    val bindingRef: String? = null,
    val toolNames: List<String> = emptyList()
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.CALL_MODEL
}

data class CallToolStep(
    override val id: String,
    val toolName: String,
    val inputKey: String,
    val outputKey: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.CALL_TOOL
}

data class RetrieveKnowledgeStep(
    override val id: String,
    val queryKey: String,
    val outputKey: String,
    val knowledgeScopeId: String? = null
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.RETRIEVE_KNOWLEDGE
}

data class BranchCase(
    val equals: String,
    val nextStepId: String
)

data class BranchStep(
    override val id: String,
    val inputKey: String,
    val cases: List<BranchCase>,
    val defaultNextStepId: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.BRANCH
}

data class ReturnStep(
    override val id: String,
    val outputKey: String
) : WorkflowStep {
    override val type: WorkflowStepType = WorkflowStepType.RETURN
}
