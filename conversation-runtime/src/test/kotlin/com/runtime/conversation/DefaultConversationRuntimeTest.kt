package com.runtime.conversation

import com.runtime.binding.BindingTargetType
import com.runtime.binding.DefaultBindingResolver
import com.runtime.binding.sqlite.BindingSqlitePersistence
import com.runtime.conversation.sqlite.SqliteConversationPersistence
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.model.AnthropicLikeModelProvider
import com.runtime.model.DefaultProviderRegistry
import com.runtime.model.GeminiLikeModelProvider
import com.runtime.model.openai.OpenAiCompatibleModelProvider
import com.runtime.secret.sqlite.SqliteSecretProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultConversationRuntimeTest {

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
    fun sendMessage_persistsUserThenAssistantOnProviderSuccess() = runBlocking {
        val convDb = Files.createTempFile("crt-conv", ".db").toAbsolutePath().toString()
        val bindDb = Files.createTempFile("crt-bind", ".db").toAbsolutePath().toString()
        val secDb = Files.createTempFile("crt-sec", ".db").toAbsolutePath().toString()
        val convPersistence = SqliteConversationPersistence(convDb)
        val bindPersistence = BindingSqlitePersistence(bindDb)
        val secrets = SqliteSecretProvider(secDb)
        try {
            val baseUrl = server.url("/v1/").toString().trimEnd('/')
            seedBindingAndSecret(bindPersistence, secrets, baseUrl)

            val resolver = DefaultBindingResolver(
                bindPersistence.modelBindingRepository,
                bindPersistence.providerDefinitionRepository,
                bindPersistence.modelProfileRepository,
                bindPersistence.credentialRefRepository,
                secrets
            )
            val registry = DefaultProviderRegistry(
                listOf(
                    OpenAiCompatibleModelProvider(OkHttpClient()),
                    AnthropicLikeModelProvider(),
                    GeminiLikeModelProvider()
                )
            )
            val runtime = DefaultConversationRuntime(convPersistence.store, resolver, registry)

            val created = assertIs<AppResult.Success<Conversation>>(
                convPersistence.store.createConversation("ws-1", "t")
            )
            val conv = created.value

            val body =
                """
                {
                  "id": "cmpl-1",
                  "choices": [{
                    "message": { "role": "assistant", "content": "OK from model" },
                    "finish_reason": "stop"
                  }],
                  "usage": { "prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3 }
                }
                """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result = assertIs<AppResult.Success<SendMessageResult>>(
                runtime.sendMessage(
                    SendMessageRequest(
                        workspaceId = "ws-1",
                        conversationId = conv.id,
                        userMessage = "hello there"
                    )
                )
            )
            assertEquals("OK from model", result.value.providerResult.text)
            assertEquals(MessageRole.USER, result.value.userMessage.role)
            assertEquals("hello there", result.value.userMessage.content)
            assertEquals(MessageRole.ASSISTANT, result.value.assistantMessage.role)
            assertEquals("OK from model", result.value.assistantMessage.content)

            val hist = assertIs<AppResult.Success<List<ConversationMessage>>>(
                convPersistence.store.loadMessageHistory(conv.id)
            )
            assertEquals(2, hist.value.size)
            assertEquals("hello there", hist.value[0].content)
            assertEquals("OK from model", hist.value[1].content)
        } finally {
            convPersistence.close()
            bindPersistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(convDb))
            Files.deleteIfExists(java.nio.file.Paths.get(bindDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secDb))
        }
    }

    @Test
    fun sendMessage_keepsUserWhenProviderFails() = runBlocking {
        val convDb = Files.createTempFile("crt-conv2", ".db").toAbsolutePath().toString()
        val bindDb = Files.createTempFile("crt-bind2", ".db").toAbsolutePath().toString()
        val secDb = Files.createTempFile("crt-sec2", ".db").toAbsolutePath().toString()
        val convPersistence = SqliteConversationPersistence(convDb)
        val bindPersistence = BindingSqlitePersistence(bindDb)
        val secrets = SqliteSecretProvider(secDb)
        try {
            val baseUrl = server.url("/v1/").toString().trimEnd('/')
            seedBindingAndSecret(bindPersistence, secrets, baseUrl)

            val resolver = DefaultBindingResolver(
                bindPersistence.modelBindingRepository,
                bindPersistence.providerDefinitionRepository,
                bindPersistence.modelProfileRepository,
                bindPersistence.credentialRefRepository,
                secrets
            )
            val registry = DefaultProviderRegistry(
                listOf(OpenAiCompatibleModelProvider(OkHttpClient()), AnthropicLikeModelProvider(), GeminiLikeModelProvider())
            )
            val runtime = DefaultConversationRuntime(convPersistence.store, resolver, registry)

            val conv = assertIs<AppResult.Success<Conversation>>(
                convPersistence.store.createConversation("ws-1", null)
            ).value

            server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

            val fail = runtime.sendMessage(
                SendMessageRequest(workspaceId = "ws-1", conversationId = conv.id, userMessage = "x")
            )
            assertTrue(fail is AppResult.Failure)

            val hist = assertIs<AppResult.Success<List<ConversationMessage>>>(
                convPersistence.store.loadMessageHistory(conv.id)
            )
            assertEquals(1, hist.value.size)
            assertEquals(MessageRole.USER, hist.value[0].role)
        } finally {
            convPersistence.close()
            bindPersistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(convDb))
            Files.deleteIfExists(java.nio.file.Paths.get(bindDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secDb))
        }
    }

    @Test
    fun sendMessage_failsWhenConversationMissing() = runBlocking {
        val convDb = Files.createTempFile("crt-conv3", ".db").toAbsolutePath().toString()
        val bindDb = Files.createTempFile("crt-bind3", ".db").toAbsolutePath().toString()
        val secDb = Files.createTempFile("crt-sec3", ".db").toAbsolutePath().toString()
        val convPersistence = SqliteConversationPersistence(convDb)
        val bindPersistence = BindingSqlitePersistence(bindDb)
        val secrets = SqliteSecretProvider(secDb)
        try {
            seedBindingAndSecret(bindPersistence, secrets, server.url("/v1/").toString().trimEnd('/'))
            val resolver = DefaultBindingResolver(
                bindPersistence.modelBindingRepository,
                bindPersistence.providerDefinitionRepository,
                bindPersistence.modelProfileRepository,
                bindPersistence.credentialRefRepository,
                secrets
            )
            val registry = DefaultProviderRegistry(listOf(OpenAiCompatibleModelProvider(OkHttpClient())))
            val runtime = DefaultConversationRuntime(convPersistence.store, resolver, registry)
            val r = runtime.sendMessage(
                SendMessageRequest(workspaceId = "ws-1", conversationId = "no-such", userMessage = "a")
            )
            assertTrue(r is AppResult.Failure)
            assertEquals(ErrorCodes.NOT_FOUND, (r as AppResult.Failure).error.code)
        } finally {
            convPersistence.close()
            bindPersistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(convDb))
            Files.deleteIfExists(java.nio.file.Paths.get(bindDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secDb))
        }
    }

    private suspend fun seedBindingAndSecret(
        bindPersistence: BindingSqlitePersistence,
        secrets: SqliteSecretProvider,
        providerBaseUrl: String
    ) {
        assertIs<AppResult.Success<Unit>>(secrets.putSecret("alias-key", "sk-test"))
        val now = 1L
        bindPersistence.withConnection { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    INSERT INTO provider_definitions
                    ("id","providerType","name","baseUrl","defaultHeadersJson","capabilitiesJson","createdAtEpochMs","updatedAtEpochMs")
                    VALUES ('p1','OPENAI_COMPATIBLE','P','$providerBaseUrl','{}','[]',$now,$now)
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    INSERT INTO credential_refs
                    ("id","providerId","displayName","secretAlias","createdAtEpochMs","updatedAtEpochMs")
                    VALUES ('c1','p1','C','alias-key',$now,$now)
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    INSERT INTO model_profiles
                    ("id","providerId","modelId","displayName","contextWindow","capabilitiesJson","createdAtEpochMs","updatedAtEpochMs")
                    VALUES ('m1','p1','gpt-test','M',4096,'[]',$now,$now)
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    INSERT INTO model_bindings
                    ("id","targetType","targetId","providerId","modelProfileId","credentialRefId","priority","createdAtEpochMs","updatedAtEpochMs")
                    VALUES ('b1','WORKSPACE','ws-1','p1','m1','c1',10,$now,$now)
                    """.trimIndent()
                )
            }
        }
    }
}
