package com.runtime.workflow

import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultWorkflowParserTest {

    private val parser = DefaultWorkflowParser()

    private val minimalYaml =
        """
        id: summarize_with_knowledge
        name: Summarize With Knowledge
        version: 0.1.0
        steps:
          - id: build_query
            type: template
            template: |
              User question: {{inputs.user_question}}
            outputKey: query_text

          - id: retrieve_context
            type: retrieve_knowledge
            queryKey: query_text
            outputKey: retrieved_context

          - id: call_model
            type: call_model
            inputKey: final_prompt
            outputKey: final_answer
            toolNames: [a, b]

          - id: route
            type: branch
            inputKey: decision_result
            cases:
              - equals: USE_TOOL
                nextStepId: fetch_web
            defaultNextStepId: answer_directly

          - id: fetch_web
            type: call_tool
            toolName: web_fetch
            inputKey: decision_result
            outputKey: fetched_result

          - id: return_result
            type: return
            outputKey: final_answer
        """.trimIndent()

    @Test
    fun parsesP0Steps() = runBlocking {
        val r = assertIs<AppResult.Success<ParsedWorkflowDefinition>>(parser.parse(minimalYaml))
        assertEquals("summarize_with_knowledge", r.value.id)
        assertEquals(6, r.value.steps.size)
        assertTrue(r.value.steps[0] is TemplateStep)
        assertTrue(r.value.steps[1] is RetrieveKnowledgeStep)
        assertTrue(r.value.steps[2] is CallModelStep)
        assertEquals(listOf("a", "b"), (r.value.steps[2] as CallModelStep).toolNames)
        assertTrue(r.value.steps[3] is BranchStep)
        assertEquals(1, (r.value.steps[3] as BranchStep).cases.size)
        assertTrue(r.value.steps[4] is CallToolStep)
        assertTrue(r.value.steps[5] is ReturnStep)
    }

    @Test
    fun unsupportedStepTypeFails() = runBlocking {
        val yaml =
            """
            id: x
            name: x
            version: 1
            steps:
              - id: bad
                type: foreach
                outputKey: o
            """.trimIndent()
        val r = parser.parse(yaml)
        assertTrue(r is AppResult.Failure)
    }

    @Test
    fun invalidYamlFails() = runBlocking {
        val r = parser.parse("not: yaml: [[")
        assertTrue(r is AppResult.Failure)
    }
}
