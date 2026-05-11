package com.runtime.workflow

import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultWorkflowValidatorTest {

    private val parser = DefaultWorkflowParser()
    private val validator = DefaultWorkflowValidator()

    private suspend fun parseAndValidate(yaml: String): AppResult<Unit> {
        val p = parser.parse(yaml)
        if (p is AppResult.Failure) return p
        return validator.validate((p as AppResult.Success).value)
    }

    @Test
    fun acceptsLinearTemplateReturn() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: t
                type: template
                template: "hi"
                outputKey: a
              - id: r
                type: return
                outputKey: a
            """.trimIndent()
        assertIs<AppResult.Success<Unit>>(parseAndValidate(yaml))
    }

    @Test
    fun rejectsMissingTerminalReturn() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: t
                type: template
                template: "hi"
                outputKey: a
            """.trimIndent()
        assertTrue(parseAndValidate(yaml) is AppResult.Failure)
    }

    @Test
    fun rejectsDuplicateStepIds() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: t
                type: template
                template: "a"
                outputKey: x
              - id: t
                type: template
                template: "b"
                outputKey: y
              - id: r
                type: return
                outputKey: y
            """.trimIndent()
        assertTrue(parseAndValidate(yaml) is AppResult.Failure)
    }

    @Test
    fun rejectsDuplicateProducedOutputKeys() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: a
                type: template
                template: "1"
                outputKey: same
              - id: b
                type: template
                template: "2"
                outputKey: same
              - id: r
                type: return
                outputKey: same
            """.trimIndent()
        assertTrue(parseAndValidate(yaml) is AppResult.Failure)
    }

    @Test
    fun rejectsUnknownInputKey() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: m
                type: call_model
                inputKey: missing_key
                outputKey: out
              - id: r
                type: return
                outputKey: out
            """.trimIndent()
        assertTrue(parseAndValidate(yaml) is AppResult.Failure)
    }

    @Test
    fun acceptsCallModelAfterTemplateOutput() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: t
                type: template
                template: "x"
                outputKey: prompt
              - id: m
                type: call_model
                inputKey: prompt
                outputKey: ans
              - id: r
                type: return
                outputKey: ans
            """.trimIndent()
        assertIs<AppResult.Success<Unit>>(parseAndValidate(yaml))
    }

    @Test
    fun branchRejectsBackwardJump() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: first
                type: template
                template: "a"
                outputKey: x
              - id: b
                type: branch
                inputKey: x
                cases:
                  - equals: "1"
                    nextStepId: first
                defaultNextStepId: last
              - id: mid
                type: template
                template: "m"
                outputKey: y
              - id: last
                type: template
                template: "l"
                outputKey: z
              - id: r
                type: return
                outputKey: z
            """.trimIndent()
        assertTrue(parseAndValidate(yaml) is AppResult.Failure)
    }

    @Test
    fun branchValidWhenTargetsForward() = runBlocking {
        val yaml =
            """
            id: w
            name: W
            version: 1
            steps:
              - id: setup
                type: template
                template: "q"
                outputKey: q
              - id: route
                type: branch
                inputKey: q
                cases:
                  - equals: A
                    nextStepId: a
                defaultNextStepId: a
              - id: a
                type: template
                template: "a"
                outputKey: o
              - id: r
                type: return
                outputKey: o
            """.trimIndent()
        assertIs<AppResult.Success<Unit>>(parseAndValidate(yaml))
    }
}
