package com.runtime.tool

import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultToolRegistryTest {

    private fun echoHandler(name: String = "echo") = object : ToolHandler {
        override val manifest = ToolManifest(
            name = name,
            description = "echo",
            inputSchemaJson = "{}",
            outputSchemaJson = "{}"
        )

        override suspend fun execute(request: ToolExecutionRequest): com.runtime.core.AppResult<ToolExecutionResult> =
            com.runtime.core.AppResult.Success(ToolExecutionResult(outputJson = request.argumentsJson))
    }

    @Test
    fun registerGetAndList() {
        val reg = DefaultToolRegistry()
        assertIs<AppResult.Success<Unit>>(reg.register(echoHandler("a")))
        assertIs<AppResult.Success<Unit>>(reg.register(echoHandler("b")))
        val h = assertIs<AppResult.Success<ToolHandler>>(reg.getTool("a"))
        assertEquals("a", h.value.manifest.name)
        assertEquals(2, reg.listTools().size)
        assertEquals(listOf("a", "b"), reg.listTools().map { it.name })
    }

    @Test
    fun duplicateRegisterFails() {
        val reg = DefaultToolRegistry()
        val h = echoHandler("same")
        assertIs<AppResult.Success<Unit>>(reg.register(h))
        assertTrue(reg.register(h) is AppResult.Failure)
    }

    @Test
    fun unknownToolFails() {
        val reg = DefaultToolRegistry()
        assertTrue(reg.getTool("nope") is AppResult.Failure)
    }

    @Test
    fun executeViaRegistry() = runBlocking {
        val reg = DefaultToolRegistry()
        reg.register(echoHandler())
        val handler = assertIs<AppResult.Success<ToolHandler>>(reg.getTool("echo")).value
        val out = assertIs<AppResult.Success<ToolExecutionResult>>(
            handler.execute(ToolExecutionRequest(toolName = "echo", argumentsJson = """{"x":1}"""))
        )
        assertEquals("""{"x":1}""", out.value.outputJson)
    }
}
