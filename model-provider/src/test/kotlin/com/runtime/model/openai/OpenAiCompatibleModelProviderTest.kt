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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenAiCompatibleModelProviderTest {

    private val provider = OpenAiCompatibleModelProvider()

    @Test
    fun emptyMessagesRejected() = runBlocking {
        val r = provider.generate(
            testBinding("https://api.example.com/v1"),
            ModelGenerateRequest(messages = emptyList())
        )
        assertTrue(r is AppResult.Failure)
    }

    @Test
    fun invalidEndpointReturnsFailure() = runBlocking {
        // Calling a non-existent endpoint should return a structured failure, not throw
        val r = provider.generate(
            testBinding("http://localhost:1"),
            ModelGenerateRequest(
                messages = listOf(com.runtime.model.ModelMessage("user", "hi"))
            )
        )
        assertTrue(r is AppResult.Failure)
    }

    private fun testBinding(baseUrl: String): ResolvedModelBinding {
        val providerDef = ProviderDefinition(
            id = "p1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = "test",
            baseUrl = baseUrl,
            defaultHeaders = emptyMap(),
            capabilities = emptySet()
        )
        val model = ModelProfile(
            id = "m1", providerId = "p1", modelId = "gpt-test",
            displayName = "M", contextWindow = null, capabilities = emptySet()
        )
        val cred = CredentialRef(id = "c1", providerId = "p1", displayName = "c", secretAlias = "a")
        val binding = ModelBinding(
            id = "b1", targetType = BindingTargetType.GLOBAL, targetId = null,
            providerId = "p1", modelProfileId = "m1", credentialRefId = "c1", priority = 0
        )
        return ResolvedModelBinding(binding, providerDef, model, cred, "sk-test")
    }
}
