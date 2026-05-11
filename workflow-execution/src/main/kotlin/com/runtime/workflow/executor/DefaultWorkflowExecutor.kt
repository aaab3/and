package com.runtime.workflow.executor

import com.runtime.binding.BindingResolveRequest
import com.runtime.binding.BindingResolver
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.knowledge.KnowledgeRetriever
import com.runtime.model.ModelGenerateRequest
import com.runtime.model.ModelMessage
import com.runtime.model.ModelToolSpec
import com.runtime.model.ProviderRegistry
import com.runtime.permission.PermissionDecision
import com.runtime.permission.PermissionGate
import com.runtime.permission.PermissionRequest
import com.runtime.tool.ToolExecutionRequest
import com.runtime.tool.ToolRegistry
import com.runtime.workflow.BranchStep
import com.runtime.workflow.CallModelStep
import com.runtime.workflow.CallToolStep
import com.runtime.workflow.ParsedWorkflowDefinition
import com.runtime.workflow.RetrieveKnowledgeStep
import com.runtime.workflow.ReturnStep
import com.runtime.workflow.TemplateStep
import com.runtime.workflow.WorkflowStep
import com.runtime.workflow.WorkflowValidator

/**
 * Sequential executor with forward-only branch jumps. Re-validates workflow on each run.
 */
