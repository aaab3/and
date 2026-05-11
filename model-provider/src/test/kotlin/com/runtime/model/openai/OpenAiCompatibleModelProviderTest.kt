package com.runtime.model.openai

import com.runtime.binding.BindingTargetType
import com.runtime.binding.CredentialRef
import com.runtime.binding.ModelBinding
import com.runtime.binding.ModelProfile
import com.runtime.binding.ProviderDefinition
import com.runtime.binding.ProviderType
import com.runtime.binding.ResolvedModelBinding
import com.runtime.core.AppResult
import com.runtime.model.ModelGenerateRequest
import com.runtime.model.ModelMessage
import com.runtime.model.ModelToolSpec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenAiCompatibleModelProviderTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resolveChatCompletionsUrl_appendsPath() {
        assertEquals(
            "https://api.example/v1/chat/completions",
            OpenAiCompatibleModelProvider.resolveChatCompletionsUrl("https://api.example/")
        )
        assertEquals(
            "https://api.example/v1/chat/completions",
            OpenAiCompatibleModelProvider.resolveChatCompletionsUrl("https://api.example/v1")
        )
        assertEquals(
            "https://api.example/v1/chat/completions",
            OpenAiCompatibleModelProvider.resolveChatCompletionsUrl("https://api.example/v1/chat/completions")
        )
    }

    @Test
    fun parsesTextUsageFinishReasonAndId() = runBlocking {
        val body =
            """
            {
              "id": "chatcmpl-test",
              "choices": [{
                "index": 0,
                "message": { "role": "assistant", "content": "Hello" },
                "finish_reason": "stop"
              }],
              "usage": { "prompt_tokens": 3, "completion_tokens": 5, "total_tokens": 8 }
            }
            """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val provider = OpenAiCompatibleModelProvider(OkHttpClient())
        val binding = testBinding(serverUrlV1())
        val r = assertIs<AppResult.Success<com.runtime.model.ModelGenerateResult>>(
            provider.generate(
                binding,
                ModelGenerateRequest(messages = listOf(ModelMessage("user", "hi")))
            )
        )
        assertEquals("Hello", r.value.text)
        assertEquals("stop", r.value.finishReason)
        assertEquals("chatcmpl-test", r.value.rawProviderMessageId)
        assertEquals(3, r.value.usage?.inputTokens)
        assertEquals(5, r.value.usage?.outputTokens)
        assertEquals(8, r.value.usage?.totalTokens)
        assertTrue(r.value.toolCalls.isEmpty())

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/v1/chat/completions"))
        assertEquals("Bearer sk-secret", req.getHeader("Authorization"))
    }

    @Test
    fun parsesToolCalls() = runBlocking {
        val body =
            """
            {
              "id": "x",
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": "",
                  "tool_calls": [
                    {
                      "id": "call_1",
                      "type": "function",
                      "function": { "name": "fn", "arguments": "{\"a\":1}" }
                    }
                  ]
                },
                "finish_reason": "tool_calls"
              }],
              "usage": {}
            }
            """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val provider = OpenAiCompatibleModelProvider(OkHttpClient())
        val r = assertIs<AppResult.Success<com.runtime.model.ModelGenerateResult>>(
            provider.generate(
                testBinding(serverUrlV1()),
                ModelGenerateRequest(
                    messages = listOf(ModelMessage("user", "go")),
                    tools = listOf(
                        ModelToolSpec(
                            name = "fn",
                            description = "d",
                            inputSchemaJson = """{"type":"object","properties":{"a":{"type":"number"}}}"""
                        )
                    )
                )
            )
        )
        assertEquals("", r.value.text)
        assertEquals(1, r.value.toolCalls.size)
        assertEquals("call_1", r.value.toolCalls[0].id)
        assertEquals("fn", r.value.toolCalls[0].name)
        assertEquals("{\"a\":1}", r.value.toolCalls[0].argumentsJson)

        val req = server.takeRequest()
        assertTrue(req.body.readUtf8().contains("\"tools\""))
    }

    @Test
    fun httpErrorReturnsFailure() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}""")
        )
        val provider = OpenAiCompatibleModelProvider(OkHttpClient())
        val r = provider.generate(
            testBinding(serverUrlV1()),
            ModelGenerateRequest(messages = listOf(ModelMessage("user", "x")))
        )
        assertTrue(r is AppResult.Failure)
    }

    @Test
    fun emptyMessagesRejected() = runBlocking {
        val provider = OpenAiCompatibleModelProvider(OkHttpClient())
        val r = provider.generate(
            testBinding(serverUrlV1()),
            ModelGenerateRequest(messages = emptyList())
        )
        assertTrue(r is AppResult.Failure)
    }

    private fun serverUrlV1(): String = server.url("/v1/").toString().trimEnd('/')

    private fun testBinding(baseUrl: String): ResolvedModelBinding {
        val provider = ProviderDefinition(
            id = "p1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = "test",
            baseUrl = baseUrl,
            defaultHeaders = mapOf("X-Test" to "1"),
            capabilities = emptySet()
        )
        val model = ModelProfile(
            id = "m1",
            providerId = "p1",
            modelId = "gpt-test",
            displayName = "M",
            contextWindow = null,
            capabilities = emptySet()
        )
        val cred = CredentialRef(
            id = "c1",
            providerId = "p1",
            displayName = "c",
            secretAlias = "alias"
        )
        val binding = ModelBinding(
            id = "b1",
            targetType = BindingTargetType.GLOBAL,
            targetId = null,
            providerId = "p1",
            modelProfileId = "m1",
            credentialRefId = "c1",
            priority = 0
        )
        return ResolvedModelBinding(
            binding = binding,
            provider = provider,
            model = model,
            credentialRef = cred,
            secretValue = "sk-secret"
        )
    }
}
