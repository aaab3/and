package com.runtime.model

import com.runtime.binding.BindingTargetType
import com.runtime.binding.CredentialRef
import com.runtime.binding.ModelBinding
import com.runtime.binding.ModelProfile
import com.runtime.binding.ProviderDefinition
import com.runtime.binding.ProviderType
import com.runtime.binding.ResolvedModelBinding
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.model.openai.OpenAiCompatibleModelProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultProviderRegistryTest {

    @Test
    fun returnsOpenAiByType() {
        val openAi = OpenAiCompatibleModelProvider(OkHttpClient())
        val registry = DefaultProviderRegistry(
            listOf(openAi, AnthropicLikeModelProvider(), GeminiLikeModelProvider())
        )
        val got = assertIs<AppResult.Success<ModelProvider>>(registry.getProvider(ProviderType.OPENAI_COMPATIBLE))
        assertEquals(ProviderType.OPENAI_COMPATIBLE, got.value.providerType)
    }

    @Test
    fun unknownTypeFailsWithNotFound() {
        val registry = DefaultProviderRegistry(
            listOf(OpenAiCompatibleModelProvider(OkHttpClient()))
        )
        val r = registry.getProvider(ProviderType.CUSTOM)
        assertTrue(r is AppResult.Failure)
        assertEquals(ErrorCodes.NOT_FOUND, (r as AppResult.Failure).error.code)
    }

    @Test
    fun listProvidersIncludesAllRegistered() {
        val registry = DefaultProviderRegistry(
            listOf(
                OpenAiCompatibleModelProvider(OkHttpClient()),
                AnthropicLikeModelProvider(),
                GeminiLikeModelProvider()
            )
        )
        assertEquals(3, registry.listProviders().size)
    }

    @Test
    fun anthropicSkeletonReturnsNotImplementedOnGenerate() = runBlocking {
        val p = AnthropicLikeModelProvider()
        val r = p.generate(fakeBinding(), ModelGenerateRequest(listOf(ModelMessage("user", "x"))))
        assertTrue(r is AppResult.Failure)
        assertEquals(ErrorCodes.NOT_IMPLEMENTED, (r as AppResult.Failure).error.code)
    }

    @Test
    fun geminiSkeletonReturnsNotImplementedOnGenerate() = runBlocking {
        val p = GeminiLikeModelProvider()
        val r = p.generate(fakeBinding(), ModelGenerateRequest(listOf(ModelMessage("user", "x"))))
        assertTrue(r is AppResult.Failure)
        assertEquals(ErrorCodes.NOT_IMPLEMENTED, (r as AppResult.Failure).error.code)
    }

    private fun fakeBinding(): ResolvedModelBinding {
        val provider = ProviderDefinition(
            id = "p",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = "n",
            baseUrl = "https://x/v1",
            defaultHeaders = emptyMap(),
            capabilities = emptySet()
        )
        val model = ModelProfile(
            id = "m",
            providerId = "p",
            modelId = "m",
            displayName = "d",
            contextWindow = null,
            capabilities = emptySet()
        )
        val cred = CredentialRef("c", "p", "d", "a")
        val binding = ModelBinding(
            "b",
            BindingTargetType.GLOBAL,
            null,
            "p",
            "m",
            "c",
            0
        )
        return ResolvedModelBinding(binding, provider, model, cred, "s")
    }
}