class DefaultWorkflowExecutor(
    private val workflowValidator: WorkflowValidator,
    private val bindingResolver: BindingResolver,
    private val providerRegistry: ProviderRegistry,
    private val toolRegistry: ToolRegistry,
    private val permissionGate: PermissionGate,
    private val knowledgeRetriever: KnowledgeRetriever
) : WorkflowExecutor {

    override suspend fun execute(request: WorkflowExecutionRequest): AppResult<WorkflowExecutionResult> {
        if (request.workspaceId.isBlank()) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "workspaceId must not be blank")
            )
        }

        when (val v = workflowValidator.validate(request.workflow)) {
            is AppResult.Failure -> return v
            is AppResult.Success -> Unit
        }

        val context = request.inputs
            .mapKeys { (k, _) -> "inputs.$k" }
            .toMutableMap()

        val workflow = request.workflow
        val steps = workflow.steps
        val idToIndex = steps.withIndex().associate { it.value.id to it.index }

        var pc = 0
        var iterations = 0
        val maxIter = (steps.size + 1) * 8

        while (pc < steps.size && iterations < maxIter) {
            iterations++
            when (val step = steps[pc]) {
                is BranchStep -> {
                    val nextId = resolveBranch(step, context)
                    val nextPc = idToIndex[nextId]
                        ?: return AppResult.Failure(
                            AppError(
                                ErrorCodes.EXECUTION_ERROR,
                                message = "branch target step not found",
                                metadata = mapOf("nextStepId" to nextId)
                            )
                        )
                    pc = nextPc
                }
                is ReturnStep -> {
                    val output = context[step.outputKey] ?: ""
                    return AppResult.Success(
                        WorkflowExecutionResult(output = output, context = context.toMap())
                    )
                }
                else -> {
                    when (val r = executeStep(step, request, context)) {
                        is AppResult.Failure -> return r
                        is AppResult.Success -> pc++
                    }
                }
            }
        }

        return AppResult.Failure(
            AppError(
                ErrorCodes.EXECUTION_ERROR,
                message = "Workflow execution did not complete with return"
            )
        )
    }

    private fun resolveBranch(step: BranchStep, context: Map<String, String>): String {
        val value = context[step.inputKey]?.trim().orEmpty()
        for (c in step.cases) {
            if (c.equals.trim() == value) return c.nextStepId
        }
        return step.defaultNextStepId
    }

    private suspend fun executeStep(
        step: WorkflowStep,
        request: WorkflowExecutionRequest,
        context: MutableMap<String, String>
    ): AppResult<Unit> =
        when (step) {
            is TemplateStep -> {
                context[step.outputKey] = TemplateRenderer.render(step.template, context)
                AppResult.Success(Unit)
            }
            is CallModelStep -> executeCallModel(step, request, context)
            is CallToolStep -> executeCallTool(step, request, context)
            is RetrieveKnowledgeStep -> executeRetrieve(step, context)
            is BranchStep, is ReturnStep ->
                AppResult.Failure(
                    AppError(ErrorCodes.EXECUTION_ERROR, message = "Unexpected control-flow step in executeStep")
                )
        }

    private suspend fun executeCallModel(
        step: CallModelStep,
        request: WorkflowExecutionRequest,
        context: MutableMap<String, String>
    ): AppResult<Unit> {
        val bindingReq = BindingResolveRequest(
            workspaceId = request.workspaceId,
            skillId = request.skillId,
            conversationId = request.conversationId
        )
        val resolved = when (val r = bindingResolver.resolve(bindingReq)) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val provider = when (val p = providerRegistry.getProvider(resolved.provider.providerType)) {
            is AppResult.Failure -> return p
            is AppResult.Success -> p.value
        }
        val tools = when (val t = buildToolSpecs(step.toolNames)) {
            is AppResult.Failure -> return t
            is AppResult.Success -> t.value
        }
        val prompt = context[step.inputKey] ?: ""
        val genReq = ModelGenerateRequest(
            messages = listOf(ModelMessage(role = "user", content = prompt)),
            tools = tools
        )
        return when (val g = provider.generate(resolved, genReq)) {
            is AppResult.Failure -> g
            is AppResult.Success -> {
                context[step.outputKey] = g.value.text
                AppResult.Success(Unit)
            }
        }
    }

    private fun buildToolSpecs(names: List<String>): AppResult<List<ModelToolSpec>> {
        if (names.isEmpty()) return AppResult.Success(emptyList())
        val out = mutableListOf<ModelToolSpec>()
        for (name in names) {
            when (val t = toolRegistry.getTool(name)) {
                is AppResult.Failure -> return t
                is AppResult.Success -> {
                    val m = t.value.manifest
                    out.add(
                        ModelToolSpec(
                            name = m.name,
                            description = m.description,
                            inputSchemaJson = m.inputSchemaJson
                        )
                    )
                }
            }
        }
        return AppResult.Success(out)
    }

    private suspend fun executeCallTool(
        step: CallToolStep,
        request: WorkflowExecutionRequest,
        context: MutableMap<String, String>
    ): AppResult<Unit> {
        val handler = when (val t = toolRegistry.getTool(step.toolName)) {
            is AppResult.Failure -> return t
            is AppResult.Success -> t.value
        }
        val permReq = PermissionRequest(
            toolName = step.toolName,
            conversationId = request.conversationId,
            skillId = request.skillId,
            workspaceId = request.workspaceId,
            requiresUserConfirmation = handler.manifest.requiresUserConfirmation
        )
        val decision = when (val d = permissionGate.evaluate(permReq)) {
            is AppResult.Failure -> return d
            is AppResult.Success -> d.value
        }
        when (decision) {
            is PermissionDecision.Allow -> Unit
            is PermissionDecision.Deny ->
                return AppResult.Failure(
                    AppError(ErrorCodes.PERMISSION_DENIED, message = decision.reason)
                )
            is PermissionDecision.RequiresUserConfirmation ->
                return AppResult.Failure(
                    AppError(ErrorCodes.PERMISSION_DENIED, message = decision.reason)
                )
        }
        val args = context[step.inputKey] ?: "{}"
        val execReq = ToolExecutionRequest(
            toolName = step.toolName,
            argumentsJson = args,
            conversationId = request.conversationId,
            skillId = request.skillId,
            workspaceId = request.workspaceId
        )
        return when (val e = handler.execute(execReq)) {
            is AppResult.Failure -> e
            is AppResult.Success -> {
                context[step.outputKey] = e.value.outputJson
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun executeRetrieve(
        step: RetrieveKnowledgeStep,
        context: MutableMap<String, String>
    ): AppResult<Unit> {
        val query = context[step.queryKey] ?: ""
        return when (val r = knowledgeRetriever.retrieve(query, topK = 5)) {
            is AppResult.Failure -> r
            is AppResult.Success -> {
                val text = formatRetrieved(r.value)
                context[step.outputKey] = text
                AppResult.Success(Unit)
            }
        }
    }

    private fun formatRetrieved(chunks: List<com.runtime.knowledge.RetrievedChunk>): String =
        chunks.joinToString("\n---\n") { c ->
            buildString {
                append("sourcePath=").append(c.sourcePath).append('\n')
                append("score=").append(c.score).append('\n')
                append(c.text)
            }
        }
}
