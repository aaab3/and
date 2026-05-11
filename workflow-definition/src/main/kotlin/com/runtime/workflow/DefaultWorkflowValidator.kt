package com.runtime.workflow

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Static validation only — no execution. Complements [DefaultWorkflowParser].
 */
class DefaultWorkflowValidator : WorkflowValidator {

    override suspend fun validate(workflow: ParsedWorkflowDefinition): AppResult<Unit> = withContext(Dispatchers.Default) {
        if (workflow.id.isBlank() || workflow.name.isBlank() || workflow.version.isBlank()) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.VALIDATION_FAILED, message = "Workflow id, name, and version must be non-blank")
            )
        }
        if (workflow.steps.isEmpty()) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.VALIDATION_FAILED, message = "Workflow must contain at least one step")
            )
        }

        val idToIndex = mutableMapOf<String, Int>()
        workflow.steps.forEachIndexed { index, step ->
            if (step.id.isBlank()) {
                return@withContext AppResult.Failure(
                    AppError(
                        ErrorCodes.VALIDATION_FAILED,
                        message = "Step id must not be blank",
                        metadata = mapOf("stepIndex" to "$index")
                    )
                )
            }
            if (idToIndex.put(step.id, index) != null) {
                return@withContext AppResult.Failure(
                    AppError(
                        ErrorCodes.VALIDATION_FAILED,
                        message = "Duplicate workflow step id: ${step.id}",
                        metadata = mapOf("stepId" to step.id)
                    )
                )
            }
        }

        val outputKeys = mutableSetOf<String>()
        workflow.steps.forEachIndexed { index, step ->
            when (val r = validateStepFields(step, index)) {
                is AppResult.Failure -> return@withContext r
                is AppResult.Success -> Unit
            }
            when (val r = validateKeyReferences(step, index, outputKeys)) {
                is AppResult.Failure -> return@withContext r
                is AppResult.Success -> Unit
            }
            val produced = producedOutputKey(step)
            if (produced != null) {
                if (!outputKeys.add(produced)) {
                    return@withContext AppResult.Failure(
                        AppError(
                            ErrorCodes.VALIDATION_FAILED,
                            message = "Duplicate outputKey: $produced",
                            metadata = mapOf("outputKey" to produced)
                        )
                    )
                }
            }
        }

        workflow.steps.forEachIndexed { index, step ->
            if (step is BranchStep) {
                when (val r = validateBranch(step, index, idToIndex)) {
                    is AppResult.Failure -> return@withContext r
                    is AppResult.Success -> Unit
                }
            }
        }

        val last = workflow.steps.last()
        if (last !is ReturnStep) {
            return@withContext AppResult.Failure(
                AppError(
                    ErrorCodes.VALIDATION_FAILED,
                    message = "Workflow must end with a return step (terminal return)",
                    metadata = mapOf("lastStepType" to last.type.name)
                )
            )
        }

        AppResult.Success(Unit)
    }

    /** Keys written into execution context (return only *reads* outputKey). */
    private fun producedOutputKey(step: WorkflowStep): String? =
        when (step) {
            is TemplateStep -> step.outputKey
            is CallModelStep -> step.outputKey
            is CallToolStep -> step.outputKey
            is RetrieveKnowledgeStep -> step.outputKey
            is ReturnStep -> null
            is BranchStep -> null
        }

    private fun validateStepFields(step: WorkflowStep, index: Int): AppResult<Unit> {
        fun fail(msg: String): AppResult<Unit> = AppResult.Failure(
            AppError(
                ErrorCodes.VALIDATION_FAILED,
                message = msg,
                metadata = mapOf("stepIndex" to "$index", "stepId" to step.id)
            )
        )
        return when (step) {
            is TemplateStep -> {
                if (step.template.isBlank()) return fail("template must not be blank")
                if (step.outputKey.isBlank()) return fail("outputKey must not be blank")
                AppResult.Success(Unit)
            }
            is CallModelStep -> {
                if (step.inputKey.isBlank()) return fail("inputKey must not be blank")
                if (step.outputKey.isBlank()) return fail("outputKey must not be blank")
                AppResult.Success(Unit)
            }
            is CallToolStep -> {
                if (step.toolName.isBlank()) return fail("toolName must not be blank")
                if (step.inputKey.isBlank()) return fail("inputKey must not be blank")
                if (step.outputKey.isBlank()) return fail("outputKey must not be blank")
                AppResult.Success(Unit)
            }
            is RetrieveKnowledgeStep -> {
                if (step.queryKey.isBlank()) return fail("queryKey must not be blank")
                if (step.outputKey.isBlank()) return fail("outputKey must not be blank")
                AppResult.Success(Unit)
            }
            is BranchStep -> {
                if (step.inputKey.isBlank()) return fail("inputKey must not be blank")
                if (step.defaultNextStepId.isBlank()) return fail("defaultNextStepId must not be blank")
                if (step.cases.isEmpty()) {
                    return fail("branch must have at least one case")
                }
                val equalsSeen = mutableSetOf<String>()
                for (c in step.cases) {
                    if (c.equals.isBlank()) return fail("branch case equals must not be blank")
                    if (c.nextStepId.isBlank()) return fail("branch case nextStepId must not be blank")
                    if (!equalsSeen.add(c.equals)) {
                        return fail("duplicate branch case equals: ${c.equals}")
                    }
                }
                AppResult.Success(Unit)
            }
            is ReturnStep -> {
                if (step.outputKey.isBlank()) return fail("outputKey must not be blank")
                AppResult.Success(Unit)
            }
        }
    }

    /**
     * Keys available before step [index]: `inputs.*` and outputKeys produced by steps `[0, index)`.
     * [outputKeysSoFar] is the set of output keys from steps before [index] (caller maintains while iterating).
     */
    private fun validateKeyReferences(
        step: WorkflowStep,
        index: Int,
        outputKeysSoFar: Set<String>
    ): AppResult<Unit> {
        fun checkKey(name: String, value: String): AppResult<Unit> {
            if (isAllowedKeyReference(value, outputKeysSoFar)) return AppResult.Success(Unit)
            return AppResult.Failure(
                AppError(
                    code = ErrorCodes.VALIDATION_FAILED,
                    message = "Unknown $name: must be inputs.* or a prior step outputKey",
                    metadata = mapOf(
                        "stepIndex" to "$index",
                        "stepId" to step.id,
                        name to value
                    )
                )
            )
        }
        return when (step) {
            is TemplateStep -> AppResult.Success(Unit)
            is CallModelStep -> checkKey("inputKey", step.inputKey)
            is CallToolStep -> checkKey("inputKey", step.inputKey)
            is RetrieveKnowledgeStep -> checkKey("queryKey", step.queryKey)
            is BranchStep -> checkKey("inputKey", step.inputKey)
            is ReturnStep -> checkKey("outputKey", step.outputKey)
        }
    }

    private fun isAllowedKeyReference(key: String, outputKeysSoFar: Set<String>): Boolean {
        if (key.startsWith("inputs.")) return true
        return key in outputKeysSoFar
    }

    private fun validateBranch(
        branch: BranchStep,
        branchIndex: Int,
        idToIndex: Map<String, Int>
    ): AppResult<Unit> {
        fun resolve(nextId: String): AppResult<Int> {
            val idx = idToIndex[nextId]
                ?: return AppResult.Failure(
                    AppError(
                        ErrorCodes.VALIDATION_FAILED,
                        message = "branch references unknown step id: $nextId",
                        metadata = mapOf("stepId" to branch.id, "nextStepId" to nextId)
                    )
                )
            return AppResult.Success(idx)
        }

        if (branch.id == branch.defaultNextStepId) {
            return AppResult.Failure(
                AppError(
                    ErrorCodes.VALIDATION_FAILED,
                    message = "branch defaultNextStepId must not point to self",
                    metadata = mapOf("stepId" to branch.id)
                )
            )
        }

        when (val defIdx = resolve(branch.defaultNextStepId)) {
            is AppResult.Failure -> return defIdx
            is AppResult.Success -> {
                if (defIdx.value <= branchIndex) {
                    return AppResult.Failure(
                        AppError(
                            ErrorCodes.VALIDATION_FAILED,
                            message = "branch defaultNextStepId must target a later step (no backward jumps)",
                            metadata = mapOf("stepId" to branch.id, "nextStepId" to branch.defaultNextStepId)
                        )
                    )
                }
            }
        }

        for (c in branch.cases) {
            if (c.nextStepId == branch.id) {
                return AppResult.Failure(
                    AppError(
                        ErrorCodes.VALIDATION_FAILED,
                        message = "branch case must not point to self",
                        metadata = mapOf("stepId" to branch.id)
                    )
                )
            }
            when (val idx = resolve(c.nextStepId)) {
                is AppResult.Failure -> return idx
                is AppResult.Success -> {
                    if (idx.value <= branchIndex) {
                        return AppResult.Failure(
                            AppError(
                                ErrorCodes.VALIDATION_FAILED,
                                message = "branch case nextStepId must target a later step (no backward jumps)",
                                metadata = mapOf("stepId" to branch.id, "nextStepId" to c.nextStepId)
                            )
                        )
                    }
                }
            }
        }

        return AppResult.Success(Unit)
    }
}
