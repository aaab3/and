package com.runtime.workflow.executor

import com.runtime.binding.BindingResolveRequest
import com.runtime.binding.BindingResolver
import com.runtime.binding.BindingTargetType
import com.runtime.binding.CredentialRef
import com.runtime.binding.ModelBinding
import com.runtime.binding.ModelProfile
import com.runtime.binding.ProviderDefinition
import com.runtime.binding.ProviderType
import com.runtime.binding.ResolvedModelBinding
import com.runtime.core.AppResult
import com.runtime.knowledge.DefaultKnowledgeRetriever
import com.runtime.knowledge.EmbeddedChunk
import com.runtime.knowledge.HashEmbeddingProvider
import com.runtime.knowledge.InMemoryVectorIndex
import com.runtime.model.ModelGenerateRequest
import com.runtime.model.ModelProvider
import com.runtime.model.ModelGenerateResult
import com.runtime.permission.DefaultPermissionGate
import com.runtime.tool.DefaultToolRegistry
import com.runtime.tool.ToolExecutionRequest
import com.runtime.tool.ToolHandler
import com.runtime.tool.ToolManifest
import com.runtime.workflow.BranchStep
import com.runtime.workflow.BranchCase
import com.runtime.workflow.CallModelStep
import com.runtime.workflow.CallToolStep
import com.runtime.workflow.DefaultWorkflowParser
import com.runtime.workflow.DefaultWorkflowValidator
import com.runtime.workflow.ParsedWorkflowDefinition
import com.runtime.workflow.RetrieveKnowledgeStep
import com.runtime.workflow.ReturnStep
import com.runtime.workflow.TemplateStep
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultWorkflowExecutorTest {

    private val validator = DefaultWorkflowValidator()
    private val parser = DefaultWorkflowParser()

    private val fakeBinding = ResolvedModelBinding(
        binding = ModelBinding(
            "b", BindingTargetType.GLOBAL, null, "p", "m", "c", 0
        ),
        provider = ProviderDefinition(
            "p", ProviderType.OPENAI_COMPATIBLE, "n", "https://x/v1", emptyMap(), emptySet()
        ),
        model = ModelProfile("m", "p", "gpt", "M", null, emptySet()),
        credentialRef = CredentialRef("c", "p", "d", "a"),
        secretValue = "sk"
    )

    private class FakeBindingResolver(private val value: ResolvedModelBinding) : BindingResolver {
        override suspend fun resolve(request: BindingResolveRequest): AppResult<ResolvedModelBinding> =
            AppResult.Success(value)
    }

    private class StubModelProvider(private val reply: String) : ModelProvider {
        override val providerType = ProviderType.OPENAI_COMPATIBLE
        override suspend fun generate(
            binding: ResolvedModelBinding,
            request: ModelGenerateRequest
        ): AppResult<ModelGenerateResult> =
            AppResult.Success(ModelGenerateResult(text = reply))
    }

    private fun executor(
        model: ModelProvider = StubModelProvider("MODEL"),
        tools: DefaultToolRegistry = DefaultToolRegistry()
    ) = DefaultWorkflowExecutor(
        workflowValidator = validator,
        bindingResolver = FakeBindingResolver(fakeBinding),
        providerRegistry = com.runtime.model.DefaultProviderRegistry(
            listOf(model, com.runtime.model.AnthropicLikeModelProvider(), com.runtime.model.GeminiLikeModelProvider())
        ),
        toolRegistry = tools,
        permissionGate = DefaultPermissionGate(),
        knowledgeRetriever = DefaultKnowledgeRetriever(HashEmbeddingProvider(64), InMemoryVectorIndex())
    )

    @Test
    fun templateAndReturn() = runBlocking {
        val wf = ParsedWorkflowDefinition(
            id = "w",
            name = "w",
            version = "1",
            steps = listOf(
                TemplateStep("t", "Hello {{inputs.name}}", "greet"),
                ReturnStep("r", "greet")
            )
        )
        val ex = executor()
        val r = assertIs<AppResult.Success<WorkflowExecutionResult>>(
            ex.execute(
                WorkflowExecutionRequest(
                    workflow = wf,
                    workspaceId = "ws",
                    inputs = mapOf("name" to "World")
                )
            )
        )
        assertEquals("Hello World", r.value.output.trim())
        assertEquals("Hello World", r.value.context["greet"])
    }

    @Test
    fun callModelWritesContext() = runBlocking {
        val wf = ParsedWorkflowDefinition(
            id = "w",
            name = "w",
            version = "1",
            steps = listOf(
                TemplateStep("t", "prompt", "p"),
                CallModelStep("m", "p", "ans", toolNames = emptyList()),
                ReturnStep("r", "ans")
            )
        )
        assertIs<AppResult.Success<Unit>>(validator.validate(wf))
        val r = assertIs<AppResult.Success<WorkflowExecutionResult>>(
            executor().execute(WorkflowExecutionRequest(wf, workspaceId = "ws"))
        )
        assertEquals("MODEL", r.value.output)
    }

    @Test
    fun callToolThroughRegistryAndGate() = runBlocking {
        val reg = DefaultToolRegistry()
        assertIs<AppResult.Success<Unit>>(
            reg.register(
                object : ToolHandler {
                    override val manifest = ToolManifest(
                        name = "double",
                        description = "d",
                        inputSchemaJson = "{}",
                        outputSchemaJson = "{}"
                    )
                    override suspend fun execute(request: ToolExecutionRequest) =
                        AppResult.Success(
                            com.runtime.tool.ToolExecutionResult(outputJson = """{"k":2}""")
                        )
                }
            )
        )
        val wf = ParsedWorkflowDefinition(
            id = "w",
            name = "w",
            version = "1",
            steps = listOf(
                TemplateStep("t", """{"x":1}""", "args"),
                CallToolStep("c", "double", "args", "out"),
                ReturnStep("r", "out")
            )
        )
        assertIs<AppResult.Success<Unit>>(validator.validate(wf))
        val r = assertIs<AppResult.Success<WorkflowExecutionResult>>(
            executor(tools = reg).execute(WorkflowExecutionRequest(wf, workspaceId = "ws"))
        )
        assertTrue(r.value.output.contains("k"))
    }

    @Test
    fun branchJumpsToSharedTarget() = runBlocking {
        val wf = ParsedWorkflowDefinition(
            id = "w",
            name = "w",
            version = "1",
            steps = listOf(
                TemplateStep("t0", "GO", "route"),
                BranchStep(
                    "b",
                    "route",
                    cases = listOf(BranchCase("GO", "join")),
                    defaultNextStepId = "join"
                ),
                TemplateStep("join", "done", "final"),
                ReturnStep("ret", "final")
            )
        )
        assertIs<AppResult.Success<Unit>>(validator.validate(wf))
        val r = assertIs<AppResult.Success<WorkflowExecutionResult>>(
            executor().execute(WorkflowExecutionRequest(wf, workspaceId = "ws"))
        )
        assertEquals("done", r.value.output.trim())
    }

    @Test
    fun retrieveKnowledgeUsesRetriever() = runBlocking {
        val index = InMemoryVectorIndex()
        val embedder = HashEmbeddingProvider(64)
        val docChunk = com.runtime.knowledge.DocumentChunk(
            id = "c1",
            documentId = "d1",
            text = "alpha beta gamma uniquephrase",
            index = 0,
            metadata = mapOf("sourcePath" to "/tmp/doc.txt")
        )
        val vec = assertIs<AppResult.Success<List<List<Float>>>>(
            embedder.embed(listOf(docChunk.text))
        ).value.first()
        assertIs<AppResult.Success<Unit>>(index.upsert(listOf(EmbeddedChunk(docChunk, vec))))
        val retriever = DefaultKnowledgeRetriever(embedder, index)
        val wf = ParsedWorkflowDefinition(
            id = "w",
            name = "w",
            version = "1",
            steps = listOf(
                TemplateStep("q", "uniquephrase", "query"),
                RetrieveKnowledgeStep("rk", "query", "ctx"),
                ReturnStep("r", "ctx")
            )
        )
        assertIs<AppResult.Success<Unit>>(validator.validate(wf))
        val ex = DefaultWorkflowExecutor(
            validator,
            FakeBindingResolver(fakeBinding),
            com.runtime.model.DefaultProviderRegistry(listOf(StubModelProvider("x"))),
            DefaultToolRegistry(),
            DefaultPermissionGate(),
            retriever
        )
        val r = assertIs<AppResult.Success<WorkflowExecutionResult>>(
            ex.execute(WorkflowExecutionRequest(wf, workspaceId = "ws"))
        )
        assertTrue(r.value.output.contains("uniquephrase"))
        assertTrue(r.value.output.contains("sourcePath="))
    }

    @Test
    fun invalidWorkflowFailsValidation() = runBlocking {
        val yaml =
            """
            id: bad
            name: bad
            version: 1
            steps:
              - id: only
                type: template
                template: "x"
                outputKey: o
            """.trimIndent()
        val wf = assertIs<AppResult.Success<ParsedWorkflowDefinition>>(parser.parse(yaml)).value
        val r = executor().execute(WorkflowExecutionRequest(wf, workspaceId = "ws"))
        assertTrue(r is AppResult.Failure)
    }
}
