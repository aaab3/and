package com.runtime.workflow

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml

/**
 * Parses YAML only; does not execute or validate cross-step references (Prompt 11).
 */
class DefaultWorkflowParser : WorkflowParser {

    private val yaml: Yaml = Yaml()

    override suspend fun parse(yamlContent: String): AppResult<ParsedWorkflowDefinition> = withContext(Dispatchers.IO) {
        val rootObj = try {
            yaml.load<Any?>(yamlContent)
        } catch (e: Exception) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, message = "Invalid YAML", cause = e.message)
            )
        }
        if (rootObj !is Map<*, *>) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, message = "Workflow root must be a YAML mapping")
            )
        }
        @Suppress("UNCHECKED_CAST")
        val root = rootObj as Map<String, Any?>
        parseDefinition(root)
    }

    private fun parseDefinition(root: Map<String, Any?>): AppResult<ParsedWorkflowDefinition> {
        fun req(key: String): AppResult<String> {
            val v = root[key]?.toString()?.trim()
            return if (v.isNullOrEmpty()) {
                AppResult.Failure(AppError(ErrorCodes.PARSE_ERROR, message = "Missing required workflow field: $key"))
            } else {
                AppResult.Success(v)
            }
        }
        val id = when (val r = req("id")) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val name = when (val r = req("name")) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val version = when (val r = req("version")) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val stepsRaw = root["steps"]
            ?: return AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, message = "Missing workflow field: steps")
            )
        if (stepsRaw !is List<*>) {
            return AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, message = "workflow.steps must be a YAML sequence")
            )
        }
        val steps = mutableListOf<WorkflowStep>()
        stepsRaw.forEachIndexed { index, el ->
            if (el !is Map<*, *>) {
                return AppResult.Failure(
                    AppError(
                        ErrorCodes.PARSE_ERROR,
                        message = "Each workflow step must be a mapping",
                        metadata = mapOf("stepIndex" to "$index")
                    )
                )
            }
            @Suppress("UNCHECKED_CAST")
            val stepMap = el as Map<String, Any?>
            when (val parsed = parseStep(stepMap, index)) {
                is AppResult.Failure -> return parsed
                is AppResult.Success -> steps.add(parsed.value)
            }
        }
        return AppResult.Success(ParsedWorkflowDefinition(id = id, name = name, version = version, steps = steps))
    }

    private fun parseStep(map: Map<String, Any?>, index: Int): AppResult<WorkflowStep> {
        val id = map["id"]?.toString()?.trim()
            ?: return AppResult.Failure(
                AppError(
                    ErrorCodes.PARSE_ERROR,
                    message = "Step missing id",
                    metadata = mapOf("stepIndex" to "$index")
                )
            )
        val typeStr = map["type"]?.toString()?.trim()?.lowercase()
            ?: return AppResult.Failure(
                AppError(
                    ErrorCodes.PARSE_ERROR,
                    message = "Step missing type",
                    metadata = mapOf("stepIndex" to "$index", "stepId" to id)
                )
            )
        return when (typeStr) {
            "template" -> parseTemplate(id, map, index)
            "call_model" -> parseCallModel(id, map, index)
            "call_tool" -> parseCallTool(id, map, index)
            "retrieve_knowledge" -> parseRetrieveKnowledge(id, map, index)
            "branch" -> parseBranch(id, map, index)
            "return" -> parseReturn(id, map, index)
            else -> AppResult.Failure(
                AppError(
                    ErrorCodes.PARSE_ERROR,
                    message = "Unsupported workflow step type: $typeStr",
                    metadata = mapOf("stepIndex" to "$index", "stepId" to id)
                )
            )
        }
    }

    private fun parseTemplate(id: String, map: Map<String, Any?>, index: Int): AppResult<TemplateStep> {
        val template = map["template"]?.toString()
            ?: return stepFieldError(index, id, "template")
        val outputKey = map["outputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "outputKey")
        return AppResult.Success(TemplateStep(id = id, template = template, outputKey = outputKey))
    }

    private fun parseCallModel(id: String, map: Map<String, Any?>, index: Int): AppResult<CallModelStep> {
        val inputKey = map["inputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "inputKey")
        val outputKey = map["outputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "outputKey")
        val bindingRef = map["bindingRef"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val toolNames = stringList(map["toolNames"])
        return AppResult.Success(
            CallModelStep(
                id = id,
                inputKey = inputKey,
                outputKey = outputKey,
                bindingRef = bindingRef,
                toolNames = toolNames
            )
        )
    }

    private fun parseCallTool(id: String, map: Map<String, Any?>, index: Int): AppResult<CallToolStep> {
        val toolName = map["toolName"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "toolName")
        val inputKey = map["inputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "inputKey")
        val outputKey = map["outputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "outputKey")
        return AppResult.Success(CallToolStep(id, toolName, inputKey, outputKey))
    }

    private fun parseRetrieveKnowledge(id: String, map: Map<String, Any?>, index: Int): AppResult<RetrieveKnowledgeStep> {
        val queryKey = map["queryKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "queryKey")
        val outputKey = map["outputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "outputKey")
        val knowledgeScopeId = map["knowledgeScopeId"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return AppResult.Success(
            RetrieveKnowledgeStep(id, queryKey, outputKey, knowledgeScopeId)
        )
    }

    private fun parseBranch(id: String, map: Map<String, Any?>, index: Int): AppResult<BranchStep> {
        val inputKey = map["inputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "inputKey")
        val defaultNext = map["defaultNextStepId"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "defaultNextStepId")
        val casesRaw = map["cases"]
            ?: return stepFieldError(index, id, "cases")
        if (casesRaw !is List<*>) {
            return AppResult.Failure(
                AppError(
                    ErrorCodes.PARSE_ERROR,
                    message = "branch.cases must be a sequence",
                    metadata = mapOf("stepIndex" to "$index", "stepId" to id)
                )
            )
        }
        val cases = mutableListOf<BranchCase>()
        casesRaw.forEachIndexed { ci, item ->
            if (item !is Map<*, *>) {
                return AppResult.Failure(
                    AppError(
                        ErrorCodes.PARSE_ERROR,
                        message = "branch case must be a mapping",
                        metadata = mapOf("stepIndex" to "$index", "caseIndex" to "$ci")
                    )
                )
            }
            @Suppress("UNCHECKED_CAST")
            val cm = item as Map<String, Any?>
            val eq = cm["equals"]?.toString()
                ?: return AppResult.Failure(
                    AppError(
                        ErrorCodes.PARSE_ERROR,
                        message = "branch case missing equals",
                        metadata = mapOf("stepIndex" to "$index", "caseIndex" to "$ci")
                    )
                )
            val next = cm["nextStepId"]?.toString()?.trim()
                ?: return AppResult.Failure(
                    AppError(
                        ErrorCodes.PARSE_ERROR,
                        message = "branch case missing nextStepId",
                        metadata = mapOf("stepIndex" to "$index", "caseIndex" to "$ci")
                    )
                )
            cases.add(BranchCase(equals = eq, nextStepId = next))
        }
        return AppResult.Success(BranchStep(id, inputKey, cases, defaultNext))
    }

    private fun parseReturn(id: String, map: Map<String, Any?>, index: Int): AppResult<ReturnStep> {
        val outputKey = map["outputKey"]?.toString()?.trim()
            ?: return stepFieldError(index, id, "outputKey")
        return AppResult.Success(ReturnStep(id, outputKey))
    }

    private fun stepFieldError(stepIndex: Int, stepId: String, field: String): AppResult<Nothing> =
        AppResult.Failure(
            AppError(
                code = ErrorCodes.PARSE_ERROR,
                message = "Step missing required field: $field",
                metadata = mapOf("stepIndex" to "$stepIndex", "stepId" to stepId)
            )
        )

    private fun stringList(obj: Any?): List<String> =
        when (obj) {
            null -> emptyList()
            is List<*> -> obj.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            is String -> listOf(obj.trim()).filter { it.isNotEmpty() }
            else -> emptyList()
        }
}
